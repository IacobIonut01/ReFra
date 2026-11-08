package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.render.video.AudioInfo
import com.dot.gallery.editor.render.video.UnsupportedVideoException
import com.dot.gallery.editor.render.video.VideoInfo
import kotlin.math.roundToInt
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_pix_fmt_desc_get
import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * Container probe shared by probe/frame/export: opens the resolved file with
 * an FFmpeg grabber and reports [VideoInfo] plus the stream facts the
 * decoder needs (colour space, range) through [FfmpegProbeResult].
 *
 * Scope gates (initial SDR 8-bit profile):
 * - exactly one decodable video stream, dimensions positive, ≤4096 per side
 *   and ≤ 8 847 360 px total;
 * - component depth ≤ 8 bit and no PQ/HLG transfer — HDR/10-bit is refused
 *   rather than silently tone-mapped;
 * - BT.2020 / P3 colour is refused (no colour-management in scope);
 * - audio absent/mono/stereo only, >2 channels refused;
 * - display rotation restricted to near-quarter-turns;
 * - positive finite duration, capped at 24 h;
 * - a cover-art-only "video" (ATTACHED_PIC) is refused when no real video
 *   stream exists, and a real one is selected explicitly when present.
 * Odd dimensions probe fine but are refused by the exporter.
 */
object FfmpegProbe {

    /**
     * Probes [source] (already resolved to a seekable local file). [reference]
     * is the caller's original opaque reference, stored verbatim in the asset.
     *
     * Stream selection follows the grabber's own choice — read back through
     * `getVideoStream()`/`getAudioStream()` after `start()` — except that an
     * attached picture can never be the editable stream: when the default
     * pick carries `AV_DISPOSITION_ATTACHED_PIC`, the first real video stream
     * is pinned and the grabber is reopened so rotation/metadata describe the
     * actual decoded stream.
     */
    fun probe(source: ResolvedVideoSource, reference: String): FfmpegProbeResult {
        FfmpegSupport.require()
        var grabber = openGrabber(source)
        try {
            val oc = grabber.formatContext
                ?: throw IllegalArgumentException(
                    "no format context: ${source.displayName}"
                )
            var videoIndex = grabber.videoStream
            if (videoIndex >= 0 &&
                (oc.streams(videoIndex)?.disposition() ?: 0) and
                AV_DISPOSITION_ATTACHED_PIC != 0
            ) {
                // Cover art is not editable video — reselect a real stream.
                val real = firstRealVideoStream(oc)
                    ?: throw UnsupportedVideoException(
                        "${source.displayName} has only cover art, no video"
                    )
                try {
                    grabber.stop()
                } finally {
                    grabber.release()
                }
                grabber = openGrabber(source, videoStream = real)
                videoIndex = real
            }
            return inspect(grabber, videoIndex, source, reference)
        } finally {
            try {
                grabber.stop()
            } finally {
                grabber.release()
            }
        }
    }

    /**
     * Probes an audio-bearing source for the audio-lane import path. Any
     * container with a usable audio stream qualifies — an MP4's embedded
     * track is a legitimate music source — but scope still applies: the
     * selected audio stream must exist, be mono or stereo, carry a positive
     * sample rate and a positive duration within the 24 h cap.
     */
    fun probeAudio(
        source: ResolvedVideoSource,
        reference: String
    ): FfmpegAudioProbeResult {
        FfmpegSupport.require()
        val grabber = FFmpegFrameGrabber(source.path)
        try {
            grabber.start()
        } catch (e: Exception) {
            grabber.release()
            throw IllegalArgumentException(
                "cannot open audio ${source.displayName}: ${e.message}", e
            )
        }
        try {
            val oc = grabber.formatContext
                ?: throw IllegalArgumentException(
                    "no format context: ${source.displayName}"
                )
            val audioIndex = grabber.audioStream
            if (audioIndex < 0) {
                throw UnsupportedVideoException(
                    "no audio stream in ${source.displayName}"
                )
            }
            val par = oc.streams(audioIndex)?.codecpar()
                ?: throw UnsupportedVideoException(
                    "unreadable audio stream in ${source.displayName}"
                )
            val channels = par.ch_layout()?.nb_channels() ?: 0
            if (channels < 1) {
                throw UnsupportedVideoException(
                    "audio stream reports no channels in ${source.displayName}"
                )
            }
            val rate = par.sample_rate()
            if (rate <= 0) {
                throw UnsupportedVideoException(
                    "audio stream reports no sample rate in " +
                        source.displayName
                )
            }
            val lengthUs = knownLengthUs(grabber)
            if (lengthUs <= 0L || lengthUs > MAX_DURATION_US) {
                throw UnsupportedVideoException(
                    "audio duration $lengthUs us is outside the supported range"
                )
            }
            val asset = MediaAsset.Audio(
                id = ASSET_ID,
                reference = reference,
                duration = EditorTime.fromMicroseconds(lengthUs),
                sampleRate = rate,
                channels = channels
            )
            return FfmpegAudioProbeResult(
                info = AudioInfo(
                    asset = asset,
                    displayName = source.displayName
                ),
                audioStreamIndex = audioIndex
            )
        } finally {
            try {
                grabber.stop()
            } finally {
                grabber.release()
            }
        }
    }

