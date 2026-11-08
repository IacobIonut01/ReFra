package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import com.dot.gallery.editor.core.time.TimeRange
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.video.AudioInfo
import com.dot.gallery.editor.render.video.MicPermission
import com.dot.gallery.editor.render.video.VideoEditorSession
import com.dot.gallery.editor.render.video.VideoEditorState
import com.dot.gallery.editor.render.video.VideoExportResult
import com.dot.gallery.editor.render.video.VideoFrame
import com.dot.gallery.editor.render.video.VideoInfo
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.render.video.VideoMediaAccess
import com.dot.gallery.editor.render.video.VideoPlayback
import com.dot.gallery.editor.render.video.VoiceRecorder
import com.dot.gallery.editor.render.video.VoiceSupport
import com.dot.gallery.editor.ui.TAG_HEADER_MORE
import com.dot.gallery.editor.ui.TAG_VIDEO_V3_INLINE_INSPECTOR
import com.dot.gallery.editor.ui.TestEditorRoot
import com.dot.gallery.editor.ui.captureWindowImage
import com.dot.gallery.editor.ui.samplePixels
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Native captures of the real [VideoEditorScreen] for design review, saved
 * under `build/review/`. A live [VideoEditorSession] runs the whole
 * preview/thumbnail/state pipeline; the media port is a fixture that
 * serves the real `editor_sample.jpg` pixels as decoded frames, so the
 * capture shows the actual preview bitmap path rather than a stand-in.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class VideoEditorCaptureTest {

    /**
     * Decoded frames are real pixels at frame size — the primary source
     * reads the sample image, an `overlay`-marked reference reads a
     * solid accent frame so a two-source capture shows visibly distinct
     * media on each lane. Every source probes with its own asset id, so
     * a multi-source sequence keeps real per-source identity.
     */
    private class CaptureVideoMedia : VideoMediaAccess {
        private val framePixels = samplePixels()
        private val overlayPixels = SrgbPixels(
            PixelSize(640, 360),
            IntArray(640 * 360) { 0xFF3E6FB0.toInt() }
        )

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

        override suspend fun frame(
            reference: String,
            at: EditorTime,
            maxEdge: Int
        ) = VideoFrame(
            at,
            if (reference.contains("overlay")) overlayPixels else framePixels
        )

        override suspend fun export(
            sequence: com.dot.gallery.editor.core.model.VideoSequence,
            onPublished: (VideoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): VideoExportResult = error("captures never export")

        override suspend fun probeAudio(reference: String) = AudioInfo(
            asset = MediaAsset.Audio(
                id = reference, reference = reference,
                duration = EditorTime.fromMicroseconds(6_000_000),
                sampleRate = 48_000, channels = 2
            ),
            displayName = reference.substringAfterLast('/')
        )

        override suspend fun waveform(
            reference: String,
            buckets: Int
        ): FloatArray = FloatArray(buckets) { i ->
            // A natural-looking envelope, distinct per source so two
            // lanes never paint the same shape.
            val phase = 0.11f + (reference.length % 5) * 0.03f
            (0.3f + 0.55f * kotlin.math.abs(
                kotlin.math.sin(i * phase + reference.length)
            )).coerceIn(0f, 1f)
        }

        override fun close() {}
    }

    private class IdlePlayback : VideoPlayback {
        override suspend fun play(
            sequence: com.dot.gallery.editor.core.model.VideoSequence,
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

    /**
     * A scripted mic port for the voice captures — [permission] decides
     * whether the surface shows the recorder rows or the
     * `x-mic-permission` card. The take itself never runs in a capture.
     */
    private class CaptureVoiceRecorder(
        var permission: MicPermission = MicPermission.Granted
    ) : VoiceRecorder {
        override val inputLabel = "Capture microphone"

        override suspend fun permissionState() = permission

        override suspend fun start(monitor: Boolean) =
            error("captures never record")
    }

    private fun ComposeUiTest.stagedVideo(
        darkTheme: Boolean,
        fontScale: Float,
        seekToUs: Long,
        fullTimeline: Boolean = true,
        voiceRecorder: VoiceRecorder? = null,
        onRequestMicrophone: (() -> Unit)? = null
    ): VideoEditorSession {
        lateinit var session: VideoEditorSession
        runOnUiThread {
            session = VideoEditorSession(
                CoroutineScope(Dispatchers.Main),
                CaptureVideoMedia(), IdlePlayback(),
                voiceRecorder = voiceRecorder
            ) {}
            session.open("fixture://sample-coast.mp4")
        }
        setContent {
            TestEditorRoot(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale)
                ) {
                    VideoEditorScreen(
                        session = session,
                        onClose = {},
                        onRetryOpen = {
                            session.open("fixture://sample-coast.mp4")
                        },
                        onRequestMicrophone = onRequestMicrophone,
                        // Review captures model a host with a picker.
                        onRequestAddMedia = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        awaitReady(session)
        if (seekToUs > 0) {
            runOnUiThread {
                session.seek(EditorTime.fromMicroseconds(seekToUs))
            }
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                !s.seeking && s.position.ticks > 0
            }
            awaitReady(session)
        }
        if (fullTimeline) awaitFilmstripTiles()
        return session
    }

    private fun ComposeUiTest.awaitReady(session: VideoEditorSession) {
        waitUntil(timeoutMillis = 30_000) {
            val s: VideoEditorState = session.state.value
            s.sequence != null && !s.opening && !s.seeking &&
                s.preview != null &&
                // All four filmstrip cells of the selected clip arrived —
                // a capture with empty tiles verifies nothing about the
                // real thumbnail path.
                s.thumbnails.count { it.clipId == s.selectedClipId } >= 4
        }
        waitForIdle()
    }

    /**
     * Waits for the lane's first four tile Image nodes — not just
     * `state.thumbnails`. The tiles render from bitmaps converted on a
     * background dispatcher inside `produceState`, which `waitForIdle`
     * does not track; only the composed nodes prove the filmstrip
     * actually painted before the screenshot runs.
     */
    private fun ComposeUiTest.awaitFilmstripTiles() {
        waitUntil(timeoutMillis = 15_000) {
            (0 until 4).all { i ->
                // Tile tags live inside the clip's merged clickable node —
                // they only exist in the unmerged tree.
                onAllNodesWithTag(
                    TAG_VIDEO_TILE_PREFIX + i,
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }
        waitForIdle()
    }

    @Test
    fun captureWide() = runDesktopComposeUiTest(width = 1280, height = 800) {
        // Seeked to 1 s: the timecode, timeline playhead and strip marker
        // all show real state.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            savePng(onRoot().captureToImage(), "redesign-video-wide")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun capturePortrait() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            savePng(onRoot().captureToImage(), "redesign-video-portrait")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLandscape() = runDesktopComposeUiTest(width = 800, height = 360) {
        // Short-landscape default: the timeline is collapsed to the mini
        // strip — the large preview IS the shipped state at this size, so
        // no filmstrip tiles exist to await here.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000,
            fullTimeline = false
        )
        try {
            savePng(onRoot().captureToImage(), "redesign-video-landscape")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLight() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val session = stagedVideo(
            darkTheme = false, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            savePng(onRoot().captureToImage(), "redesign-video-light")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Figma V2 canonical-phone capture (412 × 832 editor content) ──

    /**
     * Imports the overlay fixture through the real
     * `session.addVideoSource` path — the capture's second lane is a
     * genuinely probed/decoded source, not a painted row.
     */
    private fun ComposeUiTest.awaitOverlayImported(
        session: VideoEditorSession,
        awaitTiles: Boolean = true
    ) {
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
                s.preview != null &&
                // The imported clip becomes the selection — its four
                // filmstrip tiles prove the lane painted real media.
                s.thumbnails.count { it.clipId == s.selectedClipId } >= 4
        }
        waitForIdle()
        if (awaitTiles) awaitFilmstripTiles()
    }

    /**
     * Imports an audio source through the real `session.addAudioSource`
     * path and waits for the PCM-peak summary — the lane only paints a
     * real waveform once it lands.
     */
    private fun ComposeUiTest.awaitAudioImported(
        session: VideoEditorSession,
        reference: String = "fixture://song.mp3",
        trackCount: Int = 1
    ) {
        runOnUiThread { session.addAudioSource(reference) }
        waitUntil(timeoutMillis = 15_000) {
            val s = session.state.value
            !s.importing && !s.seeking &&
                (s.sequence?.audioTracks?.size ?: 0) >= trackCount &&
                (s.sequence?.audioSources?.size ?: 0) >= trackCount &&
                s.waveforms.size >= trackCount
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openDockDestination(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openCatalogueTool(
        tag: String,
        group: String? = null
    ) {
        openDockDestination(com.dot.gallery.editor.ui.video.TAG_VIDEO_TOOLS)
        if (group != null) {
            onNodeWithTag("editor.catalogue.group." + group)
                .performClick()
        }
        onNodeWithTag(tag).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    @Test
    fun captureFigmaV2Home() = runDesktopComposeUiTest(width = 412, height = 832) {
        // Seeked to 1 s so the timecode badge, transport readout, ruler
        // playhead and clip tiles all show real session state. Two real
        // trims plus one undo leave an applied edit AND both Undo/Redo
        // enabled, like the approved reference — never painted over. A
        // real second source lands at the playhead so the timeline shows
        // the V2 overlay lane above V1.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            runOnUiThread {
                session.trimSelected(
                    TimeRange(
                        EditorTime.fromMicroseconds(500_000),
                        EditorTime.fromMicroseconds(5_500_000)
                    )
                )
                session.trimSelected(
                    TimeRange(
                        EditorTime.fromMicroseconds(1_000_000),
                        EditorTime.fromMicroseconds(5_000_000)
                    )
                )
                session.undo()
            }
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                s.isDirty && s.canUndo && s.canRedo &&
                    !s.isDrafting && !s.seeking
            }
            awaitOverlayImported(session)
            savePng(onRoot().captureToImage(), "figma-v2-implementation/v-home")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2AddMedia() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            openDockDestination(TAG_VIDEO_MEDIA)
            savePng(
                captureWindowImage(),
                "figma-v2-implementation/v-add-media"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Arrange() = runDesktopComposeUiTest(width = 412, height = 832) {
        // Two sources so Arrange rows have a real selected clip and the
        // overlay lane exists to reorder against.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitOverlayImported(session)
            openCatalogueTool(TAG_VIDEO_TAB_CLIPS)
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-arrange"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Transform() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitOverlayImported(session)
            openCatalogueTool(TAG_VIDEO_TOOL_TRANSFORM, "Picture")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-transform"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Colour() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitOverlayImported(session)
            openCatalogueTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-colour"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Layers() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitOverlayImported(session)
            openDockDestination(TAG_VIDEO_LAYERS)
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-layers"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureXVideoDesktop() = runDesktopComposeUiTest(width = 1440, height = 960) {
        // The 4-pane composition with a real two-source sequence: bin
        // rows, composited stage and the V2-over-V1 desktop timeline.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitOverlayImported(session)
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/x-video-desktop"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2AddAudio() = runDesktopComposeUiTest(width = 412, height = 832) {
        // One imported lane so the sheet shows the "In this project"
        // roster above the Files/Record/Extract rows.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitAudioImported(session)
            openCatalogueTool(TAG_VIDEO_TOOL_ADD_AUDIO, "Audio")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-add-audio"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2ClipAudio() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitAudioImported(session)
            runOnUiThread {
                session.selectAudioClip(
                    session.state.value.sequence!!
                        .audioTracks.single().clips.single().id
                )
            }
            waitForIdle()
            openCatalogueTool(TAG_VIDEO_TOOL_CLIP_AUDIO, "Audio")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-clip-audio"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Mixer() = runDesktopComposeUiTest(width = 412, height = 832) {
        // Two real lanes → two lane cards plus Source sound and Master.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitAudioImported(session, "fixture://song.mp3")
            awaitAudioImported(session, "fixture://voice.wav", trackCount = 2)
            openCatalogueTool(TAG_VIDEO_TOOL_MIXER, "Audio")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-mixer"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Routing() = runDesktopComposeUiTest(width = 412, height = 832) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitAudioImported(session)
            openCatalogueTool(TAG_VIDEO_TOOL_ROUTING, "Audio")
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-routing"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Ducking() = runDesktopComposeUiTest(width = 412, height = 832) {
        // Two real lanes give the sidechain a target and a trigger; the
        // capture shows the recipe enabled — not the empty-lanes hint.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            awaitAudioImported(session, "fixture://song.mp3")
            awaitAudioImported(session, "fixture://voice.wav", trackCount = 2)
            openCatalogueTool(TAG_VIDEO_TOOL_DUCKING, "Audio")
            onNodeWithTag(TAG_DUCKING_ENABLE).performClick()
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                s.sequence?.audioTracks?.any { it.ducking.enabled } == true &&
                    !s.isDrafting
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-ducking"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Eq() = runDesktopComposeUiTest(width = 412, height = 832) {
        // The Voice-clarity preset so the response card draws the real
        // RBJ curve rather than the flat line.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            awaitAudioImported(session)
            openCatalogueTool(TAG_VIDEO_TOOL_EQ, "Audio")
            onNodeWithTag(TAG_EQ_PRESET_VOICE).performClick()
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                s.sequence?.audioTracks?.any { it.eq.active } == true &&
                    !s.isDrafting
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-eq"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV2Voice() = runDesktopComposeUiTest(width = 412, height = 832) {
        // A ready mic — the recorder rows (input, count-in, monitor)
        // over the red Record button.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000,
            voiceRecorder = CaptureVoiceRecorder()
        )
        try {
            openCatalogueTool(TAG_VIDEO_TOOL_VOICE, "Audio")
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.voiceSupport == VoiceSupport.Ready
            }
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/v-voice"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureXMicPermission() = runDesktopComposeUiTest(width = 412, height = 832) {
        // The ask-first state — rationale, Continue (the host's runtime
        // prompt seam) and Not now.
        var micPrompts = 0
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000,
            voiceRecorder = CaptureVoiceRecorder(MicPermission.Requestable),
            onRequestMicrophone = { micPrompts++ }
        )
        try {
            openCatalogueTool(TAG_VIDEO_TOOL_VOICE, "Audio")
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.voiceSupport ==
                    VoiceSupport.RequiresPermission
            }
            onNodeWithTag(TAG_MIC_CARD).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/x-mic-permission"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureXMixerDesktop() = runDesktopComposeUiTest(width = 1440, height = 960) {
        // The desktop task surface: strip bank plus the routing pane —
        // two real lanes give two channel strips beside Source and
        // Master.
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            awaitAudioImported(session, "fixture://song.mp3")
            awaitAudioImported(session, "fixture://voice.wav", trackCount = 2)
            onNodeWithTag(com.dot.gallery.editor.ui.TAG_HEADER_MORE).performClick()
            onNodeWithTag(TAG_VIDEO_MORE_MIXER).performClick()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/x-mixer-desktop"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureXMixerLight() = runDesktopComposeUiTest(width = 1440, height = 960) {
        val session = stagedVideo(
            darkTheme = false, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            awaitAudioImported(session, "fixture://song.mp3")
            awaitAudioImported(session, "fixture://voice.wav", trackCount = 2)
            onNodeWithTag(com.dot.gallery.editor.ui.TAG_HEADER_MORE).performClick()
            onNodeWithTag(TAG_VIDEO_MORE_MIXER).performClick()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/x-mixer-light"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureXVideoLandscape() = runDesktopComposeUiTest(width = 892, height = 412) {
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000,
            fullTimeline = false
        )
        try {
            awaitOverlayImported(session, awaitTiles = false)
            savePng(
                onRoot().captureToImage(),
                "figma-v2-implementation/x-video-landscape"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureLargeFont() = runDesktopComposeUiTest(width = 390, height = 844) {
        val session = stagedVideo(
            darkTheme = true, fontScale = 2f, seekToUs = 1_000_000
        )
        try {
            savePng(onRoot().captureToImage(), "redesign-video-largefont")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── Figma V3 captures ────────────────────────────────────────────
    // The review set for `editor/figma-review/v3-latest/` — the timeline
    // home, a compact inline tool surface and the desktop workspace.

    @Test
    fun captureFigmaV3HomeTimeline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        // Same real session recipe as the approved reference — 1 s
        // seek, two trims plus an undo (applied edit, Undo+Redo lit),
        // and a second probed source on the overlay lane.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            runOnUiThread {
                session.trimSelected(
                    TimeRange(
                        EditorTime.fromMicroseconds(500_000),
                        EditorTime.fromMicroseconds(5_500_000)
                    )
                )
                session.trimSelected(
                    TimeRange(
                        EditorTime.fromMicroseconds(1_000_000),
                        EditorTime.fromMicroseconds(5_000_000)
                    )
                )
                session.undo()
            }
            waitUntil(timeoutMillis = 15_000) {
                val s = session.state.value
                s.isDirty && s.canUndo && s.canRedo &&
                    !s.isDrafting && !s.seeking
            }
            awaitOverlayImported(session)
            savePng(
                onRoot().captureToImage(),
                "figma-v3-implementation/v-home-timeline"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3ToolInline() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        // An active tool rides the flat V3 inline panel over the
        // timeline — Arrange is the realest: a selected clip plus the
        // overlay lane to reorder against.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            awaitOverlayImported(session)
            openCatalogueTool(TAG_VIDEO_TAB_CLIPS)
            onNodeWithTag(TAG_VIDEO_V3_INLINE_INSPECTOR).assertIsDisplayed()
            waitForIdle()
            savePng(
                onRoot().captureToImage(),
                "figma-v3-implementation/v-tool-inline"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun captureFigmaV3Desktop() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        // The desktop workspace: media bin, stage and timeline lanes
        // with a real second source for the overlay row.
        val session = stagedVideo(
            darkTheme = true, fontScale = 1f, seekToUs = 1_000_000
        )
        try {
            awaitOverlayImported(session)
            savePng(
                onRoot().captureToImage(),
                "figma-v3-implementation/x-video-desktop"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    // ── R2: V4 video workspace review set (build/review/r2-v4/) ──────

    private fun ComposeUiTest.r2Session(
        darkTheme: Boolean = true,
        fontScale: Float = 1f,
        overlay: Boolean = true,
        audio: Boolean = false
    ): VideoEditorSession {
        val session = stagedVideo(
            darkTheme = darkTheme, fontScale = fontScale, seekToUs = 1_000_000
        )
        runOnUiThread {
            session.trimSelected(
                TimeRange(
                    EditorTime.fromMicroseconds(500_000),
                    EditorTime.fromMicroseconds(5_500_000)
                )
            )
        }
        waitUntil(timeoutMillis = 15_000) {
            val s = session.state.value
            s.isDirty && s.canUndo && !s.isDrafting && !s.seeking
        }
        if (overlay) awaitOverlayImported(session)
        if (audio) awaitAudioImported(session)
        return session
    }

    private fun ComposeUiTest.r2Capture(
        name: String,
        window: Boolean = false,
        darkTheme: Boolean = true,
        fontScale: Float = 1f,
        overlay: Boolean = true,
        audio: Boolean = false,
        steps: ComposeUiTest.(VideoEditorSession) -> Unit = {}
    ) {
        val session = r2Session(darkTheme, fontScale, overlay, audio)
        try {
            steps(session)
            waitForIdle()
            savePng(
                if (window) captureWindowImage() else onRoot().captureToImage(),
                "r2-v4/$name"
            )
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test
    fun r2PhoneHome() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-home", audio = true)
    }

    @Test
    fun r2PhoneHomeOneLane() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-home-one-lane", overlay = false)
    }

    @Test
    fun r2PhoneTools() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-tools", window = true) {
            openDockDestination(TAG_VIDEO_TOOLS)
        }
    }

    @Test
    fun r2PhoneTrim() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-trim") { openCatalogueTool(TAG_VIDEO_TAB_TRIM) }
    }

    @Test
    fun r2PhoneArrange() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-arrange") { openCatalogueTool(TAG_VIDEO_TAB_CLIPS) }
    }

    @Test
    fun r2PhoneTransform() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-transform") {
            openCatalogueTool(TAG_VIDEO_TOOL_TRANSFORM, "Picture")
        }
    }

    @Test
    fun r2PhoneColour() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-colour") {
            openCatalogueTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
        }
    }

    @Test
    fun r2PhoneLayers() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-layers") { openDockDestination(TAG_VIDEO_LAYERS) }
    }

    @Test
    fun r2PhoneAddMedia() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-add-media", window = true) {
            openDockDestination(TAG_VIDEO_MEDIA)
        }
    }

    @Test
    fun r2PhoneMixer() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-mixer", audio = true) {
            openCatalogueTool(TAG_VIDEO_TOOL_MIXER, "Audio")
        }
    }

    @Test
    fun r2PhoneExport() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-export", window = true) {
            openDockDestination(TAG_VIDEO_SAVE)
        }
    }

    @Test
    fun r2PhoneEmpty() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-empty", overlay = false) { session ->
            runOnUiThread { session.deleteSelected() }
            waitUntil(timeoutMillis = 15_000) {
                session.state.value.sequence?.tracks
                    ?.all { it.clips.isEmpty() } == true
            }
        }
    }

    @Test
    fun r2PhoneLargeFont() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-font2", fontScale = 2f)
    }

    @Test
    fun r2PhoneLight() = runDesktopComposeUiTest(width = 412, height = 832) {
        r2Capture("phone-light", darkTheme = false)
    }

    @Test
    fun r2Landscape() = runDesktopComposeUiTest(width = 892, height = 412) {
        r2Capture("landscape")
    }

    @Test
    fun r2TabletPortrait() = runDesktopComposeUiTest(width = 800, height = 1280) {
        r2Capture("tablet-portrait", audio = true)
    }

    @Test
    fun r2TabletLandscape() = runDesktopComposeUiTest(width = 1280, height = 800) {
        r2Capture("tablet-landscape", audio = true)
    }

    @Test
    fun r2Desktop() = runDesktopComposeUiTest(width = 1440, height = 960) {
        r2Capture("desktop", audio = true)
    }

    @Test
    fun r2DesktopExport() = runDesktopComposeUiTest(width = 1440, height = 960) {
        r2Capture("desktop-export", window = true) {
            openDockDestination(TAG_VIDEO_SAVE)
        }
    }

    private enum class StudioState { Home, Tools, More, Focus, Empty }

    /** One state of one studio form, under `build/review/studio-forms/`. */
    private fun studioForm(
        form: String,
        width: Int,
        height: Int,
        state: StudioState
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val session = stagedVideo(darkTheme = true, fontScale = 1f, seekToUs = 1_000_000)
        try {
            when (state) {
                StudioState.Home -> {
                    awaitOverlayImported(session)
                    awaitAudioImported(session)
                }
                StudioState.Tools -> onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
                StudioState.More -> onNodeWithTag(TAG_HEADER_MORE).performClick()
                StudioState.Focus -> {
                    onNodeWithTag(TAG_HEADER_MORE).performClick()
                    // Landscape forms also keep a focus button on the
                    // transport; the sheet's row is the last composed.
                    onAllNodesWithTag(TAG_VIDEO_FOCUS).onLast().performClick()
                }
                StudioState.Empty -> {
                    runOnUiThread { session.deleteSelected() }
                    waitUntil(timeoutMillis = 15_000) {
                        session.state.value.sequence?.tracks
                            ?.all { it.clips.isEmpty() } == true
                    }
                }
            }
            waitForIdle()
            savePng(captureWindowImage(), "studio-forms/$form-${state.name.lowercase()}")
        } finally {
            runOnUiThread { session.close() }
        }
    }

    @Test fun captureStudioPhoneHome() = studioForm("phone", 390, 884, StudioState.Home)

    @Test fun captureStudioPhoneTools() = studioForm("phone", 390, 884, StudioState.Tools)

    @Test fun captureStudioPhoneMore() = studioForm("phone", 390, 884, StudioState.More)

    @Test fun captureStudioPhoneFocus() = studioForm("phone", 390, 884, StudioState.Focus)

    @Test fun captureStudioPhoneEmpty() = studioForm("phone", 390, 884, StudioState.Empty)

    @Test fun captureStudioPhoneLandscapeHome() = studioForm("phoneLandscape", 884, 390, StudioState.Home)

    @Test fun captureStudioPhoneLandscapeTools() = studioForm("phoneLandscape", 884, 390, StudioState.Tools)

    @Test fun captureStudioPhoneLandscapeMore() = studioForm("phoneLandscape", 884, 390, StudioState.More)

    @Test fun captureStudioPhoneLandscapeFocus() = studioForm("phoneLandscape", 884, 390, StudioState.Focus)

    @Test fun captureStudioPhoneLandscapeEmpty() = studioForm("phoneLandscape", 884, 390, StudioState.Empty)

    @Test fun captureStudioTabletPortraitHome() = studioForm("tabletPortrait", 800, 1280, StudioState.Home)

    @Test fun captureStudioTabletPortraitTools() = studioForm("tabletPortrait", 800, 1280, StudioState.Tools)

    @Test fun captureStudioTabletPortraitMore() = studioForm("tabletPortrait", 800, 1280, StudioState.More)

    @Test fun captureStudioTabletPortraitFocus() = studioForm("tabletPortrait", 800, 1280, StudioState.Focus)

    @Test fun captureStudioTabletPortraitEmpty() = studioForm("tabletPortrait", 800, 1280, StudioState.Empty)

    @Test fun captureStudioTabletLandscapeHome() = studioForm("tabletLandscape", 1280, 800, StudioState.Home)

    @Test fun captureStudioTabletLandscapeTools() = studioForm("tabletLandscape", 1280, 800, StudioState.Tools)

    @Test fun captureStudioTabletLandscapeMore() = studioForm("tabletLandscape", 1280, 800, StudioState.More)

    @Test fun captureStudioTabletLandscapeFocus() = studioForm("tabletLandscape", 1280, 800, StudioState.Focus)

    @Test fun captureStudioTabletLandscapeEmpty() = studioForm("tabletLandscape", 1280, 800, StudioState.Empty)

    private fun savePng(bitmap: ImageBitmap, name: String) {
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG encode failed")
        val dir = File("build/review").apply { mkdirs() }
        File(dir, "$name.png")
            .apply { parentFile?.mkdirs() }
            .writeBytes(data.bytes)
    }
}
