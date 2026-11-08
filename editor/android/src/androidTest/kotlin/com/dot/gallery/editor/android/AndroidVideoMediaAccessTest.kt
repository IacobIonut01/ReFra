package com.dot.gallery.editor.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.videocodec.FfmpegSupport
import java.io.File
import java.nio.ByteBuffer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test for the packaged FFmpeg slice: the .so bundle must
 * load, pass the codec audit, decode frames and encode an exportable MP4 —
 * the whole 64-bit-only video pipeline, exercised on real hardware. A skip
 * is deliberate only when the ABI cannot carry video at all.
 */
@RunWith(AndroidJUnit4::class)
class AndroidVideoMediaAccessTest {

    private lateinit var context: Context
    private lateinit var dir: File
    private lateinit var fixture: File
    private val errors = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dir = File(context.cacheDir, "videotest").apply { mkdirs() }
        FfmpegSupport.require() // fails the run, not one test, when absent
        fixture = writeFixture(File(dir, "clip.mp4"))
    }

    private fun access() =
        AndroidVideoMediaAccess(context, onError = { errors += it })

    private fun writeFixture(out: File): File {
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
    fun packagedCodecsAuditClean() {
        val audit = FfmpegSupport.audit()
        assertFalse("packaged FFmpeg must not enable GPL", audit.gplEnabled)
        assertTrue(audit.openh264)
        assertTrue(audit.aac)
    }

    /**
     * The staged copy of a non-seekable `content://` source is made once
     * per opened source: probe, frame and export all lease the same
     * backing (provider opened exactly twice — seekability probe plus the
     * one copy), and closing the access releases it.
     */
    @Test
    fun stagedSourceIsCopiedOnceAcrossProbeFrameAndExport() = runBlocking {
        val name = "clip.mp4"
        PipeContentProvider.payloads[name] = fixture.readBytes()
        val uri = PipeContentProvider.uri(name).toString()
        val access = access()
        try {
            val info = access.probe(uri)
            assertEquals(160, info.asset.size.width)
            val frame = access.frame(uri, EditorTime.Zero, 160)
            assertEquals(160, frame.pixels.size.width)
            assertEquals(2, PipeContentProvider.openCount(name))
            val sequence = sequenceFor(uri, info.asset.duration)
            var receipt: com.dot.gallery.editor.render.video.VideoExportResult? =
                null
            access.export(
                sequence, onPublished = { receipt = it }, onProgress = {}
            )
            assertNotNull(receipt)
            // The export leased the same backing — still no third copy.
            assertEquals(2, PipeContentProvider.openCount(name))
        } finally {
            access.close()
        }
        // Retired with no live leases: the staged import is gone.
        val imports = File(context.cacheDir, "editor/imports")
        assertTrue(imports.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun probeFrameAndExportOnDevice() = runBlocking {
        val access = access()
        try {
        val info = access.probe("file://${fixture.absolutePath}")
        assertEquals(160, info.asset.size.width)
        assertEquals(120, info.asset.size.height)
        val frame = access.frame(
            "file://${fixture.absolutePath}", EditorTime.Zero, 160
        )
        assertEquals(160, frame.pixels.size.width)

        val sequence = sequenceFor(
            "file://${fixture.absolutePath}", info.asset.duration
        )
        var receipt: com.dot.gallery.editor.render.video.VideoExportResult? =
            null
        val result = access.export(
            sequence, onPublished = { receipt = it }, onProgress = {}
        )
        assertNotNull(receipt)
        assertEquals("video/mp4", result.mimeType)
        assertTrue(result.reference.startsWith("content://"))
        // The published row is readable back through the resolver.
        val probed = access.probe(result.reference)
        assertEquals(160, probed.asset.size.width)
        assertEquals(120, probed.asset.size.height)
        // The source file was only read.
        assertTrue(fixture.isFile && fixture.length() > 0)
        } finally {
            access.close()
        }
    }
}