    /** Starts a grabber; on a failed start the instance is released. */
    private fun openGrabber(
        source: ResolvedVideoSource,
        videoStream: Int = -1
    ): FFmpegFrameGrabber {
        val grabber = FFmpegFrameGrabber(source.path)
        if (videoStream >= 0) grabber.videoStream = videoStream
        try {
            grabber.start()
        } catch (e: Exception) {
            grabber.release()
            throw IllegalArgumentException(
                "cannot open video ${source.displayName}: ${e.message}", e
            )
        }
        return grabber
    }

    /** First video stream that is not attached cover art, or null. */
    private fun firstRealVideoStream(oc: AVFormatContext): Int? {
        for (i in 0 until oc.nb_streams()) {
            val stream = oc.streams(i) ?: continue
            val par = stream.codecpar() ?: continue
            if (par.codec_type() == AVMEDIA_TYPE_VIDEO &&
                stream.disposition() and AV_DISPOSITION_ATTACHED_PIC == 0
            ) {
                return i
            }
        }
        return null
    }

    private fun inspect(
        grabber: FFmpegFrameGrabber,
        videoIndex: Int,
        source: ResolvedVideoSource,
        reference: String
    ): FfmpegProbeResult {
        val oc = grabber.formatContext
            ?: throw IllegalArgumentException("no format context: ${source.displayName}")
        // Only the streams the decoder will actually use count: the selected
        // video stream, and the selected audio stream (or none).
        val videoStream = if (videoIndex >= 0) oc.streams(videoIndex) else null
        val video = videoStream?.codecpar()
            ?: throw UnsupportedVideoException("no video stream in ${source.displayName}")
        val avg = videoStream.avg_frame_rate()
        val avgNum = avg?.num() ?: 0
        val avgDen = avg?.den() ?: 0
        val audioIndex = grabber.audioStream
        val audioParams: AVCodecParameters? =
            if (audioIndex >= 0) oc.streams(audioIndex)?.codecpar() else null

        val width = video.width()
        val height = video.height()
        if (width <= 0 || height <= 0 ||
            width > MAX_DIMENSION || height > MAX_DIMENSION ||
            width.toLong() * height > MAX_PIXELS
        ) {
            throw UnsupportedVideoException(
                "video ${width}x$height exceeds limits " +
                    "($MAX_DIMENSION max dimension, $MAX_PIXELS max pixels)"
            )
        }
        // Surround sound is downmixed by the stereo PCM reader, so the rest
        // of the pipeline only ever sees one or two channels.
        val audioChannels = (audioParams?.ch_layout()?.nb_channels() ?: 0)
            .coerceAtMost(MAX_CHANNELS)
        val audioRate = audioParams?.sample_rate()?.takeIf { it > 0 }

        val lengthUs = knownLengthUs(grabber)
        if (lengthUs <= 0L || lengthUs > MAX_DURATION_US) {
            throw UnsupportedVideoException(
                "video duration $lengthUs us is outside the supported range"
            )
        }
        val rotation = rotationDegrees(grabber.displayRotation)
        val frameRate = representableRate(avgNum, avgDen)

        val asset = MediaAsset.Video(
            id = ASSET_ID,
            reference = reference,
            size = PixelSize(width, height),
            duration = EditorTime.fromMicroseconds(lengthUs),
            frameRate = frameRate,
            hasAudio = audioChannels > 0,
            rotationDegrees = rotation
        )
        return FfmpegProbeResult(
            info = VideoInfo(
                asset = asset,
                displayName = source.displayName,
                audioSampleRate = if (audioChannels > 0) audioRate else null,
                audioChannels = audioChannels.takeIf { it > 0 }
            ),
            rawSize = PixelSize(width, height),
            colorspace = video.color_space(),
            colorRange = video.color_range(),
            colorPrimaries = video.color_primaries(),
            colorTrc = video.color_trc(),
            videoCodecId = video.codec_id(),
            audioCodecId = audioParams?.codec_id(),
            videoStreamIndex = videoIndex,
            audioStreamIndex = audioIndex,
            videoProfile = video.profile(),
            videoBitDepth = bitDepth(video.format()),
            avgFps = if (avgNum > 0 && avgDen > 0) {
                avgNum.toDouble() / avgDen
            } else 0.0
        )
    }

