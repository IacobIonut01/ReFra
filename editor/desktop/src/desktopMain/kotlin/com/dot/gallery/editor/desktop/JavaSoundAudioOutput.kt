package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.render.video.VideoAudioOutput
import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * JavaSound [VideoAudioOutput]: a [SourceDataLine] for signed 16-bit
 * little-endian stereo PCM at 48 kHz. `playedFrames` is the device's own
 * [SourceDataLine.getLongFramePosition] — the real clock, not the write
 * cursor. Writes are capped by `available()` so a full buffer never blocks
 * the caller; the wait is a small cancellable delay.
 *
 * An unavailable line throws [IOException] — a sink failure is an explicit
 * playback failure, never silent audio. [start] after [close] is rejected:
 * the engine builds a fresh sink per play.
 */
class JavaSoundAudioOutput : VideoAudioOutput {

    private var line: SourceDataLine? = null

    /** Shorts per sample frame — set at [start]; writes stay frame-aligned. */
    private var frameShorts = 0

    @Volatile
    private var lastTotal = 0L

    @Volatile
    private var closed = false

    override val playedFrames: Long
        get() {
            val current = line?.longFramePosition ?: return lastTotal
            lastTotal = current
            return current
        }

    override fun start(sampleRate: Int, channels: Int) {
        check(!closed) { "audio output is closed" }
        check(line == null) { "audio output already started" }
        require(channels in 1..2) { "unsupported channel count: $channels" }
        val format = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate.toFloat(), 16, channels, channels * 2,
            sampleRate.toFloat(), false
        )
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val created = try {
            AudioSystem.getLine(info) as? SourceDataLine
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: throw IOException("no audio output line for $format")
        try {
            created.open(format, BUFFER_BYTES)
            created.start()
        } catch (e: Exception) {
            created.close()
            throw IOException("cannot open audio output line", e)
        }
        frameShorts = channels
        line = created
    }

    /**
     * Writes [count] interleaved samples starting at [offset]; drains in
     * `available()`-sized pieces rounded down to whole sample frames — a
     * SourceDataLine only accepts whole frames — waiting with a short
     * cancellable delay while the device buffer is full.
     */
    override suspend fun write(samples: ShortArray, offset: Int, count: Int) {
        val out = line ?: throw IOException("audio output is not started")
        require(offset >= 0 && count >= 0 && offset + count <= samples.size) {
            "write range out of bounds: offset=$offset count=$count " +
                "size=${samples.size}"
        }
        require(count % frameShorts == 0) {
            "write of $count samples is not frame-aligned ($frameShorts/frame)"
        }
        var i = offset
        val end = offset + count
        val scratch = ByteArray(minOf(count * 2, SCRATCH_BYTES))
        while (i < end) {
            coroutineContext.ensureActive()
            if (closed) return
            val capacity = out.available() / 2
            if (capacity <= 0) {
                delay(SLEEP_MS)
                continue
            }
            var take = minOf(capacity, end - i, scratch.size / 2)
            take -= take % frameShorts
            if (take <= 0) {
                delay(SLEEP_MS)
                continue
            }
            for (j in 0 until take) {
                val s = samples[i + j].toInt()
                scratch[j * 2] = (s and 0xFF).toByte()
                scratch[j * 2 + 1] = (s ushr 8).toByte()
            }
            val written = out.write(scratch, 0, take * 2)
            i += written / 2
            if (written <= 0) delay(SLEEP_MS)
        }
    }

    override fun pauseAndFlush() {
        line?.let {
            it.stop()
            it.flush()
        }
    }

    override fun close() {
        closed = true
        line?.let {
            try {
                it.stop()
            } finally {
                it.close()
            }
        }
        line = null
    }

    private companion object {
        /** ~85 ms of stereo 16-bit 48 kHz output buffer. */
        const val BUFFER_BYTES = 48_000 * 4 / 12
        const val SCRATCH_BYTES = 16_384
        const val SLEEP_MS = 4L
    }
}
