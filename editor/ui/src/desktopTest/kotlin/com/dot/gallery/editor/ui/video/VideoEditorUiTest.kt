package com.dot.gallery.editor.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.AudioEq
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.model.VideoContentMode
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
import com.dot.gallery.editor.render.video.VideoInsertion
import com.dot.gallery.editor.render.video.VideoLevels
import com.dot.gallery.editor.render.video.VideoMediaAccess
import com.dot.gallery.editor.render.video.VideoPlayback
import com.dot.gallery.editor.render.video.VoiceCapture
import com.dot.gallery.editor.render.video.VoiceRecorder
import com.dot.gallery.editor.render.video.VoiceRecordingState
import com.dot.gallery.editor.render.video.VoiceSupport
import com.dot.gallery.editor.render.video.VoiceTake
import com.dot.gallery.editor.ui.TAG_CATALOGUE_GRID
import com.dot.gallery.editor.ui.TAG_HEADER_MORE
import com.dot.gallery.editor.ui.EditorToolIds
import com.dot.gallery.editor.ui.EditorToolGroup
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import com.dot.gallery.editor.ui.TAG_CATALOGUE_HANDLE
import com.dot.gallery.editor.ui.TAG_CATALOGUE_SEARCH
import com.dot.gallery.editor.ui.TAG_RECOVERY_DISCARD
import com.dot.gallery.editor.ui.TAG_RECOVERY_RELINK_PREFIX
import com.dot.gallery.editor.ui.TAG_RECOVERY_RETRY
import com.dot.gallery.editor.ui.TAG_RECOVERY_SURFACE
import com.dot.gallery.editor.ui.TAG_VALUE_CONFIRM
import com.dot.gallery.editor.ui.TAG_VALUE_FIELD
import com.dot.gallery.editor.ui.TAG_VALUE_FIELD_SECOND
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_HEADER
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_PREVIEW
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TIMELINE
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TOOLBAR
import com.dot.gallery.editor.ui.TAG_VIDEO_REGION_TRANSPORT
import com.dot.gallery.editor.ui.TAG_VIDEO_TIMELINE_EFFECT_PREFIX
import com.dot.gallery.editor.ui.TestEditorRoot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end UI tests for the video editor: a real [VideoEditorSession]
 * over in-memory media/playback fakes drives [VideoEditorScreen] — the
 * draft/enabled interlocks these cover cannot be reproduced by
 * recording-only action stubs.
 */
@OptIn(ExperimentalTestApi::class)
class VideoEditorUiTest {

    private class FakePlayback : VideoPlayback {
        var playGate: CompletableDeferred<Unit>? = null
        var plays = 0
        var emitPosition: Boolean = false
        var levelsToEmit: VideoLevels? = null
        var closed = false

        override suspend fun play(
            sequence: VideoSequence,
            from: EditorTime,
            muted: Boolean,
            onFrame: (VideoFrame) -> Unit,
            onPosition: (EditorTime) -> Unit,
            onLevels: (VideoLevels) -> Unit
        ) {
            plays++
            levelsToEmit?.let { onLevels(it) }
            playGate?.await()
            if (emitPosition) onPosition(sequence.duration)
        }

        override fun close() {
            closed = true
        }
    }

