package com.dot.gallery.editor.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * The Android [VoiceRecorder]: platform `AudioRecord` capturing 48 kHz
 * mono PCM16 straight into a `cacheDir/editor/voice` WAV — the header's
 * sizes are patched in place when the take stops, so a cancelled take
 * deletes one file and nothing else ever leaks. [monitor] mirrors the
 * input through a `AudioTrack` cue — the honest headphone path; the
 * surface tells users to plug headphones in because speaker monitoring
 * feeds back.
 *
 * Permission is the host's job: [permissionState] only reports, the
 * activity's `ActivityResult` contract prompts, and [start] surfaces a
 * refused grant as a [VoiceRecorderException] the session turns into an
 * explicit error.
 */
class AndroidVoiceRecorder(
    context: Context,
    private val onError: (Throwable) -> Unit = {}
) : VoiceRecorder {

    private val appContext = context.applicationContext

    override val inputLabel: String
        get() {
            val manager = appContext.getSystemService(Context.AUDIO_SERVICE)
                as? AudioManager ?: return "Microphone"
            val input = runCatching {
                manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { device ->
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                    }
            }.getOrNull()
            return input?.productName?.toString() ?: "Microphone"
        }

    override suspend fun permissionState(): MicPermission =
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            MicPermission.Granted
        } else {
            MicPermission.Requestable
        }

    override suspend fun start(monitor: Boolean): VoiceCapture {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            throw VoiceRecorderException("microphone input is unavailable")
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (error: SecurityException) {
            throw VoiceRecorderException(
                "microphone permission was not granted", error
            )
        } catch (error: IllegalArgumentException) {
            throw VoiceRecorderException("microphone input is unavailable", error)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw VoiceRecorderException("microphone is unavailable")
        }
        val cue = if (monitor) {
            val minOut = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minOut > 0) {
                try {
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minOut * 2,
                        AudioTrack.MODE_STREAM
                    ).takeIf { it.state == AudioTrack.STATE_INITIALIZED }
                } catch (error: IllegalArgumentException) {
                    onError(error)
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
        val directory = File(appContext.cacheDir, "editor/voice")
        if (!directory.exists() && !directory.mkdirs()) {
            record.release()
            cue?.release()
            throw VoiceRecorderException(
                "cannot create the voice-over cache directory"
            )
        }
        val file = try {
            File.createTempFile("voice-", ".wav", directory)
        } catch (error: IOException) {
            record.release()
            cue?.release()
            throw VoiceRecorderException(
                "cannot create the voice-over temp file", error
            )
        }
        try {
            record.startRecording()
            cue?.play()
        } catch (error: IllegalStateException) {
            record.release()
            cue?.release()
            file.delete()
            throw VoiceRecorderException("microphone could not start", error)
        }
        return AndroidVoiceCapture(file, record, cue, onError)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}

/**
 * One open `AudioRecord` take: a daemon thread drains the input into the
 * WAV after the placeholder header and tracks the per-chunk peak for the
 * meter. [stop] finalizes the file's sizes in place; [cancel] deletes
 * it — a take never leaves a half-written file behind either way.
 */
private class AndroidVoiceCapture(
    private val file: File,
    private val recorder: AudioRecord,
    private val cue: AudioTrack?,
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
        try {
            recorder.stop()
        } catch (error: IllegalStateException) {
            onError(error)
        }
        recorder.release()
        cue?.let {
            try {
                it.stop()
            } catch (error: IllegalStateException) {
                onError(error)
            }
            it.release()
        }
    }

    private fun captureLoop() {
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            RandomAccessFile(file, "rw").use { out ->
                out.write(WavPcm16.header(0, SAMPLE_RATE, 1))
                out.seek(WavPcm16.HEADER_BYTES.toLong())
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
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
