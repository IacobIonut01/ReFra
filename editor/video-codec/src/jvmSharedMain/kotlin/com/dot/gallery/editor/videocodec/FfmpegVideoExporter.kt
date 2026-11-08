package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.render.video.UnsupportedVideoException
import com.dot.gallery.editor.render.video.VideoFrameCompositor
import java.io.File
import java.nio.ShortBuffer
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.av_color_primaries_name
import org.bytedeco.ffmpeg.global.avutil.av_color_space_name
import org.bytedeco.ffmpeg.global.avutil.av_color_transfer_name
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_BT709
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

/**
 * Encodes a validated single-source cut sequence to H.264/AAC MP4 through the
 * packaged FFmpeg runtime — no system ffmpeg, no RGB round-trip (frames move
 * RAW → contiguous YUV420P through [FfmpegPixelConverter] and straight into
 * `recordImage`, so the recorder's own BT.601-assuming swscale never runs).
 *
 * Export scope — anything else fails loudly, never dropped:
 * - the [SingleSourcePlan] shape (one track, one source, packed clips,
 *   neutral adjustments, ≤24 h);
 * - output cadence exactly 30 fps CFR;
 * - raw dimensions even (H.264 4:2:0 requirement); odd inputs preview fine;
 * - per-frame drift (resolution, depth, HDR transfer, colour tags changing
 *   mid-stream) is rejected before `sws_scale` — no silent downconversion.
 *
 * Audio is interleaved with video at every output frame (never all-then-all —
 * the muxer buffer would grow unboundedly); each frame writes exactly the
 * 48 kHz stereo sample window under it via [SingleSourcePlan.fillPcmWindow].
 *
 * The output file is staged by the caller and verified (reopened + probed)
 * before publication — an unverified export never reaches a receipt.
 */
/**
 * What [FfmpegVideoExporter.encode] actually wrote — the post-encode
 * verification needs it: a remuxed export keeps the source codec (HEVC
 * stays HEVC) and can carry splice slack in its duration.
 */
class VideoExportReport(
    /** Codec of the muxed video stream — H.264 after re-encode, the
     * source's codec after a packet remux. */
    val videoCodecId: Int,
    /** Extra output duration introduced by remux splice shifts, in ticks. */
    val slackTicks: Long
)

