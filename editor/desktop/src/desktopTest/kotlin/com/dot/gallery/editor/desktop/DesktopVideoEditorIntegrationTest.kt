package com.dot.gallery.editor.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoFrame
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.render.video.VideoMediaAccess
import com.dot.gallery.editor.render.video.VideoPlayback
import com.dot.gallery.editor.ui.EditorTheme
import com.dot.gallery.editor.ui.video.VideoEditorScreen
import com.dot.gallery.editor.videocodec.FfmpegProbe
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Full-path check through the real [DesktopVideoMediaAccess]: probe, scrub,
 * split and delete driven by the actual UI, Save copy encoded by the
 * packaged FFmpeg pipeline, the result re-probed from disk, and the source
 * verified byte-identical. Playback is a controllable fake — the real
 * engine path is covered by `FfmpegVideoPlaybackTest`; wiring the editor
 * window to a host audio device is not what this test certifies.
 * Tags are the `videoEditor.*` literals — they are :editor:ui internals.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopVideoEditorIntegrationTest {

    /** Pauses on command, reports positions when asked — no audio device. */
    private class HarnessPlayback : VideoPlayback {
        override suspend fun play(
            sequence: VideoSequence,
            from: EditorTime,
            muted: Boolean,
            onFrame: (VideoFrame) -> Unit,
            onPosition: (EditorTime) -> Unit,
            onLevels: (VideoLevels) -> Unit
        ) {
            // Idle forever; cancellation unwinds it.
            kotlinx.coroutines.awaitCancellation()
        }

        override fun close() {}
    }

    @Test
    fun uiDrivenSplitAndDeleteSavesRealMp4CopyAndLeavesSourceAlone() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val dir = Files.createTempDirectory("editor-video-it")
            val source = SampleVideoFixture.writeCoastClip(
                dir.resolve("sample-coast.mp4").toFile()
            ).toPath()
            val sourceBytes = Files.readAllBytes(source)
            val errors = mutableListOf<Throwable>()

            var session: VideoEditorSession? = null
            runOnUiThread {
                session = VideoEditorSession(
                    scope = CoroutineScope(Dispatchers.Main),
                    media = DesktopVideoMediaAccess(
                        outputDirectory = dir,
                        onError = { errors += it }
                    ),
                    playback = HarnessPlayback(),
                    workerDispatcher = Dispatchers.IO
                ) { errors += it }
                session!!.open(source.toString())
            }
            try {
                setContent {
                    EditorTheme {
                        VideoEditorScreen(
                            session = session!!,
                            onClose = {},
                            onRetryOpen = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                waitUntil(timeoutMillis = 20_000) {
                    session!!.state.value.let {
                        it.sequence != null && !it.opening && it.preview != null
                    }
                }

                // Scrub to ~2 s via the sequence slider's progress
                // semantics, then split at the playhead.
                onNodeWithTag("videoEditor.timeline").performSemanticsAction(
                    SemanticsActions.SetProgress
                ) { setProgress -> setProgress(1f / 3f) }
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.let {
                        it.position.ticks > 0L && !it.seeking
                    }
                }
                onNodeWithTag("videoEditor.split").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.sequence?.tracks?.get(0)
                        ?.clips?.size == 2
                }

                // Drop the second half: select the clip-2 tile, delete.
                onNodeWithTag("videoEditor.clip.clip-2").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.selectedClipId == "clip-2"
                }
                onNodeWithTag("videoEditor.delete").performClick()
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.sequence?.tracks?.get(0)
                        ?.clips?.size == 1
                }
                val committed = session!!.state.value.sequence!!

                // Save via the UI; the published receipt carries the file.
                onNodeWithTag("videoEditor.save").performClick()
                // Export opens the sheet of encoder facts; confirm runs it.
                onNodeWithTag("videoEditor.export.confirm").performClick()
                waitUntil(timeoutMillis = 60_000) {
                    session!!.state.value.let {
                        it.exported != null && !it.exporting
                    }
                }

                val result = session!!.state.value.exported!!
                val output = Path.of(URI.create(result.reference))
                assertTrue(Files.exists(output), "exported copy must exist")
                assertEquals("video/mp4", result.mimeType)
                assertEquals(committed.duration, result.duration)

                // Re-probe the artifact with the packaged decoder: real
                // H.264/AAC container, upright size and a cut duration.
                val resolver = DesktopVideoSourceResolver()
                val reprobe = runBlocking {
                    resolver.resolve(result.reference).use { resolved ->
                        FfmpegProbe.probe(resolved, result.reference)
                    }
                }
                assertEquals(320, reprobe.info.asset.size.width)
                assertEquals(240, reprobe.info.asset.size.height)
                assertTrue(reprobe.info.hasAudio)
                // ~2 s cut; the slider-driven split lands within a frame.
                assertTrue(
                    reprobe.info.asset.duration.let {
                        it > EditorTime.fromMicroseconds(1_800_000) &&
                            it < EditorTime.fromMicroseconds(2_200_000)
                    },
                    "exported duration ${reprobe.info.asset.duration} is not ~2 s"
                )

                assertTrue(
                    Files.readAllBytes(source).contentEquals(sourceBytes),
                    "source file must be untouched"
                )
                assertTrue(errors.isEmpty(), "unexpected errors: $errors")
            } finally {
                runOnUiThread { session?.close() }
            }
        }

    /**
     * The design-review capture that runs on the real decode path: the
     * packaged FFmpeg media access serves actual 'Sample coast.mp4'
     * frames at 1280x800, seeked to 1 s, with the filmstrip populated —
     * unlike the fixture-port captures in :editor:ui, nothing here is a
     * stand-in.
     */
    @Test
    fun realFileWideCapture() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val dir = Files.createTempDirectory("editor-video-capture")
        val source = SampleVideoFixture.writeCoastClip(
            dir.resolve("sample-coast.mp4").toFile()
        ).toPath()
        val errors = mutableListOf<Throwable>()

        var session: VideoEditorSession? = null
        runOnUiThread {
            session = VideoEditorSession(
                scope = CoroutineScope(Dispatchers.Main),
                media = DesktopVideoMediaAccess(
                    outputDirectory = dir,
                    onError = { errors += it }
                ),
                playback = HarnessPlayback(),
                workerDispatcher = Dispatchers.IO
            ) { errors += it }
            session!!.open(source.toString())
        }
        try {
            setContent {
                EditorTheme {
                    VideoEditorScreen(
                        session = session!!,
                        onClose = {},
                        onRetryOpen = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            waitUntil(timeoutMillis = 20_000) {
                session!!.state.value.let {
                    it.sequence != null && !it.opening && it.preview != null
                }
            }
            // Seek to 1 s — the timecode, playhead and strip marker show
            // real mid-clip state — then wait out the filmstrip tiles.
            runOnUiThread {
                session!!.seek(EditorTime.fromMicroseconds(1_000_000))
            }
            waitUntil(timeoutMillis = 15_000) {
                session!!.state.value.let { !it.seeking && it.position.ticks > 0L }
            }
            waitUntil(timeoutMillis = 20_000) {
                session!!.state.value.thumbnails.count {
                    it.clipId == session!!.state.value.selectedClipId
                } >= 4
            }
            waitForIdle()

            val bitmap = onRoot().captureToImage()
            val data = org.jetbrains.skia.Image
                .makeFromBitmap(bitmap.asSkiaBitmap())
                .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                ?: error("PNG encode failed")
            val out = File("build/review").apply { mkdirs() }
            File(out, "redesign-video-realfile.png").writeBytes(data.bytes)
            assertTrue(errors.isEmpty(), "unexpected errors: $errors")
        } finally {
            runOnUiThread { session?.close() }
        }
    }

    /**
     * R2 review set on the real decode path: the packaged FFmpeg media
     * access at phone portrait, phone landscape, tablet portrait, tablet
     * landscape and desktop window sizes, seeked to 1 s with tiles.
     */
    private fun realCapture(width: Int, height: Int, name: String) =
        runDesktopComposeUiTest(width = width, height = height) {
            val dir = Files.createTempDirectory("editor-video-r2")
            val source = SampleVideoFixture.writeCoastClip(
                dir.resolve("sample-coast.mp4").toFile()
            ).toPath()
            val errors = mutableListOf<Throwable>()
            var session: VideoEditorSession? = null
            runOnUiThread {
                session = VideoEditorSession(
                    scope = CoroutineScope(Dispatchers.Main),
                    media = DesktopVideoMediaAccess(
                        outputDirectory = dir,
                        onError = { errors += it }
                    ),
                    playback = HarnessPlayback(),
                    workerDispatcher = Dispatchers.IO
                ) { errors += it }
                session!!.open(source.toString())
            }
            try {
                setContent {
                    EditorTheme {
                        VideoEditorScreen(
                            session = session!!,
                            onClose = {},
                            onRetryOpen = {},
                            onRequestAddMedia = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                waitUntil(timeoutMillis = 20_000) {
                    session!!.state.value.let {
                        it.sequence != null && !it.opening && it.preview != null
                    }
                }
                runOnUiThread {
                    session!!.seek(EditorTime.fromMicroseconds(1_000_000))
                }
                waitUntil(timeoutMillis = 15_000) {
                    session!!.state.value.let { !it.seeking && it.position.ticks > 0L }
                }
                waitUntil(timeoutMillis = 20_000) {
                    session!!.state.value.thumbnails.count {
                        it.clipId == session!!.state.value.selectedClipId
                    } >= 4
                }
                waitForIdle()
                val bitmap = onRoot().captureToImage()
                val data = org.jetbrains.skia.Image
                    .makeFromBitmap(bitmap.asSkiaBitmap())
                    .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)
                    ?: error("PNG encode failed")
                val out = File("build/review/r2-real").apply { mkdirs() }
                File(out, "$name.png").writeBytes(data.bytes)
                assertTrue(errors.isEmpty(), "unexpected errors: $errors")
            } finally {
                runOnUiThread { session?.close() }
            }
        }

    @Test
    fun r2RealPhonePortrait() = realCapture(412, 892, "phone-portrait")

    @Test
    fun r2RealPhoneLandscape() = realCapture(892, 412, "phone-landscape")

    @Test
    fun r2RealTabletPortrait() = realCapture(800, 1280, "tablet-portrait")

    @Test
    fun r2RealTabletLandscape() = realCapture(1280, 800, "tablet-landscape")

    @Test
    fun r2RealDesktop() = realCapture(1440, 960, "desktop")

    @Test
    fun r2RealSmallDesktop() = realCapture(1200, 640, "desktop-small")
}
