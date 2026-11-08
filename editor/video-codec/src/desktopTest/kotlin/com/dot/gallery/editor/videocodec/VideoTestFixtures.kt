package com.dot.gallery.editor.videocodec

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

/**
 * Deterministic H.264/AAC fixture built with the packaged FFmpeg runtime —
 * never a system ffmpeg and never a checked-in binary.
 *
 * 4 s, 320x240, 30 fps CFR, OpenH264 + AAC 48 kHz stereo:
 * - colour bands: red second 0-1, green 1-2, blue 2-3, white 3-4;
 * - a 440 Hz sine for the first two seconds, 880 Hz after, amplitude 4000,
 *   generated exactly as `sin(2*pi*f*n/48000)` per sample;
 * - BT.601 stream tags, since that is what a phone-class SDR clip reports.
 *
 * [rotated] writes a +90 degree display matrix instead of rotating pixels —
 * the preview/upright pipeline is what carries the turn.
 */
object VideoTestFixtures {

    const val WIDTH = 320
    const val HEIGHT = 240
    const val FPS = 30
    const val SECONDS = 4
    const val FRAMES = FPS * SECONDS
    const val SAMPLE_RATE = 48_000
    const val SAMPLES_PER_FRAME = SAMPLE_RATE / FPS // 1600

    fun createFixture(directory: File, name: String = "fixture.mp4"): File =
        write(directory, name, rotated = false)

    fun createRotatedFixture(directory: File, name: String = "rotated.mp4"): File =
        write(directory, name, rotated = true)