class FfmpegVideoExporter(
    private val resolver: VideoSourceResolver
) {

    /** Shared linear-light compositor — identical math to the preview. */
    private val compositor = VideoFrameCompositor()

    /**
     * Encodes [sequence] into [outputFile] (a staging path the caller owns).
     * Cancellable between frames; a cancel or failure leaves a partial file
     * the caller deletes. Progress reaches 0.9 after the last frame — the
     * remaining 0.1 belongs to verification and publication.
     *
     * Two paths, one contract: the [SingleSourcePlan] fast path passes
     * decoded frames straight through (byte/behaviour identical to the
     * pre-layers encoder); anything else — extra sources, extra tracks, a
     * non-identity clip recipe — is composited per output frame through the
     * shared [com.dot.gallery.editor.render.video.VideoFrameCompositor] so
     * an overlay/transform/blend is baked, never dropped. In the composited
     * path the canvas is [VideoSequence.size] (even, required) already
     * upright — no display rotation is written — and the audio window is
     * the shared mixer's output: Main-lane embedded sound at unity plus
     * every audible audio lane, silence elsewhere.
     */
    suspend fun encode(
        sequence: VideoSequence,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): VideoExportReport {
        require(sequence.frameRate == OUTPUT_FRAME_RATE) {
            "output frame rate must be 30 fps (is ${sequence.frameRate})"
        }
        if (avcodec_find_encoder_by_name(OPENH264) == null) {
            throw UnsupportedVideoException("libopenh264 encoder is unavailable")
        }
        if (SingleSourcePlan.isEligible(sequence)) {
            val plan = SingleSourcePlan.validate(sequence)
            val source = resolver.resolve(plan.source.reference)
            try {
                val probe = FfmpegProbe.probe(source, plan.source.reference)
                // The packet remux is preferred over any encode: it copies
                // the compressed stream, re-encoding only the GOP span
                // before an unaligned cut. Any limitation it hits throws and
                // falls back to the full encode — a remux failure never
                // ships a partial result.
                if (FfmpegRemuxer.supports(probe)) {
                    try {
                        val outcome = FfmpegRemuxer.export(
                            source, probe,
                            plan.clips.map { it.sourceRange },
                            outputFile, onProgress
                        )
                        return VideoExportReport(
                            outcome.videoCodecId, outcome.slackTicks
                        )
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        VideoCodecEvents.fallback(
                            "packet remux failed; re-encoding", t
                        )
                        outputFile.delete()
                    }
                }
                val raw = probe.rawSize
                if (raw.width % 2 != 0 || raw.height % 2 != 0) {
                    throw UnsupportedVideoException(
                        "H.264 requires even dimensions; source is " +
                            "${raw.width}x${raw.height}"
                    )
                }
                encodeWithHwRetry(plan, probe, source, outputFile, onProgress)
            } finally {
                source.close()
            }
            return VideoExportReport(AV_CODEC_ID_H264, 0L)
        }
        encodeCompositeWithHwRetry(sequence, outputFile, onProgress)
        return VideoExportReport(AV_CODEC_ID_H264, 0L)
    }

    /**
     * The re-encode attempt prefers a packaged hardware encoder when the
     * platform offers one ([HardwareEncode]); any failure — open refused or
     * a mid-encode break — retries the whole encode in software, the same
     * one-way failover the decoder already makes.
     */
    private suspend fun encodeWithHwRetry(
        plan: SingleSourcePlan,
        probe: FfmpegProbeResult,
        source: ResolvedVideoSource,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val hw = HardwareEncode.select(
            probe.rawSize.width, probe.rawSize.height,
            if (probe.avgFps > 0.0) probe.avgFps else OUTPUT_FPS
        )
        try {
            encode(plan, probe, source, outputFile, onProgress, hw)
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (hw == null) throw t
            VideoCodecEvents.fallback(
                "hardware encoder $hw failed; re-encoding in software", t
            )
            outputFile.delete()
            encode(plan, probe, source, outputFile, onProgress, null)
        }
    }

    private suspend fun encodeCompositeWithHwRetry(
        sequence: VideoSequence,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val canvas = sequence.size
        val hw = if (canvas.width % 2 == 0 && canvas.height % 2 == 0) {
            HardwareEncode.select(canvas.width, canvas.height, OUTPUT_FPS)
        } else null
        try {
            encodeComposite(sequence, outputFile, onProgress, hw)
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (hw == null) throw t
            VideoCodecEvents.fallback(
                "hardware encoder $hw failed; re-encoding in software", t
            )
            outputFile.delete()
            encodeComposite(sequence, outputFile, onProgress, null)
        }
    }

    /**
     * The composited path: resolve/probe every referenced source once, then
     * for each 30 fps output instant decode the active visual clips on
     * their own clocks, composite the stack onto the sequence canvas and
     * convert the straight-sRGB result to the recorder's YUV420P planes.
     */
    private suspend fun encodeComposite(
        sequence: VideoSequence,
        outputFile: File,
        onProgress: (Float) -> Unit,
        encoderName: String?
    ) {
        val plan = CompositeVideoPlan.validate(sequence)
        val canvas = sequence.size
        if (canvas.width % 2 != 0 || canvas.height % 2 != 0) {
            throw UnsupportedVideoException(
                "H.264 requires even dimensions; canvas is " +
                    "${canvas.width}x${canvas.height}"
            )
        }
        val totalFrames = SingleSourcePlan.ceilDiv(
            sequence.duration.ticks, TICKS_PER_FRAME
        )
        require(totalFrames <= Int.MAX_VALUE.toLong()) {
            "output frame count $totalFrames exceeds the recorder's index"
        }
        // One resolved handle per declared source — only clip-referenced
        // ones are opened for decode below.
        val resolved = LinkedHashMap<String, ResolvedVideoSource>(
            sequence.sources.size
        )
        try {
            for (asset in sequence.sources) {
                resolved[asset.id] = resolver.resolve(asset.reference)
            }
            for (asset in sequence.audioSources) {
                resolved[asset.id] = resolver.resolve(asset.reference)
            }
            val probes = HashMap<String, FfmpegProbeResult>(resolved.size)
            for (asset in sequence.sources) {
                probes[asset.id] = FfmpegProbe.probe(
                    resolved.getValue(asset.id), asset.reference
                )
            }
            val mainProbe = sequence.tracks
                .firstOrNull { it.kind == VideoTrackKind.Main }
                ?.clips?.firstOrNull()?.let { probes[it.sourceId] }
            // The output matrix is the Main source's when there is one —
            // the fixed fallback otherwise; tags describe the planes.
            val compositeConverter = FfmpegPixelConverter(
                mainProbe?.colorspace ?: AVCOL_SPC_UNSPECIFIED,
                mainProbe?.colorRange ?: AVCOL_RANGE_UNSPECIFIED,
                canvas.height
            )
            try {
                writeCompositeStreams(
                    plan, resolved, probes, compositeConverter,
                    totalFrames, outputFile, onProgress, encoderName
                )
            } finally {
                compositeConverter.close()
            }
        } finally {
            resolved.values.forEach { it.close() }
        }
    }

    private suspend fun writeCompositeStreams(
        plan: CompositeVideoPlan,
        resolved: Map<String, ResolvedVideoSource>,
        probes: Map<String, FfmpegProbeResult>,
        compositeConverter: FfmpegPixelConverter,
        totalFrames: Long,
        outputFile: File,
        onProgress: (Float) -> Unit,
        encoderName: String?
    ) {
        val sequence = plan.sequence
        val canvas = sequence.size
        val audioPlan = CompositeAudioPlan.validate(sequence)
        // Decoders only for sources a visual clip actually references,
        // keyed by source id; each carries its own probe-programmed
        // converter so colour and rotation are handled per source before
        // compositing.
        val referencedIds = sequence.tracks
            .flatMap { it.clips }
            .mapTo(LinkedHashSet()) { it.sourceId }
        val decoders = LinkedHashMap<String, SourceDecoder>(referencedIds.size)
        val pcmReaders = LinkedHashMap<String, FfmpegPcmReader>()
        try {
            for (id in referencedIds) {
                val probe = probes.getValue(id)
                val source = resolved.getValue(id)
                val converter = FfmpegPixelConverter(
                    probe.colorspace, probe.colorRange, probe.rawSize.height
                )
                decoders[id] = SourceDecoder(
                    probe, converter,
                    FfmpegVideoReader(
                        source.path, converter,
                        probe.info.asset.rotationDegrees,
                        probe.videoStreamIndex, HardwareDecode.select(probe)
                    )
                )
            }
            for (id in audioPlan.audioSourceIds) {
                val decoder = decoders[id]
                if (decoder != null) {
                    if (decoder.probe.info.hasAudio) {
                        pcmReaders[id] = FfmpegPcmReader(
                            resolved.getValue(id).path,
                            decoder.probe.audioStreamIndex
                        )
                    }
                } else {
                    // Audio-only lane source: probe selects its stream —
                    // the same reader shape as the Main lane's inputs.
                    val asset = sequence.audioSourceById(id) ?: continue
                    val probe = FfmpegProbe.probeAudio(
                        resolved.getValue(id), asset.reference
                    )
                    pcmReaders[id] = FfmpegPcmReader(
                        resolved.getValue(id).path, probe.audioStreamIndex
                    )
                }
            }
            val mainProbe = plan.mainClips.firstOrNull()?.let {
                probes[it.sourceId]
            }
            val recorder = FFmpegFrameRecorder(
                outputFile.absolutePath, canvas.width, canvas.height,
                if (pcmReaders.isEmpty()) 0 else 2
            )
            try {
                configureComposite(
                    recorder, compositeConverter, mainProbe, canvas, encoderName
                )
                recorder.start()
                val totalSamples = sequence.duration
                    .samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
                val compositedPixels = IntArray(canvas.width * canvas.height)
                for (n in 0 until totalFrames) {
                    coroutineContext.ensureActive()
                    val timelineT = sequence.frameRate.timeAtFrame(n)
                    val active = sequence.activeVisualClips(timelineT)
                    val only = active.singleOrNull()
                    val onlyDecoder = only?.let { decoders.getValue(it.sourceId) }
                    if (only != null && onlyDecoder != null && passesThroughUntouched(
                            only, onlyDecoder.probe, canvas,
                            onlyDecoder.converter.outputColorspace,
                            compositeConverter.outputColorspace
                        )
                    ) {
                        val raw = onlyDecoder.reader.rawFrameAt(
                            only.sourceRange.start + (timelineT - only.timelineStart)
                        )
                        validateExportFrame(raw, onlyDecoder.probe)
                        recorder.frameNumber = n.toInt()
                        recorder.recordImage(
                            canvas.width, canvas.height, Frame.DEPTH_UBYTE, 1,
                            canvas.width, AV_PIX_FMT_YUV420P,
                            onlyDecoder.converter.toYuv420p(raw, canvas.width, canvas.height)
                        )
                        if (pcmReaders.isNotEmpty()) {
                            writeCompositeAudioFrame(
                                audioPlan, pcmReaders, recorder, n, totalSamples
                            )
                        }
                        onProgress((n + 1).toFloat() / totalFrames * 0.9f)
                        continue
                    }
                    val layers = ArrayList<VideoFrameCompositor.Layer>(4)
                    for (clip in active) {
                        val decoder = decoders.getValue(clip.sourceId)
                        val sourceT = clip.sourceRange.start +
                            (timelineT - clip.timelineStart)
                        val raw = decoder.reader.rawFrameAt(sourceT)
                        validateExportFrame(raw, decoder.probe)
                        val pixels = decoder.converter.toSrgb(
                            raw, maxOf(
                                decoder.probe.rawSize.width,
                                decoder.probe.rawSize.height
                            ), decoder.probe.info.asset.rotationDegrees
                        )
                        layers += VideoFrameCompositor.Layer.of(clip, pixels)
                    }
                    val composited = compositor.composite(canvas, layers)
                    composited.copyPixelsInto(compositedPixels)
                    val yuv = compositeConverter.srgbToYuv420p(
                        compositedPixels, canvas.width, canvas.height
                    )
                    recorder.frameNumber = n.toInt()
                    recorder.recordImage(
                        canvas.width, canvas.height, Frame.DEPTH_UBYTE, 1,
                        canvas.width, AV_PIX_FMT_YUV420P, yuv
                    )
                    if (pcmReaders.isNotEmpty()) {
                        writeCompositeAudioFrame(
                            audioPlan, pcmReaders, recorder, n, totalSamples
                        )
                    }
                    onProgress((n + 1).toFloat() / totalFrames * 0.9f)
                }
                coroutineContext.ensureActive()
                recorder.stop()
            } finally {
                recorder.release()
            }
        } finally {
            decoders.values.forEach { it.close() }
            pcmReaders.values.forEach { it.close() }
        }
    }

    /**
     * Whether this output frame is just [clip]'s decoded picture: the only layer,
     * unedited, already canvas-sized and upright, on the matrix the output is
     * tagged with. Most of an audio-only edit is exactly this, and so is any
     * stretch of a timeline with no overlay above the main clip.
     */
    internal fun passesThroughUntouched(
        clip: VideoClip,
        probe: FfmpegProbeResult,
        canvas: PixelSize,
        sourceOutputColorspace: Int,
        canvasOutputColorspace: Int
    ): Boolean =
        clip.hasNeutralVisuals &&
            clip.adjustments == ColorAdjustments() &&
            probe.info.asset.rotationDegrees == 0 &&
            probe.rawSize == canvas &&
            sourceOutputColorspace == canvasOutputColorspace

    /** Per-source decode bundle: probe, converter and frame cursor. */
    private class SourceDecoder(
        val probe: FfmpegProbeResult,
        val converter: FfmpegPixelConverter,
        val reader: FfmpegVideoReader
    ) : AutoCloseable {
        override fun close() {
            reader.close()
            converter.close()
        }
    }

    /**
     * Writes the 48 kHz stereo window under output frame [n] — the shared
     * mixer's sum of every audible input (Main-lane embedded audio plus
     * the audio lanes), identical samples to what preview playback heard.
     */
    private suspend fun writeCompositeAudioFrame(
        plan: CompositeAudioPlan,
        readers: Map<String, FfmpegPcmReader>,
        recorder: FFmpegFrameRecorder,
        n: Long,
        totalSamples: Long
    ) {
        val rate = FfmpegPcmReader.OUTPUT_RATE
        val windowStart =
            plan.sequence.frameRate.timeAtFrame(n).samplesAtOrAfter(rate)
        val windowEnd = minOf(
            plan.sequence.frameRate.timeAtFrame(n + 1).samplesAtOrAfter(rate),
            totalSamples
        )
        if (windowEnd <= windowStart) return
        val out = ShortArray((windowEnd - windowStart).toInt() * 2)
        val frames = plan.fillPcmWindow(readers, windowStart, windowEnd, out)
        if (frames > 0) {
            recorder.recordSamples(
                FfmpegPcmReader.OUTPUT_RATE, 2, ShortBuffer.wrap(out, 0, frames * 2)
            )
        }
    }

    private fun configureComposite(
        recorder: FFmpegFrameRecorder,
        converter: FfmpegPixelConverter,
        mainProbe: FfmpegProbeResult?,
        canvas: PixelSize,
        encoderName: String?
    ) {
        recorder.format = "mp4"
        recorder.videoCodecName = encoderName ?: OPENH264
        recorder.videoCodec = AV_CODEC_ID_H264
        recorder.pixelFormat = AV_PIX_FMT_YUV420P
        recorder.frameRate = OUTPUT_FPS
        recorder.gopSize = OUTPUT_FPS.toInt()
        recorder.videoBitrate = (
            canvas.width.toLong() * canvas.height * OUTPUT_FPS.toLong() * 12 / 100
            ).coerceIn(1_000_000L, 30_000_000L).toInt()
        if (recorder.audioChannels > 0) {
            recorder.audioCodecName = "aac"
            recorder.audioCodec = AV_CODEC_ID_AAC
            recorder.sampleRate = FfmpegPcmReader.OUTPUT_RATE
            recorder.audioBitrate = 192_000
        }
        recorder.setOption("movflags", "+faststart")
        av_color_space_name(converter.outputColorspace)
            ?.getString()?.takeUnless { it == "unknown" }
            ?.let { recorder.setVideoOption("colorspace", it) }
        // Colour tags ride the Main source when it exists — the same rule
        // as the single-source path. Composited pixels are upright: no
        // display rotation is written.
        if (mainProbe != null) {
            av_color_primaries_name(exportPrimaries(mainProbe))
                ?.getString()?.takeUnless { it == "unknown" }
                ?.let { recorder.setVideoOption("color_primaries", it) }
            av_color_transfer_name(exportTransfer(mainProbe))
                ?.getString()?.takeUnless { it == "unknown" }
                ?.let { recorder.setVideoOption("color_trc", it) }
        }
        recorder.setVideoOption("color_range", "tv")
    }

    private suspend fun encode(
        plan: SingleSourcePlan,
        probe: FfmpegProbeResult,
        source: ResolvedVideoSource,
        outputFile: File,
        onProgress: (Float) -> Unit,
        encoderName: String?
    ) {
        val rawW = probe.rawSize.width
        val rawH = probe.rawSize.height
        val totalFrames = SingleSourcePlan.ceilDiv(
            plan.sequence.duration.ticks, TICKS_PER_FRAME
        )
        require(totalFrames <= Int.MAX_VALUE.toLong()) {
            "output frame count $totalFrames exceeds the recorder's index"
        }
        val converter = FfmpegPixelConverter(
            probe.colorspace, probe.colorRange, rawH
        )
        try {
            // Export never rotates pixels — the display matrix carries it.
            val reader = FfmpegVideoReader(
                source.path, converter, 0, probe.videoStreamIndex,
                HardwareDecode.select(probe)
            )
            try {
                val pcm = if (probe.info.hasAudio) {
                    FfmpegPcmReader(source.path, probe.audioStreamIndex)
                } else {
                    null
                }
                try {
                    writeStreams(
                        plan, probe, converter, reader, pcm,
                        rawW, rawH, totalFrames, outputFile, onProgress,
                        encoderName
                    )
                } finally {
                    pcm?.close()
                }
            } finally {
                reader.close()
            }
        } finally {
            converter.close()
        }
    }

    private suspend fun writeStreams(
        plan: SingleSourcePlan,
        probe: FfmpegProbeResult,
        converter: FfmpegPixelConverter,
        reader: FfmpegVideoReader,
        pcm: FfmpegPcmReader?,
        rawW: Int,
        rawH: Int,
        totalFrames: Long,
        outputFile: File,
        onProgress: (Float) -> Unit,
        encoderName: String?
    ) {
        val recorder = FFmpegFrameRecorder(
            outputFile.absolutePath, rawW, rawH,
            if (pcm != null) 2 else 0
        )
        try {
            configure(recorder, probe, converter, encoderName)
            recorder.start()
            val totalSamples =
                plan.sequence.duration.samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
            for (n in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val timelineT = plan.sequence.frameRate.timeAtFrame(n)
                val clip = plan.clipAt(timelineT)
                val sourceT =
                    clip.sourceRange.start + (timelineT - clip.timelineStart)
                val frame = reader.rawFrameAt(sourceT)
                validateExportFrame(frame, probe)
                val yuv = converter.toYuv420p(frame, rawW, rawH)
                recorder.frameNumber = n.toInt()
                recorder.recordImage(
                    rawW, rawH, Frame.DEPTH_UBYTE, 1, rawW,
                    AV_PIX_FMT_YUV420P, yuv
                )
                if (pcm != null) {
                    writeAudioFrame(plan, pcm, recorder, n, totalSamples)
                }
                onProgress((n + 1).toFloat() / totalFrames * 0.9f)
            }
            coroutineContext.ensureActive()
            recorder.stop()
        } finally {
            recorder.release()
        }
    }

    /**
     * Mid-stream drift is out of scope: the encode was planned for the probed
     * stream, so a frame that changed resolution, depth or colour tags must
     * fail rather than be silently rescaled/downconverted.
     */
    private fun validateExportFrame(frame: AVFrame, probe: FfmpegProbeResult) {
        if (frame.width() != probe.rawSize.width ||
            frame.height() != probe.rawSize.height
        ) {
            throw UnsupportedVideoException(
                "source resolution changed mid-stream to " +
                    "${frame.width()}x${frame.height()}"
            )
        }
    }

    /**
     * An HDR or wide-gamut source is tone mapped on the way out, so its export
     * is BT.709 SDR and must say so rather than repeat the source's tags.
     */
    private fun exportPrimaries(probe: FfmpegProbeResult): Int =
        if (ColourToneMap.forTags(probe.colorTrc, probe.colorPrimaries) != null) {
            AVCOL_PRI_BT709
        } else {
            probe.colorPrimaries
        }

    private fun exportTransfer(probe: FfmpegProbeResult): Int =
        if (ColourToneMap.forTags(probe.colorTrc, probe.colorPrimaries) != null) {
            AVCOL_TRC_BT709
        } else {
            probe.colorTrc
        }

    /** Writes the 48 kHz stereo sample window under output frame [n]. */
    private suspend fun writeAudioFrame(
        plan: SingleSourcePlan,
        pcm: FfmpegPcmReader,
        recorder: FFmpegFrameRecorder,
        n: Long,
        totalSamples: Long
    ) {
        val rate = FfmpegPcmReader.OUTPUT_RATE
        val windowStart =
            plan.sequence.frameRate.timeAtFrame(n).samplesAtOrAfter(rate)
        val windowEnd = minOf(
            plan.sequence.frameRate.timeAtFrame(n + 1).samplesAtOrAfter(rate),
            totalSamples
        )
        if (windowEnd <= windowStart) return
        val out = ShortArray((windowEnd - windowStart).toInt() * 2)
        val frames = plan.fillPcmWindow(pcm, windowStart, windowEnd, out)
        if (frames > 0) {
            recorder.recordSamples(
                FfmpegPcmReader.OUTPUT_RATE, 2, ShortBuffer.wrap(out, 0, frames * 2)
            )
        }
    }

    private fun configure(
        recorder: FFmpegFrameRecorder,
        probe: FfmpegProbeResult,
        converter: FfmpegPixelConverter,
        encoderName: String?
    ) {
        val rawW = probe.rawSize.width
        val rawH = probe.rawSize.height
        recorder.format = "mp4"
        recorder.videoCodecName = encoderName ?: OPENH264
        recorder.videoCodec = AV_CODEC_ID_H264
        recorder.pixelFormat = AV_PIX_FMT_YUV420P
        recorder.frameRate = OUTPUT_FPS
        recorder.gopSize = OUTPUT_FPS.toInt()
        recorder.videoBitrate = (
            rawW.toLong() * rawH * OUTPUT_FPS.toLong() * 12 / 100
            ).coerceIn(1_000_000L, 30_000_000L).toInt()
        if (probe.info.hasAudio) {
            recorder.audioCodecName = "aac"
            recorder.audioCodec = AV_CODEC_ID_AAC
            recorder.sampleRate = FfmpegPcmReader.OUTPUT_RATE
            recorder.audioChannels = 2
            recorder.audioBitrate = 192_000
        }
        recorder.setOption("movflags", "+faststart")
        // Colour tags describe exactly what the converter wrote: the fixed
        // output matrix, limited range, and the probed primaries/transfer.
        // FFmpeg returns null or "unknown" for unnameable values — those stay
        // untagged rather than being rewritten to a different standard.
        av_color_space_name(converter.outputColorspace)
            ?.getString()?.takeUnless { it == "unknown" }
            ?.let { recorder.setVideoOption("colorspace", it) }
        av_color_primaries_name(exportPrimaries(probe))
            ?.getString()?.takeUnless { it == "unknown" }
            ?.let { recorder.setVideoOption("color_primaries", it) }
        av_color_transfer_name(exportTransfer(probe))
            ?.getString()?.takeUnless { it == "unknown" }
            ?.let { recorder.setVideoOption("color_trc", it) }
        recorder.setVideoOption("color_range", "tv")
        // Original display rotation survives as a display matrix, never as
        // re-rotated pixels. The probe normalizes to positive CCW degrees —
        // the same value round-trips through setDisplayRotation.
        val rotation = probe.info.asset.rotationDegrees
        if (rotation != 0) {
            recorder.setDisplayRotation(rotation.toDouble())
        }
    }

    companion object {
        private const val OPENH264 = "libopenh264"
        private const val OUTPUT_FPS = 30.0
        private val OUTPUT_FRAME_RATE = FrameRate(30)
        private val TICKS_PER_FRAME = OUTPUT_FRAME_RATE.ticksPerFrame
    }
}
