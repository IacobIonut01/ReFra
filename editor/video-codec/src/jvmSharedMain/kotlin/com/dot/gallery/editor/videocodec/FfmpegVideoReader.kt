package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoFrame
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.ensureActive
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.av_frame_clone
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * Sequential frame cursor over one video source in RAW image mode — decoded
 * `AVFrame`s (in `frame.opaque`) keep their native planes so colour-correct
 * conversion happens in [FfmpegPixelConverter], not in the grabber's
 * BT.601-assuming BGR path.
 *
 * Contract:
 * - [frameAt] returns the frame whose display interval covers the requested
 *   time — i.e. the last frame at or before the target. The reported
 *   [VideoFrame.time] is the frame's actual PTS, never the request.
 * - Seeks land on `target - 1 s` and roll forward, so a target before the
 *   first frame yields the first frame and EOF yields the last.
 * - Forward reads reuse the cursor (current + one lookahead only);
 *   a backward read reseeks once.
 * - Coroutine cancellation is checked after every native grab; the grab
 *   itself is not interruptible.
 * - Not thread-safe — one caller thread, one clip-owner at a time.
 *
 * Hardware decoding: when [hwDecoderName] names a packaged `*_mediacodec`
 * decoder (chosen by [HardwareDecode]), it is pinned as the grabber's video
 * codec. Any failure — the decoder cannot open, the platform codec instance
 * is exhausted, or decode breaks mid-stream — fails over to the software
 * decoder once, reseeking to the last delivered position so playback/export
 * continues with no error and no lost frames. Hardware frames can also lose
 * their colour tags (a Tensor decoder reported SMPTE170M for a BT.709
 * stream); tags are restamped from the probed stream so an HLG source can
 * never silently skip tone mapping.
 */
