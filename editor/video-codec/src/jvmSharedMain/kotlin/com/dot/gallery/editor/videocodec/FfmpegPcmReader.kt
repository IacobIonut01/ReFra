package com.dot.gallery.editor.videocodec

import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.ensureActive
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer

/**
 * Windowed PCM reader over one source: a dedicated grabber resamples the
 * audio to interleaved s16 stereo at [OUTPUT_RATE] Hz, and [read] serves
 * `[startSample, startSample+count)` in *source* sample coordinates.
 *
 * - Each decoded chunk's `frame.timestamp` (µs) is mapped to a sample index
 *   with round-to-nearest, so a chunk start carries ≤1 sample uncertainty —
 *   documented for callers doing frame-exact audio tests.
 * - Negative timestamps (AAC encoder-delay priming) are trimmed at 0.
 * - Missing source coverage — gaps between chunks and EOF — is rendered as
 *   silence; time is never renumbered by dropping data.
 * - One chunk of lookahead is kept; reads that run ahead of the decoder by
 *   more than [RESEEK_AHEAD_SAMPLES] or any backward read reseek to
 *   `target − 1 s` and roll forward — landing exactly on the target PTS can
 *   discard the AAC packet the target sample lives in.
 * - Not thread-safe — confine to the decode owner.
 */
class FfmpegPcmReader(
    private val path: String,
    private val audioStreamIndex: Int = -1
) : AutoCloseable {

    private val grabber = FFmpegFrameGrabber(path)
    private var started = false

    /** One decoded chunk: first source sample index + interleaved stereo data. */
    private class Chunk(val start: Long, val samples: ShortArray) {
        val frames: Int get() = samples.size / OUTPUT_CHANNELS
        val end: Long get() = start + frames
    }

    private var pending: Chunk? = null
    private var eof = false

    /** Container start time: MPEG-TS and MPEG-PS timestamps do not begin at 0. */
    private var startOffsetUs = 0L

    /** Sample index the next decoded chunk is expected to start at. */
    private var decodeCursor = 0L

    fun start() {
        if (started) return
        if (audioStreamIndex >= 0) grabber.audioStream = audioStreamIndex
        grabber.sampleMode = FrameGrabber.SampleMode.SHORT
        grabber.sampleFormat = AV_SAMPLE_FMT_S16
        grabber.sampleRate = OUTPUT_RATE
        grabber.audioChannels = OUTPUT_CHANNELS
        grabber.start()
        startOffsetUs = grabber.containerStartUs()
        started = true
    }

    /**
     * Returns [count] interleaved stereo sample frames (ShortArray of
     * `count * 2`) beginning at source sample [startSample]; positions with no
     * source coverage are silence.
     */
    suspend fun read(startSample: Long, count: Int): ShortArray {
        require(startSample >= 0L && startSample <= MAX_SAMPLE_INDEX) {
            "startSample out of range: $startSample"
        }
        require(count >= 0 && count <= Int.MAX_VALUE / OUTPUT_CHANNELS) {
            "count out of range: $count"
        }
        if (count == 0) return ShortArray(0)
        val end = try {
            Math.addExact(startSample, count.toLong())
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("window overflows: $startSample+$count")
        }
        start()
        val coveredFrom = pending?.start ?: decodeCursor
        val coveredTo = pending?.end ?: decodeCursor
        if (startSample < coveredFrom ||
            startSample > coveredTo + RESEEK_AHEAD_SAMPLES
        ) {
            reseek(startSample)
        }
        val out = ShortArray(count * OUTPUT_CHANNELS)
        var position = startSample
        while (position < end) {
            coroutineContext.ensureActive()
            val chunk = pending ?: grabChunk()?.also { pending = it }
            if (chunk == null) {
                eof = true
                break // remainder stays silence
            }
            if (position < chunk.start) {
                // Gap between decoded chunks — silence, keep time intact.
                position = minOf(chunk.start, end)
                continue
            }
            val skip = (position - chunk.start).toInt()
            val take = minOf(end - position, (chunk.frames - skip).toLong()).toInt()
            if (take <= 0) {
                pending = null
                decodeCursor = chunk.end
                continue
            }
            System.arraycopy(
                chunk.samples, skip * OUTPUT_CHANNELS,
                out, (position - startSample).toInt() * OUTPUT_CHANNELS,
                take * OUTPUT_CHANNELS
            )
            position += take
            if (skip + take >= chunk.frames) {
                pending = null
                decodeCursor = chunk.end
            }
        }
        return out
    }

    /**
     * Lands the decode one second behind the target so the AAC packet holding
     * [sampleIndex] survives, then lets the read loop roll forward to it.
     */
    private fun reseek(sampleIndex: Long) {
        val targetUs = Math.floorDiv(sampleIndex * 1_000_000L, OUTPUT_RATE.toLong())
        grabber.setAudioTimestamp(max(0L, targetUs - SEEK_BACK_US))
        pending = null
        eof = false
        decodeCursor = max(0L, sampleIndex)
    }

    /** Grabs and normalizes the next audio chunk; null at stream EOF. */
    private suspend fun grabChunk(): Chunk? {
        while (true) {
            coroutineContext.ensureActive()
            val frame = grabber.grabSamples() ?: return null
            // Samples arrive interleaved in a single ShortBuffer.
            val buffer = frame.samples?.firstOrNull() as? ShortBuffer
                ?: continue
            val raw = ShortArray(buffer.remaining())
            buffer.get(raw)
            // Round-to-nearest µs→sample mapping; may be negative for priming.
            // The timestamp is clamped before multiplying: a corrupt/huge PTS
            // must never wrap the Long product.
            val ts = (frame.timestamp - startOffsetUs).coerceIn(
                -MAX_TIMESTAMP_US, MAX_TIMESTAMP_US
            )
            val rawStart = Math.floorDiv(
                ts * OUTPUT_RATE + 500_000L, 1_000_000L
            )
            val start = max(0L, rawStart)
            val trim = (start - rawStart).toInt() * OUTPUT_CHANNELS
            val samples = if (trim > 0 && trim < raw.size) {
                raw.copyOfRange(trim, raw.size)
            } else if (trim >= raw.size) {
                ShortArray(0)
            } else {
                raw
            }
            if (samples.isEmpty()) continue // fully-primed chunk: skip
            return Chunk(start, samples)
        }
    }

    override fun close() {
        try {
            if (started) grabber.stop()
        } finally {
            grabber.release()
        }
    }

    companion object {
        const val OUTPUT_RATE = 48_000
        const val OUTPUT_CHANNELS = 2

        /** ~1 s of audio; decode through instead of reseeking below this. */
        private const val RESEEK_AHEAD_SAMPLES = OUTPUT_RATE.toLong()

        /** Video-side companion seek lands the same distance behind. */
        private const val SEEK_BACK_US = 1_000_000L

        /** Clamps a raw PTS before the sample-rate multiply (25 h). */
        private const val MAX_TIMESTAMP_US = 25L * 3_600 * 1_000_000

        /** Same bound in samples — keeps `sampleIndex * 1_000_000` in Long. */
        private const val MAX_SAMPLE_INDEX =
            MAX_TIMESTAMP_US / 1_000_000L * OUTPUT_RATE
    }
}