    /**
     * A video-only fixture — no audio stream, solid colour — used to drive
     * the playback engine's monotonic-clock path.
     */
    fun createSilentFixture(
        directory: File,
        seconds: Int,
        name: String = "silent.mp4"
    ): File {
        FfmpegSupport.require()
        val out = File(directory, name)
        val recorder = FFmpegFrameRecorder(out.absolutePath, WIDTH, HEIGHT, 0)
        val rgb = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3)
        try {
            recorder.format = "mp4"
            recorder.videoCodecName = "libopenh264"
            recorder.videoCodec = AV_CODEC_ID_H264
            recorder.pixelFormat = AV_PIX_FMT_YUV420P
            recorder.frameRate = FPS.toDouble()
            recorder.gopSize = FPS
            recorder.videoBitrate = 200_000
            recorder.start()
            repeat(FPS * seconds) {
                rgb.clear()
                while (rgb.hasRemaining()) rgb.put(64.toByte())
                rgb.flip()
                recorder.recordImage(
                    WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3,
                    WIDTH * 3, AV_PIX_FMT_RGB24, rgb
                )
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        return out
    }

    /**
     * An audio-only WAV fixture — 48 kHz stereo s16 sine — written with a
     * plain RIFF header rather than a codec, so every decoded sample is
     * the exact `sin(2*pi*f*n/48000)*amplitude` value with no encoder
     * priming. Audio lanes read it through [FfmpegPcmReader] like any
     * music file.
     */
    fun createToneFile(
        directory: File,
        name: String,
        frequencyHz: Double,
        amplitude: Int = 16_000,
        seconds: Int = SECONDS
    ): File {
        val out = File(directory, name)
        val frames = SAMPLE_RATE * seconds
        val dataSize = frames * 4 // stereo × s16
        val header = ByteBuffer.allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
        header.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(),
            'F'.code.toByte(), 'F'.code.toByte()))
        header.putInt(36 + dataSize)
        header.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(),
            'V'.code.toByte(), 'E'.code.toByte()))
        header.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(),
            't'.code.toByte(), ' '.code.toByte()))
        header.putInt(16) // PCM fmt chunk
        header.putShort(1) // PCM
        header.putShort(2) // stereo
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 4) // byte rate
        header.putShort(4) // block align
        header.putShort(16) // bits per sample
        header.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(),
            't'.code.toByte(), 'a'.code.toByte()))
        header.putInt(dataSize)
        val pcm = ByteBuffer.allocate(dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until frames) {
            val v = (sin(2.0 * PI * frequencyHz * n / SAMPLE_RATE) *
                amplitude).roundToInt().toShort()
            pcm.putShort(v)
            pcm.putShort(v)
        }
        out.writeBytes(header.array() + pcm.array())
        return out
    }

    /** Root-mean-square level of interleaved s16 samples — mix-energy oracle. */
    fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return kotlin.math.sqrt(sum / samples.size)
    }

    /**
     * Rising-edge zero crossings of the left channel in interleaved stereo
     * PCM — the lead-set 100 ms frequency oracle for retained segments:
     * 440 Hz yields 42..46 crossings per 4 800 frames, 880 Hz 86..90.
     */
    fun risingCrossings(stereo: ShortArray): Int {
        var result = 0
        for (i in 2 until stereo.size step 2) {
            if (stereo[i - 2] <= 0 && stereo[i] > 0) result++
        }
        return result
    }

    /** Expected dominant RGB channel for a timeline second, or "all" for white. */
    fun bandAt(second: Int): Triple<Int, Int, Int> = when (second) {
        0 -> Triple(255, 0, 0)
        1 -> Triple(0, 200, 0)
        2 -> Triple(0, 0, 255)
        else -> Triple(235, 235, 235)
    }

    private fun write(directory: File, name: String, rotated: Boolean): File {
        FfmpegSupport.require()
        val out = File(directory, name)
        val recorder = FFmpegFrameRecorder(out.absolutePath, WIDTH, HEIGHT, 2)
        try {
            recorder.format = "mp4"
            recorder.videoCodecName = "libopenh264"
            recorder.videoCodec = AV_CODEC_ID_H264
            recorder.pixelFormat = AV_PIX_FMT_YUV420P
            recorder.frameRate = FPS.toDouble()
            recorder.gopSize = FPS
            recorder.videoBitrate = 400_000
            recorder.audioCodecName = "aac"
            recorder.audioCodec = AV_CODEC_ID_AAC
            // Codec sample format stays fltp (what AAC accepts); the
            // recorder's internal swr converts the s16 buffers we feed it.
            recorder.sampleRate = SAMPLE_RATE
            recorder.audioChannels = 2
            recorder.audioBitrate = 96_000
            recorder.setVideoOption("colorspace", "smpte170m")
            recorder.setVideoOption("color_primaries", "smpte170m")
            recorder.setVideoOption("color_trc", "smpte170m")
            if (rotated) recorder.setDisplayRotation(90.0)
            recorder.start()

            val rgb = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3)
            val pcm = ShortBuffer.allocate(SAMPLES_PER_FRAME * 2)
            var sampleIndex = 0L
            for (n in 0 until FRAMES) {
                val (r, g, b) = bandAt(n / FPS)
                rgb.clear()
                while (rgb.hasRemaining()) {
                    rgb.put(r.toByte()); rgb.put(g.toByte()); rgb.put(b.toByte())
                }
                rgb.flip()
                recorder.recordImage(
                    WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3, WIDTH * 3,
                    AV_PIX_FMT_RGB24, rgb
                )
                val frequency = if (n < FPS * 2) 440.0 else 880.0
                pcm.clear()
                for (s in 0 until SAMPLES_PER_FRAME) {
                    val v = (sin(2.0 * PI * frequency * (sampleIndex + s) /
                        SAMPLE_RATE) * 4000.0).roundToInt().toShort()
                    pcm.put(v); pcm.put(v)
                }
                pcm.flip()
                sampleIndex += SAMPLES_PER_FRAME
                recorder.recordSamples(SAMPLE_RATE, 2, pcm)
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        return out
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65_536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Dominant-channel check tolerant of H.264 quantization. */
    fun assertDominant(
        argb: Int, wantR: Int, wantG: Int, wantB: Int, margin: Int = 80
    ) {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        if (wantR == wantG && wantG == wantB) {
            // White band: all channels bright and close together.
            check(minOf(r, g, b) > 150 && maxOf(r, g, b) - minOf(r, g, b) < 80) {
                "expected a bright neutral pixel, got r=$r g=$g b=$b"
            }
        } else {
            val want = Triple(wantR, wantG, wantB)
            val got = Triple(r, g, b)
            val dominant = want.toList().indexOf(want.toList().max())
            val others = got.toList().filterIndexed { i, _ -> i != dominant }
            check(got.toList()[dominant] > others.max() + margin) {
                "expected dominant channel $dominant in $got (band $want)"
            }
        }
    }
}
