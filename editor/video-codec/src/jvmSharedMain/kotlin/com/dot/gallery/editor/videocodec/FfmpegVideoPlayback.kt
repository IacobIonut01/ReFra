package com.dot.gallery.editor.videocodec

import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.video.VideoAudioOutput
import com.dot.gallery.editor.render.video.VideoFrame
import com.dot.gallery.editor.render.video.VideoFrameCompositor
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.render.video.VideoMixMeter
import com.dot.gallery.editor.render.video.VideoPlayback
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Shared playback engine for a validated [SingleSourcePlan]: a forward frame
 * cursor, a windowed PCM reader and a platform [VideoAudioOutput] whose
 * `playedFrames` is the real clock.
 *
 * Clock rules:
 * - a source with audio always runs the output — muted writes zeros, so the
 *   clock stays real and a broken sink fails loudly instead of pretending;
 * - a silent source runs on the injected [timeSource] (monotonic in
 *   production) with no output device.
 * - `from == duration` restarts at zero; `from` outside `[0, duration]` or a
 *   non-positive [previewMaxEdge] is rejected.
 *
 * The video scheduler emits a new frame only when the output frame index
 * (timeline position ÷ [VideoSequence.frameRate]) changes. `onFrame`
 * timestamps are *sequence* time — the source PTS is meaningless to the
 * timeline after trims; `media.frame` keeps reporting source time. Preview
 * frames may be dropped by the owner downstream, audio never is — the
 * producer keeps at most [MAX_LEAD_BLOCKS]×[BLOCK_FRAMES] samples (~133 ms)
 * in flight. All native handles are confined to [workerDispatcher]; a [Mutex]
 * guarantees a new [play] cannot overlap a previous one's teardown.
 */
