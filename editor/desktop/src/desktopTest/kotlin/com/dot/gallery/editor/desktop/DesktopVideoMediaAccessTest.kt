package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.videocodec.FfmpegExportVerification
import com.dot.gallery.editor.videocodec.FfmpegSupport
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

/**
 * DesktopVideoMediaAccess contract: probe/frame through the resolver, export
 * producing a verified sibling `-edited-*.mp4` with a real publication
 * receipt, the source file untouched, and a cancelled export leaving neither
 * staging nor published files behind.
 */
class DesktopVideoMediaAccessTest {

    private lateinit var dir: File
    private lateinit var fixture: File
    private val errors = mutableListOf<Throwable>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("desktopvideo").toFile()
        fixture = writeFixture(File(dir, "clip.mp4"))
    }

    private fun access() = DesktopVideoMediaAccess(onError = { errors += it })

    /** A 1 s 160x120 H.264-only clip — enough to exercise the port contract. */
    private fun writeFixture(out: File): File {
        FfmpegSupport.require()
        val recorder = FFmpegFrameRecorder(out.absolutePath, 160, 120, 0)
            .apply {
                format = "mp4"
                videoCodecName = "libopenh264"
                pixelFormat = AV_PIX_FMT_YUV420P
                frameRate = 30.0
                gopSize = 30
                videoBitrate = 200_000
            }
        val rgb = ByteBuffer.allocateDirect(160 * 120 * 3)
        try {
            recorder.start()
            repeat(30) {
                rgb.clear()
                while (rgb.hasRemaining()) {
                    rgb.put(96.toByte()); rgb.put(32.toByte()); rgb.put(16.toByte())
                }
                rgb.flip()
                recorder.recordImage(
                    160, 120, Frame.DEPTH_UBYTE, 3, 160 * 3,
                    AV_PIX_FMT_RGB24, rgb
                )
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        return out
    }

    private fun sequenceFor(reference: String, duration: EditorTime) =
        VideoSequence(
            size = PixelSize(160, 120),
            frameRate = FrameRate(30),
            sources = persistentListOf(
                MediaAsset.Video(
                    id = "source", reference = reference,
                    size = PixelSize(160, 120), duration = duration,
                    frameRate = FrameRate(30), hasAudio = false
                )
            ),
            tracks = persistentListOf(
                VideoTrack(
                    "track", persistentListOf(
                        VideoClip(
                            "clip-1", "source", EditorTime.Zero,
                            TimeRange(EditorTime.Zero, duration)
                        )
                    )
                )
            )
        )

    @Test
    fun `probe and frame read through the resolver`() = runTest {
        val access = access()
        val info = access.probe(fixture.absolutePath)
        assertEquals(160, info.asset.size.width)
        assertEquals(120, info.asset.size.height)
        assertFalse(info.hasAudio)
        val frame = access.frame(fixture.absolutePath, EditorTime.Zero, 160)
        assertEquals(160, frame.pixels.size.width)
        assertEquals(120, frame.pixels.size.height)
    }

    @Test
    fun `export writes a verified sibling copy with a receipt`() = runTest {
        val access = access()
        val info = access.probe(fixture.absolutePath)
        val sequence = sequenceFor(fixture.absolutePath, info.asset.duration)
        var receipt: VideoExportResult? = null
        val result = access.export(
            sequence, onPublished = { receipt = it }, onProgress = {}
        )
        // The receipt is what was published; the result mirrors it.
        assertNotNull(receipt)
        assertEquals(receipt, result)
        assertEquals("video/mp4", result.mimeType)
        assertEquals(PixelSize(160, 120), result.size)
        assertEquals(sequence.duration, result.duration)
        val output = File(java.net.URI(result.reference))
        assertTrue(output.isFile)
        assertTrue(output.name.contains("-edited-"))
        assertEquals(dir.canonicalFile, output.parentFile.canonicalFile)
        // Reopened stream facts match the input's: H.264 at 160x120, no audio.
        val staged = DesktopVideoSourceResolver()
            .resolve(output.absolutePath)
        try {
            FfmpegExportVerification.verify(
                staged, PixelSize(160, 120), 0, sequence.duration,
                expectAudio = false
            )
        } finally {
            staged.close()
        }
        // The source file is only ever read.
        assertEquals(info.asset.size, access.probe(fixture.absolutePath).asset.size)
    }

    @Test
    fun `a cancelled export leaves no staging or published file`() = runTest {
        val access = access()
        val info = access.probe(fixture.absolutePath)
        val sequence = sequenceFor(fixture.absolutePath, info.asset.duration)
        val job = async {
            access.export(sequence, onPublished = {}, onProgress = {
                throw kotlinx.coroutines.CancellationException("user")
            })
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> { job.await() }
        val leftovers = dir.listFiles().orEmpty().filter {
            it.name.startsWith(".refra-") || it.name.contains("-edited-")
        }
        assertTrue(leftovers.isEmpty(), "leftover files: $leftovers")
    }

    @Test
    fun `unsupported references fail without touching the filesystem`() =
        runTest {
            val access = access()
            assertFailsWith<IllegalArgumentException> {
                access.probe("https://example.invalid/clip.mp4")
            }
            assertFailsWith<java.io.IOException> {
                access.probe(File(dir, "absent.mp4").absolutePath)
            }
        }
}
