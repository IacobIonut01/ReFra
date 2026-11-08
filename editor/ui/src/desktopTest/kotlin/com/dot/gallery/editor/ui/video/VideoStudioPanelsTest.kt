package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.render.video.AudioInfo
import com.dot.gallery.editor.render.video.MicPermission
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoFrame
import com.dot.gallery.editor.render.video.VideoInfo
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.render.video.VideoMediaAccess
import com.dot.gallery.editor.render.video.VideoPlayback
import com.dot.gallery.editor.render.video.VoiceRecorder
import com.dot.gallery.editor.ui.TestEditorRoot
import com.dot.gallery.editor.ui.V3VideoDarkColors
import com.dot.gallery.editor.ui.V3VideoLightColors
import com.dot.gallery.editor.ui.captureWindowImage
import com.dot.gallery.editor.ui.samplePixels
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * The redesigned tool panels and export sheet: behaviour the old surfaces
 * did not have (panel-header Reset, chips that swap the ruler, the disabled
 * reorder ends) plus a phone capture of every panel under
 * `build/review/studio-panels/` for comparison with the design.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class VideoStudioPanelsTest {

    private class CaptureVideoMedia(
        private val exportGate: CompletableDeferred<VideoExportResult>? = null
    ) : VideoMediaAccess {
        private val framePixels = samplePixels()

        override suspend fun probe(reference: String) = VideoInfo(
            asset = MediaAsset.Video(
                id = reference, reference = reference,
                size = PixelSize(framePixels.size.width, framePixels.size.height),
                duration = EditorTime.fromMicroseconds(6_000_000),
                frameRate = FrameRate(30), hasAudio = true
            ),
            displayName = reference.substringAfterLast('/'),
            audioSampleRate = 48_000,
            audioChannels = 2
        )

        override suspend fun frame(reference: String, at: EditorTime, maxEdge: Int) =
            VideoFrame(at, framePixels)

        override suspend fun export(
            sequence: VideoSequence,
            onPublished: (VideoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): VideoExportResult {
            onProgress(0.58f)
            val result = (exportGate ?: error("this capture never exports")).await()
            onPublished(result)
            return result
        }

        override suspend fun probeAudio(reference: String) = AudioInfo(
            asset = MediaAsset.Audio(
                id = reference, reference = reference,
                duration = EditorTime.fromMicroseconds(6_000_000),
                sampleRate = 48_000, channels = 2
            ),
            displayName = reference.substringAfterLast('/')
        )

        override suspend fun waveform(reference: String, buckets: Int): FloatArray =
            FloatArray(buckets) { i ->
                val phase = 0.11f + (reference.length % 5) * 0.03f
                (0.3f + 0.55f * kotlin.math.abs(
                    kotlin.math.sin(i * phase + reference.length)
                )).coerceIn(0f, 1f)
            }

        override fun close() {}
    }

    private class IdlePlayback : VideoPlayback {
        override suspend fun play(
            sequence: VideoSequence,
            from: EditorTime,
            muted: Boolean,
            onFrame: (VideoFrame) -> Unit,
            onPosition: (EditorTime) -> Unit,
            onLevels: (VideoLevels) -> Unit
        ) {
            kotlinx.coroutines.awaitCancellation()
        }

        override fun close() {}
    }

    private class CaptureVoiceRecorder(
        private val permission: MicPermission
    ) : VoiceRecorder {
        override val inputLabel = "Capture microphone"
        override suspend fun permissionState() = permission
        override suspend fun start(monitor: Boolean) = error("captures never record")
    }

    private fun ComposeUiTest.stagedVideo(
        media: CaptureVideoMedia = CaptureVideoMedia(),
        voiceRecorder: VoiceRecorder? = null
    ): VideoEditorSession {
        lateinit var session: VideoEditorSession
        runOnUiThread {
            session = VideoEditorSession(
                CoroutineScope(Dispatchers.Main), media, IdlePlayback(),
                voiceRecorder = voiceRecorder
            ) {}
            session.open("fixture://sample-coast.mp4")
        }
        setContent {
            TestEditorRoot(darkTheme = true) {
                VideoEditorScreen(
                    session = session,
                    onClose = {},
                    onRetryOpen = { session.open("fixture://sample-coast.mp4") },
                    onRequestMicrophone = {},
                    onRequestAddMedia = {},
                    onOpenExport = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        waitUntil(timeoutMillis = 30_000) {
            val s = session.state.value
            s.sequence != null && !s.opening && !s.seeking && s.preview != null &&
                s.thumbnails.count { it.clipId == s.selectedClipId } >= 4
        }
        waitForIdle()
        return session
    }

    private fun ComposeUiTest.awaitOverlayImported(session: VideoEditorSession) {
        runOnUiThread {
            session.addVideoSource(
                "fixture://overlay-harbour.mp4",
                com.dot.gallery.editor.render.video.VideoInsertion.Overlay
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            val s = session.state.value
            !s.importing && !s.seeking &&
                (s.sequence?.sources?.size ?: 0) >= 2 &&
                (s.sequence?.tracks?.size ?: 0) >= 2 &&
                s.preview != null
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitAudioImported(
        session: VideoEditorSession,
        reference: String,
        trackCount: Int
    ) {
        runOnUiThread { session.addAudioSource(reference) }
        waitUntil(timeoutMillis = 15_000) {
            val s = session.state.value
            !s.importing && !s.seeking &&
                (s.sequence?.audioTracks?.size ?: 0) >= trackCount &&
                s.waveforms.size >= trackCount
        }
        waitForIdle()
    }

    private fun ComposeUiTest.stageTwoLanes(session: VideoEditorSession) {
        awaitAudioImported(session, "fixture://Music.mp3", 1)
        awaitAudioImported(session, "fixture://Voice-over.wav", 2)
    }

    private fun ComposeUiTest.openTool(toolTag: String, group: String) {
        onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
        onNodeWithTag("editor.catalogue.group.$group").performClick()
        onNodeWithTag(toolTag).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        // The sheet leaves and the panel's spring settles.
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun save(image: ImageBitmap, name: String) {
        val data = Image.makeFromBitmap(image.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        File("build/review/studio-panels/$name.png")
            .apply { parentFile?.mkdirs() }
            .writeBytes(data.bytes)
    }

    private fun capture(
        name: String,
        setUp: ComposeUiTest.(VideoEditorSession) -> Unit,
        voiceRecorder: VoiceRecorder? = null
    ) = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedVideo(voiceRecorder = voiceRecorder)
        try {
            setUp(session)
            mainClock.advanceTimeBy(500)
            waitForIdle()
            save(captureWindowImage(), name)
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test fun captureTrim() = capture("trim", { openTool(TAG_VIDEO_TAB_TRIM, "Edit") })

    @Test fun captureArrange() = capture("arrange", { openTool(TAG_VIDEO_TAB_CLIPS, "Edit") })

    @Test fun captureTransform() =
        capture("transform", { openTool(TAG_VIDEO_TOOL_TRANSFORM, "Picture") })

    @Test fun captureColour() = capture("colour", { openTool(TAG_VIDEO_TOOL_COLOUR, "Picture") })

    @Test fun captureLayers() = capture("layers", { session ->
        awaitOverlayImported(session)
        onNodeWithTag(TAG_VIDEO_LAYERS).performClick()
        waitForIdle()
    })

    @Test fun captureAddMedia() = capture("add-media", {
        onNodeWithTag(TAG_VIDEO_MEDIA).performClick()
        waitForIdle()
    })

    @Test fun captureAddAudio() = capture("add-audio", { openTool(TAG_VIDEO_TOOL_ADD_AUDIO, "Audio") })

    @Test fun captureClipAudio() = capture("clip-audio", { session ->
        stageTwoLanes(session)
        openTool(TAG_VIDEO_TOOL_CLIP_AUDIO, "Audio")
    })

    @Test fun captureMixer() = capture("mixer", { session ->
        stageTwoLanes(session)
        openTool(TAG_VIDEO_TOOL_MIXER, "Audio")
    })

    @Test fun captureRouting() = capture("routing", { session ->
        stageTwoLanes(session)
        openTool(TAG_VIDEO_TOOL_ROUTING, "Audio")
    })

    @Test fun captureDucking() = capture("ducking", { session ->
        stageTwoLanes(session)
        openTool(TAG_VIDEO_TOOL_DUCKING, "Audio")
        onNodeWithTag(TAG_DUCKING_ENABLE).performClick()
        waitForIdle()
    })

    @Test fun captureEq() = capture("eq", { session ->
        stageTwoLanes(session)
        openTool(TAG_VIDEO_TOOL_EQ, "Audio")
    })

    @Test fun captureVoicePermission() = capture(
        "voice-permission",
        { openTool(TAG_VIDEO_TOOL_VOICE, "Audio") },
        CaptureVoiceRecorder(MicPermission.Requestable)
    )

    @Test fun captureVoiceReady() = capture(
        "voice-ready",
        { openTool(TAG_VIDEO_TOOL_VOICE, "Audio") },
        CaptureVoiceRecorder(MicPermission.Granted)
    )

    @Test fun captureExportOptions() = capture("export-options", {
        onNodeWithTag(TAG_VIDEO_SAVE).performClick()
        waitForIdle()
    })

    @Test
    fun captureExportProgressAndDone() = runDesktopComposeUiTest(width = 390, height = 884) {
        val gate = CompletableDeferred<VideoExportResult>()
        val session = stagedVideo(media = CaptureVideoMedia(gate))
        try {
            onNodeWithTag(TAG_VIDEO_SAVE).performClick()
            onNodeWithTag(TAG_VIDEO_EXPORT_CONFIRM).performClick()
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.exportProgress == 0.58f
            }
            waitForIdle()
            save(captureWindowImage(), "export-progress")
            gate.complete(
                VideoExportResult(
                    reference = "fixture://sample-coast-edit.mp4",
                    mimeType = "video/mp4",
                    size = PixelSize(1280, 720),
                    duration = EditorTime.fromMicroseconds(6_000_000)
                )
            )
            waitUntil(timeoutMillis = 15_000) { session.state.value.exported != null }
            waitForIdle()
            onNodeWithTag(TAG_VIDEO_EXPORT_DONE).assertIsEnabled()
            save(captureWindowImage(), "export-done")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureExportFailed() = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedVideo()
        try {
            onNodeWithTag(TAG_VIDEO_SAVE).performClick()
            onNodeWithTag(TAG_VIDEO_EXPORT_CONFIRM).performClick()
            // The fixture refuses a real export, which is a real failure.
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.error != null && !session.state.value.exporting
            }
            waitForIdle()
            onNodeWithTag(TAG_VIDEO_EXPORT_RETRY).assertIsEnabled()
            save(captureWindowImage(), "export-failed")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun trimResetLivesInThePanelHeaderAndRestoresTheSource() =
        runDesktopComposeUiTest(width = 390, height = 884) {
            val session = stagedVideo()
            try {
                openTool(TAG_VIDEO_TAB_TRIM, "Edit")
                onNodeWithTag(TAG_VIDEO_PANEL_RESET).assertIsNotEnabled()
                onNodeWithTag(TAG_VIDEO_TRIM_IN_PLUS).performClick()
                waitUntil(timeoutMillis = 5_000) { session.selected().sourceRange.start.ticks > 0L }
                waitForIdle()
                onNodeWithTag(TAG_VIDEO_PANEL_RESET).assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 5_000) { session.selected().sourceRange.start.ticks == 0L }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun colourChipSwapsTheRulerAndResetClearsTheClip() =
        runDesktopComposeUiTest(width = 390, height = 884) {
            val session = stagedVideo()
            try {
                openTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
                onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + "contrast").assertDoesNotExist()
                onNodeWithTag(TAG_VIDEO_COLOUR_PARAM_PREFIX + "contrast").performClick()
                waitForIdle()
                onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + "exposure").assertDoesNotExist()
                onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + "contrast")
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
                waitUntil(timeoutMillis = 5_000) {
                    session.selected().adjustments.contrast != 1f
                }
                waitForIdle()
                onNodeWithTag(TAG_VIDEO_PANEL_RESET).performClick()
                waitUntil(timeoutMillis = 5_000) {
                    session.selected().adjustments.contrast == 1f
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun tabletWidthPanelShowsEveryRulerWithoutChips() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val session = stagedVideo()
            try {
                openTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
                onNodeWithTag(TAG_VIDEO_COLOUR_PARAM_PREFIX + "contrast").assertDoesNotExist()
                listOf("exposure", "contrast", "highlights", "shadows").forEach { key ->
                    onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + key).assertExists()
                }
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun tonalControlsSitOneStepAboveThePanelInEveryScheme() {
        listOf(V3VideoDarkColors, V3VideoLightColors).forEach { scheme ->
            assertNotEquals(scheme.surfaceContainer, scheme.surfaceContainerHigh)
        }
        assertEquals(Color(0xFF004787), V3VideoDarkColors.primaryContainer)
    }

    @Test
    fun aTonalActionPaintsTheHighContainerOnTheScreen() =
        runDesktopComposeUiTest(width = 390, height = 884) {
            val session = stagedVideo()
            try {
                openTool(TAG_VIDEO_TAB_CLIPS, "Edit")
                val pixels = onNodeWithTag(TAG_VIDEO_ARRANGE_DUPLICATE)
                    .captureToImage().toPixelMap()
                // Past the corner, before the centred icon and label.
                assertEquals(
                    V3VideoDarkColors.surfaceContainerHigh,
                    pixels[8, pixels.height / 2]
                )
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun phoneLandscapeStacksTheTrimSteppers() =
        runDesktopComposeUiTest(width = 884, height = 390) {
            val session = stagedVideo()
            try {
                // Phone landscape opens on Trim in its narrow side panel.
                val inMinus = onNodeWithTag(TAG_VIDEO_TRIM_IN_MINUS).fetchSemanticsNode().boundsInRoot
                val outMinus = onNodeWithTag(TAG_VIDEO_TRIM_OUT_MINUS).fetchSemanticsNode().boundsInRoot
                assertEquals(inMinus.left, outMinus.left)
                assertTrue(outMinus.top >= inMinus.bottom)
            } finally {
                runOnUiThread { session.close() }
            }
        }

    @Test
    fun aWidePhoneKeepsChipsOverOneRuler() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo()
        try {
            openTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
            onNodeWithTag(TAG_VIDEO_COLOUR_PARAM_PREFIX + "contrast").assertExists()
            onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + "contrast").assertDoesNotExist()
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun arrangeDisablesReorderAtTheEnds() = runDesktopComposeUiTest(width = 390, height = 884) {
        val session = stagedVideo()
        try {
            runOnUiThread { session.seek(EditorTime.fromMicroseconds(1_000_000)) }
            waitUntil(timeoutMillis = 15_000) { !session.state.value.seeking }
            openTool(TAG_VIDEO_TAB_CLIPS, "Edit")
            onNodeWithTag(TAG_VIDEO_SPLIT).performClick()
            waitUntil(timeoutMillis = 5_000) {
                session.state.value.sequence?.tracks?.get(0)?.clips?.size == 2
            }
            waitForIdle()
            assertEquals("clip-1", session.state.value.selectedClipId)
            onNodeWithTag(TAG_VIDEO_MOVE_EARLIER).assertIsNotEnabled()
            onNodeWithTag(TAG_VIDEO_MOVE_LATER).assertIsEnabled()
        } finally {
            runOnUiThread { session.close() }
        }
    }

    private fun VideoEditorSession.selected() = state.value.sequence!!.tracks
        .flatMap { it.clips }
        .first { it.id == state.value.selectedClipId }
}