class FfmpegVideoPlayback(
    private val resolver: VideoSourceResolver,
    private val audioOutputFactory: () -> VideoAudioOutput,
    private val workerDispatcher: CoroutineDispatcher,
    private val previewMaxEdge: Int = DEFAULT_PREVIEW_MAX_EDGE,
    private val timeSource: TimeSource = TimeSource.Monotonic
) : VideoPlayback {

    private val playMutex = Mutex()
    private val closed = AtomicBoolean(false)

    /** Shared linear-light compositor — identical math to stills/export. */
    private val compositor = VideoFrameCompositor()

    init {
        require(previewMaxEdge > 0) { "previewMaxEdge must be positive: $previewMaxEdge" }
    }

    override suspend fun play(
        sequence: VideoSequence,
        from: EditorTime,
        muted: Boolean,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit
    ) {
        check(!closed.get()) { "playback is closed" }
        playMutex.withLock {
            withContext(workerDispatcher) {
                playLocked(sequence, from, muted, onFrame, onPosition, onLevels)
            }
        }
    }

    private suspend fun playLocked(
        sequence: VideoSequence,
        from: EditorTime,
        muted: Boolean,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit
    ) {
        require(from >= EditorTime.Zero) { "playhead is negative: ${from.ticks}" }
        // Playing at the very end restarts; anything past it is a caller bug.
        require(from <= sequence.duration) {
            "playhead ${from.ticks} past duration ${sequence.duration.ticks}"
        }
        val start = if (from == sequence.duration) EditorTime.Zero else from
        // Registered before start() so even a partial sink start gets closed.
        val audio = mutableListOf<VideoAudioOutput>()
        try {
            if (SingleSourcePlan.isEligible(sequence)) {
                playSingle(
                    sequence, start, muted, audio, onFrame, onPosition, onLevels
                )
            } else {
                playComposite(
                    sequence, start, muted, audio, onFrame, onPosition, onLevels
                )
            }
        } finally {
            // Never leave an audio handle or file resource across a cancel.
            withContext(NonCancellable) {
                audio.forEach { it.close() }
            }
        }
    }

    /** The one-source fast path: a single decoder, PCM off the same file. */
    private suspend fun playSingle(
        sequence: VideoSequence,
        start: EditorTime,
        muted: Boolean,
        audio: MutableList<VideoAudioOutput>,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit
    ) {
        val plan = SingleSourcePlan.validate(sequence)
        val source = resolver.resolve(plan.source.reference)
        try {
            val probe = FfmpegProbe.probe(source, plan.source.reference)
            val converter = FfmpegPixelConverter(
                probe.colorspace, probe.colorRange, probe.rawSize.height
            )
            try {
                // Preview rotates pixels upright via the display rotation.
                val reader = FfmpegVideoReader(
                    source.path, converter, probe.info.asset.rotationDegrees,
                    probe.videoStreamIndex, HardwareDecode.select(probe)
                )
                try {
                    val pcm = if (probe.info.hasAudio) {
                        FfmpegPcmReader(source.path, probe.audioStreamIndex)
                    } else {
                        null
                    }
                    try {
                        runPlayback(
                            sequence, start, muted,
                            frameAt = { timelineT ->
                                val clip = plan.clipAt(timelineT)
                                reader.frameAt(
                                    clip.sourceRange.start +
                                        (timelineT - clip.timelineStart),
                                    previewMaxEdge
                                ).pixels
                            },
                            fillPcm = pcm?.let { pcmReader ->
                                { ws: Long, we: Long, out: ShortArray, _ ->
                                    plan.fillPcmWindow(pcmReader, ws, we, out)
                                }
                            },
                            audio, onFrame, onPosition, onLevels
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
        } finally {
            // A source lease must not outlive its decoders even on cancel.
            withContext(NonCancellable) { source.close() }
        }
    }

    /**
     * The multi-source path: one decoder per visual-clip-referenced source,
     * the shared compositor stacking each output frame, and one PCM reader
     * per audible input the shared mixer sums — the Main lane's embedded
     * audio at unity (unless the sequence mutes it) plus every audible
     * audio lane. Overlay lanes' embedded audio is ignored, never silently
     * mixed.
     */
    private suspend fun playComposite(
        sequence: VideoSequence,
        start: EditorTime,
        muted: Boolean,
        audio: MutableList<VideoAudioOutput>,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit
    ) {
        val plan = CompositeVideoPlan.validate(sequence)
        val audioPlan = CompositeAudioPlan.validate(sequence)
        val canvas = boundedCanvas(sequence.size)
        val referencedIds = sequence.tracks
            .flatMap { it.clips }
            .mapTo(LinkedHashSet()) { it.sourceId }
        val resolved = LinkedHashMap<String, ResolvedVideoSource>(
            referencedIds.size
        )
        // Per-source decode bundles — converter is shared with the reader.
        val decoders = LinkedHashMap<String, SourceDecoder>(referencedIds.size)
        val pcmReaders = LinkedHashMap<String, FfmpegPcmReader>()
        val audioResolved = LinkedHashMap<String, ResolvedVideoSource>()
        try {
            for (id in referencedIds) {
                val asset = sequence.sources.first { it.id == id }
                val source = resolver.resolve(asset.reference)
                resolved[id] = source
                val probe = FfmpegProbe.probe(source, asset.reference)
                val converter = FfmpegPixelConverter(
                    probe.colorspace, probe.colorRange, probe.rawSize.height
                )
                decoders[id] = SourceDecoder(
                    probe,
                    FfmpegVideoReader(
                        source.path, converter,
                        probe.info.asset.rotationDegrees,
                        probe.videoStreamIndex, HardwareDecode.select(probe)
                    ),
                    converter
                )
            }
            for (id in audioPlan.audioSourceIds) {
                val decoder = decoders[id]
                if (decoder != null) {
                    // A visual source — its probe already gates hasAudio.
                    if (decoder.probe.info.hasAudio) {
                        pcmReaders[id] = FfmpegPcmReader(
                            resolved.getValue(id).path,
                            decoder.probe.audioStreamIndex
                        )
                    }
                } else {
                    // An audio-only lane source: resolve once, pin the
                    // probed audio stream on its own reader.
                    val asset = sequence.audioSourceById(id) ?: continue
                    val source = resolver.resolve(asset.reference)
                    audioResolved[id] = source
                    val probe = FfmpegProbe.probeAudio(source, asset.reference)
                    pcmReaders[id] = FfmpegPcmReader(
                        source.path, probe.audioStreamIndex
                    )
                }
            }
            runPlayback(
                sequence, start, muted,
                frameAt = { timelineT ->
                    var sampleAt = timelineT
                    var clips = sequence.activeVisualClips(sampleAt)
                    if (clips.isEmpty()) {
                        val duration = sequence.duration
                        sampleAt = EditorTime(duration.ticks - 1L)
                        clips = sequence.activeVisualClips(sampleAt)
                    }
                    val layers = ArrayList<VideoFrameCompositor.Layer>(
                        clips.size
                    )
                    for (clip in clips) {
                        val decoder = decoders.getValue(clip.sourceId)
                        val sourceT = clip.sourceRange.start +
                            (sampleAt - clip.timelineStart)
                        layers += VideoFrameCompositor.Layer.of(
                            clip,
                            decoder.reader.frameAt(sourceT, previewMaxEdge)
                                .pixels
                        )
                    }
                    compositor.composite(canvas, layers, sequence.size)
                },
                fillPcm = if (pcmReaders.isEmpty()) {
                    null
                } else {
                    { ws: Long, we: Long, out: ShortArray, meter ->
                        audioPlan.fillPcmWindow(pcmReaders, ws, we, out, meter)
                    }
                },
                audio, onFrame, onPosition, onLevels
            )
        } finally {
            // Never leave a decoder or a file lease across a cancel.
            withContext(NonCancellable) {
                try {
                    decoders.values.forEach { it.close() }
                    pcmReaders.values.forEach { it.close() }
                } finally {
                    try {
                        audioResolved.values.forEach { it.close() }
                    } finally {
                        resolved.values.forEach { it.close() }
                    }
                }
            }
        }
    }

    /** Per-source decode bundle: probe, cursor and colour converter. */
    private class SourceDecoder(
        val probe: FfmpegProbeResult,
        val reader: FfmpegVideoReader,
        val converter: FfmpegPixelConverter
    ) : AutoCloseable {
        override fun close() {
            reader.close()
            converter.close()
        }
    }

    /** Canvas bounded to the preview edge, aspect preserved. */
    private fun boundedCanvas(size: PixelSize): PixelSize {
        val scale = minOf(
            1f, previewMaxEdge.toFloat() /
                maxOf(size.width, size.height).toFloat()
        )
        return if (scale >= 1f) size else PixelSize(
            (size.width * scale).toInt().coerceAtLeast(1),
            (size.height * scale).toInt().coerceAtLeast(1)
        )
    }


    private suspend fun runPlayback(
        sequence: VideoSequence,
        start: EditorTime,
        muted: Boolean,
        frameAt: suspend (EditorTime) -> SrgbPixels,
        fillPcm: (suspend (Long, Long, ShortArray, VideoMixMeter?) -> Int)?,
        audio: MutableList<VideoAudioOutput>,
        onFrame: (VideoFrame) -> Unit,
        onPosition: (EditorTime) -> Unit,
        onLevels: (VideoLevels) -> Unit
    ) = coroutineScope {
        val tpf = sequence.frameRate.ticksPerFrame
        val totalTicks = sequence.duration.ticks
        val totalSamples =
            sequence.duration.samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
        // The first frame lands before the clock starts ticking.
        emitAt(sequence, frameAt, start, tpf, -1L, onFrame)
        onPosition(start)

        // The sink starts only once the first frame is ready — the hardware
        // counter is zero-based at start and `basePlayed` makes that explicit.
        val output = if (fillPcm != null) {
            audioOutputFactory().also { created ->
                audio += created
                created.start(
                    FfmpegPcmReader.OUTPUT_RATE, FfmpegPcmReader.OUTPUT_CHANNELS
                )
            }
        } else {
            null
        }
        val startSample = start.samplesAtOrAfter(FfmpegPcmReader.OUTPUT_RATE)
        val basePlayed = output?.playedFrames ?: 0L

        // Audio producer: bounded lookahead measured against the device's
        // own position since start — absolute indices minus the start sample,
        // so a non-zero resume never overflows the lead before the first write.
        val audioJob = if (fillPcm != null && output != null) {
            launch {
                var written = startSample
                val block = ShortArray(BLOCK_FRAMES * 2)
                while (written < totalSamples) {
                    coroutineContext.ensureActive()
                    val lead = (written - startSample) -
                        (output.playedFrames - basePlayed)
                    if (lead >= MAX_LEAD_BLOCKS * BLOCK_FRAMES) {
                        delay(SCHEDULE_MS)
                        continue
                    }
                    val count = minOf(
                        BLOCK_FRAMES.toLong(), totalSamples - written
                    ).toInt()
                    if (muted) {
                        block.fill(0, 0, count * 2)
                        output.write(block, 0, count * 2)
                        written += count
                        onLevels(VideoLevels())
                        continue
                    }
                    val meter = VideoMixMeter(sequence.audioTracks.size)
                    fillPcm(written, written + count, block, meter)
                    output.write(block, 0, count * 2)
                    written += count
                    // Master meters the exact written block — uniform
                    // across both paths. A composite fill also left
                    // per-input energy in the meter; the single-source
                    // fast path skips it, so the lone input takes the
                    // output level.
                    val master = rms(block, count)
                    onLevels(
                        if (meter.frames > 0) {
                            meter.snapshot().copy(master = master)
                        } else {
                            VideoLevels(sourceSound = master, master = master)
                        }
                    )
                }
            }
        } else null

        // The clock: device-played samples when audio exists (muted included —
        // zeros still advance it), monotonic elapsed otherwise. Microseconds
        // fit a 24 h clip comfortably — the old nanosecond product overflowed
        // a Long after ~13 s.
        val startMark = timeSource.markNow()
        val startTicks = start.ticks
        var lastIndex = -1L
        fun timelineTicks(): Long {
            val elapsed = if (output != null) {
                (output.playedFrames - basePlayed) * TICKS_PER_AUDIO_SAMPLE
            } else {
                EditorTime.fromMicroseconds(
                    startMark.elapsedNow().inWholeMicroseconds
                ).ticks
            }
            return startTicks + elapsed
        }

        while (true) {
            coroutineContext.ensureActive()
            val ticks = timelineTicks()
            if (ticks >= totalTicks) break
            val index = ticks / tpf
            val pos = EditorTime(ticks)
            onPosition(pos)
            lastIndex = emitAt(sequence, frameAt, pos, tpf, lastIndex, onFrame)
            delay(SCHEDULE_MS)
        }
        // Exact end marker before unwinding — listeners never see the tail
        // silence wait.
        onPosition(sequence.duration)
        audioJob?.join()
    }

    /**
     * Emits the frame for output index [pos.ticks]/tpf unless it is the one
     * already delivered; returns the delivered index (or [lastIndex]).
     * The callback's [VideoFrame.time] is *sequence* time — the pixel buffer
     * came from the source clock but the timeline is what the owner tracks.
     */
    private suspend fun emitAt(
        sequence: VideoSequence,
        frameAt: suspend (EditorTime) -> SrgbPixels,
        pos: EditorTime,
        tpf: Long,
        lastIndex: Long,
        onFrame: (VideoFrame) -> Unit
    ): Long {
        val index = minOf(pos.ticks / tpf, (sequence.duration.ticks - 1L) / tpf)
        if (index == lastIndex) return lastIndex
        val timelineT = EditorTime(index * tpf)
        onFrame(VideoFrame(time = timelineT, pixels = frameAt(timelineT)))
        return index
    }

    /** Stereo RMS of [count] interleaved s16 frames — the written level. */
    private fun rms(block: ShortArray, count: Int): VideoLevels.Stereo {
        var l = 0.0
        var r = 0.0
        for (i in 0 until count) {
            val lv = block[i * 2].toDouble()
            val rv = block[i * 2 + 1].toDouble()
            l += lv * lv
            r += rv * rv
        }
        val n = count.coerceAtLeast(1)
        return VideoLevels.Stereo(
            (kotlin.math.sqrt(l / n) / Short.MAX_VALUE).toFloat(),
            (kotlin.math.sqrt(r / n) / Short.MAX_VALUE).toFloat()
        )
    }

    override fun close() {
        closed.set(true)
        // An in-flight play is owned by its caller's coroutine; cancellation
        // there runs the NonCancellable teardown.
    }

    companion object {
        private const val TICKS_PER_SECOND = EDITOR_TICKS_PER_SECOND

        /** 48 000 divides 705 600 000 exactly: one sample = 14 700 ticks. */
        private const val TICKS_PER_AUDIO_SAMPLE =
            TICKS_PER_SECOND / FfmpegPcmReader.OUTPUT_RATE.toLong()

        /** One audio block ≈ 33 ms at 48 kHz. */
        private const val BLOCK_FRAMES = 1600

        /** At most 4 blocks (~133 ms) may be queued ahead of the device. */
        private const val MAX_LEAD_BLOCKS = 4

        /** Scheduler poll interval — below a 30 fps frame period, no spin. */
        private const val SCHEDULE_MS = 8L
        private const val DEFAULT_PREVIEW_MAX_EDGE = 1280
    }
}