    private open class FakeVideoMedia(
        private val durationUs: Long = 4_000_000L
    ) : VideoMediaAccess {
        var exportPrePublishGate: CompletableDeferred<Unit>? = null
        var closed = false
        /** References returning [overlayArgb] instead of the tick frame. */
        val overlayReferences = mutableSetOf<String>()
        var overlayArgb: Int? = null
        var audioGate: CompletableDeferred<Unit>? = null
        var audioProbeError: Throwable? = null
        var waveformGate: CompletableDeferred<Unit>? = null

        override suspend fun probeAudio(reference: String): AudioInfo {
            audioGate?.await()
            audioProbeError?.let { throw it }
            return AudioInfo(
                asset = MediaAsset.Audio(
                    id = reference, reference = reference,
                    duration = EditorTime.fromMicroseconds(durationUs),
                    sampleRate = 48_000, channels = 2
                ),
                displayName = reference.substringAfterLast('/')
            )
        }

        override suspend fun waveform(
            reference: String,
            buckets: Int
        ): FloatArray {
            waveformGate?.await()
            return FloatArray(buckets) { i -> ((i * 37) % 10) / 10f }
        }

        override suspend fun probe(reference: String) = VideoInfo(
            // One asset id per reference — a second probed source keeps
            // its own identity instead of colliding with the first.
            asset = MediaAsset.Video(
                id = reference, reference = reference,
                size = PixelSize(320, 240),
                duration = EditorTime.fromMicroseconds(durationUs),
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
            if (overlayArgb != null &&
                overlayReferences.contains(reference)
            ) {
                // Canvas-aspect so a Fit overlay covers the whole
                // composite — corner pixels read the overlay colour.
                SrgbPixels(
                    PixelSize(320, 240),
                    IntArray(320 * 240) { overlayArgb!! }
                )
            } else {
                SrgbPixels(
                    PixelSize(4, 4),
                    IntArray(16) {
                        0xFF000000.toInt() or
                            (at.ticks and 0xFFFFFF).toInt()
                    }
                )
            }
        )

        override suspend fun export(
            sequence: VideoSequence,
            onPublished: (VideoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): VideoExportResult {
            exportPrePublishGate?.await()
            onProgress(0.9f)
            val result = VideoExportResult(
                "out://video/1", "video/mp4", sequence.size, sequence.duration
            )
            onPublished(result)
            onProgress(1f)
            return result
        }

        override fun close() {
            closed = true
        }
    }

    /** A scripted mic take — stop resolves through [stopGate]. */
    private class FakeVoiceCapture : VoiceCapture {
        var stopGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var cancelled = false
        var stops = 0

        override fun level() = 0.6f

        override suspend fun stop(): VoiceTake {
            stops++
            stopGate?.await()
            return VoiceTake(
                reference = "file:///cache/voice-take.wav",
                displayName = "voice-take.wav",
                duration = EditorTime.fromMicroseconds(2_000_000),
                sampleRate = 48_000,
                channels = 1
            )
        }

        override fun cancel() {
            cancelled = true
        }
    }

    /** The session's mic seam — permission scripted per test. */
    private class FakeVoiceRecorder(
        var permission: MicPermission = MicPermission.Granted
    ) : VoiceRecorder {
        var starts = 0
        var lastMonitor: Boolean? = null
        val capture = FakeVoiceCapture()

        override val inputLabel = "Fixture microphone"

        override suspend fun permissionState() = permission

        override suspend fun start(monitor: Boolean): VoiceCapture {
            starts++
            lastMonitor = monitor
            return capture
        }
    }

    private var session: VideoEditorSession? = null
    private var media = FakeVideoMedia()
    private var playback = FakePlayback()

    @AfterTest
    fun tearDown() {
        session?.close()
        session = null
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    private fun ComposeUiTest.liveEditor(
        widthDp: Float = 390f,
        heightDp: Float = 844f,
        fontScale: Float = 1f,
        onClose: () -> Unit = {},
        onRequestAddMedia: ((com.dot.gallery.editor.render.video.VideoInsertion) -> Unit)? = null,
        onRequestAddAudio: (() -> Unit)? = null,
        voiceRecorder: VoiceRecorder? = null,
        onRequestMicrophone: (() -> Unit)? = null,
        onRequestRelink: ((String) -> Unit)? = null
    ): VideoEditorSession {
        lateinit var s: VideoEditorSession
        runOnUiThread {
            s = VideoEditorSession(
                CoroutineScope(Dispatchers.Main),
                media, playback, voiceRecorder = voiceRecorder
            ) {}
            s.open("fixture://sample-coast.mp4")
        }
        session = s
        setContent {
            TestEditorRoot {
                CompositionLocalProvider(
                    LocalDensity provides
                        Density(LocalDensity.current.density, fontScale)
                ) {
                    Box(Modifier.size(widthDp.dp, heightDp.dp).fillMaxSize()) {
                        VideoEditorScreen(
                            session = s,
                            onClose = onClose,
                            onRetryOpen = { s.open("fixture://sample-coast.mp4") },
                            onRequestAddMedia = onRequestAddMedia,
                            onRequestAddAudio = onRequestAddAudio,
                            onRequestMicrophone = onRequestMicrophone,
                            onRequestRelink = onRequestRelink
                        )
                    }
                }
            }
        }
        awaitReady(s)
        return s
    }

    /**
     * The compact route into a real tool surface: dock Tools → the
     * catalogue sheet's cell for the tool. The grid is lazy, so the cell
     * may need a scroll at large font scales.
     */
    private fun ComposeUiTest.openVideoTool(
        toolTag: String,
        group: String? = null
    ) {
        onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        if (group != null) {
            onNodeWithTag("editor.catalogue.group." + group)
                .performClick()
        }
        onNodeWithTag(toolTag).performScrollTo()
        .performSemanticsAction(SemanticsActions.OnClick)
    }

    /**
     * Undo from wherever the current layout keeps it — the utility pill
     * on the phone home, the rail/header on wide layouts, or the tool
     * header's More menu while a phone tool is open.
     */
    private fun ComposeUiTest.clickUndo() {
        if (onAllNodesWithTag(TAG_VIDEO_UNDO).fetchSemanticsNodes().isEmpty()) {
            onNodeWithTag(TAG_HEADER_MORE).performClick()
        }
        onNodeWithTag(TAG_VIDEO_UNDO).performClick()
    }

    private fun ComposeUiTest.awaitReady(
        s: VideoEditorSession,
        timeoutMs: Long = 15_000
    ) {
        waitUntil(timeoutMillis = timeoutMs) {
            s.state.value.sequence != null && !s.state.value.opening
        }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitState(
        timeoutMs: Long = 15_000,
        condition: (VideoEditorState) -> Boolean
    ) {
        val s = session ?: error("no session")
        waitUntil(timeoutMillis = timeoutMs) { condition(s.state.value) }
    }

    // ---- transport / timeline ----

    @Test
    fun timelineScrubPausesAndResumesPlayback() = runComposeUiTest {
        val s = liveEditor()
        playback.playGate = CompletableDeferred()
        onNodeWithTag(TAG_VIDEO_PLAY).performClick()
        awaitState { it.playing }

        // Dragging the scrubber pauses for the gesture.
        onNodeWithTag(TAG_VIDEO_TIMELINE).performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.5f, 0f))
        }
        awaitState { !it.playing }
        onNodeWithTag(TAG_VIDEO_TIMELINE).performTouchInput { up() }
        // Release resumed playback — the gesture captured the intent.
        awaitState { it.playing }
        runOnUiThread { s.pause() }
        awaitState { !it.playing }
    }

    @Test
    fun quickScrubWhilePlayingResumesWithoutRecomposition() =
        runComposeUiTest {
            val s = liveEditor()
            playback.playGate = CompletableDeferred()
            onNodeWithTag(TAG_VIDEO_PLAY).performClick()
            awaitState { it.playing }

            // One injected batch — down, move, up with no waits. The
            // intent is captured on pointer down at the Initial pass, so
            // the first seek's pause cannot poison the snapshot and the
            // release resumes even though nothing recomposed mid-gesture.
            onNodeWithTag(TAG_VIDEO_TIMELINE).performTouchInput {
                down(center)
                moveTo(center + Offset(center.x * 0.4f, 0f))
                up()
            }
            awaitState { it.playing }
            runOnUiThread { s.pause() }
            awaitState { !it.playing }
        }

    @Test
    fun frameStepsSeekOneFrame() = runComposeUiTest {
        val s = liveEditor()
        val tpf = FrameRate(30).ticksPerFrame
        // The V4 phone transport carries Play, timecode and sound only;
        // frame stepping is the arrow keys on the focused preview.
        onNodeWithTag(TAG_VIDEO_PREVIEW).performSemanticsAction(
            SemanticsActions.RequestFocus
        )
        fun press(key: Key) = onNodeWithTag(TAG_VIDEO_PREVIEW)
            .performKeyInput { keyDown(key); keyUp(key) }
        press(Key.DirectionRight)
        awaitState { it.position == EditorTime(tpf) && !it.seeking }
        press(Key.DirectionRight)
        awaitState { it.position == EditorTime(2 * tpf) && !it.seeking }
        press(Key.DirectionLeft)
        awaitState { it.position == EditorTime(tpf) && !it.seeking }
        // Stepping back at 0 clamps, never wraps.
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        awaitState { it.position == EditorTime.Zero && !it.seeking }
    }

    // ---- trim inspector ----

    @Test
    fun trimDragCommitsExactlyOneUndoStep() = runComposeUiTest {
        val s = liveEditor()
        // The trim form lives in the Tools sheet's Trim surface — open
        // it, then drag the start handle rightwards: commit fires once,
        // snapped to a frame boundary.
        openVideoTool(TAG_VIDEO_TAB_TRIM)
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput {
            down(Offset(left + 8f, centerY))
            moveTo(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.3f, centerY))
            up()
        }
        // Dirty turns true during the draft — wait for the Stop to commit
        // it, otherwise canUndo is read before history lands.
        awaitState { it.isDirty && !it.seeking && !it.isDrafting }
        val clip = s.state.value.sequence!!.tracks[0].clips.single()
        assertTrue(
            clip.sourceRange.start.ticks > 0L,
            "expected a trimmed in-point, got ${clip.sourceRange.start}"
        )
        assertEquals(
            0L,
            clip.sourceRange.start.ticks % FrameRate(30).ticksPerFrame,
            "committed in-point must sit on a source frame boundary"
        )
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState {
            it.sequence?.tracks?.get(0)?.clips?.single()?.sourceRange?.start ==
                EditorTime.Zero
        }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun midGestureResizeCancelsTrimDraft() = runComposeUiTest {
        // As with the photo slider: Skiko cannot inject a pointer cancel,
        // so the cancel path is disposing the RangeSlider mid-gesture —
        // compact → sidebar replaces the inspector branch.
        lateinit var s: VideoEditorSession
        runOnUiThread {
            s = VideoEditorSession(
                CoroutineScope(Dispatchers.Main), media, playback
            ) {}
            s.open("fixture://sample-coast.mp4")
        }
        session = s
        var winWidth by mutableStateOf(390.dp)
        var winHeight by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(winWidth, winHeight)) {
                    VideoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://sample-coast.mp4") }
                    )
                }
            }
        }
        awaitReady(s)

        openVideoTool(TAG_VIDEO_TAB_TRIM)
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput {
            down(Offset(left + 8f, centerY))
            moveTo(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.3f, centerY))
            // Pointer deliberately held.
        }
        waitForIdle()
        assertTrue(s.state.value.isDrafting)

        winWidth = 1280.dp
        winHeight = 800.dp
        waitForIdle()
        // The disposed slider dropped the draft — nothing committed.
        assertFalse(s.state.value.isDirty)
        assertFalse(s.state.value.canUndo)

        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput { up() }
        // A fresh drag still commits normally afterwards. Pointer id 1:
        // the pointer that was held across the disposal may still be
        // latched in the dispatcher's bookkeeping.
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput {
            down(1, Offset(left + 8f, centerY))
            moveTo(1, Offset(width * 0.25f, centerY))
            moveTo(1, Offset(width * 0.3f, centerY))
            up(1)
        }
        waitForIdle()
        awaitState { it.isDirty }
        // Stop the debounced thumbnail publish before the scene tears down —
        // a state write landing after dispose queues a redraw on the dead
        // owner (RootNodeOwner is already disposed).
        runOnUiThread { s.close() }
        waitForIdle()
    }

    @Test
    fun trimDragKeepsAnUnmovedOutPointExact() = runComposeUiTest {
        // 4.021 s source: the committed out-point 2 s sits on a frame
        // boundary, but its Float fraction round-trips to ~60.0005 frames —
        // re-snapping an untouched endpoint would ceil it one frame too
        // far.
        media = FakeVideoMedia(durationUs = 4_021_000L)
        val s = liveEditor()
        runOnUiThread {
            s.trimSelected(TimeRange(us(0), us(2_000_000)))
        }
        awaitState { it.sequence?.duration == us(2_000_000) }

        // Drag only the In handle rightwards in a single injected batch.
        openVideoTool(TAG_VIDEO_TAB_TRIM)
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput {
            down(Offset(left + 8f, centerY))
            moveTo(Offset(width * 0.25f, centerY))
            up()
        }
        awaitState {
            it.sequence?.tracks?.single()?.clips?.single()
                ?.sourceRange?.start != EditorTime.Zero
        }
        val clip = s.state.value.sequence!!.tracks[0].clips.single()
        assertTrue(clip.sourceRange.start > EditorTime.Zero)
        // The untouched out-point survived at exactly 2 s.
        assertEquals(us(2_000_000), clip.sourceRange.endExclusive)
    }

    @Test
    fun anUnmovedTrimTouchCommitsNoHistory() = runComposeUiTest {
        val s = liveEditor()
        // Press and release where the in-thumb already sits: the press
        // snaps the nearest thumb to the touch point, so landing at or
        // inside the thumb center clamps back to fraction 0 — the range
        // is unchanged and nothing lands on the undo stack. (A tap
        // further along the bare track is a real reposition, not a
        // no-op touch.)
        openVideoTool(TAG_VIDEO_TAB_TRIM)
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).performTouchInput {
            down(Offset(left + 1f, centerY))
            up()
        }
        waitForIdle()
        assertFalse(s.state.value.isDirty)
        assertFalse(s.state.value.canUndo)
    }

    // ---- clip strip + toolbar ----

    @Test
    fun splitSelectDeleteUndoRoundTrip() = runComposeUiTest {
        val s = liveEditor()
        runOnUiThread { s.seek(us(1_000_000)) }
        awaitState { !it.seeking }
        // The compact clip commands live in the Tools sheet's Clips
        // surface — the timeline lane is only a selector there.
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_SPLIT).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
        assertEquals("clip-1", s.state.value.selectedClipId)

        // Selecting the second clip keeps the playhead where it is.
        runOnUiThread { s.selectClip("clip-2") }
        awaitState {
            it.selectedClipId == "clip-2" && it.position == us(1_000_000)
        }

        onNodeWithTag(TAG_VIDEO_DELETE).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 1 }
        assertEquals("clip-1", s.state.value.selectedClipId)

        clickUndo()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
    }

    @Test
    fun reorderButtonsRespectBounds() = runComposeUiTest {
        // Wide layout: the timeline command row owns Split; Arrange opens
        // the inspector whose Earlier/Later move the selection.
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_COMMAND_ARRANGE).performClick()
        // Single clip: both moves disabled, split disabled at position 0.
        onNodeWithTag(TAG_VIDEO_MOVE_EARLIER + ".arrange").assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_MOVE_LATER + ".arrange").assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_SPLIT).assertIsNotEnabled()

        runOnUiThread { s.seek(us(1_000_000)) }
        awaitState { !it.seeking }
        onNodeWithTag(TAG_VIDEO_SPLIT).assertIsEnabled().performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }

        // Left part selected: earlier still disabled, later enabled.
        onNodeWithTag(TAG_VIDEO_MOVE_EARLIER + ".arrange").assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_MOVE_LATER + ".arrange").assertIsEnabled()
        onNodeWithTag(TAG_VIDEO_MOVE_LATER + ".arrange").performClick()
        awaitState {
            it.sequence?.tracks?.get(0)?.clips?.first()?.id == "clip-2"
        }
        onNodeWithTag(TAG_VIDEO_MOVE_LATER + ".arrange").assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_MOVE_EARLIER + ".arrange").assertIsEnabled()
    }

    @Test
    fun emptyTimelineKeepsNavigation() = runComposeUiTest {
        val s = liveEditor()
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_DELETE).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.isEmpty() == true }
        // The empty state lives on the timeline — leave the Clips
        // surface back to it.
        onNodeWithTag(TAG_VIDEO_CONTEXT_BACK).performClick()
        // The empty card + Undo; Close still there; no transport to
        // play nothing; Export disabled.
        onNodeWithTag(TAG_VIDEO_EMPTY_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_PLAY).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_SAVE).assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_EMPTY_UNDO).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 1 }
    }

    // ---- export + close ----

    @Test
    fun exportProgressCancelAndDirtyClose() = runComposeUiTest {
        var closed = false
        media.exportPrePublishGate = CompletableDeferred()
        val s = liveEditor(onClose = { closed = true })
        // Make an edit so the sequence is dirty.
        runOnUiThread {
            s.trimSelected(TimeRange(us(500_000), us(3_500_000)))
        }
        awaitState { it.isDirty }

        // The header check opens the export sheet; its primary starts.
        onNodeWithTag(TAG_VIDEO_SAVE).performClick()
        onNodeWithTag(TAG_VIDEO_EXPORT_CONFIRM).performClick()
        awaitState { it.exporting }
        onNodeWithTag(TAG_VIDEO_EXPORT_STRIP).assertExists()
        onNodeWithTag(TAG_VIDEO_EXPORT_SHEET_CANCEL).performClick()
        awaitState { !it.exporting }
        // A cancelled run closes the sheet back to the editor.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(TAG_VIDEO_EXPORT_SHEET)
                .fetchSemanticsNodes().isEmpty()
        }

        // Still dirty: Back asks, confirm closes exactly once.
        onNodeWithTag(TAG_VIDEO_BACK).performClick()
        onNodeWithTag(TAG_VIDEO_DISCARD_CONFIRM).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_DISCARD_CONFIRM).performClick()
        assertTrue(closed)
    }

    @Test
    fun resizePreservesSelectedClip() = runComposeUiTest {
        lateinit var s: VideoEditorSession
        runOnUiThread {
            s = VideoEditorSession(
                CoroutineScope(Dispatchers.Main), media, playback
            ) {}
            s.open("fixture://sample-coast.mp4")
        }
        session = s
        var width by mutableStateOf(390.dp)
        var height by mutableStateOf(844.dp)
        setContent {
            TestEditorRoot {
                Box(Modifier.size(width, height)) {
                    VideoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://sample-coast.mp4") }
                    )
                }
            }
        }
        awaitReady(s)
        runOnUiThread { s.seek(us(1_000_000)) }
        awaitState { !it.seeking }
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_SPLIT).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
        runOnUiThread { s.selectClip("clip-2") }

        width = 1280.dp
        height = 800.dp
        waitForIdle()
        assertEquals("clip-2", s.state.value.selectedClipId)
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-2").assertIsSelected()
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-1").assertIsNotSelected()
        // Stop the debounced thumbnail publish before the scene tears down —
        // a state write landing after dispose queues a redraw on the dead
        // owner (RootNodeOwner is already disposed).
        runOnUiThread { s.close() }
        waitForIdle()
    }

    // ---- a11y / input ----

    @Test
    fun largeFontKeepsVideoActionsReachable() = runComposeUiTest {
        liveEditor(fontScale = 2f)
        onNodeWithTag(TAG_VIDEO_BACK).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_UNDO).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_REDO).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_SAVE).assertIsDisplayed().assertIsEnabled()
        onNodeWithTag(TAG_VIDEO_PLAY).assertIsDisplayed()
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_SPLIT).assertExists()
    }

    @Test
    fun keyboardShortcutsDrivePlaybackUndoAndSave() = runComposeUiTest {
        val s = liveEditor()
        playback.playGate = CompletableDeferred()
        media.exportPrePublishGate = CompletableDeferred()

        // Focus the preview surface, then Space toggles playback.
        onNodeWithTag(TAG_VIDEO_PREVIEW).performSemanticsAction(
            SemanticsActions.RequestFocus
        )
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.Spacebar); keyUp(Key.Spacebar)
        }
        awaitState { it.playing }
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.Spacebar); keyUp(Key.Spacebar)
        }
        awaitState { !it.playing }

        // Ctrl+Z / Ctrl+Shift+Z route through undo/redo (nothing yet —
        // no history), then make an edit and undo it via the keyboard.
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Z); keyUp(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        runOnUiThread {
            s.trimSelected(TimeRange(us(0), us(1_000_000)))
        }
        awaitState { it.isDirty }
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.Z); keyUp(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        awaitState {
            it.sequence?.duration == us(4_000_000) && !it.isDirty
        }

        // Ctrl+S starts the export.
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.S); keyUp(Key.S)
            keyUp(Key.CtrlLeft)
        }
        awaitState { it.exporting }
        media.exportPrePublishGate?.complete(Unit)
        awaitState { it.exported != null }
        assertEquals("out://video/1", s.state.value.exported?.reference)
    }

    @Test
    fun muteToggleOnlyAffectsPreviewMonitoring() = runComposeUiTest {
        // Mute monitors the preview only — a wide-layout transport
        // command (the compact transport carries no mute per the V2
        // anatomy; it returns with the Audio destination's backend).
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_MUTE).assertIsEnabled().performClick()
        awaitState { it.muted }
        // The export still carries audio — muting never edits the sequence.
        assertFalse(s.state.value.isDirty)
        onNodeWithTag(TAG_VIDEO_MUTE).performClick()
        awaitState { !it.muted }
    }

    @Test
    fun errorRetryCallsOpenOnSessionBackedScreen() = runComposeUiTest {
        // Failing media: open lands the Open error with a retry affordance.
        media = object : FakeVideoMedia() {
            override suspend fun probe(reference: String): VideoInfo =
                throw IllegalStateException("probe failed")
        }
        lateinit var s: VideoEditorSession
        runOnUiThread {
            s = VideoEditorSession(
                CoroutineScope(Dispatchers.Main), media, playback
            ) {}
            s.open("fixture://bad.mp4")
        }
        session = s
        var retried = false
        setContent {
            TestEditorRoot {
                Box(Modifier.size(390.dp, 844.dp)) {
                    VideoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { retried = true }
                    )
                }
            }
        }
        awaitState { it.error != null && !it.opening }
        onNodeWithTag(TAG_VIDEO_ERROR).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_ERROR_RETRY).performClick()
        assertTrue(retried)
    }

    @Test
    fun everyToolbarActionIsALabelledClickableNode() = runComposeUiTest {
        liveEditor(1280f, 800f)
        val tags = listOf(
            TAG_VIDEO_COMMAND_TRIM, TAG_VIDEO_SPLIT, TAG_VIDEO_DELETE,
            TAG_VIDEO_COMMAND_ARRANGE, TAG_VIDEO_ZOOM_OUT, TAG_VIDEO_ZOOM_FIT,
            TAG_VIDEO_ZOOM_IN
        )
        val bounds = tags.map { tag ->
            val node = onNodeWithTag(tag).fetchSemanticsNode()
            // One merged button per action: announced label, Button role,
            // a real click action — never a bare unlabelled icon.
            assertTrue(
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.isNotEmpty() == true,
                "$tag is missing a content description"
            )
            assertTrue(
                node.config.getOrNull(SemanticsProperties.Role) ==
                    Role.Button,
                "$tag is missing Role.Button"
            )
            assertTrue(
                node.config.getOrNull(SemanticsActions.OnClick) != null,
                "$tag is missing an OnClick action"
            )
            node.boundsInRoot
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                assertFalse(
                    bounds[i].overlaps(bounds[j]),
                    "toolbar buttons ${tags[i]} / ${tags[j]} overlap"
                )
            }
        }
    }

    @Test
    fun largeFontCommandsStayDisjointAndReachable() = runComposeUiTest {
        liveEditor(fontScale = 2f)
        // The compact clip commands live in the Tools sheet's Clips
        // surface — split/delete/reorder plus the zoom trio, all reachable
        // at any font scale.
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        val tags = listOf(TAG_VIDEO_SPLIT, TAG_VIDEO_DELETE)
        val bounds = tags.map { tag ->
            onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
                .boundsInRoot
        }
        assertFalse(
            bounds[0].overlaps(bounds[1]),
            "command buttons ${tags[0]} / ${tags[1]} overlap"
        )
        // The reorder commands share the grid and stay reachable by scroll.
        listOf(TAG_VIDEO_MOVE_EARLIER, TAG_VIDEO_MOVE_LATER).forEach { tag ->
            onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun shortLandscapeKeepsAllNudgeButtonsReachable() = runComposeUiTest {
        lateinit var s: VideoEditorSession
        runOnUiThread {
            s = VideoEditorSession(
                CoroutineScope(Dispatchers.Main), media, playback
            ) {}
            s.open("fixture://sample-coast.mp4")
        }
        session = s
        setContent {
            TestEditorRoot {
                Box(Modifier.size(800.dp, 360.dp)) {
                    VideoEditorScreen(
                        session = s,
                        onClose = {},
                        onRetryOpen = { s.open("fixture://x.mp4") }
                    )
                }
            }
        }
        awaitReady(s)
        // Separate In and Out rows — all four 48 dp nudge targets scroll
        // into reach inside the bounded inspector.
        listOf(
            TAG_VIDEO_TRIM_IN_MINUS, TAG_VIDEO_TRIM_IN_PLUS,
            TAG_VIDEO_TRIM_OUT_MINUS, TAG_VIDEO_TRIM_OUT_PLUS
        ).forEach { tag ->
            onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    // ---- timeline trim grips ----

    @Test
    fun timelineGripDragCommitsOneUndoableTrim() = runComposeUiTest {
        // Trim grips are a wide-timeline control — the compact timeline
        // is a pure selector; trim precision lives in the Trim surface.
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-1").performClick()
        onNodeWithTag(TAG_VIDEO_GRIP_START, useUnmergedTree = true).assertIsDisplayed()
        // Drag the in-grip rightwards — one begin/update/commit lands as a
        // single history entry, snapped down to a source frame boundary.
        onNodeWithTag(TAG_VIDEO_GRIP_START, useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(center + Offset(80f, 0f))
            up()
        }
        awaitState { it.isDirty && !it.isDrafting }
        val clip = s.state.value.sequence!!.tracks[0].clips.single()
        assertTrue(
            clip.sourceRange.start > EditorTime.Zero,
            "expected the in-grip drag to move the in-point"
        )
        assertEquals(
            0L,
            clip.sourceRange.start.ticks % FrameRate(30).ticksPerFrame,
            "grip-committed in-point must sit on a source frame boundary"
        )
        assertTrue(s.state.value.canUndo)
        runOnUiThread { s.undo() }
        awaitState {
            it.sequence?.tracks?.get(0)?.clips?.single()
                ?.sourceRange?.start == EditorTime.Zero
        }
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun gripDragReturnedToOriginCommitsNoHistory() = runComposeUiTest {
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-1").performClick()
        // Drag out and back to the exact start: the grip keeps publishing
        // on every pointer move (returning to base republishes the
        // captured range), the session no-ops identical values, and the
        // commit writes nothing.
        onNodeWithTag(TAG_VIDEO_GRIP_START, useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(center + Offset(80f, 0f))
            moveTo(center)
            up()
        }
        waitForIdle()
        val clip = s.state.value.sequence!!.tracks[0].clips.single()
        assertEquals(EditorTime.Zero, clip.sourceRange.start)
        assertFalse(s.state.value.isDrafting)
        assertFalse(s.state.value.isDirty)
        assertFalse(s.state.value.canUndo)
    }

    @Test
    fun gripDraftCancelsWhenSelectionChanges() = runComposeUiTest {
        val s = liveEditor(1280f, 800f)
        // Split at the midpoint: two ~equal clips wide enough (>96 dp)
        // that grips stay composed on whichever is selected.
        runOnUiThread { s.seek(us(3_000_000)) }
        awaitState { !it.seeking }
        onNodeWithTag(TAG_VIDEO_SPLIT).performClick()
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-1").performClick()
        awaitState { it.selectedClipId == "clip-1" }

        onNodeWithTag(TAG_VIDEO_GRIP_START, useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 0f))
            // Held — the draft is live.
        }
        waitForIdle()
        assertTrue(s.state.value.isDrafting)

        // Switching selection cancels the owned draft; the stale pointer's
        // release must not commit against the new owner's clip.
        runOnUiThread { s.selectClip("clip-2") }
        awaitState { it.selectedClipId == "clip-2" && !it.isDrafting }
        val clip1 = s.state.value.sequence!!.tracks[0].clips
            .first { it.id == "clip-1" }
        assertEquals(EditorTime.Zero, clip1.sourceRange.start)

        // The held pointer outlives its disposed grip — release it through
        // the timeline node, which is always composed.
        waitForIdle()
        onNodeWithTag(TAG_VIDEO_TIMELINE).performTouchInput { up() }
        waitForIdle()
        assertFalse(s.state.value.isDrafting)
        // Only the split is on the undo stack — the cancelled drag left
        // nothing behind.
        runOnUiThread { s.undo() }
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 1 }
        assertFalse(s.state.value.canUndo)
    }

    // ---- short landscape collapsed timeline ----

    @Test
    fun shortLandscapeKeepsPreviewAndLanes() = runComposeUiTest {
        // 800 dp wide but only 340 tall: the phone landscape form puts the
        // dock on a rail and the inspector beside the canvas column, so
        // the stage stays recognizable above the compact lanes.
        liveEditor(800f, 340f)
        onNodeWithTag(TAG_VIDEO_TIMELINE).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_CLIP_STRIP).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_PANEL).assertIsDisplayed()
        val previewPx = onNodeWithTag(TAG_VIDEO_PREVIEW)
            .fetchSemanticsNode().boundsInRoot.height
        val previewDp = previewPx / density.density
        assertTrue(
            previewDp >= 90f,
            "preview is ${previewDp}dp tall — short landscape must leave " +
                "a recognizable canvas"
        )
    }

    // ---- scroll/zoom geometry regressions ----

    /**
     * Live scroll offset of the shared timeline scroll state, read off
     * clip-1: it starts at content x = 0, so its unclipped
     * `positionInRoot` is `viewportLeft - scroll` while the tagged node's
     * clipped `boundsInRoot.left` is the viewport edge. Works for both the
     * expanded ruler (a child inside the scroll content) and the collapsed
     * mini strip (the scroll viewport node itself).
     */
    private fun ComposeUiTest.timelineScrollPx(): Float {
        val tl = onAllNodesWithTag(TAG_VIDEO_TIMELINE)
            .fetchSemanticsNodes().firstOrNull() ?: return 0f
        val clip = onAllNodesWithTag(
            TAG_VIDEO_CLIP_PREFIX + "clip-1", useUnmergedTree = true
        ).fetchSemanticsNodes().firstOrNull() ?: return 0f
        return tl.boundsInRoot.left - clip.positionInRoot.x
    }

    /**
     * A tap at [fraction] of the VISIBLE viewport. `performTouchInput`
     * positions are relative to the node's clipped bounds, so the injected
     * x is just `width * fraction` — while the content coordinate the
     * scrub handler maps is `scroll + viewport x`. clip-1 spans the whole
     * sequence, so its width IS the content width.
     */
    private fun ComposeUiTest.timelineHit(
        s: VideoEditorSession,
        fraction: Float
    ): Pair<Float, Long> {
        val tl = onNodeWithTag(TAG_VIDEO_TIMELINE).fetchSemanticsNode()
        val clip = onNodeWithTag(
            TAG_VIDEO_CLIP_PREFIX + "clip-1", useUnmergedTree = true
        ).fetchSemanticsNode()
        val contentW = clip.size.width.toFloat()
        val scrollPx = tl.boundsInRoot.left - clip.positionInRoot.x
        val injectX = tl.boundsInRoot.width * fraction
        val contentX = scrollPx + injectX
        val total = s.state.value.sequence!!.duration.ticks
        val expected = (contentX.toDouble() / contentW * total)
            .roundToLong().coerceIn(0L, total)
        return injectX to expected
    }

    /** Playhead's x inside the visible viewport, derived from geometry. */
    private fun ComposeUiTest.playheadViewX(s: VideoEditorSession): Float {
        val clip = onNodeWithTag(
            TAG_VIDEO_CLIP_PREFIX + "clip-1", useUnmergedTree = true
        ).fetchSemanticsNode()
        val fraction = s.state.value.position.ticks.toDouble() /
            s.state.value.sequence!!.duration.ticks
        val contentX = (fraction * clip.size.width).toFloat()
        return contentX - timelineScrollPx()
    }

    @Test
    fun rulerScrubCountsScrollExactlyOnce() = runComposeUiTest {
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_CLIP_STRIP).assertIsDisplayed()

        // Zoomed past fit with a real scroll offset: a tap at a quarter of
        // the viewport must map (scroll + viewport/4)/content — the pointer
        // offset is already content space, so counting scroll again would
        // land the seek noticeably late.
        runOnUiThread { s.seek(us(4_500_000)) }
        awaitState { !it.seeking && it.position.ticks > 0L }
        onNodeWithTag(TAG_VIDEO_ZOOM_IN).performClick()
        onNodeWithTag(TAG_VIDEO_ZOOM_IN).performClick()
        waitUntil(timeoutMillis = 10_000) { timelineScrollPx() > 0f }
        val tpf = FrameRate(30).ticksPerFrame

        val (rulerX, rulerExpected) = timelineHit(s, 0.25f)
        onNodeWithTag(TAG_VIDEO_TIMELINE).performTouchInput {
            down(Offset(rulerX, centerY))
            up()
        }
        awaitState {
            !it.seeking && abs(it.position.ticks - rulerExpected) <= tpf
        }
    }

    @Test
    fun zoomKeepsThePlayheadAtItsViewportX() = runComposeUiTest {
        val s = liveEditor(1280f, 800f)
        onNodeWithTag(TAG_VIDEO_CLIP_STRIP).assertIsDisplayed()

        runOnUiThread { s.seek(us(4_500_000)) }
        awaitState { !it.seeking && it.position.ticks > 0L }
        val beforeX = playheadViewX(s)

        // Two zoom-ins must scroll the content while the playhead stays
        // put on screen — within 1 dp, not geometry-only.
        onNodeWithTag(TAG_VIDEO_ZOOM_IN).performClick()
        onNodeWithTag(TAG_VIDEO_ZOOM_IN).performClick()
        waitUntil(timeoutMillis = 10_000) { timelineScrollPx() > 0f }
        waitForIdle()
        val afterX = playheadViewX(s)
        val driftDp = abs(afterX - beforeX) / density.density
        assertTrue(
            driftDp <= 1f,
            "playhead drifted ${driftDp}dp under zoom (was ${beforeX}px," +
                " now ${afterX}px)"
        )
        assertTrue(timelineScrollPx() > 0f, "zoom-in must scroll")
    }

    // ── Canonical phone regions (412 × 832 editor content) ──────────
    // runDesktopComposeUiTest sizes the scene itself — runComposeUiTest's
    // default window is only 768 tall, which would clamp every Box below.

    @Test
    fun canonicalVideoRegionsMatchSpec() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        val s = liveEditor(
            widthDp = 412f, heightDp = 832f, onRequestAddMedia = {}
        )

        // Studio phone stack: the header, the stage, the transport, the
        // timeline card and the 72 dp glass dock at the foot — in order.
        val header = onNodeWithTag(TAG_VIDEO_REGION_HEADER).getUnclippedBoundsInRoot()
        val preview = onNodeWithTag(TAG_VIDEO_REGION_PREVIEW).getUnclippedBoundsInRoot()
        val transport = onNodeWithTag(TAG_VIDEO_REGION_TRANSPORT).getUnclippedBoundsInRoot()
        val timeline = onNodeWithTag(TAG_VIDEO_REGION_TIMELINE).getUnclippedBoundsInRoot()
        val dock = onNodeWithTag(TAG_VIDEO_REGION_TOOLBAR)
            .assertHeightIsEqualTo(72.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(header.bottom <= preview.top, "header/stage overlap")
        assertTrue(preview.bottom <= transport.top, "stage/transport overlap")
        assertTrue(transport.bottom <= timeline.top, "transport/timeline overlap")
        assertTrue(timeline.bottom <= dock.top, "timeline/dock overlap")
        assertEquals(820f, dock.bottom.value, 0.6f)
        assertTrue(
            (preview.bottom - preview.top).value >= 160f,
            "a one-lane sequence must leave the stage room: $preview"
        )

        // Header anatomy: the glass Close, the Undo · Redo capsule and the
        // Export split button with its More chevron — all inside.
        val root = onRoot().getBoundsInRoot()
        listOf(
            TAG_VIDEO_BACK, TAG_HEADER_MORE, TAG_VIDEO_SAVE,
            TAG_VIDEO_UNDO, TAG_VIDEO_REDO
        ).forEach { tag ->
            val bounds = onNodeWithTag(tag)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue(
                bounds.left >= root.left && bounds.right <= root.right &&
                    bounds.top >= root.top && bounds.bottom <= root.bottom,
                "$tag clipped: $bounds not inside $root"
            )
        }
        onNodeWithTag(TAG_VIDEO_EXPORT_MENU).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_PREVIEW_TIMECODE).assertDoesNotExist()

        // Transport: the readout, then previous · Play · next, then sound;
        // Play is the 64 dp cookie at home.
        val timecode = onNodeWithTag(TAG_VIDEO_TIMECODE).getUnclippedBoundsInRoot()
        val prev = onNodeWithTag(TAG_VIDEO_PREV_FRAME).getUnclippedBoundsInRoot()
        val play = onNodeWithTag(TAG_VIDEO_PLAY).getUnclippedBoundsInRoot()
        val next = onNodeWithTag(TAG_VIDEO_NEXT_FRAME).getUnclippedBoundsInRoot()
        val mute = onNodeWithTag(TAG_VIDEO_MUTE).getUnclippedBoundsInRoot()
        assertTrue(timecode.right <= prev.left, "timecode/previous out of order")
        assertTrue(prev.right <= play.left && play.right <= next.left, "step group out of order")
        assertTrue(next.right <= mute.left, "next/mute out of order")
        assertEquals(64f, (play.right - play.left).value, 0.6f)

        // The command and zoom groups ride the timeline card.
        listOf(
            TAG_VIDEO_COMMAND_TRIM, TAG_VIDEO_SPLIT, TAG_VIDEO_DELETE,
            TAG_VIDEO_COMMAND_ARRANGE, TAG_VIDEO_ZOOM_OUT, TAG_VIDEO_ZOOM_FIT,
            TAG_VIDEO_ZOOM_IN
        ).forEach { onNodeWithTag(it).assertIsDisplayed() }

        // Only the lanes the sequence has.
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V1").assertIsDisplayed()
        assertTrue(
            onAllNodesWithTag(TAG_VIDEO_LANE_PREFIX + "A1").fetchSemanticsNodes().isEmpty() &&
                onAllNodesWithTag(TAG_VIDEO_LANE_PREFIX + "V2").fetchSemanticsNodes().isEmpty(),
            "no lane may be fabricated"
        )

        // Real clip selection and ruler seek semantics.
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + "clip-1").assertIsSelected()
        onNodeWithTag(TAG_VIDEO_TIMELINE).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { setProgress -> setProgress(0.5f) }
        awaitState {
            it.position == us(2_000_000) && !it.seeking
        }

        // Exactly four dock destinations; Focus lives in the More sheet.
        listOf(TAG_VIDEO_MEDIA, TAG_VIDEO_LAYERS, TAG_VIDEO_AUDIO, TAG_VIDEO_TOOLS)
            .forEach { tag ->
                onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
            }
        onNodeWithTag(TAG_VIDEO_FOCUS).assertDoesNotExist()
        onNodeWithTag(TAG_HEADER_MORE).performClick()
        onNodeWithTag(TAG_VIDEO_FOCUS).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun mediaDestinationNeedsAHostPicker() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveEditor(widthDp = 412f, heightDp = 832f)
        // No picker wired: Media refuses instead of opening a dead sheet.
        onNodeWithTag(TAG_VIDEO_MEDIA).assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_TOOLS).assertIsEnabled()
    }

    @Test
    fun canonicalVideoToolsAndFocusFlow() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveEditor(widthDp = 412f, heightDp = 832f)

        // Tools opens the All-tools modal: search plus the four video
        // categories over the real registry.
        onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        onNodeWithTag(TAG_CATALOGUE_SEARCH).assertIsDisplayed()
        // One handle — the modal's own.
        onNodeWithTag(TAG_CATALOGUE_HANDLE).assertDoesNotExist()
        listOf("Edit", "Picture", "Audio", "Deliver").forEach { label ->
            assertTrue(
                onAllNodesWithText(label)
                    .fetchSemanticsNodes().isNotEmpty(),
                "category $label missing from the video Tools sheet"
            )
        }
        onNodeWithTag(TAG_VIDEO_TAB_TRIM).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TAB_CLIPS).assertIsDisplayed()

        // A tool replaces the home chrome: its own header + check, the
        // lane strip it acts on and the inspector — no pill, no pills.
        onNodeWithTag(TAG_VIDEO_TAB_TRIM).performClick()
        onNodeWithTag(TAG_VIDEO_TRIM_SLIDER).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TOOL_DONE).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_REGION_TIMELINE).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_PANEL).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_COMMAND_TRIM).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_CONTEXT_BACK).performClick()
        onNodeWithTag(TAG_VIDEO_PANEL).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_REGION_TOOLBAR).assertIsDisplayed()

        // Arrange owns split/duplicate/delete/move; the check returns.
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_SPLIT).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_DELETE).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_MOVE_EARLIER).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_MOVE_LATER).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TOOL_DONE).performClick()
        onNodeWithTag(TAG_VIDEO_REGION_TOOLBAR).assertIsDisplayed()

        // Focus hides the timeline and the pill; More brings it back.
        onNodeWithTag(TAG_HEADER_MORE).performClick()
        onNodeWithTag(TAG_VIDEO_FOCUS).performClick()
        onNodeWithTag(TAG_VIDEO_REGION_TIMELINE).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_REGION_TOOLBAR).assertDoesNotExist()
        onNodeWithTag(TAG_HEADER_MORE).assertDoesNotExist()
        // The glass exit on the frame returns to the studio.
        onNodeWithTag(TAG_VIDEO_FOCUS).performClick()
        onNodeWithTag(TAG_VIDEO_REGION_TIMELINE).assertIsDisplayed()
    }

    @Test
    fun videoFont2KeepsHeaderAndDockReachable() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveEditor(widthDp = 412f, heightDp = 832f, fontScale = 2f)
        listOf(
            TAG_VIDEO_BACK, TAG_HEADER_MORE, TAG_VIDEO_SAVE,
            TAG_VIDEO_UNDO, TAG_VIDEO_REDO, TAG_VIDEO_ZOOM_FIT,
            TAG_VIDEO_PLAY,
            TAG_VIDEO_MEDIA, TAG_VIDEO_LAYERS, TAG_VIDEO_AUDIO,
            TAG_VIDEO_TOOLS
        ).forEach { tag ->
            onNodeWithTag(tag).assertIsDisplayed()
        }
        // The sheet still opens and its lazy grid scrolls to the cells.
        onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TAB_TRIM)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // ---- R2: V4 layout, catalogue, shortcuts and export ----

    @Test
    fun layoutModeFollowsTheWindow() {
        assertEquals(VideoLayout.Compact, videoLayoutFor(412f, 892f))
        // Tablet portrait keeps the phone stack.
        assertEquals(VideoLayout.Compact, videoLayoutFor(800f, 1280f))
        assertEquals(VideoLayout.Landscape, videoLayoutFor(892f, 412f))
        assertEquals(VideoLayout.Landscape, videoLayoutFor(1024f, 768f))
        // Tablet landscape and desktop windows get the pane workspace.
        assertEquals(VideoLayout.Desktop, videoLayoutFor(1280f, 800f))
        assertEquals(VideoLayout.Desktop, videoLayoutFor(1440f, 960f))
        assertEquals(VideoLayout.Compact, videoLayoutFor(0f, 0f))
    }

    @Test
    fun catalogueListsOnlyImplementedTools() {
        val byGroup = videoToolEntries().groupBy({ it.descriptor.group }) {
            it.descriptor.id
        }
        assertEquals(
            listOf(
                EditorToolIds.VideoAddMedia, EditorToolIds.VideoArrange,
                EditorToolIds.VideoTrim, EditorToolIds.VideoLayers
            ),
            byGroup[EditorToolGroup.Edit]
        )
        assertEquals(
            listOf(EditorToolIds.VideoTransform, EditorToolIds.VideoColour),
            byGroup[EditorToolGroup.Picture]
        )
        assertEquals(
            listOf(
                EditorToolIds.VideoAddAudio, EditorToolIds.VideoClipAudio,
                EditorToolIds.VideoMixer, EditorToolIds.VideoRouting,
                EditorToolIds.VideoDucking, EditorToolIds.VideoEq,
                EditorToolIds.VideoVoice
            ),
            byGroup[EditorToolGroup.Audio]
        )
        assertEquals(listOf(EditorToolIds.VideoExport), byGroup[EditorToolGroup.Deliver])
        assertEquals(
            setOf(
                EditorToolGroup.Edit, EditorToolGroup.Picture,
                EditorToolGroup.Audio, EditorToolGroup.Deliver
            ),
            byGroup.keys
        )
    }

    @Test
    fun catalogueSearchSpansCategoriesAndExportOpensTheSheet() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        liveEditor(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_VIDEO_TOOLS).performClick()
        onNodeWithTag("editor.catalogue.group.Picture").performClick()
        onNodeWithTag(TAG_VIDEO_TOOL_TRANSFORM).assertIsDisplayed()
        // A query reaches Edit's Trim from the Picture category.
        onNodeWithTag(TAG_CATALOGUE_SEARCH).performTextInput("trim")
        onNodeWithTag(TAG_VIDEO_TAB_TRIM).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TOOL_TRANSFORM).assertDoesNotExist()
        onNodeWithTag(TAG_CATALOGUE_SEARCH).performTextReplacement("")
        onNodeWithTag("editor.catalogue.group.Deliver").performClick()
        onNodeWithTag(TAG_VIDEO_TOOL_EXPORT).performClick()
        onNodeWithTag(TAG_VIDEO_EXPORT_SHEET).assertExists()
        onNodeWithTag(TAG_VIDEO_EXPORT_CONFIRM).assertIsEnabled()
    }

    @Test
    fun keyboardShortcutMapping() {
        fun key(k: Key, meta: Boolean = false, shift: Boolean = false) =
            videoKeyShortcut(k, meta, shift)
        assertEquals(VideoShortcut.PlayPause, key(Key.Spacebar))
        assertEquals(VideoShortcut.StepBackSecond, key(Key.J))
        assertEquals(VideoShortcut.Pause, key(Key.K))
        assertEquals(VideoShortcut.Play, key(Key.L))
        assertEquals(VideoShortcut.PreviousFrame, key(Key.DirectionLeft))
        assertEquals(VideoShortcut.NextFrame, key(Key.DirectionRight))
        assertEquals(VideoShortcut.StepBackSecond, key(Key.DirectionLeft, shift = true))
        assertEquals(VideoShortcut.ForwardSecond, key(Key.DirectionRight, shift = true))
        assertEquals(VideoShortcut.Split, key(Key.S))
        assertEquals(VideoShortcut.Delete, key(Key.Delete))
        assertEquals(VideoShortcut.Delete, key(Key.Backspace))
        // Single keys never fire under a modifier chord.
        assertEquals(null, key(Key.S, meta = true))
        assertEquals(VideoShortcut.Undo, videoChordShortcut(Key.Z, true, false))
        assertEquals(VideoShortcut.Redo, videoChordShortcut(Key.Z, true, true))
        assertEquals(VideoShortcut.Export, videoChordShortcut(Key.S, true, false))
        assertEquals(VideoShortcut.Escape, videoChordShortcut(Key.Escape, false, false))
        assertEquals(null, videoChordShortcut(Key.Z, false, false))
    }

    @Test
    fun jklSplitAndDeleteKeysDriveTheSession() = runComposeUiTest {
        val s = liveEditor()
        playback.playGate = CompletableDeferred()
        onNodeWithTag(TAG_VIDEO_PREVIEW).performSemanticsAction(
            SemanticsActions.RequestFocus
        )
        fun press(k: Key) = onNodeWithTag(TAG_VIDEO_PREVIEW)
            .performKeyInput { keyDown(k); keyUp(k) }
        press(Key.L)
        awaitState { it.playing }
        press(Key.K)
        awaitState { !it.playing }
        runOnUiThread { s.seek(us(2_000_000)) }
        awaitState { !it.seeking && it.position == us(2_000_000) }
        press(Key.J)
        awaitState { !it.seeking && it.position == us(1_000_000) }
        press(Key.S)
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
        press(Key.Delete)
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 1 }
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft)
            keyDown(Key.Z); keyUp(Key.Z)
            keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        onNodeWithTag(TAG_VIDEO_PREVIEW).performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.Z); keyUp(Key.Z); keyUp(Key.CtrlLeft)
        }
        awaitState { it.sequence?.tracks?.get(0)?.clips?.size == 2 }
    }

    @Test
    fun exportSheetShowsOnlyTheEncoderContract() =
        runDesktopComposeUiTest(width = 412, height = 832) {
        media.exportPrePublishGate = CompletableDeferred()
        val s = liveEditor(widthDp = 412f, heightDp = 832f)
        onNodeWithTag(TAG_VIDEO_SAVE).performClick()
        onNodeWithTag(TAG_VIDEO_EXPORT_SHEET).assertExists()
        // Read-only facts — the exporter writes H.264/AAC MP4 at a
        // constant 30 fps at the sequence size; nothing else is offered.
        val keys = listOf("resolution", "frameRate", "codec", "bitrate", "audio", "destination")
        keys.forEach { key ->
            onNodeWithTag(TAG_VIDEO_EXPORT_ROW_PREFIX + key).assertExists()
        }
        onNodeWithText("320 × 240").assertExists()
        onNodeWithText("30 fps · constant").assertExists()
        onNodeWithText("H.264 · MP4").assertExists()
        assertTrue(
            onAllNodesWithTag(TAG_VIDEO_EXPORT_ROW_PREFIX + "resolution")
                .fetchSemanticsNodes().single()
                .config.getOrNull(SemanticsActions.OnClick) == null,
            "export facts are not pickers"
        )
        onNodeWithTag(TAG_VIDEO_EXPORT_CONFIRM).performClick()
        awaitState { it.exporting }
        onNodeWithTag(TAG_VIDEO_EXPORT_SHEET_CANCEL).assertExists()
        onNodeWithTag(TAG_VIDEO_EXPORT_HIDE).assertExists()
        media.exportPrePublishGate?.complete(Unit)
        awaitState { it.exported != null && !it.exporting }
        onNodeWithTag(TAG_VIDEO_EXPORT_DONE).assertExists().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(TAG_VIDEO_EXPORT_SHEET)
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals("out://video/1", s.state.value.exported?.reference)
    }

    // ---- V1b multi-track surfaces ----

    private fun ComposeUiTest.importOverlay(s: VideoEditorSession) {
        runOnUiThread {
            s.addVideoSource(
                "fixture://overlay-harbour.mp4",
                VideoInsertion.Overlay
            )
        }
        awaitState {
            !it.importing && !it.seeking &&
                (it.sequence?.sources?.size ?: 0) >= 2 &&
                (it.sequence?.tracks?.size ?: 0) >= 2 &&
                it.preview != null
        }
        waitForIdle()
    }

    @Test
    fun addMediaSheetCarriesTheChosenInsertionToTheHost() =
        runComposeUiTest {
        var requested: VideoInsertion? = null
        val s = liveEditor(onRequestAddMedia = { insertion ->
            requested = insertion
            // The host resolves the pick and feeds every URI through the
            // session with the sheet's chosen insertion — here, one.
            session?.addVideoSource("fixture://picked.mp4", insertion)
        })
        onNodeWithTag(TAG_VIDEO_MEDIA).performClick()
        onNodeWithTag(TAG_VIDEO_INSERT_PREFIX + "Overlay").performClick()
        onNodeWithTag(TAG_VIDEO_INSERT_PREFIX + "Overlay").assertIsSelected()
        onNodeWithTag(TAG_VIDEO_CHOOSE_VIDEOS).performClick()

        assertEquals(VideoInsertion.Overlay, requested)
        awaitState {
            !it.importing && (it.sequence?.sources?.size ?: 0) == 2
        }
        // The overlay insertion created a real overlay lane — the clip
        // did not silently land on Main.
        assertTrue(
            s.state.value.sequence!!.tracks.any {
                it.kind == VideoTrackKind.Overlay
            }
        )
    }

    @Test
    fun addMediaEndInsertionLandsOnTheMainTrack() = runComposeUiTest {
        val s = liveEditor(onRequestAddMedia = { insertion ->
            session?.addVideoSource("fixture://picked.mp4", insertion)
        })
        onNodeWithTag(TAG_VIDEO_MEDIA).performClick()
        onNodeWithTag(TAG_VIDEO_INSERT_PREFIX + "End").performClick()
        onNodeWithTag(TAG_VIDEO_CHOOSE_VIDEOS).performClick()
        awaitState {
            !it.importing && (it.sequence?.sources?.size ?: 0) == 2
        }
        val main = s.state.value.sequence!!.tracks.single {
            it.kind == VideoTrackKind.Main
        }
        assertEquals(2, main.clips.size)
    }

    @Test
    fun twoSourceTimelineRendersOverlayAndMainLanes() = runComposeUiTest {
        val s = liveEditor()
        importOverlay(s)
        // Gutter labels — the overlay lane V2 sits above Main V1.
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2").assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V1").assertIsDisplayed()
        val v2 = onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2").getBoundsInRoot()
        val v1 = onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V1").getBoundsInRoot()
        assertTrue(v2.top < v1.top, "V2 lane must sit above V1")

        // The overlay clip is a real selectable node on its lane.
        val overlayClip = s.state.value.sequence!!.tracks
            .single { it.kind == VideoTrackKind.Overlay }.clips.single()
        onNodeWithTag(TAG_VIDEO_CLIP_PREFIX + overlayClip.id)
            .performClick()
        awaitState { it.selectedClipId == overlayClip.id }
    }

    @Test
    fun timelineBadgesPaintLocalizedRecipeText() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
        val s = liveEditor(widthDp = 1280f, heightDp = 800f)
        val mainTrack = s.state.value.sequence!!.tracks.single {
            it.kind == VideoTrackKind.Main
        }
        val mainClip = mainTrack.clips.single()
        // A neutral recipe carries no badge at all.
        onNodeWithTag(TAG_VIDEO_TIMELINE_EFFECT_PREFIX + mainClip.id)
            .assertDoesNotExist()

        runOnUiThread {
            s.setAudioLink(mainTrack.id, mainClip.id, linked = false)
        }
        awaitState {
            it.sequence?.tracks?.single { t -> t.id == mainTrack.id }
                ?.clips?.single()?.audioMuted == true
        }
        // The strip reads localized resources — never enum `.name`. The
        // clip row merges its subtree, so the badge is unmerged-only.
        val strip = TAG_VIDEO_TIMELINE_EFFECT_PREFIX + mainClip.id
        onNodeWithTag(strip, useUnmergedTree = true).assertIsDisplayed()
        onNode(
            hasText("Muted") and hasAnyAncestor(hasTestTag(strip)),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun layerVisibilityToggleChangesTheCompositePreview() =
        runComposeUiTest {
        val s = liveEditor()
        media.overlayReferences += "fixture://overlay-harbour.mp4"
        media.overlayArgb = 0xFF3366CC.toInt()
        importOverlay(s)

        // The overlay covers the canvas — wait for the post-import
        // composite, then the frame reads the overlay's colour, not the
        // main lane's tick colour.
        fun previewPixel() =
            s.state.value.preview?.copyPixels()?.firstOrNull()
        fun closeTo(expected: Int, actual: Int?) = actual != null &&
            (0..3).all { c ->
                abs(((expected shr (c * 8)) and 0xFF) -
                    ((actual shr (c * 8)) and 0xFF)) <= 2
            }
        waitUntil(timeoutMillis = 15_000) {
            closeTo(0xFF3366CC.toInt(), previewPixel())
        }

        onNodeWithTag(TAG_VIDEO_LAYERS).performClick()
        val overlayTrack = s.state.value.sequence!!.tracks.single {
            it.kind == VideoTrackKind.Overlay
        }
        onNodeWithTag(TAG_VIDEO_TRACK_VISIBLE_PREFIX + overlayTrack.id)
            .performClick()
        awaitState {
            it.sequence?.tracks?.single { t -> t.id == overlayTrack.id }
                ?.visible == false
        }
        awaitState { it.preview != null }
        // Hidden lane: the preview is the main clip's own colour again.
        waitUntil(timeoutMillis = 15_000) {
            !closeTo(0xFF3366CC.toInt(), previewPixel())
        }

        // One undo restores the lane — visibility is a history step.
        clickUndo()
        awaitState {
            it.sequence?.tracks?.single { t -> t.id == overlayTrack.id }
                ?.visible == true
        }
    }

    @Test
    fun arrangeDuplicateMoveDeleteAndUndo() = runComposeUiTest {
        val s = liveEditor()
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        fun clipCount() =
            s.state.value.sequence!!.tracks.flatMap { it.clips }.size

        onNodeWithTag(TAG_VIDEO_ARRANGE_DUPLICATE).performClick()
        awaitState {
            it.sequence?.tracks?.flatMap { t -> t.clips }?.size == 2
        }
        // The duplicate is placed right after the original on Main.
        val duplicated = s.state.value.sequence!!.tracks
            .single { it.kind == VideoTrackKind.Main }.clips
        assertEquals(
            duplicated[0].timelineRange.endExclusive,
            duplicated[1].timelineStart
        )

        // Move earlier swaps the selected duplicate back before it.
        onNodeWithTag(TAG_VIDEO_MOVE_EARLIER).performClick()
        awaitState {
            it.sequence?.tracks?.single { t ->
                t.kind == VideoTrackKind.Main
            }?.clips?.first()?.id == duplicated[1].id
        }

        onNodeWithTag(TAG_VIDEO_DELETE).performClick()
        awaitState {
            it.sequence?.tracks?.flatMap { t -> t.clips }?.size == 1
        }
        clickUndo()
        awaitState {
            it.sequence?.tracks?.flatMap { t -> t.clips }?.size == 2
        }
        assertEquals(2, clipCount())
    }

    @Test
    fun transformModeAndColourRowsMutateTheSelectedClip() =
        runComposeUiTest {
        val s = liveEditor()
        fun clip() = s.state.value.sequence!!.tracks
            .flatMap { it.clips }
            .first { it.id == s.state.value.selectedClipId }

        openVideoTool(TAG_VIDEO_TOOL_TRANSFORM, "Picture")
        onNodeWithTag(TAG_VIDEO_TRANSFORM_MODE_PREFIX + "Crop")
            .performClick()
        awaitState {
            clip().contentMode == VideoContentMode.Crop
        }
        clickUndo()
        awaitState { clip().contentMode == VideoContentMode.Fit }

        // A slider drag on a transform pair row lands through the clip
        // draft seam and commits as one step.
        onNodeWithTag(TAG_VIDEO_TRANSFORM_SR).performSemanticsAction(
            SemanticsActions.SetProgress
        ) { it(200f) }
        awaitState {
            abs(clip().transform.scale - 2f) < 0.01f && !it.isDrafting
        }

        // Clip colour — the Light tab's exposure slider writes the
        // clip's ColorAdjustments via the same seam.
        onNodeWithTag(TAG_VIDEO_CONTEXT_BACK).performClick()
        openVideoTool(TAG_VIDEO_TOOL_COLOUR, "Picture")
        onNodeWithTag(TAG_VIDEO_COLOUR_SLIDER_PREFIX + "exposure")
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(50f)
            }
        awaitState {
            clip().adjustments.exposureStops != 0f && !it.isDrafting
        }
    }

    @Test
    fun tabletLandscapeShowsRailStageInspectorAndTimeline() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveEditor(widthDp = 1440f, heightDp = 960f)
        importOverlay(s)
        // The labelled dock rail on the left, the stage beside the solid
        // inspector card, and the full-width timeline card underneath.
        val rail = onNodeWithTag(TAG_VIDEO_REGION_TOOLBAR)
            .assertWidthIsEqualTo(80.dp)
            .getUnclippedBoundsInRoot()
        val stage = onNodeWithTag(TAG_VIDEO_REGION_PREVIEW).getUnclippedBoundsInRoot()
        val panel = onNodeWithTag(TAG_VIDEO_PANEL).getUnclippedBoundsInRoot()
        val timeline = onNodeWithTag(TAG_VIDEO_REGION_TIMELINE).getUnclippedBoundsInRoot()
        assertTrue(rail.right <= stage.left, "rail/stage overlap")
        assertTrue(stage.right <= panel.left, "stage/inspector overlap")
        assertTrue(stage.bottom <= timeline.top && panel.bottom <= timeline.top)
        assertTrue(timeline.left < stage.left + 1.dp && timeline.right >= panel.right - 1.dp)
        // The header carries the source name; the command row is labelled.
        onNodeWithText("sample-coast.mp4").assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_COMMAND_TRIM).assertIsDisplayed()
        // Both real lanes are drawn, the overlay above Main.
        val v2 = onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2").getBoundsInRoot()
        val v1 = onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V1").getBoundsInRoot()
        assertTrue(v2.top < v1.top, "V2 lane must sit above V1")
    }

    @Test
    fun landscapeKeepsVideoChromeReachable() =
        runDesktopComposeUiTest(width = 892, height = 412) {
        liveEditor(widthDp = 892f, heightDp = 412f)
        listOf(
            TAG_VIDEO_BACK, TAG_VIDEO_UNDO, TAG_VIDEO_REDO,
            TAG_VIDEO_SAVE, TAG_VIDEO_PREVIEW, TAG_VIDEO_PANEL,
            TAG_VIDEO_MEDIA, TAG_VIDEO_TOOLS, TAG_VIDEO_PLAY
        ).forEach { tag ->
            onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    // ---- A1b audio tools ----

    private fun ComposeUiTest.importAudio(
        s: VideoEditorSession,
        reference: String = "fixture://song.mp3",
        trackCount: Int = 1
    ) {
        runOnUiThread { s.addAudioSource(reference) }
        awaitState { st ->
            !st.importing &&
                (st.sequence?.audioTracks?.size ?: 0) >= trackCount
        }
        waitForIdle()
    }

    private fun audioTrack(s: VideoEditorSession, index: Int = 0) =
        s.state.value.sequence!!.audioTracks[index]

    private fun audioClip(s: VideoEditorSession, index: Int = 0) =
        audioTrack(s, index).clips.single()

    @Test
    fun audioDockOpensTheAudioCatalogue() = runComposeUiTest {
        liveEditor()
        onNodeWithTag(TAG_VIDEO_AUDIO).performClick()
        onNodeWithTag(TAG_CATALOGUE_GRID).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_TOOL_ADD_AUDIO).assertExists()
        onNodeWithTag(TAG_VIDEO_TOOL_CLIP_AUDIO).assertExists()
        onNodeWithTag(TAG_VIDEO_TOOL_MIXER).assertExists()
    }

    @Test
    fun addAudioSheetFilesRowInvokesTheHostPicker() = runComposeUiTest {
        var picks = 0
        val s = liveEditor(onRequestAddAudio = {
            picks++
            session?.addAudioSource("fixture://picked.wav")
        })
        openVideoTool(TAG_VIDEO_TOOL_ADD_AUDIO, "Audio")
        // Record routes to the Voice tool — covered by the voice tests.
        onNodeWithTag(TAG_AUDIO_RECORD).assertIsEnabled()
        onNodeWithTag(TAG_AUDIO_FILES).performClick()
        assertEquals(1, picks)
        awaitState {
            !it.importing && it.sequence?.audioTracks?.size == 1
        }
        assertEquals("picked.wav", audioTrack(s).name)
    }

    @Test
    fun addAudioExtractMovesSelectedClipSoundToALane() = runComposeUiTest {
        val s = liveEditor()
        val mainClip = s.state.value.sequence!!.tracks
            .single { it.kind == VideoTrackKind.Main }.clips.single()
        openVideoTool(TAG_VIDEO_TOOL_ADD_AUDIO, "Audio")
        onNodeWithTag(TAG_AUDIO_EXTRACT).performClick()
        awaitState {
            !it.importing && it.sequence?.audioTracks?.size == 1
        }
        val seq = s.state.value.sequence!!
        val extracted = seq.audioTracks.single().clips.single()
        // The lane clip mirrors the video clip's placement and source
        // window; the embedded input muted in the same history step.
        assertEquals(mainClip.timelineStart, extracted.timelineStart)
        assertEquals(mainClip.sourceRange, extracted.sourceRange)
        assertTrue(
            seq.tracks.single { it.kind == VideoTrackKind.Main }
                .clips.single().audioMuted
        )
        clickUndo()
        awaitState {
            it.sequence?.audioTracks?.isEmpty() == true &&
                it.sequence!!.tracks.single { t ->
                    t.kind == VideoTrackKind.Main
                }.clips.single().audioMuted == false
        }
    }

    @Test
    fun audioLaneShowsWaveformLabelAndSelectsClips() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        awaitState { it.waveforms.isNotEmpty() }
        val track = audioTrack(s)
        val clip = track.clips.single()
        onNodeWithTag(TAG_VIDEO_AUDIO_LANE_PREFIX + track.id)
            .assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "A1").assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_AUDIO_CLIP_PREFIX + clip.id)
            .performClick()
        awaitState { it.selectedAudioClipId == clip.id }
        onNodeWithTag(TAG_VIDEO_AUDIO_CLIP_PREFIX + clip.id)
            .assertIsSelected()
    }

    @Test
    fun addTrackMenuCreatesAnAudioLaneThatTakesAClipMove() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        val clip = audioClip(s)
        onNodeWithTag(TAG_VIDEO_ADD_TRACK).performScrollTo().performClick()
        onNodeWithTag(TAG_VIDEO_ADD_TRACK_AUDIO).performClick()
        awaitState { it.sequence?.audioTracks?.size == 2 }
        val fresh = audioTrack(s, 1)
        assertEquals("Audio 2", fresh.name)
        assertTrue(fresh.clips.isEmpty())

        // The accessibility stand-in for the drag lands the clip on the
        // lane below at the same start, as one undoable step.
        onNodeWithTag(TAG_VIDEO_AUDIO_CLIP_PREFIX + clip.id)
            .performCustomAccessibilityActionWithLabel("Move to the track below")
        awaitState { st -> st.sequence?.audioTracks?.get(1)?.clips?.size == 1 }
        val moved = audioTrack(s, 1).clips.single()
        assertEquals(clip.id, moved.id)
        assertEquals(clip.timelineStart, moved.timelineStart)
        assertTrue(audioTrack(s, 0).clips.isEmpty())
        clickUndo()
        awaitState { st -> st.sequence?.audioTracks?.get(0)?.clips?.size == 1 }
    }

    @Test
    fun laneMenuDeletesAnAddedVideoTrack() = runComposeUiTest {
        val s = liveEditor()
        onNodeWithTag(TAG_VIDEO_ADD_TRACK).performScrollTo().performClick()
        onNodeWithTag(TAG_VIDEO_ADD_TRACK_VIDEO).performClick()
        awaitState { st ->
            st.sequence?.tracks?.count { it.kind == VideoTrackKind.Overlay } == 1
        }
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2").performScrollTo().performClick()
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2" + TAG_VIDEO_LANE_MOVE_UP_SUFFIX)
            .assertIsNotEnabled()
        onNodeWithTag(TAG_VIDEO_LANE_PREFIX + "V2" + TAG_VIDEO_LANE_DELETE_SUFFIX)
            .performClick()
        awaitState { st ->
            st.sequence?.tracks?.none { it.kind == VideoTrackKind.Overlay } == true
        }
    }

    @Test
    fun clipAudioRowsReachTheModelAsHistorySteps() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        val clip = audioClip(s)
        runOnUiThread { s.selectAudioClip(clip.id) }
        openVideoTool(TAG_VIDEO_TOOL_CLIP_AUDIO, "Audio")

        // The precision ruler speaks dB through SetProgress — one apply,
        // one undo step.
        onNodeWithTag(TAG_AUDIO_CLIP_GAIN_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-12f)
            }
        awaitState { audioClip(s).gainDb == -12f && !it.isDrafting }
        clickUndo()
        awaitState { audioClip(s).gainDb == 0f }

        // Each fade has its own ruler; the readout takes an exact value.
        onNodeWithTag("$TAG_AUDIO_FADES.in.chip").performScrollTo().performClick()
        onNodeWithTag("$TAG_AUDIO_FADES.in.value").performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("1")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { audioClip(s).fadeIn == us(1_000_000L) }
        onNodeWithTag("$TAG_AUDIO_FADES.out.chip").performScrollTo().performClick()
        onNodeWithTag("$TAG_AUDIO_FADES.out.value").performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("2")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { audioClip(s).fadeOut == us(2_000_000L) }

        // The mute switch writes straight to the clip.
        onNodeWithTag(TAG_AUDIO_CLIP_MUTE).performClick()
        awaitState { audioClip(s).muted }
    }

    @Test
    fun mixerCardsDriveGainMuteSoloSourceAndMaster() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s, "fixture://song.mp3")
        importAudio(s, "fixture://voice.wav", trackCount = 2)
        openVideoTool(TAG_VIDEO_TOOL_MIXER, "Audio")

        val track = audioTrack(s)
        // Fader writes lane gain; the card row keeps the numeric readout.
        onNodeWithTag(TAG_AUDIO_FADER_PREFIX + track.id)
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-6f)
            }
        awaitState { audioTrack(s).gainDb == -6f && !it.isDrafting }
        onNodeWithTag(TAG_AUDIO_MUTE_PREFIX + track.id).performClick()
        awaitState { audioTrack(s).muted }
        onNodeWithTag(TAG_AUDIO_MUTE_PREFIX + track.id).performClick()
        awaitState { !audioTrack(s).muted }
        onNodeWithTag(TAG_AUDIO_SOLO_PREFIX + track.id).performClick()
        awaitState { audioTrack(s).solo }

        // The virtual source-sound input and the master bus — they ride
        // past the lane cards in the horizontal scroll.
        onNodeWithTag(TAG_AUDIO_SOURCE_MUTE)
            .performScrollTo().performClick()
        awaitState { it.sequence?.sourceSoundMuted == true }
        onNodeWithTag(TAG_AUDIO_MASTER_GAIN)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(3f)
            }
        awaitState { it.sequence?.masterGainDb == 3f }
        onNodeWithTag(TAG_AUDIO_LIMITER)
            .performScrollTo().performClick()
        awaitState { it.sequence?.limiterEnabled == false }

        // Channels, Ducking and EQ are real task jumps now — each opens
        // its registered surface through the workspace context.
        onNodeWithTag(TAG_AUDIO_ACTION_DUCKING).assertIsEnabled()
        onNodeWithTag(TAG_AUDIO_ACTION_EQ).assertIsEnabled()
        // The links sit in the panel header, above the scrolled limiter.
        onNodeWithTag(TAG_AUDIO_ACTION_DUCKING).performScrollTo().performClick()
        onNodeWithTag(TAG_DUCKING_TARGET).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_CONTEXT_BACK).performClick()
        openVideoTool(TAG_VIDEO_TOOL_MIXER, "Audio")
        onNodeWithTag(TAG_AUDIO_ACTION_EQ).performClick()
        onNodeWithTag(TAG_EQ_TRACK).assertIsDisplayed()
        onNodeWithTag(TAG_VIDEO_CONTEXT_BACK).performClick()
        openVideoTool(TAG_VIDEO_TOOL_MIXER, "Audio")
        onNodeWithTag(TAG_AUDIO_ACTION_CHANNELS).performClick()
        onNodeWithTag(TAG_AUDIO_ROUTING).assertIsDisplayed()
    }

    @Test
    fun routingWritesLinkedGainsPanAndDownmix() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        openVideoTool(TAG_VIDEO_TOOL_ROUTING, "Audio")
        fun routing() = audioClip(s).routing

        // Linked channels mirror the entered gain across L and R.
        onNodeWithTag(TAG_AUDIO_ROUTING_LEFT).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("-6")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            routing().leftGainDb == -6f && routing().rightGainDb == -6f
        }

        // Unlink, then the right channel moves on its own.
        onNodeWithTag(TAG_AUDIO_ROUTING_LINK).performClick()
        awaitState { !routing().linkChannels }
        onNodeWithTag("$TAG_AUDIO_ROUTING_RIGHT.chip").performClick()
        onNodeWithTag(TAG_AUDIO_ROUTING_RIGHT).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("3")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            routing().rightGainDb == 3f && routing().leftGainDb == -6f
        }

        // The mono-downmix switch lands on every clip. The row sits at the
        // bottom of the surface — scroll to it inside the panel.
        onNodeWithTag(TAG_AUDIO_ROUTING_MONO)
            .performScrollTo().performClick()
        awaitState { routing().monoDownmix }
    }

    @Test
    fun arrangeLinkSwitchExtractsAudioToALane() = runComposeUiTest {
        val s = liveEditor()
        openVideoTool(TAG_VIDEO_TAB_CLIPS)
        onNodeWithTag(TAG_VIDEO_ARRANGE_LINK).assertIsOn()
        onNodeWithTag(TAG_VIDEO_ARRANGE_LINK).performClick()
        awaitState {
            !it.importing && it.sequence?.audioTracks?.size == 1 &&
                it.sequence!!.tracks.single { t ->
                    t.kind == VideoTrackKind.Main
                }.clips.single().audioMuted
        }
        onNodeWithTag(TAG_VIDEO_ARRANGE_LINK).assertIsOff()
        // Toggling back on re-links the embedded input — the extracted
        // lane remains (removing it is a lane op, not a link op).
        onNodeWithTag(TAG_VIDEO_ARRANGE_LINK).performClick()
        awaitState {
            it.sequence!!.tracks.single { t ->
                t.kind == VideoTrackKind.Main
            }.clips.single().audioMuted == false
        }
        onNodeWithTag(TAG_VIDEO_ARRANGE_LINK).assertIsOn()
    }

    @Test
    fun playbackLevelsFlowIntoMixerMeters() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        playback.levelsToEmit = VideoLevels(
            sourceSound = VideoLevels.Stereo(0.4f, 0.4f),
            lanes = listOf(VideoLevels.Stereo(0.7f, 0.5f)),
            master = VideoLevels.Stereo(0.6f, 0.6f)
        )
        // Gate the engine so playback stays open and the emitted
        // levels persist instead of being cleared by playback end.
        playback.playGate = CompletableDeferred()
        runOnUiThread { s.play() }
        awaitState {
            it.levels.lanes.firstOrNull()?.left == 0.7f &&
                it.levels.master.left == 0.6f
        }
        openVideoTool(TAG_VIDEO_TOOL_MIXER, "Audio")
        onNodeWithTag(TAG_AUDIO_METER_PREFIX + "sourceSound")
            .assertExists()
        runOnUiThread { s.pause() }
        awaitState { it.levels.lanes.isEmpty() }
    }

    @Test
    fun tabletMixerShowsStripsAndLinksToRouting() =
        runDesktopComposeUiTest(width = 1440, height = 960) {
        val s = liveEditor(widthDp = 1440f, heightDp = 960f)
        importAudio(s, "fixture://song.mp3")
        importAudio(s, "fixture://voice.wav", trackCount = 2)
        onNodeWithTag(com.dot.gallery.editor.ui.TAG_HEADER_MORE).performClick()
            onNodeWithTag(TAG_VIDEO_MORE_MIXER).performClick()
        onNodeWithTag(TAG_AUDIO_MIXER).assertIsDisplayed()
        val track = audioTrack(s)
        onNodeWithTag(TAG_AUDIO_FADER_PREFIX + track.id)
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-6f)
            }
        awaitState { audioTrack(s).gainDb == -6f }
        // The panel header's Channels link switches the inspector to
        // Routing; the stage stays beside it.
        onNodeWithTag(TAG_AUDIO_ACTION_CHANNELS).performClick()
        onNodeWithTag(TAG_AUDIO_ROUTING).assertIsDisplayed()
        onNodeWithTag(TAG_AUDIO_MIXER).assertDoesNotExist()
        onNodeWithTag(TAG_VIDEO_REGION_PREVIEW).assertIsDisplayed()
    }

    // ---- A1c audio tools: ducking, EQ, voice-over ----

    @Test
    fun duckingSurfaceWritesTheRecipeAsHistorySteps() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s, "fixture://song.mp3")
        importAudio(s, "fixture://voice.wav", trackCount = 2)
        openVideoTool(TAG_VIDEO_TOOL_DUCKING, "Audio")
        onNodeWithTag(TAG_DUCKING_TARGET).assertIsDisplayed()

        val target = audioTrack(s)
        val trigger = audioTrack(s, 1)
        // The enable switch adopts the only other lane as its sidechain.
        onNodeWithTag(TAG_DUCKING_ENABLE).performClick()
        awaitState {
            audioTrack(s).ducking.enabled &&
                audioTrack(s).ducking.triggerTrackId == trigger.id
        }

        // Reduction rides the shared dB ruler — one SetProgress, one
        // history step.
        onNodeWithTag(TAG_DUCKING_REDUCTION)
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(-24f)
            }
        awaitState {
            audioTrack(s).ducking.reductionDb == -24f && !it.isDrafting
        }

        // Attack and release readouts take exact values on the same lane.
        onNodeWithTag("$TAG_DUCKING_ATTACK.chip").performScrollTo().performClick()
        onNodeWithTag(TAG_DUCKING_ATTACK).performScrollTo().performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("150")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            audioTrack(s).ducking.attackMs == 150f && !it.isDrafting
        }
        onNodeWithTag("$TAG_DUCKING_RELEASE.chip").performScrollTo().performClick()
        onNodeWithTag(TAG_DUCKING_RELEASE).performScrollTo().performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("900")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState {
            audioTrack(s).ducking.releaseMs == 900f && !it.isDrafting
        }

        // Preview drives the real mix; every write was its own step.
        // The button is the surface's last row — scroll it into the
        // sheet before clicking.
        val playsBefore = playback.plays
        onNodeWithTag(TAG_DUCKING_PREVIEW).performScrollTo().performClick()
        awaitState { playback.plays > playsBefore }
        clickUndo()
        awaitState { audioTrack(s).ducking.releaseMs == 300f }
        clickUndo()
        awaitState { audioTrack(s).ducking.attackMs == 80f }
    }

    @Test
    fun eqSurfacePresetsBandsAndBypassWriteTheLane() = runComposeUiTest {
        val s = liveEditor()
        importAudio(s)
        openVideoTool(TAG_VIDEO_TOOL_EQ, "Audio")
        onNodeWithTag(TAG_EQ_CURVE).assertIsDisplayed()

        // The Voice-clarity preset lands the whole recipe in one step.
        onNodeWithTag(TAG_EQ_PRESET_VOICE).performScrollTo().performClick()
        awaitState {
            !it.isDrafting && audioTrack(s).eq.run {
                lowDb == -2f && midDb == 1.5f && highDb == 4f &&
                    highPassHz == 90f && !bypass
            }
        }

        // A band row's dialog writes a precise value.
        onNodeWithTag(TAG_EQ_LOW).performScrollTo().performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("-4")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { audioTrack(s).eq.lowDb == -4f && !it.isDrafting }
        onNodeWithTag("$TAG_EQ_HIGHPASS.chip").performScrollTo().performClick()
        onNodeWithTag(TAG_EQ_HIGHPASS).performScrollTo().performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("120")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()
        awaitState { audioTrack(s).eq.highPassHz == 120f && !it.isDrafting }

        // Bypass keeps the recipe — and is itself one undoable step.
        // The switch is the surface's last row — scroll it into the
        // sheet before clicking.
        onNodeWithTag(TAG_EQ_BYPASS).performScrollTo().performClick()
        awaitState { audioTrack(s).eq.bypass }
        clickUndo()
        awaitState { !audioTrack(s).eq.bypass }
        onNodeWithTag(TAG_EQ_PRESET_FLAT).performScrollTo().performClick()
        awaitState { audioTrack(s).eq == AudioEq() }
    }

    @Test
    fun voicePermissionCardPromptsDismissesAndRecovers() = runComposeUiTest {
        var micPrompts = 0
        val recorder = FakeVoiceRecorder(MicPermission.Requestable)
        val s = liveEditor(
            voiceRecorder = recorder,
            onRequestMicrophone = { micPrompts++ }
        )
        openVideoTool(TAG_VIDEO_TOOL_VOICE, "Audio")
        // The surface refreshes support on entry — the ask-first card.
        awaitState { it.voiceSupport == VoiceSupport.RequiresPermission }
        onNodeWithTag(TAG_MIC_CARD).assertIsDisplayed()
        onNodeWithTag(TAG_VOICE_RECORD).assertDoesNotExist()

        // Not now returns to the timeline without a prompt.
        onNodeWithTag(TAG_MIC_NOT_NOW).performClick()
        waitForIdle()
        onNodeWithTag(TAG_MIC_CARD).assertDoesNotExist()
        assertEquals(0, micPrompts)

        // Reopen: Continue fires the host's real prompt seam.
        openVideoTool(TAG_VIDEO_TOOL_VOICE, "Audio")
        awaitState { it.voiceSupport == VoiceSupport.RequiresPermission }
        onNodeWithTag(TAG_MIC_CONTINUE).performClick()
        assertEquals(1, micPrompts)

        // The host granted — re-query flips the card into the recorder.
        recorder.permission = MicPermission.Granted
        runOnUiThread { s.refreshVoicePermission() }
        awaitState { it.voiceSupport == VoiceSupport.Ready }
        onNodeWithTag(TAG_MIC_CARD).assertDoesNotExist()
        onNodeWithTag(TAG_VOICE_RECORD).assertIsDisplayed()
    }

    @Test
    fun voiceTakeLandsAsALaneThroughTheRealSurface() = runComposeUiTest {
        val recorder = FakeVoiceRecorder()
        val s = liveEditor(voiceRecorder = recorder)
        // The Add-audio sheet's Record row is the second route in.
        openVideoTool(TAG_VIDEO_TOOL_ADD_AUDIO, "Audio")
        onNodeWithTag(TAG_AUDIO_RECORD).performClick()
        awaitState { it.voiceSupport == VoiceSupport.Ready }
        onNodeWithTag(TAG_VOICE_RECORD).assertIsDisplayed()

        // Zero the count-in so the take starts immediately.
        onNodeWithTag(TAG_VOICE_COUNTIN).performClick()
        onNodeWithTag(TAG_VALUE_FIELD).performTextReplacement("0")
        onNodeWithTag(TAG_VALUE_CONFIRM).performClick()

        onNodeWithTag(TAG_VOICE_RECORD).performClick()
        awaitState {
            it.voiceRecording?.phase == VoiceRecordingState.Phase.Recording
        }
        onNodeWithTag(TAG_VOICE_STATUS).assertIsDisplayed()
        assertEquals(1, recorder.starts)
        assertEquals(true, recorder.lastMonitor)

        onNodeWithTag(TAG_VOICE_STOP).performClick()
        awaitState {
            it.voiceRecording == null &&
                it.sequence?.audioTracks?.size == 1
        }
        assertEquals("voice-take.wav", audioTrack(s).name)
        clickUndo()
        awaitState { it.sequence?.audioTracks?.isEmpty() == true }
    }

    // ---- project recovery ----

    /**
     * A media seam whose probe fails for one scripted reference — the
     * session's relink/collect path must name exactly this one.
     */
    private class MissingProbeMedia(
        private val missing: String
    ) : FakeVideoMedia() {
        override suspend fun probe(reference: String): VideoInfo {
            if (reference == missing) {
                throw IllegalArgumentException("gone: $reference")
            }
            return super.probe(reference)
        }
    }

    /**
     * The recovery surface lists the missing reference's leaf name and
     * drives all three actions: the per-asset Relink asks the host for a
     * pick (which lands the project), Retry re-probes and re-reports the
     * still-missing asset, and Discard keeps the open document while
     * clearing the blocked restore.
     */
    @Test
    fun recoverySurfaceRelinksRetriesAndDiscards() = runComposeUiTest {
        val missing = "fixture://gone.mp4"
        media = MissingProbeMedia(missing)
        var relinkRequest: String? = null
        val s = liveEditor(onRequestRelink = { relinkRequest = it })

        // A persisted project whose overlay source is gone.
        val base = s.state.value.sequence!!
        val target = base.addSource(
            MediaAsset.Video(
                id = "src-gone", reference = missing,
                size = PixelSize(320, 240),
                duration = us(4_000_000),
                frameRate = FrameRate(30), hasAudio = false
            )
        )
        runOnUiThread { s.restoreProject(target, null, us(0)) }
        awaitState { it.recovery != null }
        waitForIdle()

        onNodeWithTag(TAG_RECOVERY_SURFACE).assertIsDisplayed()
        onNodeWithText("gone.mp4").assertIsDisplayed()

        // Relink asks the host — the returned pick resolves the replay.
        onNodeWithTag(
            TAG_RECOVERY_RELINK_PREFIX + missing
        ).performClick()
        waitForIdle()
        assertEquals(missing, relinkRequest)
        runOnUiThread { s.relinkRestore(missing, "fixture://found.mp4") }
        awaitState { it.recovery == null && it.sequence != null }
        waitForIdle()
        onNodeWithTag(TAG_RECOVERY_SURFACE).assertDoesNotExist()
        // The relinked source keeps its id and carries the new reference.
        assertTrue(
            s.state.value.sequence!!.sources.any {
                it.id == "src-gone" &&
                    it.reference == "fixture://found.mp4"
            }
        )

        // Re-block: Retry re-probes, the asset is still gone, recovery
        // re-reports instead of clearing.
        runOnUiThread { s.restoreProject(target, null, us(0)) }
        awaitState { it.recovery != null }
        waitForIdle()
        onNodeWithTag(TAG_RECOVERY_RETRY).performClick()
        waitForIdle()
        // Still blocked — the retried probe failed again, recovery
        // re-reports the same missing reference.
        assertNotNull(s.state.value.recovery)
        onNodeWithTag(TAG_RECOVERY_SURFACE).assertIsDisplayed()

        // Discard keeps the current document and clears the restore.
        onNodeWithTag(TAG_RECOVERY_DISCARD).performClick()
        awaitState { it.recovery == null }
        waitForIdle()
        onNodeWithTag(TAG_RECOVERY_SURFACE).assertDoesNotExist()
        assertTrue(s.state.value.sequence != null)
    }
}