class FfmpegVideoReader(
    private val path: String,
    private val converter: FfmpegPixelConverter,
    private val rotationDegrees: Int,
    private val videoStreamIndex: Int = -1,
    private val hwDecoderName: String? = null,
    grabberFactory: (String) -> FFmpegFrameGrabber = { FFmpegFrameGrabber(it) }
) : AutoCloseable {

    private val newGrabber = grabberFactory
    private var grabber: FFmpegFrameGrabber = grabberFactory(path)

    /** The held frame: cloned AVFrame + its PTS in microseconds. */
    private class Held(val frame: AVFrame, val ptsUs: Long)

    private var current: Held? = null
    private var next: Held? = null
    private var eof = false
    private var started = false

    /** Software-only after a hardware failure; the fallback is one-way. */
    private var softwareOnly = hwDecoderName == null

    /** PTS of the newest frame the cursor produced — the fallback reseek. */
    private var lastGrabbedUs = Long.MIN_VALUE

    /** A frame recovered during a software reseek, consumed by next grab. */
    private var pending: Held? = null

    /** Container start time: MPEG-TS and MPEG-PS timestamps do not begin at 0. */
    private var startOffsetUs = 0L

    /** Probed stream colour tags, captured at open for hardware restamping. */
    private var streamTrc = AVCOL_TRC_UNSPECIFIED
    private var streamPri = AVCOL_PRI_UNSPECIFIED
    private var streamCs = AVCOL_SPC_UNSPECIFIED
    private var streamRange = AVCOL_RANGE_UNSPECIFIED

    fun start() {
        if (started) return
        try {
            configure(grabber)
            grabber.start()
        } catch (t: Throwable) {
            if (softwareOnly) throw t
            VideoCodecEvents.fallback(
                "hardware decoder $hwDecoderName failed to open", t
            )
            softwareOnly = true
            releaseQuietly(grabber)
            grabber = newGrabber(path)
            configure(grabber)
            grabber.start()
        }
        startOffsetUs = grabber.containerStartUs()
        captureStreamTags()
        started = true
    }

    private fun configure(target: FFmpegFrameGrabber) {
        if (videoStreamIndex >= 0) target.videoStream = videoStreamIndex
        target.imageMode = FrameGrabber.ImageMode.RAW
        if (!softwareOnly && hwDecoderName != null) {
            target.videoCodecName = hwDecoderName
        }
    }

    /** Stream-level tags the hardware decoder may drop or misreport. */
    private fun captureStreamTags() {
        val index = if (videoStreamIndex >= 0) videoStreamIndex else grabber.videoStream
        val par = runCatching {
            grabber.formatContext?.streams(index)?.codecpar()
        }.getOrNull() ?: return
        streamTrc = par.color_trc()
        streamPri = par.color_primaries()
        streamCs = par.color_space()
        streamRange = par.color_range()
    }

    /**
     * The frame covering [at] (timeline-less: source PTS domain), pixels
     * converted upright and bounded to [maxEdge].
     */
    suspend fun frameAt(at: EditorTime, maxEdge: Int): VideoFrame {
        start()
        val targetUs = at.toMicrosecondsFloor()
        val held = advanceTo(targetUs)
        val pixels = converter.toSrgb(held.frame, maxEdge, rotationDegrees)
        return VideoFrame(
            time = EditorTime.fromMicroseconds(max(0L, held.ptsUs)),
            pixels = pixels
        )
    }

    /**
     * The raw frame covering [at] for the exporter — pixels stay native, the
     * caller runs [FfmpegPixelConverter.toYuv420p] on them.
     */
    suspend fun rawFrameAt(at: EditorTime): AVFrame {
        start()
        return advanceTo(at.toMicrosecondsFloor()).frame
    }

    /**
     * Advances the cursor so that [Held] whose interval contains [targetUs]
     * is current. Display intervals are inferred from the following frame's
     * PTS (EOF = open-ended).
     */
    private suspend fun advanceTo(targetUs: Long): Held {
        val cur = current
        // Backward target → reseek behind and roll forward. A target within the
        // seek margin simply reads from the top: no seek can land better, and
        // seeking an MPEG-TS to its own start can overshoot to the end.
        if (cur == null || targetUs < cur.ptsUs) {
            val seekUs = targetUs - SEEK_BACK_US
            try {
                if (seekUs > 0L) {
                    grabber.setVideoTimestamp(seekUs)
                } else if (cur != null || eof) {
                    grabber.restart()
                }
            } catch (t: Throwable) {
                if (softwareOnly) throw t
                // A seek the hardware path rejected is retried on a software
                // cursor, landing at the same target.
                reopenSoftware(null, seekUs.takeIf { it > 0L }, t)
            }
            releaseHeld(current); releaseHeld(next)
            current = null; next = null; eof = false
        }
        if (current == null) {
            current = grabNext() ?: throw java.io.EOFException(
                "video stream produced no frames"
            )
        }
        // Roll forward while the next frame's PTS is still <= target: the
        // held interval [current.pts, next.pts) must contain the target.
        while (true) {
            coroutineContext.ensureActive()
            val n = next ?: grabNext()?.also { next = it }
            if (n == null) return current!! // EOF — last frame holds
            if (n.ptsUs > targetUs) return current!!
            releaseHeld(current)
            current = n
            next = null
        }
    }

    /** Grabs the next decoded frame and clones it (grabber owns the raw one). */
    private fun grabNext(): Held? {
        pending?.let { pending = null; return it }
        val frame = try {
            grabber.grabImage()
        } catch (t: Throwable) {
            if (softwareOnly) throw t
            // Mid-stream hardware failure: reopen in software and resume
            // after the last delivered frame, invisible to the caller.
            reopenSoftware(lastGrabbedUs, null, t)
            return grabNext()
        }
        val opaque = frame?.opaque as? AVFrame ?: run {
            eof = true
            return null
        }
        val clone = av_frame_clone(opaque)
            ?: throw IllegalStateException("av_frame_clone failed")
        if (!softwareOnly) restampStreamTags(clone)
        val ptsUs = frame.timestamp - startOffsetUs
        lastGrabbedUs = ptsUs
        return Held(clone, ptsUs)
    }

    /**
     * Reopens the file in software after a hardware failure. With a
     * [resumeAfterUs] position the cursor decodes forward past every frame
     * already delivered so the next grab returns the true successor;
     * [thenSeekUs] replays a failed seek target on the fresh cursor. Either
     * way playback and export continue at the same position, invisible to
     * the caller.
     */
    private fun reopenSoftware(
        resumeAfterUs: Long?,
        thenSeekUs: Long?,
        cause: Throwable
    ) {
        softwareOnly = true
        VideoCodecEvents.fallback(
            "hardware decoder $hwDecoderName failed mid-stream; " +
                "continuing in software", cause
        )
        releaseQuietly(grabber)
        grabber = newGrabber(path)
        configure(grabber)
        grabber.start()
        startOffsetUs = grabber.containerStartUs()
        releaseHeld(pending); pending = null
        when {
            thenSeekUs != null -> grabber.setVideoTimestamp(thenSeekUs)
            resumeAfterUs != null && resumeAfterUs != Long.MIN_VALUE -> {
                val seekUs = resumeAfterUs - SEEK_BACK_US
                if (seekUs > 0L) grabber.setVideoTimestamp(seekUs)
                // A seek lands on the nearest keyframe at or before the
                // target — roll forward dropping frames already seen.
                while (true) {
                    val frame = grabber.grabImage() ?: break
                    val opaque = frame.opaque as? AVFrame ?: break
                    if (frame.timestamp - startOffsetUs > resumeAfterUs) {
                        val clone = av_frame_clone(opaque)
                        if (clone != null) {
                            pending = Held(clone, frame.timestamp - startOffsetUs)
                            lastGrabbedUs = pending!!.ptsUs
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * Reapplies the stream's colour tags to a hardware-decoded frame:
     * unspecified fields take the stream's, and a stream tagged HDR wins
     * whenever the decoder reported a non-HDR transfer — otherwise the
     * tone map in [FfmpegPixelConverter] would silently not run.
     */
    private fun restampStreamTags(frame: AVFrame) {
        if (frame.color_trc() == AVCOL_TRC_UNSPECIFIED) {
            frame.color_trc(streamTrc)
        }
        if (frame.color_primaries() == AVCOL_PRI_UNSPECIFIED) {
            frame.color_primaries(streamPri)
        }
        if (frame.colorspace() == AVCOL_SPC_UNSPECIFIED) {
            frame.colorspace(streamCs)
        }
        if (frame.color_range() == AVCOL_RANGE_UNSPECIFIED) {
            frame.color_range(streamRange)
        }
        val hdr = streamTrc == AVCOL_TRC_SMPTE2084 ||
            streamTrc == AVCOL_TRC_ARIB_STD_B67
        val frameHdr = frame.color_trc() == AVCOL_TRC_SMPTE2084 ||
            frame.color_trc() == AVCOL_TRC_ARIB_STD_B67
        if (hdr && !frameHdr) {
            frame.color_trc(streamTrc)
            frame.color_primaries(streamPri)
            frame.colorspace(streamCs)
            frame.color_range(streamRange)
        }
    }

    private fun releaseQuietly(target: FFmpegFrameGrabber) {
        try {
            target.stop()
        } catch (_: Throwable) {
        } finally {
            runCatching { target.release() }
        }
    }

    private fun releaseHeld(held: Held?) {
        held?.let { av_frame_free(it.frame) }
    }

    override fun close() {
        releaseHeld(current); current = null
        releaseHeld(next); next = null
        releaseHeld(pending); pending = null
        try {
            grabber.stop()
        } finally {
            grabber.release()
        }
    }

    private companion object {
        const val SEEK_BACK_US = 1_000_000L
    }
}
