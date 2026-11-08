package com.dot.gallery.editor.desktop

import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import com.dot.gallery.editor.videocodec.FfmpegSupport

/**
 * Test-only sample clip for UI captures and the dev harness: 6 s, 320x240,
 * 30 fps H.264 + AAC — a 320x240 window panning across `editor_sample.jpg`
 * (own test asset) with a quiet 330 Hz tone underneath. Generated through
 * the packaged FFmpegFrameRecorder at test/task time — no binary is
 * committed; see TEST_ASSETS.md.
 *
 * `main` writes the clip for the `generateVideoSample` Gradle task and for
 * pushing to a device; tests call [writeCoastClip] directly.
 */
object SampleVideoFixture {

    const val WIDTH = 320
    const val HEIGHT = 240
    const val FPS = 30
    const val SECONDS = 6
    const val SAMPLE_RATE = 48_000

    fun writeCoastClip(
        out: File,
        image: BufferedImage = loadSampleImage()
    ): File {
        FfmpegSupport.require()
        require(image.width >= WIDTH && image.height >= HEIGHT) {
            "sample image ${image.width}x${image.height} smaller than " +
                "${WIDTH}x$HEIGHT crop"
        }
        val recorder = FFmpegFrameRecorder(out.absolutePath, WIDTH, HEIGHT, 2)
        val samplesPerFrame = SAMPLE_RATE / FPS
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
            recorder.sampleRate = SAMPLE_RATE
            recorder.audioChannels = 2
            recorder.audioBitrate = 64_000
            recorder.start()

            val rgb = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3)
            val pcm = ShortBuffer.allocate(samplesPerFrame * 2)
            val panRange = image.width - WIDTH
            var sampleIndex = 0L
            for (n in 0 until FPS * SECONDS) {
                // Ping-pong pan: 0 -> right edge -> 0 over the clip.
                val phase = (n.toDouble() / (FPS * SECONDS) * 2.0)
                    .let { if (it < 1.0) it else 2.0 - it }
                val x = (panRange * phase).roundToInt()
                val crop = image.getSubimage(
                    x, (image.height - HEIGHT) / 2, WIDTH, HEIGHT
                )
                rgb.clear()
                for (y in 0 until HEIGHT) {
                    for (cx in 0 until WIDTH) {
                        val argb = crop.getRGB(cx, y)
                        rgb.put((argb shr 16 and 0xFF).toByte())
                        rgb.put((argb shr 8 and 0xFF).toByte())
                        rgb.put((argb and 0xFF).toByte())
                    }
                }
                rgb.flip()
                recorder.recordImage(
                    WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3,
                    WIDTH * 3, AV_PIX_FMT_RGB24, rgb
                )
                // Quiet monitoring tone — present, not loud.
                pcm.clear()
                for (s in 0 until samplesPerFrame) {
                    val v = (sin(
                        2.0 * PI * 330.0 * (sampleIndex + s) / SAMPLE_RATE
                    ) * 1200.0).roundToInt().toShort()
                    pcm.put(v); pcm.put(v)
                }
                pcm.flip()
                sampleIndex += samplesPerFrame
                recorder.recordSamples(SAMPLE_RATE, 2, pcm)
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        return out
    }

    fun loadSampleImage(): BufferedImage =
        SampleVideoFixture::class.java.getResourceAsStream("/editor_sample.jpg")
            .use { ImageIO.read(it) }
            ?: error("editor_sample.jpg missing from test resources")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "usage: SampleVideoFixture <image-path-or-> <output.mp4>"
        }
        val image = if (args[0] == "-") {
            loadSampleImage()
        } else {
            ImageIO.read(File(args[0]))
        }
        val out = File(args[1]).absoluteFile
        out.parentFile?.mkdirs()
        writeCoastClip(out, image)
        println("wrote ${out.path} (${out.length()} bytes)")
    }
}
