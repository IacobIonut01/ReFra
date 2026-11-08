package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.time.EDITOR_TICKS_PER_SECOND
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.MicPermission
import com.dot.gallery.editor.render.video.VoiceCapture
import com.dot.gallery.editor.render.video.VoiceRecorder
import com.dot.gallery.editor.render.video.VoiceRecorderException
import com.dot.gallery.editor.render.video.VoiceTake
import com.dot.gallery.editor.render.video.WavPcm16
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs

/**
 * The desktop [VoiceRecorder]: JavaSound `TargetDataLine` at 48 kHz
 * mono PCM16 little-endian — the same WAV byte shape the Android
 * recorder emits. Desktop platforms have no runtime mic prompt at the
 * JavaSound layer (the OS gates the process, not the line), so
 * [permissionState] answers by capability: a supported line is
 * [MicPermission.Granted], an absent one is [MicPermission.Denied] —
 * the honest "no usable input" state.
 *
 * [monitor] mirrors the input through a `SourceDataLine` cue — the
 * headphone path; the surface warns about speaker feedback.
 */
class JavaSoundVoiceRecorder(
    private val outputDirectory: File? = null,
    private val onError: (Throwable) -> Unit = {}
) : VoiceRecorder {

    override val inputLabel: String get() = "System microphone"

    override suspend fun permissionState(): MicPermission =
        if (AudioSystem.isLineSupported(LINE_INFO)) {
            MicPermission.Granted
        } else {
            MicPermission.Denied
        }

    override suspend fun start(monitor: Boolean): VoiceCapture {
        val input = try {
            (AudioSystem.getLine(LINE_INFO) as TargetDataLine)
        } catch (error: LineUnavailableException) {
            throw VoiceRecorderException(
                "no usable microphone input on this host", error
            )
        }
        val cue = if (monitor) {
            runCatching {
                (AudioSystem.getLine(
                    DataLine.Info(SourceDataLine::class.java, FORMAT)
                ) as SourceDataLine)
            }.onFailure(onError).getOrNull()
        } else {
            null
        }
        val directory = outputDirectory
            ?: Files.createTempDirectory("editor-voice").toFile()
        val file = try {
            File.createTempFile("voice-", ".wav", directory)
        } catch (error: IOException) {
            throw VoiceRecorderException(
                "cannot create the voice-over temp file", error
            )
        }
        try {
            input.open(FORMAT)
            input.start()
            cue?.open(FORMAT)
            cue?.start()
        } catch (error: LineUnavailableException) {
            input.close()
            cue?.close()
            file.delete()
            throw VoiceRecorderException("microphone could not start", error)
        }
        return JavaSoundVoiceCapture(file, input, cue, onError)
    }

    private companion object {
        val FORMAT = AudioFormat(48_000f, 16, 1, true, false)
        val LINE_INFO = DataLine.Info(TargetDataLine::class.java, FORMAT)
    }
}

/**
 * One open `TargetDataLine` take — same file contract as the Android
 * capture: drain thread appends PCM after the placeholder header,
 * [stop] patches sizes in place, [cancel] deletes the take.
 */
private class JavaSoundVoiceCapture(
    private val file: File,
    private val input: TargetDataLine,
    private val cue: SourceDataLine?,
    private val onError: (Throwable) -> Unit
) : VoiceCapture {

    @Volatile private var running = true
    @Volatile private var peak = 0f
    @Volatile private var ioFailure: Throwable? = null
    private val finished = AtomicBoolean(false)
    private val dataBytes = AtomicLong(0)

    private val thread = Thread(::captureLoop).apply {
        name = "editor-voice-capture"
        isDaemon = true
        start()
    }

    override fun level(): Float = peak

    override suspend fun stop(): VoiceTake {
        if (!finished.compareAndSet(false, true)) {
            throw VoiceRecorderException("the take already ended")
        }
        running = false
        thread.join(JOIN_MS)
        teardown()
        ioFailure?.let {
            file.delete()
            throw VoiceRecorderException("the take could not be written", it)
        }
        val bytes = dataBytes.get()
        try {
            RandomAccessFile(file, "rw").use { out ->
                out.write(WavPcm16.header(bytes.toInt(), SAMPLE_RATE, 1))
            }
        } catch (error: IOException) {
            file.delete()
            throw VoiceRecorderException(
                "could not finalize the voice-over file", error
            )
        }
        val frames = bytes / 2
        val ticks = frames * EDITOR_TICKS_PER_SECOND / SAMPLE_RATE
        return VoiceTake(
            reference = file.toURI().toString(),
            displayName = DISPLAY_NAME,
            duration = EditorTime(ticks),
            sampleRate = SAMPLE_RATE,
            channels = 1
        )
    }

    override fun cancel() {
        if (!finished.compareAndSet(false, true)) return
        running = false
        thread.join(JOIN_MS)
        teardown()
        if (!file.delete() && file.exists()) {
            onError(IOException("could not delete voice take $file"))
        }
    }

    private fun teardown() {
        input.stop()
        input.close()
        cue?.stop()
        cue?.close()
    }

    private fun captureLoop() {
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            RandomAccessFile(file, "rw").use { out ->
                out.write(WavPcm16.header(0, SAMPLE_RATE, 1))
                out.seek(WavPcm16.HEADER_BYTES.toLong())
                while (running) {
                    val read = input.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    out.write(buffer, 0, read)
                    dataBytes.addAndGet(read.toLong())
                    peak = peakOf(buffer, read)
                    cue?.write(buffer, 0, read)
                }
            }
        } catch (error: IOException) {
            ioFailure = error
            running = false
        }
    }

    /** The chunk's peak sample magnitude in [0, 1] — the meter's value. */
    private fun peakOf(buffer: ByteArray, length: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < length) {
            val sample = buffer[i].toInt() and 0xFF or
                (buffer[i + 1].toInt() shl 8)
            val magnitude = abs(sample.toShort().toInt())
            if (magnitude > max) max = magnitude
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHUNK_BYTES = 4_096
        const val JOIN_MS = 2_000L
        const val DISPLAY_NAME = "Voice-over"
    }
}
