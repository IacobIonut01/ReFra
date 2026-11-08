package com.dot.gallery.editor.render.video

import com.dot.gallery.editor.core.model.AudioClip
import com.dot.gallery.editor.core.model.AudioTrack
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.VideoClip
import com.dot.gallery.editor.core.model.VideoSequence
import com.dot.gallery.editor.core.model.VideoTrack
import com.dot.gallery.editor.core.model.VideoTrackKind
import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.TimeRange
import kotlinx.collections.immutable.persistentListOf
import com.dot.gallery.editor.render.SrgbPixels
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class VideoEditorSessionTest {

    private class FakePlayback : VideoPlayback {
        var active = 0
        var maxActive = 0
        var plays = 0
        var playGate: CompletableDeferred<Unit>? = null
        var uncancellable = false
        var emitOnExit = false
        var exitPosition: EditorTime = EditorTime.Zero
        var exitColour = 0xFF778899.toInt()
        var endImmediately = false
        var lastFrom: EditorTime? = null
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
            active++
            maxActive = maxOf(maxActive, active)
            // Same end->0 restart contract the real engine honours.
            lastFrom =
                if (from == sequence.duration) EditorTime.Zero else from
            try {
                if (uncancellable) {
                    withContext(NonCancellable) { playGate?.await() }
                } else {
                    playGate?.await()
                }
                if (endImmediately) onPosition(sequence.duration)
            } finally {
                if (emitOnExit) {
                    onFrame(
                        VideoFrame(
                            exitPosition,
                            SrgbPixels(PixelSize(4, 4), IntArray(16) { exitColour })
                        )
                    )
                    onPosition(exitPosition)
                }
                active--
            }
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeMedia : VideoMediaAccess {
        val frameCalls = mutableListOf<EditorTime>()
        val frameEdges = mutableListOf<Int>()
        val frameRefs = mutableListOf<String>()
        val exportedSequences = mutableListOf<VideoSequence>()
        var frameGate: CompletableDeferred<Unit>? = null
        var uncancellableFrames = false
        var activeFrames = 0
        var maxActiveFrames = 0
        var exportPrePublishGate: CompletableDeferred<Unit>? = null
        var exportPostPublishGate: CompletableDeferred<Unit>? = null
        var exportError: Throwable? = null
        var durationUs = 4_000_000L
        var closed = false
        /** Thrown only for thumbnail-edge requests, not the seek preview. */
        var smallFrameError: Throwable? = null
        /** Per-reference probe failure — missing-media simulation. */
        var probeErrorFor: (String) -> Throwable? = { null }
        /** Per-reference frame colour override for composite previews. */
        var frameColourFor: ((String) -> Int)? = null
        /** Per-reference audio probe failure — missing-media simulation. */
        var probeAudioErrorFor: (String) -> Throwable? = { null }
        /** Gates the audio import's probe like [frameGate] gates frames. */
        var audioGate: CompletableDeferred<Unit>? = null

        fun audioInfoFor(reference: String) = AudioInfo(
            asset = MediaAsset.Audio(
                id = "asset",
                reference = reference,
                duration = EditorTime.fromMicroseconds(durationUs),
                sampleRate = 48_000,
                channels = 2
            ),
            displayName = reference.substringAfterLast('/')
        )

        fun infoFor(reference: String) = VideoInfo(
            asset = MediaAsset.Video(
                id = "asset",
                reference = reference,
                size = PixelSize(320, 240),
                duration = EditorTime.fromMicroseconds(durationUs),
                frameRate = null,
                hasAudio = true
            ),
            displayName = reference.substringAfterLast('/'),
            audioSampleRate = 48_000,
            audioChannels = 2
        )

        override suspend fun probe(reference: String): VideoInfo {
            probeErrorFor(reference)?.let { throw it }
            return infoFor(reference)
        }

        override suspend fun probeAudio(reference: String): AudioInfo {
            audioGate?.await()
            probeAudioErrorFor(reference)?.let { throw it }
            return audioInfoFor(reference)
        }

        override suspend fun frame(
            reference: String,
            at: EditorTime,
            maxEdge: Int
        ): VideoFrame {
            frameCalls += at
            frameEdges += maxEdge
            frameRefs += reference
            smallFrameError?.takeIf { maxEdge <= 200 }?.let { throw it }
            activeFrames++
            maxActiveFrames = maxOf(maxActiveFrames, activeFrames)
            try {
                val gate = frameGate
                if (uncancellableFrames) {
                    withContext(NonCancellable) { gate?.await() }
                } else {
                    gate?.await()
                }
            } finally {
                activeFrames--
            }
            // Distinct colour per call so the latest publication is visible.
            val colour = frameColourFor?.invoke(reference)
                ?: (0xFF000000.toInt() or (frameCalls.size * 53 and 0xFFFFFF))
            return VideoFrame(
                at, SrgbPixels(PixelSize(4, 4), IntArray(16) { colour })
            )
        }

        override suspend fun export(
            sequence: VideoSequence,
            onPublished: (VideoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): VideoExportResult {
            exportedSequences += sequence
            exportPrePublishGate?.await()
            exportError?.let { throw it }
            onProgress(0.9f)
            val result = VideoExportResult(
                "out://video/1", "video/mp4", sequence.size, sequence.duration
            )
            // The commit receipt fires at publication, before the return path.
            onPublished(result)
            exportPostPublishGate?.await()
            onProgress(1f)
            return result
        }

        override fun close() {
            closed = true
        }
    }

    /** A scripted voice take — level pollable, stop/cancel observable. */
    private class FakeVoiceCapture : VoiceCapture {
        var levelValue = 0.4f
        var cancelled = false
        var stops = 0
        var stopGate: CompletableDeferred<Unit>? = null
        var stopError: Throwable? = null

        override fun level() = levelValue

        override suspend fun stop(): VoiceTake {
            stops++
            stopGate?.await()
            stopError?.let { throw it }
            return VoiceTake(
                reference = "file:///cache/voice-take.wav",
                displayName = "voice-take.wav",
                duration = EditorTime.fromMicroseconds(2_000_000L),
                sampleRate = 48_000,
                channels = 1
            )
        }

        override fun cancel() {
            cancelled = true
        }
    }

    /** The session's only mic seam — every platform answer scripted. */
    private class FakeVoiceRecorder : VoiceRecorder {
        var permission = MicPermission.Granted
        var permissionError: Throwable? = null
        var startError: Throwable? = null
        var startGate: CompletableDeferred<Unit>? = null
        var starts = 0
        var lastMonitor: Boolean? = null
        var nextCapture = FakeVoiceCapture()

        override val inputLabel = "Fixture microphone"

        override suspend fun permissionState(): MicPermission {
            permissionError?.let { throw it }
            return permission
        }

        override suspend fun start(monitor: Boolean): VoiceCapture {
            startGate?.await()
            startError?.let { throw it }
            starts++
            lastMonitor = monitor
            return nextCapture
        }
    }

    private class Harness(
        val media: FakeMedia,
        val playback: FakePlayback,
        val session: VideoEditorSession,
        val errors: MutableList<Throwable>,
        val recorder: FakeVoiceRecorder? = null
    )

    /** Session children live on the test scope so [advanceUntilIdle] drives them. */
    private fun runSessionTest(
        recorder: FakeVoiceRecorder? = null,
        block: suspend TestScope.(Harness) -> Unit
    ) = runTest {
        val media = FakeMedia()
        val playback = FakePlayback()
        val errors = mutableListOf<Throwable>()
        val session = VideoEditorSession(
            this, media, playback, StandardTestDispatcher(testScheduler),
            recorder
        ) { errors += it }
        try {
            block(Harness(media, playback, session, errors, recorder))
        } finally {
            session.close()
        }
    }

    private fun us(micros: Long) = EditorTime.fromMicroseconds(micros)

    @Test
    fun `open publishes info sequence and preview`() = runSessionTest { h ->
        h.session.open("content://a")
        assertTrue(h.session.state.value.opening)
        advanceUntilIdle()
        val state = h.session.state.value
        assertFalse(state.opening)
        assertEquals("content://a", state.info?.asset?.reference)
        assertNotNull(state.sequence)
        assertEquals(1, state.sequence.tracks.size)
        assertEquals(1, state.sequence.tracks[0].clips.size)
        assertNotNull(state.selectedClipId)
        assertNotNull(state.preview)
        assertFalse(state.isDirty)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `latest seek wins but native decodes stay single-flight`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val openedCalls = h.media.frameCalls.size
            // Seek A hangs inside an uncancellable decode.
            h.media.uncancellableFrames = true
            val gateA = CompletableDeferred<Unit>()
            h.media.frameGate = gateA
            h.session.seek(us(1_000_000))
            advanceUntilIdle() // A is now suspended mid-decode
            // A hundred rapid seeks collapse to one queued request — the
            // native side stays at one decode, never a pile of grabbers.
            repeat(100) { h.session.seek(us(2_000_000 + it.toLong())) }
            val latestTarget = us(2_000_000L + 99)
            advanceUntilIdle()
            assertTrue(h.session.state.value.seeking)
            assertEquals(latestTarget, h.session.state.value.position)
            assertEquals(1, h.media.maxActiveFrames)
            // A's decode now finishes; its publication must be dropped and
            // only the newest queued request is fetched.
            gateA.complete(Unit)
            advanceUntilIdle()
            val state = h.session.state.value
            assertEquals(latestTarget, state.position)
            assertFalse(state.seeking)
            assertNotNull(state.preview)
            assertEquals(openedCalls + 2, h.media.frameCalls.size)
            assertEquals(1, h.media.maxActiveFrames)
            assertEquals(latestTarget, h.media.frameCalls.last())
        }

    @Test
    fun `seek during playback pauses first`() = runSessionTest { h ->
        h.session.open("content://a")
        advanceUntilIdle()
        h.playback.playGate = CompletableDeferred()
        h.session.play()
        advanceUntilIdle()
        assertTrue(h.session.state.value.playing)
        h.session.seek(us(1_500_000))
        val state = h.session.state.value
        assertFalse(state.playing)
        assertEquals(us(1_500_000), state.position)
        assertTrue(state.seeking)
        advanceUntilIdle()
        assertFalse(h.session.state.value.seeking)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `play then pause then play keeps a single audio job`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.playback.playGate = CompletableDeferred()
            h.session.play()
            advanceUntilIdle()
            assertTrue(h.session.state.value.playing)
            assertEquals(1, h.playback.active)
            h.session.pause()
            assertFalse(h.session.state.value.playing)
            h.session.play()
            advanceUntilIdle()
            // The second play awaited the first job's release — never two
            // live audio handles.
            assertEquals(1, h.playback.maxActive)
            assertEquals(2, h.playback.plays)
        }

    @Test
    fun `rapid play churn runs only the unwinding run and the newest`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // A holds the engine mid-play and cannot be interrupted.
            h.playback.uncancellable = true
            val gateA = CompletableDeferred<Unit>()
            h.playback.playGate = gateA
            h.session.play()
            advanceUntilIdle()
            assertEquals(1, h.playback.active)
            // pause/play B/pause/play C while A still unwinds.
            h.session.pause()
            h.session.play()
            h.session.pause()
            h.session.play()
            advanceUntilIdle()
            assertEquals(1, h.playback.plays) // only A has entered so far
            gateA.complete(Unit)
            advanceUntilIdle()
            // B was superseded while queued on the controller mutex — only
            // A (held unwinding) and C (the newest intent) ever ran.
            assertEquals(2, h.playback.plays)
            assertEquals(1, h.playback.maxActive)
            assertEquals(0, h.playback.active)
            assertFalse(h.session.state.value.playing)
        }

    @Test
    fun `a superseded play's trailing callbacks never publish`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val openPreview = h.session.state.value.preview
            h.playback.emitOnExit = true
            h.playback.exitPosition = us(999_999)
            h.playback.playGate = CompletableDeferred()
            h.session.play()
            advanceUntilIdle()
            h.session.pause()
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.playing)
            // The stale position/frame land tagged with the dead generation.
            assertNotEquals(us(999_999), state.position)
            assertEquals(EditorTime.Zero, state.position)
            assertEquals(
                openPreview?.copyPixels()?.toList(),
                state.preview?.copyPixels()?.toList()
            )
        }

    @Test
    fun `split uses the clip under the playhead not the selection`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Split [0,4s) at 1s -> clip-1 [0,1), clip-2 [1,4).
            h.session.seek(us(1_000_000))
            h.session.splitAtPlayhead()
            var clips = h.session.state.value.sequence!!.tracks[0].clips
            assertEquals(2, clips.size)
            assertEquals("clip-1", h.session.state.value.selectedClipId)
            // Select the right part, then put the playhead inside clip-1.
            h.session.selectClip("clip-2")
            h.session.seek(us(500_000))
            h.session.splitAtPlayhead()
            clips = h.session.state.value.sequence!!.tracks[0].clips
            assertEquals(3, clips.size)
            // clip-1 was split again and the left part stays selected —
            // clip-2 is untouched despite being selected.
            assertEquals("clip-1", h.session.state.value.selectedClipId)
            assertEquals(us(500_000), clips[0].sourceRange.duration)
            assertEquals("clip-3", clips[1].id)
        }

    @Test
    fun `split at a clip boundary is a no-op`() = runSessionTest { h ->
        h.session.open("content://a")
        advanceUntilIdle()
        h.session.seek(us(1_000_000))
        h.session.splitAtPlayhead()
        assertEquals(2, h.session.state.value.sequence!!.tracks[0].clips.size)
        // 1 s is exactly clip-2's start (clip-1's end) — strictly inside
        // neither, so no edit and no new clip.
        h.session.splitAtPlayhead()
        assertEquals(2, h.session.state.value.sequence!!.tracks[0].clips.size)
        assertNull(h.session.state.value.error)
    }

    @Test
    fun `undo after selecting the right split clip normalizes selection`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000))
            h.session.splitAtPlayhead()
            h.session.selectClip("clip-2")
            h.session.undo()
            val state = h.session.state.value
            // clip-2 vanished — selection falls back to the clip under the
            // playhead, never a dangling id.
            assertEquals("clip-1", state.selectedClipId)
            assertEquals(1, state.sequence?.tracks?.get(0)?.clips?.size)
        }

    @Test
    fun `delete selects the clip that moved into the slot`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000))
            h.session.splitAtPlayhead()
            h.session.seek(us(2_500_000))
            h.session.splitAtPlayhead()
            // Clips: clip-1 [0,1), clip-2 [1,2.5), clip-3 [2.5,4).
            var clips = h.session.state.value.sequence!!.tracks[0].clips
            assertEquals(3, clips.size)
            h.session.selectClip("clip-2")
            h.session.deleteSelected()
            clips = h.session.state.value.sequence!!.tracks[0].clips
            assertEquals(2, clips.size)
            // The clip that slid into index 1 is selected, not index 0.
            assertEquals("clip-3", h.session.state.value.selectedClipId)
        }

    @Test
    fun `trim split reorder repack and undo`() = runSessionTest { h ->
        h.session.open("content://a")
        advanceUntilIdle()
        val clipId = h.session.state.value.selectedClipId!!
        // Trim [0s,4s) -> [1s,3s): duration drops to 2 s.
        h.session.trimSelected(TimeRange(us(1_000_000), us(2_000_000)))
        var state = h.session.state.value
        assertEquals(us(2_000_000), state.sequence?.duration)
        assertTrue(state.canUndo)
        assertTrue(state.isDirty)
        // Split at the playhead inside the clip.
        h.session.seek(us(1_000_000))
        h.session.splitAtPlayhead()
        state = h.session.state.value
        assertEquals(2, state.sequence?.tracks?.get(0)?.clips?.size)
        // Reorder: move clip 1 after clip 2 — timeline re-packs from zero.
        h.session.moveSelected(1)
        state = h.session.state.value
        val clips = state.sequence!!.tracks[0].clips
        assertEquals(2, clips.size)
        assertEquals(EditorTime.Zero, clips[0].timelineStart)
        assertEquals(
            clips[0].timelineRange.endExclusive, clips[1].timelineStart
        )
        assertNotNull(clips[0].id)
        // Undo restores the pre-move order.
        h.session.undo()
        assertEquals(2, h.session.state.value.sequence?.tracks?.get(0)?.clips?.size)
        h.session.undo()
        assertEquals(1, h.session.state.value.sequence?.tracks?.get(0)?.clips?.size)
        h.session.undo()
        assertEquals(us(4_000_000), h.session.state.value.sequence?.duration)
    }

    @Test
    fun `deleting the last clip empties the sequence and blocks export`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.deleteSelected()
            advanceUntilIdle()
            val state = h.session.state.value
            assertEquals(0, state.sequence?.tracks?.get(0)?.clips?.size)
            assertEquals(EditorTime.Zero, state.sequence?.duration)
            assertNull(state.selectedClipId)
            assertNull(state.preview)
            // Export is disabled on an empty timeline: no export job starts.
            h.session.exportCopy()
            advanceUntilIdle()
            assertTrue(h.media.exportedSequences.isEmpty())
            // Undo restores the clip.
            h.session.undo()
            assertEquals(1, h.session.state.value.sequence?.tracks?.get(0)?.clips?.size)
        }

    @Test
    fun `export uses an immutable snapshot and new edits stay dirty`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            h.media.exportPrePublishGate = gate
            h.session.exportCopy()
            advanceUntilIdle()
            assertTrue(h.session.state.value.exporting)
            // The snapshot was taken at exportCopy time (4 s source).
            assertEquals(us(4_000_000), h.media.exportedSequences[0].duration)
            // An edit during export lands on the live document.
            h.session.trimSelected(TimeRange(us(0), us(1_000_000)))
            // The export still encodes the original snapshot.
            assertEquals(us(4_000_000), h.media.exportedSequences[0].duration)
            gate.complete(Unit)
            advanceUntilIdle()
            var state = h.session.state.value
            assertFalse(state.exporting)
            assertNotNull(state.exported)
            assertEquals("out://video/1", state.exported.reference)
            // Saved snapshot is the exported 4 s document; the live 1 s edit
            // is different — still dirty.
            assertTrue(state.isDirty)
            // Re-export the trimmed 1 s sequence to publication.
            h.session.exportCopy()
            advanceUntilIdle()
            state = h.session.state.value
            assertFalse(state.exporting)
            assertFalse(state.isDirty)
            assertEquals(us(1_000_000), h.media.exportedSequences.last().duration)
        }

    @Test
    fun `a fired publication receipt survives export cancellation`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val postPublish = CompletableDeferred<Unit>()
            h.media.exportPostPublishGate = postPublish
            h.session.exportCopy()
            advanceUntilIdle()
            // The receipt fired; the return path is now suspended.
            h.session.cancelExport()
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.exporting)
            // The published copy is still reported — the file exists.
            assertNotNull(state.exported)
            assertEquals("out://video/1", state.exported.reference)
            assertNull(state.error)
        }

    @Test
    fun `reaching the end parks the position at the duration`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Natural end: the engine reports the end marker, returns
            // inline — no second callback can be conflated away before
            // playing flips false.
            h.playback.endImmediately = true
            h.session.seek(us(2_000_000))
            h.session.play()
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.playing)
            assertFalse(state.buffering)
            assertEquals(us(4_000_000), state.position)
            // The next play is a new run from the start, matching the
            // engine's end->0 restart contract.
            h.playback.endImmediately = false
            h.playback.playGate = CompletableDeferred()
            h.session.play()
            advanceUntilIdle()
            assertEquals(EditorTime.Zero, h.playback.lastFrom)
            assertTrue(h.session.state.value.playing)
        }

    @Test
    fun `restoreCuts replays persisted cut ranges as one undoable step`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.restoreCuts(
                cuts = listOf(
                    TimeRange(us(1_000_000), us(1_000_000)),
                    TimeRange(us(3_000_000), us(1_000_000))
                ),
                selectedIndex = 1,
                position = us(1_500_000)
            )
            val state = h.session.state.value
            val clips = state.sequence!!.tracks[0].clips
            assertEquals(2, clips.size)
            assertEquals(us(2_000_000), state.sequence.duration)
            assertEquals(us(1_500_000), state.position)
            assertEquals(clips[1].id, state.selectedClipId)
            assertTrue(state.isDirty)
            assertTrue(state.canUndo)
            // One undo returns to the original uncut sequence.
            h.session.undo()
            assertEquals(
                us(4_000_000), h.session.state.value.sequence?.duration
            )
        }

    @Test
    fun `restoreCuts validates against the source before mutating`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Beyond the 4 s source — rejected, nothing changes.
            h.session.restoreCuts(
                cuts = listOf(TimeRange(us(9_000_000), us(1_000_000))),
                selectedIndex = 0,
                position = us(0)
            )
            var state = h.session.state.value
            assertEquals(VideoErrorKind.Open, state.error)
            assertEquals(us(4_000_000), state.sequence?.duration)
            assertFalse(state.isDirty)
            assertFalse(state.canUndo)
            // Out-of-range selection index is rejected the same way.
            h.session.clearError()
            h.session.restoreCuts(
                cuts = listOf(TimeRange(us(0), us(1_000_000))),
                selectedIndex = 7,
                position = us(0)
            )
            state = h.session.state.value
            assertEquals(VideoErrorKind.Open, state.error)
            assertEquals(us(4_000_000), state.sequence?.duration)
        }

    @Test
    fun `a decode failure on a clip tile never becomes an editor error`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.clearError()
            // Failing every small fetch after open: the mid-clip tile decode
            // reports through onError but the editor stays usable. Trim to
            // [1 s, 2 s) so the new midpoint (1.5 s) is a fresh cache key.
            h.media.smallFrameError = IllegalStateException("tile decode died")
            h.session.trimSelected(TimeRange(us(1_000_000), us(1_000_000)))
            advanceUntilIdle()
            assertNull(h.session.state.value.error)
            assertTrue(h.session.state.value.thumbnails.isEmpty())
            assertTrue(h.errors.isNotEmpty())
        }

    @Test
    fun `clip tiles are decoded once per filmstrip sample`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val thumbnails = h.session.state.value.thumbnails
            // Four samples per clip: 1/8, 3/8, 5/8, 7/8 of [0,4s).
            assertEquals(4, thumbnails.size)
            val clip = h.session.state.value.sequence!!.tracks[0].clips[0]
            assertEquals(clip.id, thumbnails[0].clipId)
            assertEquals(
                listOf(
                    us(500_000), us(1_500_000), us(2_500_000), us(3_500_000)
                ),
                thumbnails.map { it.sourceTime }
            )
            // A split resamples both halves on their own source ranges —
            // nothing decodes twice, stale-range tiles never publish.
            h.session.seek(us(1_000_000))
            h.session.splitAtPlayhead()
            advanceUntilIdle()
            val updated = h.session.state.value.thumbnails
            assertEquals(8, updated.size)
            // clip-1 [0,1s): 0.125/0.375/0.625/0.875 s.
            assertEquals(
                listOf(
                    us(125_000), us(375_000), us(625_000), us(875_000)
                ),
                updated.filter { it.clipId == "clip-1" }.map { it.sourceTime }
            )
            // clip-2 [1,4s): 1.375/2.125/2.875/3.625 s.
            assertEquals(
                listOf(
                    us(1_375_000), us(2_125_000), us(2_875_000), us(3_625_000)
                ),
                updated.filter { it.clipId == "clip-2" }.map { it.sourceTime }
            )
            // Small-edge decodes: 4 at open + 8 post-split; the seek
            // preview stays at full size.
            assertEquals(12, h.media.frameEdges.count { it <= 200 })
        }

    @Test
    fun `a trim drops tiles whose source instants left the range`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            assertEquals(4, h.session.state.value.thumbnails.size)
            // [2,4s) — the spec'd expected instants: 2.25/2.75/3.25/3.75 s.
            h.session.trimSelected(TimeRange(us(2_000_000), us(2_000_000)))
            advanceUntilIdle()
            val thumbs = h.session.state.value.thumbnails
            assertEquals(4, thumbs.size)
            assertEquals(
                listOf(
                    us(2_250_000), us(2_750_000), us(3_250_000), us(3_750_000)
                ),
                thumbs.map { it.sourceTime }
            )
        }

    @Test
    fun `tile cache stays bounded past the clip limit`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Halve the tail clip eight times -> 9 clips on one track.
            var boundary = 2_000_000L
            repeat(8) {
                h.session.seek(us(boundary))
                h.session.splitAtPlayhead()
                boundary += (4_000_000L - boundary) / 2
            }
            assertEquals(
                9, h.session.state.value.sequence!!.tracks[0].clips.size
            )
            advanceUntilIdle()
            // Beyond the 8-clip window only the selection is sampled.
            h.session.selectClip("clip-9")
            advanceUntilIdle()
            val thumbs = h.session.state.value.thumbnails
            // 8 window clips + the selected 9th is 36 samples; the LRU
            // bound holds 32, so clip-1's tiles are the evicted ones.
            assertEquals(32, thumbs.size)
            assertTrue(thumbs.none { it.clipId == "clip-1" })
            assertEquals(4, thumbs.count { it.clipId == "clip-9" })
        }

    @Test
    fun `restoreCuts accepts an empty list as a real delete-all`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Deleting every clip is a valid document — the restore replays
            // it instead of resurrecting the whole source.
            h.session.restoreCuts(
                cuts = emptyList(),
                selectedIndex = -1,
                position = EditorTime.Zero
            )
            // The empty preview clears through the async frame pipeline.
            advanceUntilIdle()
            val state = h.session.state.value
            assertEquals(0, state.sequence?.tracks?.get(0)?.clips?.size)
            assertEquals(EditorTime.Zero, state.sequence?.duration)
            assertTrue(state.isDirty)
            assertTrue(state.canUndo)
            assertNull(state.selectedClipId)
            assertNull(state.preview)
            assertNull(state.error)
            // One undo resurrects the original sequence.
            h.session.undo()
            assertEquals(
                us(4_000_000), h.session.state.value.sequence?.duration
            )
        }

    @Test
    fun `restoreCuts of the untouched source stays clean`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Replaying the full-source range must reproduce the initial
            // sequence verbatim — a process-death restore of an unedited
            // clip is not an edit, and an id change would fake dirt.
            h.session.restoreCuts(
                cuts = listOf(TimeRange(EditorTime.Zero, us(4_000_000))),
                selectedIndex = 0,
                position = EditorTime.Zero
            )
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.isDirty)
            assertFalse(state.canUndo)
            assertEquals("clip-1", state.sequence?.tracks?.get(0)?.clips?.single()?.id)
            assertEquals("clip-1", state.selectedClipId)
            // The counter moved past the restored ids — the next split
            // mints clip-2, never a duplicate clip-1.
            h.session.seek(us(1_000_000))
            h.session.splitAtPlayhead()
            val ids = h.session.state.value.sequence!!
                .tracks[0].clips.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
            assertTrue("clip-2" in ids)
        }

    @Test
    fun `restoreCuts rejects an empty list with a selection`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.restoreCuts(
                cuts = emptyList(),
                selectedIndex = 0,
                position = EditorTime.Zero
            )
            val state = h.session.state.value
            assertEquals(VideoErrorKind.Open, state.error)
            assertEquals(us(4_000_000), state.sequence?.duration)
            assertFalse(state.isDirty)
        }

    @Test
    fun `close cancels jobs resets state and retires media`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.playback.playGate = CompletableDeferred()
            h.session.play()
            advanceUntilIdle()
            assertTrue(h.session.state.value.playing)
            h.session.close()
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.playing)
            assertNull(state.sequence)
            assertEquals(0, h.playback.active)
            assertTrue(h.playback.closed)
            assertTrue(h.media.closed)
        }

    // ----- V1a: multi-source import and restore ------------------------

    @Test
    fun `addVideoSource End appends on Main selects and undoes as one step`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000L))
            advanceUntilIdle()

            h.session.addVideoSource("content://b", VideoInsertion.End)
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importing)
            val sequence = requireNotNull(state.sequence)
            assertEquals(
                listOf("source-1", "source-2"),
                sequence.sources.map { it.id }
            )
            val main = sequence.tracks.single()
            assertEquals(
                VideoTrackKind.Main, main.kind
            )
            assertEquals(2, main.clips.size)
            val added = main.clips[1]
            assertEquals("clip-2", added.id)
            assertEquals("source-2", added.sourceId)
            assertEquals(us(4_000_000L), added.timelineStart)
            assertEquals(us(8_000_000L), sequence.duration)
            assertEquals("clip-2", state.selectedClipId)
            // An End insert seeks the landed clip under the playhead.
            assertEquals(us(4_000_000L), state.position)
            assertTrue(state.isDirty)

            h.session.undo()
            advanceUntilIdle()
            val undone = requireNotNull(h.session.state.value.sequence)
            assertEquals(listOf("source-1"), undone.sources.map { it.id })
            assertEquals(1, undone.tracks.single().clips.size)
        }

    @Test
    fun `addVideoSource AtPlayhead splits the straddler and ripples`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(2_000_000L))
            advanceUntilIdle()

            h.session.addVideoSource("content://b", VideoInsertion.AtPlayhead)
            advanceUntilIdle()

            val main = requireNotNull(h.session.state.value.sequence)
                .tracks.single()
            assertEquals(3, main.clips.size)
            // Left of the split keeps clip-1's identity and range.
            assertEquals("clip-1", main.clips[0].id)
            assertEquals(us(0L), main.clips[0].timelineStart)
            assertEquals(us(2_000_000L), main.clips[0].sourceRange.duration)
            // The import lands at the playhead.
            assertEquals("clip-2", main.clips[1].id)
            assertEquals(us(2_000_000L), main.clips[1].timelineStart)
            assertEquals(us(4_000_000L), main.clips[1].sourceRange.duration)
            // The split remainder is rippled right past the new clip.
            assertEquals(us(6_000_000L), main.clips[2].timelineStart)
            assertEquals(us(2_000_000L), main.clips[2].sourceRange.duration)
            assertEquals(
                us(2_000_000L),
                main.clips[2].sourceRange.start
            )
            assertEquals("clip-2", h.session.state.value.selectedClipId)
        }

    @Test
    fun `addVideoSource Overlay lanes a clip at the playhead`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000L))
            advanceUntilIdle()

            h.session.addVideoSource("content://b", VideoInsertion.Overlay)
            advanceUntilIdle()

            val sequence = requireNotNull(h.session.state.value.sequence)
            assertEquals(2, sequence.tracks.size)
            val main = sequence.tracks[0]
            assertEquals(VideoTrackKind.Main, main.kind)
            // The Main lane is untouched — no ripple, no split.
            assertEquals(1, main.clips.size)
            assertEquals(us(4_000_000L), main.clips[0].sourceRange.duration)
            val overlay = sequence.tracks[1]
            assertEquals(VideoTrackKind.Overlay, overlay.kind)
            val clip = overlay.clips.single()
            assertEquals("source-2", clip.sourceId)
            assertEquals(us(1_000_000L), clip.timelineStart)
            assertEquals(us(5_000_000L), sequence.duration)

            // A second overlay where the lane is busy spawns a new lane;
            // a free span reuses the first.
            h.session.seek(us(6_000_000L))
            advanceUntilIdle()
            h.session.addVideoSource("content://c", VideoInsertion.Overlay)
            advanceUntilIdle()
            val after = requireNotNull(h.session.state.value.sequence)
            assertEquals(2, after.tracks.size)
            assertEquals(2, after.tracks[1].clips.size)
        }

    @Test
    fun `an import while one is in flight is dropped`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.media.frameGate = CompletableDeferred()
            h.session.addVideoSource("content://b", VideoInsertion.End)
            advanceUntilIdle()
            assertTrue(h.session.state.value.importing)

            // A second import during the first is ignored outright.
            h.session.addVideoSource("content://c", VideoInsertion.End)
            advanceUntilIdle()
            h.media.frameGate?.complete(Unit)
            advanceUntilIdle()

            val sequence = requireNotNull(h.session.state.value.sequence)
            assertEquals(listOf("source-1", "source-2"),
                sequence.sources.map { it.id })
            assertFalse(h.session.state.value.importing)
        }

    @Test
    fun `cancelImport drops an in-flight import before it lands`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.media.frameGate = CompletableDeferred()
            h.session.addVideoSource("content://b", VideoInsertion.End)
            advanceUntilIdle()
            assertTrue(h.session.state.value.importing)

            h.session.cancelImport()
            advanceUntilIdle()
            assertFalse(h.session.state.value.importing)
            h.media.frameGate?.complete(Unit)
            advanceUntilIdle()

            val sequence = requireNotNull(h.session.state.value.sequence)
            assertEquals(listOf("source-1"), sequence.sources.map { it.id })
            assertFalse(h.session.state.value.isDirty)
        }

    @Test
    fun `a probe failure surfaces an Open error and touches nothing`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.media.probeErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }
            h.session.addVideoSource("content://gone", VideoInsertion.End)
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importing)
            assertEquals(VideoErrorKind.Open, state.error)
            assertTrue(h.errors.isNotEmpty())
            val sequence = requireNotNull(state.sequence)
            assertEquals(listOf("source-1"), sequence.sources.map { it.id })
            assertFalse(state.isDirty)
        }

    @Test
    fun `a composite preview decodes every active clip on its own clock`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            // Opaque blue overlay over the red main — the whole preview
            // is the overlay colour because it covers the canvas.
            h.media.frameColourFor = { ref ->
                if (ref == "content://a") {
                    0xFFFF0000.toInt()
                } else {
                    0xFF0000FF.toInt()
                }
            }
            h.session.seek(us(1_000_000L))
            advanceUntilIdle()
            h.session.addVideoSource("content://b", VideoInsertion.Overlay)
            advanceUntilIdle()

            // The seek after the edit decoded both active clips.
            assertTrue("content://a" in h.media.frameRefs)
            assertTrue("content://b" in h.media.frameRefs)
            val preview = requireNotNull(h.session.state.value.preview)
            // Canvas centre is covered by both layers — opaque Normal
            // overlay wins; the fit-inside surround stays transparent.
            val centre = (preview.size.height / 2) * preview.size.width +
                preview.size.width / 2
            assertEquals(0xFF0000FF.toInt(), preview.copyPixels()[centre])
            assertEquals(0, preview.copyPixels()[0])
        }

    @Test
    fun `restoreProject probes every source and fails on missing media`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addSource(
                    MediaAsset.Video(
                        id = "source-2",
                        reference = "content://gone",
                        size = PixelSize(320, 240),
                        duration = us(4_000_000L),
                        frameRate = null,
                        hasAudio = false
                    )
                )
                .addTrack(
                    VideoTrack(
                        "track-2",
                        kind = VideoTrackKind.Overlay,
                        clips = persistentListOf(
                            VideoClip(
                                id = "clip-2",
                                sourceId = "source-2",
                                timelineStart = us(1_000_000L),
                                sourceRange = TimeRange(
                                    us(0L), us(2_000_000L)
                                )
                            )
                        )
                    )
                )
            h.media.probeErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }

            h.session.restoreProject(target, "clip-2", us(1_000_000L))
            advanceUntilIdle()

            val state = h.session.state.value
            assertNull(state.error)
            assertTrue(h.errors.isNotEmpty())
            // Missing media is recovery, not an open error — the recipe
            // stays pending behind the listed references.
            assertContentEquals(
                listOf("content://gone"),
                state.recovery?.missingReferences
            )
            // The opened document was not flattened or half-applied.
            assertEquals(base, state.sequence)
        }

    @Test
    fun `recovery retry re-probes and lands the pending project`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addSource(
                    MediaAsset.Video(
                        id = "source-2",
                        reference = "content://gone",
                        size = PixelSize(320, 240),
                        duration = us(4_000_000L),
                        frameRate = null,
                        hasAudio = false
                    )
                )
                .addTrack(
                    VideoTrack(
                        "track-2",
                        kind = VideoTrackKind.Overlay,
                        clips = persistentListOf(
                            VideoClip(
                                id = "clip-2",
                                sourceId = "source-2",
                                timelineStart = us(1_000_000L),
                                sourceRange = TimeRange(
                                    us(0L), us(2_000_000L)
                                )
                            )
                        )
                    )
                )
            h.media.probeErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }
            h.session.restoreProject(target, null, us(0L))
            advanceUntilIdle()
            assertContentEquals(
                listOf("content://gone"),
                h.session.state.value.recovery?.missingReferences
            )

            // The file comes back — the retry replays the same snapshot.
            h.media.probeErrorFor = { null }
            h.session.retryRestore()
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.recovery)
            assertEquals(target, state.sequence)
        }

    @Test
    fun `recovery discard keeps the opened document and drops the pending`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addSource(
                    MediaAsset.Video(
                        id = "source-2",
                        reference = "content://gone",
                        size = PixelSize(320, 240),
                        duration = us(4_000_000L),
                        frameRate = null,
                        hasAudio = false
                    )
                )
            h.media.probeErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }
            h.session.restoreProject(target, null, us(0L))
            advanceUntilIdle()
            assertNotNull(h.session.state.value.recovery)

            h.session.discardRestore()
            assertNull(h.session.state.value.recovery)
            // Discarding also drops the pending snapshot — a later retry
            // is a no-op, not a surprise restore.
            h.media.probeErrorFor = { null }
            h.session.retryRestore()
            advanceUntilIdle()
            assertEquals(base, h.session.state.value.sequence)
        }

    @Test
    fun `recovery relink swaps the missing reference and replays`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addSource(
                    MediaAsset.Video(
                        id = "source-2",
                        reference = "content://gone",
                        size = PixelSize(320, 240),
                        duration = us(4_000_000L),
                        frameRate = null,
                        hasAudio = false
                    )
                )
            h.media.probeErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }
            h.session.restoreProject(target, null, us(0L))
            advanceUntilIdle()
            assertContentEquals(
                listOf("content://gone"),
                h.session.state.value.recovery?.missingReferences
            )

            h.session.relinkRestore("content://gone", "content://b")
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.recovery)
            val sequence = requireNotNull(state.sequence)
            // The source id is unchanged — clip placement survives; only
            // the reference moved to the relinked file.
            val relinked = sequence.sources.single { it.id == "source-2" }
            assertEquals("content://b", relinked.reference)
        }

    @Test
    fun `restoreProject lands a valid document as one undoable step`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addSource(
                    MediaAsset.Video(
                        id = "source-2",
                        reference = "content://b",
                        size = PixelSize(320, 240),
                        duration = us(4_000_000L),
                        frameRate = null,
                        hasAudio = true
                    )
                )
                .addTrack(
                    VideoTrack(
                        "track-2",
                        kind = VideoTrackKind.Overlay,
                        clips = persistentListOf(
                            VideoClip(
                                id = "clip-2",
                                sourceId = "source-2",
                                timelineStart = us(1_000_000L),
                                sourceRange = TimeRange(
                                    us(0L), us(2_000_000L)
                                )
                            )
                        )
                    )
                )

            h.session.restoreProject(target, "clip-2", us(1_500_000L))
            advanceUntilIdle()

            val state = h.session.state.value
            assertNull(state.error)
            assertEquals(target, state.sequence)
            assertEquals("clip-2", state.selectedClipId)
            assertTrue(state.isDirty)
            // Both sources were probed and registered.
            assertEquals(
                setOf("source-1", "source-2"),
                state.sourceInfos.keys
            )
            // And the whole thing undoes back to the opened document.
            h.session.undo()
            advanceUntilIdle()
            assertEquals(base, h.session.state.value.sequence)
        }

    @Test
    fun `a clip that outruns the re-probed source duration fails`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            // The persisted document claims a 6 s source — valid against
            // its own stored asset — but the live probe reports 4 s. The
            // restore must fail rather than truncate the clip silently.
            val target = base.copy(
                sources = persistentListOf(
                    base.sources[0].copy(duration = us(6_000_000L))
                ),
                tracks = persistentListOf(
                    base.tracks[0].copy(
                        clips = persistentListOf(
                            base.tracks[0].clips[0].copy(
                                sourceRange = TimeRange(
                                    us(0L), us(6_000_000L)
                                )
                            )
                        )
                    )
                )
            )
            h.session.restoreProject(target, null, us(0L))
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.error)
            // The outrun clip makes its own source "unreadable for this
            // recipe" — it lands in the recovery list like missing media.
            assertContentEquals(
                listOf("content://a"),
                state.recovery?.missingReferences
            )
            assertEquals(base, state.sequence)
        }

    // ----- A1a: audio source import ------------------------------------

    @Test
    fun `addAudioSource lands a lane clip at the playhead and undoes as one step`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000L))
            advanceUntilIdle()

            h.session.addAudioSource("content://song.mp3")
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importing)
            val sequence = requireNotNull(state.sequence)
            assertEquals(
                listOf("source-2"), sequence.audioSources.map { it.id }
            )
            val lane = sequence.audioTracks.single()
            assertEquals("song.mp3", lane.name)
            val clip = lane.clips.single()
            assertEquals("source-2", clip.sourceId)
            assertEquals(us(1_000_000L), clip.timelineStart)
            assertEquals(us(4_000_000L), clip.sourceRange.duration)
            // The document reaches the audio clip's end — 5 s.
            assertEquals(us(5_000_000L), sequence.duration)
            assertEquals(setOf("source-2"), state.audioSourceInfos.keys)
            assertTrue(state.isDirty)

            // One history step removes source, lane and clip together.
            h.session.undo()
            advanceUntilIdle()
            val undone = requireNotNull(h.session.state.value.sequence)
            assertTrue(undone.audioSources.isEmpty())
            assertTrue(undone.audioTracks.isEmpty())
        }

    @Test
    fun `audio clips drag across lanes and onto a new lane as one step each`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.addAudioSource("content://song.mp3")
            advanceUntilIdle()
            h.session.addAudioTrack("Audio 2")
            advanceUntilIdle()
            val added = requireNotNull(h.session.state.value.sequence)
            val (first, second) = added.audioTracks
            assertEquals("Audio 2", second.name)
            assertTrue(second.clips.isEmpty())
            assertEquals(second.id, h.session.state.value.selectedAudioTrackId)
            val clipId = first.clips.single().id

            h.session.moveAudioClip(clipId, second.id, us(500_000L))
            advanceUntilIdle()
            val moved = requireNotNull(h.session.state.value.sequence)
            assertTrue(moved.audioTracks[0].clips.isEmpty())
            assertEquals(us(500_000L), moved.audioTracks[1].clips.single().timelineStart)
            assertEquals(clipId, h.session.state.value.selectedAudioClipId)
            assertEquals(second.id, h.session.state.value.selectedAudioTrackId)

            h.session.moveAudioClip(clipId, null, us(0L), "Audio 3")
            advanceUntilIdle()
            val fresh = requireNotNull(h.session.state.value.sequence)
            assertEquals(3, fresh.audioTracks.size)
            assertEquals("Audio 3", fresh.audioTracks[2].name)
            assertEquals(clipId, fresh.audioTracks[2].clips.single().id)

            // Each drop undoes as one step.
            h.session.undo()
            advanceUntilIdle()
            assertEquals(moved, h.session.state.value.sequence)
            h.session.undo()
            advanceUntilIdle()
            assertEquals(added, h.session.state.value.sequence)
        }

    @Test
    fun `a refused audio drop leaves the document and the error alone`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.addAudioSource("content://one.mp3")
            advanceUntilIdle()
            h.session.addAudioSource("content://two.mp3")
            advanceUntilIdle()
            val before = requireNotNull(h.session.state.value.sequence)
            val (first, second) = before.audioTracks

            // Both clips span 0–4 s, so landing one on the other overlaps.
            h.session.moveAudioClip(first.clips.single().id, second.id, us(1_000_000L))
            advanceUntilIdle()
            assertEquals(before, h.session.state.value.sequence)
            assertNull(h.session.state.value.error)

            h.session.applyAudioTrackEdit("Lock", second.id, AudioTrackEdit(locked = true))
            advanceUntilIdle()
            val locked = requireNotNull(h.session.state.value.sequence)
            h.session.moveAudioClip(first.clips.single().id, second.id, us(4_000_000L))
            advanceUntilIdle()
            assertEquals(locked, h.session.state.value.sequence)
        }

    @Test
    fun `audio lanes reorder delete and take the next import when empty`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.addAudioSource("content://song.mp3")
            advanceUntilIdle()
            h.session.addAudioTrack("Audio 2")
            advanceUntilIdle()
            val lanes = requireNotNull(h.session.state.value.sequence).audioTracks
            val empty = lanes[1]

            h.session.moveAudioTrack(empty.id, -1)
            advanceUntilIdle()
            assertEquals(
                listOf(empty.id, lanes[0].id),
                h.session.state.value.sequence?.audioTracks?.map { it.id }
            )
            // The edge lane stays put.
            h.session.moveAudioTrack(empty.id, -1)
            advanceUntilIdle()
            assertEquals(empty.id, h.session.state.value.sequence?.audioTracks?.first()?.id)

            // The selected empty lane takes the import instead of a new lane.
            h.session.selectAudioTrack(empty.id)
            h.session.addAudioSource("content://voice.wav")
            advanceUntilIdle()
            val filled = requireNotNull(h.session.state.value.sequence)
            assertEquals(2, filled.audioTracks.size)
            assertEquals(1, filled.audioTracks.first { it.id == empty.id }.clips.size)

            h.session.removeAudioTrack(lanes[0].id)
            advanceUntilIdle()
            val removed = requireNotNull(h.session.state.value.sequence)
            assertEquals(listOf(empty.id), removed.audioTracks.map { it.id })
            assertEquals(1, removed.audioSources.size)
        }

    @Test
    fun `a selected empty audio lane is skipped when filling is turned off`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.addAudioTrack("Audio 1")
            advanceUntilIdle()
            val empty = requireNotNull(h.session.state.value.sequence).audioTracks.single()

            h.session.fillSelectedEmptyAudioTrack = false
            h.session.selectAudioTrack(empty.id)
            h.session.addAudioSource("content://voice.wav")
            advanceUntilIdle()

            val tracks = requireNotNull(h.session.state.value.sequence).audioTracks
            assertEquals(2, tracks.size)
            assertTrue(tracks.first { it.id == empty.id }.clips.isEmpty())
        }

    @Test
    fun `overlay lanes are created reordered removed and take clip drags`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.addVideoSource("content://b", VideoInsertion.Overlay)
            advanceUntilIdle()
            h.session.addVideoTrack()
            advanceUntilIdle()
            val seq = requireNotNull(h.session.state.value.sequence)
            val overlays = seq.tracks.filter { it.kind == VideoTrackKind.Overlay }
            assertEquals(2, overlays.size)
            val (host, fresh) = overlays
            assertTrue(fresh.clips.isEmpty())
            val clipId = host.clips.single().id

            h.session.moveVideoClip(clipId, fresh.id, us(250_000L))
            advanceUntilIdle()
            val moved = requireNotNull(h.session.state.value.sequence)
            assertTrue(moved.tracks.first { it.id == host.id }.clips.isEmpty())
            assertEquals(
                us(250_000L),
                moved.tracks.first { it.id == fresh.id }.clips.single().timelineStart
            )
            assertEquals(clipId, h.session.state.value.selectedClipId)

            // Main is packed — a drop there is refused quietly.
            h.session.moveVideoClip(clipId, requireNotNull(moved.mainTrack).id, us(0L))
            advanceUntilIdle()
            assertEquals(moved, h.session.state.value.sequence)
            assertNull(h.session.state.value.error)

            h.session.moveTrack(fresh.id, -1)
            advanceUntilIdle()
            assertEquals(
                listOf(fresh.id, host.id),
                h.session.state.value.sequence?.tracks
                    ?.filter { it.kind == VideoTrackKind.Overlay }?.map { it.id }
            )

            h.session.removeTrack(fresh.id)
            advanceUntilIdle()
            val removed = requireNotNull(h.session.state.value.sequence)
            assertEquals(listOf(host.id), removed.tracks
                .filter { it.kind == VideoTrackKind.Overlay }.map { it.id })
            // The overlay's source left with its only clip.
            assertEquals(1, removed.sources.size)
            h.session.removeTrack(requireNotNull(removed.mainTrack).id)
            advanceUntilIdle()
            assertEquals(removed, h.session.state.value.sequence)
        }

    @Test
    fun `addRecordedAudio fails explicitly while the recorder is absent`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()

            h.session.addRecordedAudio()
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importing)
            assertEquals(VideoErrorKind.Unsupported, state.error)
            assertTrue(h.errors.isNotEmpty())
            val sequence = requireNotNull(state.sequence)
            assertTrue(sequence.audioSources.isEmpty())
            assertTrue(sequence.audioTracks.isEmpty())
            assertFalse(state.isDirty)
        }

    @Test
    fun `a failed audio probe surfaces an Open error and touches nothing`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.media.probeAudioErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }
            h.session.addAudioSource("content://gone")
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importing)
            assertEquals(VideoErrorKind.Open, state.error)
            assertTrue(h.errors.isNotEmpty())
            val sequence = requireNotNull(state.sequence)
            assertTrue(sequence.audioSources.isEmpty())
            assertFalse(state.isDirty)
        }

    @Test
    fun `cancelImport drops an in-flight audio import before it lands`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.media.audioGate = CompletableDeferred()
            h.session.addAudioSource("content://song")
            advanceUntilIdle()
            assertTrue(h.session.state.value.importing)

            h.session.cancelImport()
            advanceUntilIdle()
            assertFalse(h.session.state.value.importing)
            h.media.audioGate?.complete(Unit)
            advanceUntilIdle()

            val sequence = requireNotNull(h.session.state.value.sequence)
            assertTrue(sequence.audioSources.isEmpty())
            assertTrue(sequence.audioTracks.isEmpty())
            assertEquals(us(4_000_000L), sequence.duration)
            assertFalse(h.session.state.value.isDirty)
        }

    @Test
    fun `restoreProject probes audio sources and fails on missing media`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addAudioSource(
                    MediaAsset.Audio(
                        id = "source-2",
                        reference = "content://gone",
                        duration = us(4_000_000L),
                        sampleRate = 48_000,
                        channels = 2
                    )
                )
                .addAudioTrack(
                    AudioTrack(
                        "track-2",
                        clips = persistentListOf(
                            AudioClip(
                                id = "clip-2",
                                sourceId = "source-2",
                                timelineStart = us(1_000_000L),
                                sourceRange = TimeRange(
                                    us(0L), us(2_000_000L)
                                )
                            )
                        )
                    )
                )
            h.media.probeAudioErrorFor = { ref ->
                if (ref == "content://gone") {
                    IllegalArgumentException("missing media")
                } else {
                    null
                }
            }

            h.session.restoreProject(target, null, us(0L))
            advanceUntilIdle()

            val state = h.session.state.value
            assertNull(state.error)
            assertTrue(h.errors.isNotEmpty())
            assertContentEquals(
                listOf("content://gone"),
                state.recovery?.missingReferences
            )
            assertEquals(base, state.sequence)
        }

    @Test
    fun `restoreProject lands audio lanes with their infos`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            val base = requireNotNull(h.session.state.value.sequence)
            val target = base
                .addAudioSource(
                    MediaAsset.Audio(
                        id = "source-2",
                        reference = "content://song",
                        duration = us(4_000_000L),
                        sampleRate = 48_000,
                        channels = 2
                    )
                )
                .addAudioTrack(
                    AudioTrack(
                        "track-2",
                        clips = persistentListOf(
                            AudioClip(
                                id = "clip-2",
                                sourceId = "source-2",
                                timelineStart = us(1_000_000L),
                                sourceRange = TimeRange(
                                    us(0L), us(2_000_000L)
                                )
                            )
                        )
                    )
                )

            h.session.restoreProject(target, null, us(1_500_000L))
            advanceUntilIdle()

            val state = h.session.state.value
            assertNull(state.error)
            assertEquals(target, state.sequence)
            assertEquals(
                setOf("source-2"), state.audioSourceInfos.keys
            )
            h.session.undo()
            advanceUntilIdle()
            assertEquals(base, h.session.state.value.sequence)
        }

    // ---- A1c: voice-over recording ----

    @Test
    fun `voice support follows the recorder permission`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            h.session.open("content://a")
            advanceUntilIdle()

            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.Ready, h.session.state.value.voiceSupport
            )
            assertEquals(
                "Fixture microphone", h.session.state.value.voiceInput
            )

            recorder.permission = MicPermission.Requestable
            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.RequiresPermission,
                h.session.state.value.voiceSupport
            )

            recorder.permission = MicPermission.Denied
            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.Denied, h.session.state.value.voiceSupport
            )

            // A platform failure is explicit, never a stale Ready.
            recorder.permissionError =
                VoiceRecorderException("mic service unreachable")
            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.Unavailable,
                h.session.state.value.voiceSupport
            )
            assertTrue(h.errors.isNotEmpty())
        }

    @Test
    fun `a host without a recorder reports unavailable and unsupported`() =
        runSessionTest { h ->
            h.session.open("content://a")
            advanceUntilIdle()

            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.Unavailable,
                h.session.state.value.voiceSupport
            )

            h.session.beginVoiceRecording()
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.voiceRecording)
            assertEquals(VideoErrorKind.Unsupported, state.error)
            assertTrue(h.errors.single() is UnsupportedVideoException)
        }

    @Test
    fun `a refused prompt marks the refusal`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            h.recorder!!.permission = MicPermission.Requestable
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.refreshVoicePermission()
            advanceUntilIdle()
            assertEquals(
                VoiceSupport.RequiresPermission,
                h.session.state.value.voiceSupport
            )
            // The platform prompt answered No — the Denied copy is the
            // honest remedy until the user unblocks the system toggle.
            h.session.voicePermissionDenied()
            assertEquals(
                VoiceSupport.Denied, h.session.state.value.voiceSupport
            )
        }

    @Test
    fun `count-in records at the anchor and stop imports one undoable take`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.seek(us(1_000_000))
            advanceUntilIdle()
            val anchor = h.session.state.value.position

            h.session.beginVoiceRecording(countInMs = 3_000, monitor = true)
            val counting = h.session.state.value.voiceRecording
            assertNotNull(counting)
            assertEquals(
                VoiceRecordingState.Phase.CountIn, counting.phase
            )
            assertEquals(anchor, counting.anchor)
            // The lead-in plays the live mix; nothing captured yet.
            runCurrent()
            assertTrue(h.playback.plays > 0)
            assertEquals(0, recorder.starts)

            advanceTimeBy(3_000)
            // The lead-in delay lands exactly on the advance boundary —
            // advanceTimeBy runs only tasks due *before* its endpoint,
            // so runCurrent flushes the start chain. From here the level
            // poll loop is live: advanceUntilIdle would spin on its
            // self-rescheduling delay forever.
            runCurrent()
            assertEquals(1, recorder.starts)
            assertEquals(true, recorder.lastMonitor)
            val recording = h.session.state.value.voiceRecording
            assertNotNull(recording)
            assertEquals(
                VoiceRecordingState.Phase.Recording, recording.phase
            )

            // The input meter publishes the polled capture level.
            recorder.nextCapture.levelValue = 0.75f
            advanceTimeBy(40)
            assertEquals(
                0.75f,
                h.session.state.value.voiceRecording?.level
            )

            h.session.stopVoiceRecording()
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.voiceRecording)
            assertNull(state.error)
            assertFalse(recorder.nextCapture.cancelled)
            assertEquals(1, recorder.nextCapture.stops)
            // The take landed as a real lane anchored at the playhead
            // position the user armed — one history step, like an import.
            val track = state.sequence!!.audioTracks.single()
            assertEquals("voice-take.wav", track.name)
            val clip = track.clips.single()
            assertEquals(anchor, clip.timelineStart)
            val source = state.sequence.audioSources.single()
            assertEquals("file:///cache/voice-take.wav", source.reference)
            assertEquals(1, source.channels)

            h.session.undo()
            advanceUntilIdle()
            assertTrue(
                h.session.state.value.sequence!!.audioTracks.isEmpty()
            )
        }

    @Test
    fun `stop during the count-in abandons the take`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.beginVoiceRecording(countInMs = 3_000)
            advanceTimeBy(1_000)
            // Still leading in — Stop is a cancel, nothing was captured.
            h.session.stopVoiceRecording()
            advanceUntilIdle()
            assertNull(h.session.state.value.voiceRecording)
            assertEquals(0, recorder.starts)
            assertTrue(
                h.session.state.value.sequence!!.audioTracks.isEmpty()
            )
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `cancel during capture deletes the temp take`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.beginVoiceRecording(countInMs = 1_000)
            advanceTimeBy(1_000)
            runCurrent()
            // Recording — the live poll loop means no advanceUntilIdle.
            assertEquals(
                VoiceRecordingState.Phase.Recording,
                h.session.state.value.voiceRecording?.phase
            )

            h.session.cancelVoiceRecording()
            advanceUntilIdle()
            val capture = recorder.nextCapture
            assertTrue(capture.cancelled)
            assertEquals(0, capture.stops)
            assertNull(h.session.state.value.voiceRecording)
            assertTrue(
                h.session.state.value.sequence!!.audioTracks.isEmpty()
            )
        }

    @Test
    fun `a capture start failure is an explicit error`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            recorder.startError = VoiceRecorderException("mic in use")
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.beginVoiceRecording(countInMs = 1_000)
            advanceTimeBy(1_000)
            advanceUntilIdle()

            val state = h.session.state.value
            assertNull(state.voiceRecording)
            assertEquals(VideoErrorKind.Open, state.error)
            assertTrue(h.errors.single() is VoiceRecorderException)
            assertTrue(
                h.session.state.value.sequence!!.audioTracks.isEmpty()
            )
        }

    @Test
    fun `a stop failure reports and leaves the document untouched`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            val recorder = h.recorder!!
            recorder.nextCapture.stopError =
                VoiceRecorderException("take write failed")
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.beginVoiceRecording(countInMs = 1_000)
            advanceTimeBy(1_000)
            runCurrent()
            // Poll loop live — stop cancels it before advanceUntilIdle.
            h.session.stopVoiceRecording()
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.voiceRecording)
            assertEquals(VideoErrorKind.Open, state.error)
            assertTrue(h.errors.single() is VoiceRecorderException)
            assertTrue(state.sequence!!.audioTracks.isEmpty())
        }

    @Test
    fun `a second begin while a take is live is refused`() =
        runSessionTest(recorder = FakeVoiceRecorder()) { h ->
            h.session.open("content://a")
            advanceUntilIdle()
            h.session.beginVoiceRecording(countInMs = 3_000)
            val first = h.session.state.value.voiceRecording
            assertNotNull(first)
            h.session.beginVoiceRecording(countInMs = 1_000)
            // Same take, untouched — no second state, no second count-in.
            assertEquals(first, h.session.state.value.voiceRecording)
            h.session.cancelVoiceRecording()
            advanceUntilIdle()
            assertNull(h.session.state.value.voiceRecording)
        }
}