    /**
     * The container's duration, or for files that do not state one (a browser's
     * WebM recording, a live MKV capture) the end of the last packet found by
     * reading the file through. Reads no frames, so it is bounded by file size,
     * and it consumes the grabber, which the probe closes right after.
     */
    private fun knownLengthUs(grabber: FFmpegFrameGrabber): Long {
        val stated = grabber.lengthInTime
        if (stated > 0L) return stated
        val oc = grabber.formatContext ?: return stated
        var endUs = 0L
        while (true) {
            val packet = grabber.grabPacket() ?: break
            val stream = oc.streams(packet.stream_index()) ?: continue
            val base = stream.time_base() ?: continue
            if (base.den() == 0) continue
            val start = when {
                packet.pts() != AV_NOPTS_VALUE -> packet.pts()
                packet.dts() != AV_NOPTS_VALUE -> packet.dts()
                else -> continue
            }
            val end = start + packet.duration().coerceAtLeast(0L)
            endUs = maxOf(endUs, (end * 1_000_000.0 * base.num() / base.den()).toLong())
        }
        return endUs
    }

    /** Quarter-turns only; normalized to {0, 90, 180, 270} CCW. */
    private fun rotationDegrees(raw: Double): Int {
        if (!raw.isFinite()) {
            throw UnsupportedVideoException("invalid display rotation $raw")
        }
        val quarter = (raw / 90.0).roundToInt()
        if (kotlin.math.abs(raw - quarter * 90.0) > 0.01) {
            throw UnsupportedVideoException(
                "display rotation $raw° is not a quarter turn"
            )
        }
        return ((quarter * 90) % 360 + 360) % 360
    }

    /** Deepest component depth of [format], or 0 when indeterminate. */
    private fun bitDepth(format: Int): Int {
        val desc = av_pix_fmt_desc_get(format) ?: return 0
        var depth = 0
        for (i in 0 until desc.nb_components()) {
            depth = maxOf(depth, desc.comp(i).depth())
        }
        return depth
    }

    private fun representableRate(num: Int, den: Int): FrameRate? {
        if (num <= 0 || den <= 0) return null
        return try {
            FrameRate(num, den)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    const val ASSET_ID = "source"
    internal const val MAX_CHANNELS = 2
    internal const val MAX_DIMENSION = 4096
    internal const val MAX_PIXELS = 8_847_360L
    internal const val MAX_DURATION_US = 24L * 60 * 60 * 1_000_000
}

/**
 * When this container's timeline begins, in microseconds. Frame timestamps are
 * absolute, while seeks and the sequence timeline count from this point.
 */
internal fun FFmpegFrameGrabber.containerStartUs(): Long {
    val start = formatContext?.start_time() ?: return 0L
    return if (start == AV_NOPTS_VALUE || start < 0L) 0L else start
}
