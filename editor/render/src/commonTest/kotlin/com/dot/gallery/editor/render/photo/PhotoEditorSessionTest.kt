package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.WhiteBalanceModel
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.model.PixelRect
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.SrgbPixels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoEditorSessionTest {

    private class FakeRaster(
        reference: String,
        rawSize: PixelSize,
        orientation: Int = 1,
        private val previewArgb: Int = 0xFF202020.toInt()
    ) : PhotoRaster {
        override val info = PhotoInfo(reference, reference.substringAfterLast('/'), rawSize, orientation)
        var closed = false

        override suspend fun readRegion(rect: PixelRect): SrgbPixels =
            SrgbPixels(PixelSize(rect.width, rect.height), IntArray(rect.width * rect.height))

        override suspend fun readPreview(maxEdge: Int): SrgbPixels =
            SrgbPixels(info.uprightSize, IntArray(info.uprightSize.pixelCount.toInt()) { previewArgb })

        override fun close() {
            closed = true
        }
    }

    private class FakeMedia : PhotoMediaAccess {
        val rasters = mutableMapOf<String, FakeRaster>()
        val opened = mutableListOf<String>()
        val exportedDocuments = mutableListOf<PhotoDocument>()
        val published = mutableListOf<PhotoExportResult>()
        // Per-reference gate; a true value simulates a backend that cannot be
        // cancelled and still finishes its decode.
        var slowOpen: String? = null
        var slowOpenGate: CompletableDeferred<Unit>? = null
        var uncancellableOpen = false
        var exportGate: CompletableDeferred<Unit>? = null
        var preCommitGate: CompletableDeferred<Unit>? = null
        var uncancellablePreCommit = false
        var postEncodeGate: CompletableDeferred<Unit>? = null
        var postPublishGate: CompletableDeferred<Unit>? = null
        var exportError: Throwable? = null

        fun add(
            reference: String,
            width: Int = 4,
            height: Int = 2,
            orientation: Int = 1,
            previewArgb: Int = 0xFF202020.toInt()
        ): FakeRaster {
            val raster = FakeRaster(reference, PixelSize(width, height), orientation, previewArgb)
            rasters[reference] = raster
            return raster
        }

        override suspend fun open(reference: String): PhotoRaster {
            opened += reference
            suspend fun awaitGate() {
                if (reference == slowOpen) slowOpenGate?.await()
            }
            if (uncancellableOpen) withContext(NonCancellable) { awaitGate() } else awaitGate()
            return rasters[reference] ?: throw IllegalStateException()
        }

        override suspend fun export(
            document: PhotoDocument,
            format: com.dot.gallery.editor.core.model.PhotoExportFormat?,
            onPublished: (PhotoExportResult) -> Unit,
            onProgress: (Float) -> Unit
        ): PhotoExportResult {
            exportedDocuments += document
            exportGate?.await()
            suspend fun awaitPreCommit() {
                preCommitGate?.await()
            }
            if (uncancellablePreCommit) {
                withContext(NonCancellable) { awaitPreCommit() }
            } else {
                awaitPreCommit()
            }
            exportError?.let { throw it }
            onProgress(0.9f)
            postEncodeGate?.await()
            val result = PhotoExportResult("out://copy/1", "image/png", document.source.size)
            // The commit receipt fires at publication, before the return path.
            onPublished(result)
            published += result
            postPublishGate?.await()
            onProgress(1f)
            return result
        }
    }

    private fun edits(exposure: Float = 0f, quarterTurns: Int = 0) = PhotoEdits(
        color = ColorAdjustments(exposureStops = exposure),
        geometry = PhotoGeometry(quarterTurns = quarterTurns)
    )

    private class Harness(
        val media: FakeMedia,
        val session: PhotoEditorSession,
        val errors: MutableList<Throwable>
    )

    /**
     * Session children live on the test scope so [advanceUntilIdle] drives
     * them (backgroundScope work would not be). The session is always closed
     * so its collectors never outlive the test.
     */
    private fun runSessionTest(
        block: suspend TestScope.(Harness) -> Unit
    ) = runTest {
        val media = FakeMedia()
        val errors = mutableListOf<Throwable>()
        val session = PhotoEditorSession(
            this, media, StandardTestDispatcher(testScheduler)
        ) { errors += it }
        try {
            block(Harness(media, session, errors))
        } finally {
            session.close()
        }
    }

    @Test
    fun `open publishes info document and previews`() = runSessionTest { h ->
        h.media.add("content://a", 4, 2)
        h.session.open("content://a")
        assertTrue(h.session.state.value.opening)
        advanceUntilIdle()
        val state = h.session.state.value
        assertFalse(state.opening)
        assertEquals("content://a", state.document?.source?.reference)
        assertNotNull(state.preview)
        assertNotNull(state.originalPreview)
        assertFalse(state.isDirty)
        assertTrue(h.errors.isEmpty())
        // The raster is only needed for the preview decode.
        assertTrue(h.media.rasters.getValue("content://a").closed)
    }

    @Test
    fun `controls respond synchronously before render work runs`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        // No scheduler advance yet for the render itself: history moves now.
        h.session.beginEdit("exposure")
        assertTrue(h.session.state.value.isDrafting)
        h.session.updateEdits(edits(exposure = 1f))
        val state = h.session.state.value
        assertEquals(1f, state.document?.adjustments?.exposureStops)
        assertTrue(state.isDirty)
        assertTrue(state.isDrafting)
        h.session.commitEdit()
        assertTrue(h.session.state.value.canUndo)
        assertFalse(h.session.state.value.isDrafting)
    }

    @Test
    fun `one hundred slider updates commit as a single undo step`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginEdit("exposure")
        repeat(100) { h.session.updateEdits(edits(exposure = it / 50f)) }
        h.session.commitEdit()
        assertTrue(h.session.state.value.canUndo)
        h.session.undo()
        assertEquals(0f, h.session.state.value.document?.adjustments?.exposureStops)
        assertFalse(h.session.state.value.canUndo)
        h.session.redo()
        assertEquals(99f / 50f, h.session.state.value.document?.adjustments?.exposureStops)
    }

    @Test
    fun `cancelEdit discards the draft`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("set", edits(exposure = 1f))
        h.session.beginEdit("drag")
        h.session.updateEdits(edits(exposure = -2f))
        h.session.cancelEdit()
        assertEquals(1f, h.session.state.value.document?.adjustments?.exposureStops)
        assertFalse(h.session.state.value.isDrafting)
        // The earlier committed step survives.
        assertTrue(h.session.state.value.canUndo)
    }

    @Test
    fun `crop preview mode publishes the crop-dropped display document`() =
        runSessionTest { h ->
        h.media.add("a", 40, 20)
        h.session.open("a")
        advanceUntilIdle()

        // Off by default: the canvas draws the recipe itself.
        assertNull(h.session.state.value.displayDocument)

        h.session.setCropPreviewMode(true)
        var state = h.session.state.value
        // The display document drops only `geometry.crop` — turns, flips,
        // colour and layers stay on the frame behind the crop window.
        var display = state.displayDocument
        assertNotNull(display)
        assertEquals(NormalizedCrop(), display.geometry.crop)
        assertEquals(
            state.document?.geometry?.quarterTurns,
            display.geometry.quarterTurns
        )

        // A window drag rewrites only `geometry.crop` — the display
        // document is unchanged, so the drag queues no preview render.
        h.session.beginEdit("Crop")
        h.session.updateEdits(
            PhotoEdits(
                geometry = PhotoGeometry(
                    crop = NormalizedCrop(0.1, 0.2, 0.9, 0.8)
                )
            )
        )
        state = h.session.state.value
        display = state.displayDocument
        assertNotNull(display)
        assertEquals(
            NormalizedCrop(0.1, 0.2, 0.9, 0.8),
            state.document?.geometry?.crop
        )
        assertEquals(NormalizedCrop(), display.geometry.crop)
        h.session.commitEdit()

        h.session.setCropPreviewMode(false)
        assertNull(h.session.state.value.displayDocument)
    }

    @Test
    fun `a second open supersedes a slow first open`() = runSessionTest { h ->
        val a = h.media.add("a")
        h.media.add("b")
        h.media.slowOpen = "a"
        h.media.slowOpenGate = CompletableDeferred()
        h.media.uncancellableOpen = true // backend ignores cancellation
        h.session.open("a")
        // Let A reach the suspended backend call so it is genuinely in flight.
        advanceUntilIdle()
        h.session.open("b")
        advanceUntilIdle()
        assertEquals("b", h.session.state.value.document?.source?.reference)
        // Now let the stale open finish — it must not republish.
        h.media.slowOpenGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals("b", h.session.state.value.document?.source?.reference)
        assertTrue(a.closed)
    }

    @Test
    fun `export captures a snapshot and later edits stay dirty`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("first", edits(exposure = 1f))
        h.media.exportGate = CompletableDeferred()
        h.session.exportCopy()
        // The export is in flight; edits are still permitted and stay dirty.
        h.session.applyEdits("second", edits(exposure = 2f))
        h.media.exportGate?.complete(Unit)
        advanceUntilIdle()
        val state = h.session.state.value
        assertNotNull(state.exported)
        assertFalse(state.exporting)
        // The exporter saw the snapshot taken at export time.
        assertEquals(1f, h.media.exportedDocuments.single().adjustments.exposureStops)
        assertEquals(2f, state.document?.adjustments?.exposureStops)
        assertTrue(state.isDirty)
    }

    @Test
    fun `a commit receipt survives a cancelled return path`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("first", edits(exposure = 1f))
        // The fake publishes, then blocks — cancellation loses the return
        // value but the receipt proves the file exists.
        h.media.postPublishGate = CompletableDeferred()
        h.session.exportCopy()
        advanceUntilIdle()
        assertEquals(1, h.media.published.size)
        h.session.cancelExport()
        advanceUntilIdle()
        val state = h.session.state.value
        assertNotNull(state.exported)
        assertFalse(state.exporting)
        assertFalse(state.exportCancelling)
        // The published snapshot counts as saved even though the call failed.
        assertFalse(state.isDirty)
        assertNull(state.error)
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `cancelling before the export body runs still clears the flag`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.exportCopy()
        // Cancel before the job's first dispatch.
        h.session.cancelExport()
        assertTrue(h.session.state.value.exporting)
        assertTrue(h.session.state.value.exportCancelling)
        advanceUntilIdle()
        val state = h.session.state.value
        assertFalse(state.exporting)
        assertFalse(state.exportCancelling)
        assertNull(state.exported)
        assertNull(state.error)
        assertTrue(h.errors.isEmpty())
        assertTrue(h.media.exportedDocuments.isEmpty())
    }

    @Test
    fun `a non-cancellable pre-commit stage holds exporting until it finishes`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.media.preCommitGate = CompletableDeferred()
        h.media.uncancellablePreCommit = true
        h.session.exportCopy()
        advanceUntilIdle()
        h.session.cancelExport()
        advanceUntilIdle()
        // The job is still winding down: no second export may overlap.
        assertTrue(h.session.state.value.exporting)
        assertTrue(h.session.state.value.exportCancelling)
        h.session.exportCopy()
        assertEquals(1, h.media.exportedDocuments.size)
        h.media.preCommitGate?.complete(Unit)
        advanceUntilIdle()
        val state = h.session.state.value
        assertFalse(state.exporting)
        assertFalse(state.exportCancelling)
        assertNotNull(state.exported)
    }

    @Test
    fun `export is rejected while a draft is open`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginEdit("drag")
        h.session.updateEdits(edits(exposure = 1f))
        h.session.exportCopy()
        assertFalse(h.session.state.value.exporting)
        assertTrue(h.media.exportedDocuments.isEmpty())
        h.session.commitEdit()
        h.session.exportCopy()
        advanceUntilIdle()
        assertNotNull(h.session.state.value.exported)
    }

    @Test
    fun `export is rejected while a tool transaction is open`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        // A tool's outer draft blocks export the same as a gesture draft —
        // an unapplied tool recipe is never written to disk.
        h.session.beginToolEdit("Light")
        h.session.beginEdit("Adjust exposure")
        h.session.updateEdits(edits(exposure = 1f))
        h.session.commitEdit()
        assertTrue(h.session.state.value.toolEditing)
        assertTrue(h.session.state.value.isDrafting)
        h.session.exportCopy()
        assertFalse(h.session.state.value.exporting)
        assertTrue(h.media.exportedDocuments.isEmpty())
        // Applying the tool commits one step; export then succeeds and
        // writes the applied recipe.
        h.session.applyToolEdit()
        assertFalse(h.session.state.value.isDrafting)
        h.session.exportCopy()
        advanceUntilIdle()
        assertNotNull(h.session.state.value.exported)
        assertEquals(1f, h.media.exportedDocuments.single().adjustments.exposureStops)
    }

    @Test
    fun `progress from an in-flight export never reverts newer edits`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        // Park the export between the mid progress report and publication.
        h.media.postEncodeGate = CompletableDeferred()
        h.session.exportCopy()
        advanceUntilIdle()
        assertEquals(0.9f, h.session.state.value.exportProgress)
        h.session.applyEdits("later", edits(exposure = 3f))
        h.media.postEncodeGate?.complete(Unit)
        advanceUntilIdle()
        val state = h.session.state.value
        // Late progress and completion must not clobber the newer document.
        assertEquals(3f, state.document?.adjustments?.exposureStops)
        assertNotNull(state.exported)
        assertTrue(state.isDirty)
    }

    @Test
    fun `reset is one undo step and stays dirty against the last save`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("edit", edits(exposure = 1f))
        h.session.exportCopy()
        advanceUntilIdle()
        assertFalse(h.session.state.value.isDirty)
        h.session.reset()
        val state = h.session.state.value
        assertEquals(0f, state.document?.adjustments?.exposureStops)
        assertTrue(state.isDirty)
        assertTrue(state.canUndo)
        h.session.undo()
        assertEquals(1f, h.session.state.value.document?.adjustments?.exposureStops)
    }

    @Test
    fun `failed export reports an error and a retry succeeds`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.media.exportError = IllegalStateException("disk full")
        h.session.exportCopy()
        advanceUntilIdle()
        // Host details go to onError; the UI sees the fixed action message.
        assertEquals("Could not save copy", h.session.state.value.error)
        assertFalse(h.session.state.value.exporting)
        assertEquals(1, h.errors.size)
        h.media.exportError = null
        h.session.exportCopy()
        advanceUntilIdle()
        assertNotNull(h.session.state.value.exported)
        assertFalse(h.session.state.value.isDirty)
    }

    @Test
    fun `open is blocked during export and during a draft`() = runSessionTest { h ->
        h.media.add("a")
        h.media.add("b")
        h.session.open("a")
        advanceUntilIdle()
        h.media.exportGate = CompletableDeferred()
        h.session.exportCopy()
        advanceUntilIdle() // export is parked on its gate
        h.session.open("b")
        assertEquals(listOf("a"), h.media.opened)
        h.session.cancelExport()
        advanceUntilIdle()
        h.session.beginEdit("drag")
        h.session.open("b")
        assertEquals(listOf("a"), h.media.opened)
        h.session.commitEdit()
        h.session.open("b")
        advanceUntilIdle()
        assertEquals("b", h.session.state.value.document?.source?.reference)
    }

    @Test
    fun `the previous preview is retained while a new render is pending`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        val first = h.session.state.value.preview
        h.session.applyEdits("warm", edits(exposure = 1f))
        // Before the scheduled render runs the old preview must still be there.
        assertTrue(h.session.state.value.preview === first)
        advanceUntilIdle()
        assertTrue(h.session.state.value.preview !== first)
    }

    @Test
    fun `failed open reports the error and keeps the session usable`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("missing")
        advanceUntilIdle()
        assertEquals("Could not open photo", h.session.state.value.error)
        assertEquals(1, h.errors.size)
        h.session.open("a")
        advanceUntilIdle()
        assertEquals("a", h.session.state.value.document?.source?.reference)
        assertNull(h.session.state.value.error)
    }

    @Test
    fun `restored edits open dirty and render through the graph`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a", restoredEdits = edits(quarterTurns = 1))
        advanceUntilIdle()
        val state = h.session.state.value
        assertEquals(1, state.document?.geometry?.quarterTurns)
        // The restore itself is an undoable, unsaved step.
        assertTrue(state.isDirty)
        assertTrue(state.canUndo)
        // The rendered preview carries the rotation: output is height x width.
        assertEquals(PixelSize(2, 4), state.preview?.size)
        // The decoded original stays upright for compare views.
        assertEquals(PixelSize(4, 2), state.originalPreview?.size)
    }

    @Test
    fun `disabled CPU preview keeps edits instant and holds the stale frame`() =
        runSessionTest { h ->
            h.media.add("a")
            h.session.open("a")
            advanceUntilIdle()
            val first = h.session.state.value.preview
            assertNotNull(first)

            // Given the shader backend owns the canvas
            h.session.setCpuPreviewEnabled(false)
            h.session.applyEdits("set", edits(exposure = 2f))
            advanceUntilIdle()
            // When edits land — document/history move, no CPU render runs
            val edited = h.session.state.value
            assertEquals(2f, edited.document?.adjustments?.exposureStops)
            assertTrue(edited.isDirty)
            assertFalse(edited.rendering)
            assertSame(first, edited.preview)

            // Then re-enabling renders the current recipe once
            h.session.setCpuPreviewEnabled(true)
            advanceUntilIdle()
            val resumed = h.session.state.value
            assertFalse(resumed.rendering)
            assertNotSame(first, resumed.preview)
            assertNull(resumed.error)
        }

    @Test
    fun `disabling mid render drops the in flight publication`() =
        runSessionTest { h ->
            h.media.add("a")
            h.session.open("a")
            advanceUntilIdle()
            val first = h.session.state.value.preview

            // Render queued but not yet dispatched to the test scheduler.
            h.session.applyEdits("set", edits(exposure = 1f))
            h.session.setCpuPreviewEnabled(false)
            advanceUntilIdle()
            val state = h.session.state.value
            // The superseded frame must not publish into the disabled state.
            assertSame(first, state.preview)
            assertFalse(state.rendering)
        }

    @Test
    fun `preview fallback reports the error and resumes CPU rendering`() =
        runSessionTest { h ->
            h.media.add("a")
            h.session.open("a")
            advanceUntilIdle()
            val first = h.session.state.value.preview

            h.session.setCpuPreviewEnabled(false)
            val failure = RuntimeException("shader backend lost")
            h.session.reportPreviewFallback(failure)
            // Diagnostics see the failure once; no user-facing error state.
            assertEquals(listOf<Throwable>(failure), h.errors)

            h.session.applyEdits("set", edits(exposure = 1f))
            advanceUntilIdle()
            val state = h.session.state.value
            assertFalse(state.rendering)
            assertNotSame(first, state.preview)
            assertNull(state.error)
        }

    @Test
    fun `cpu preview toggling is idempotent and safe after close`() =
        runSessionTest { h ->
            h.media.add("a")
            h.session.open("a")
            advanceUntilIdle()
            val first = h.session.state.value.preview

            h.session.setCpuPreviewEnabled(false)
            h.session.setCpuPreviewEnabled(false)
            advanceUntilIdle()
            assertSame(first, h.session.state.value.preview)

            h.session.close()
            // Post-close calls are inert — no crash, no republish.
            h.session.setCpuPreviewEnabled(true)
            h.session.reportPreviewFallback(RuntimeException("late"))
            advanceUntilIdle()
            assertEquals(PhotoEditorState(), h.session.state.value)
        }

    @Test
    fun `close cancels session work and no late callback republishes`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.media.postPublishGate = CompletableDeferred()
        h.session.exportCopy()
        advanceUntilIdle()
        h.session.close()
        // Late activity from the backend resolves nothing.
        h.media.postPublishGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(PhotoEditorState(), h.session.state.value)
        assertTrue(coroutineContext[Job]!!.isActive)
    }

    // ---- tool transactions ----

    @Test
    fun `tool interactions undo locally and apply as one global step`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginToolEdit("Light")
        var state = h.session.state.value
        assertTrue(state.toolEditing)
        // The outer draft spans the whole tool; only the gesture would
        // count as an interaction.
        assertTrue(state.isDrafting)
        assertFalse(state.interactionActive)

        // Two parameter interactions land as two local steps.
        h.session.beginEdit("exposure")
        h.session.updateEdits(edits(exposure = 1f))
        h.session.commitEdit()
        h.session.beginEdit("contrast")
        h.session.updateEdits(
            PhotoEdits(
                color = ColorAdjustments(exposureStops = 1f, contrast = 2f),
                geometry = PhotoGeometry()
            )
        )
        h.session.commitEdit()

        // Undo/redo speak the local history — one interaction at a time —
        // while the published document mirrors the live recipe.
        h.session.undo()
        state = h.session.state.value
        assertEquals(1f, state.document?.adjustments?.contrast)
        assertEquals(1f, state.document?.adjustments?.exposureStops)
        assertTrue(state.canRedo)
        h.session.redo()
        assertEquals(2f, h.session.state.value.document?.adjustments?.contrast)

        // Apply lands the whole tool as exactly one global step.
        h.session.applyToolEdit()
        state = h.session.state.value
        assertFalse(state.toolEditing)
        assertFalse(state.isDrafting)
        assertTrue(state.canUndo)
        h.session.undo()
        state = h.session.state.value
        assertEquals(0f, state.document?.adjustments?.exposureStops)
        assertEquals(1f, state.document?.adjustments?.contrast)
        assertFalse(state.canUndo)
    }

    @Test
    fun `cancelToolEdit restores the pre tool recipe and preserves redo`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("set", edits(exposure = 1f))
        h.session.undo()
        // Pre-tool state: clean recipe, one redo entry parked.
        assertTrue(h.session.state.value.canRedo)

        h.session.beginToolEdit("Light")
        h.session.beginEdit("drag")
        h.session.updateEdits(edits(exposure = 2f))
        h.session.commitEdit()
        h.session.cancelToolEdit()
        val state = h.session.state.value
        assertFalse(state.toolEditing)
        assertFalse(state.isDrafting)
        // Everything the tool did is gone; the redo stack survived.
        assertEquals(0f, state.document?.adjustments?.exposureStops)
        assertTrue(state.canRedo)
        assertFalse(state.canUndo)
        h.session.redo()
        assertEquals(1f, h.session.state.value.document?.adjustments?.exposureStops)
    }

    @Test
    fun `a cancelled gesture inside a tool keeps its earlier local steps`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginToolEdit("Light")
        // A finished local step, then a gesture cancelled mid-flight.
        h.session.applyEdits("exposure", edits(exposure = 1f))
        h.session.beginEdit("drag")
        h.session.updateEdits(edits(exposure = 2f))
        h.session.cancelEdit()
        val state = h.session.state.value
        assertFalse(state.interactionActive)
        assertTrue(state.toolEditing)
        // Only the cancelled gesture is gone — the committed local step
        // stays on the tool's history.
        assertEquals(1f, state.document?.adjustments?.exposureStops)
        assertTrue(state.canUndo)

        h.session.applyToolEdit()
        h.session.undo()
        assertEquals(0f, h.session.state.value.document?.adjustments?.exposureStops)
    }

    @Test
    fun `apply is blocked while an interaction is in flight`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginToolEdit("Light")

        // A live gesture holds the local draft open: Apply must leave both
        // histories untouched rather than swallowing the in-flight value.
        h.session.beginEdit("drag")
        h.session.updateEdits(edits(exposure = 1f))
        h.session.applyToolEdit()
        var state = h.session.state.value
        assertTrue(state.toolEditing)
        assertTrue(state.interactionActive)
        assertEquals(1f, state.document?.adjustments?.exposureStops)

        // Cancelling restores only the interaction — the tool stays open.
        h.session.cancelEdit()
        state = h.session.state.value
        assertFalse(state.interactionActive)
        assertTrue(state.toolEditing)
        assertEquals(0f, state.document?.adjustments?.exposureStops)

        // With nothing on the local history Apply lands no global step.
        h.session.applyToolEdit()
        state = h.session.state.value
        assertFalse(state.toolEditing)
        assertFalse(state.canUndo)
    }

    @Test
    fun `a no op tool apply records no history and keeps the redo stack`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("set", edits(exposure = 1f))
        h.session.undo()
        assertTrue(h.session.state.value.canRedo)

        h.session.beginToolEdit("Light")
        // An interaction that never changes the value is a no-op too.
        h.session.beginEdit("peek")
        h.session.updateEdits(edits())
        h.session.commitEdit()
        h.session.applyToolEdit()
        val state = h.session.state.value
        assertFalse(state.toolEditing)
        assertFalse(state.isDrafting)
        // Nothing landed: no empty undo entry, redo preserved.
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
        assertFalse(state.isDirty)
    }

    @Test
    fun `an export receipt landing mid tool keeps the draft and marks saved`() = runSessionTest { h ->
        h.media.add("a")
        h.session.open("a")
        advanceUntilIdle()
        h.session.applyEdits("first", edits(exposure = 1f))
        // Park the export before its commit receipt, then open a tool.
        h.media.preCommitGate = CompletableDeferred()
        h.session.exportCopy()
        advanceUntilIdle()
        h.session.beginToolEdit("Light")
        assertTrue(h.session.state.value.toolEditing)
        h.session.applyEdits("exposure", edits(exposure = 2f))
        h.media.preCommitGate?.complete(Unit)
        advanceUntilIdle()

        val state = h.session.state.value
        // The receipt landed; the tool draft is still open and dirty
        // against the saved snapshot.
        assertNotNull(state.exported)
        assertTrue(state.isDrafting)
        assertTrue(state.toolEditing)
        assertTrue(state.isDirty)
        assertEquals(2f, state.document?.adjustments?.exposureStops)
        // Undo still speaks the local history, not the exported snapshot.
        assertTrue(state.canUndo)
        // The exporter captured the recipe frozen at export start.
        assertEquals(1f, h.media.exportedDocuments.single().adjustments.exposureStops)

        // Apply resolves the draft: current differs from the snapshot.
        h.session.applyToolEdit()
        assertTrue(h.session.state.value.isDirty)
        h.session.undo()
        // Undo returns to the exported recipe — now clean, not dirty.
        val undone = h.session.state.value
        assertEquals(1f, undone.document?.adjustments?.exposureStops)
        assertFalse(undone.isDirty)
    }

    @Test
    fun `a source switch discards the tool transaction`() = runSessionTest { h ->
        h.media.add("a")
        h.media.add("b")
        h.session.open("a")
        advanceUntilIdle()
        h.session.beginToolEdit("Light")
        h.session.applyEdits("exposure", edits(exposure = 1f))
        assertTrue(h.session.state.value.toolEditing)

        // Unlike a bare gesture draft, an open tool transaction never
        // blocks the switch — it is discarded with the old source.
        h.session.open("b")
        advanceUntilIdle()
        val state = h.session.state.value
        assertEquals("b", state.document?.source?.reference)
        assertFalse(state.toolEditing)
        assertFalse(state.isDrafting)
        assertFalse(state.interactionActive)
        assertFalse(state.canUndo)
        assertEquals(0f, state.document?.adjustments?.exposureStops)
    }

    @Test
    fun `white balance sample reads the recipe before white balance`() =
        runSessionTest { h ->
        h.media.add("a", 16, 8, previewArgb = 0xFF9A8C80.toInt())
        h.session.open("a")
        advanceUntilIdle()
        val before = assertNotNull(h.session.sampleBeforeWhiteBalance(0.5f, 0.5f))
        assertTrue(before[0] > before[1] && before[1] > before[2], before.toList().toString())

        // White balance and later finishing stages never reach the sample.
        val tinted = PhotoEdits(
            finishing = PhotoFinishing(
                whiteBalance = PhotoWhiteBalance(3000f, 40f),
                vignette = PhotoVignette(amount = -0.6f)
            )
        )
        h.session.applyEdits("wb", tinted)
        val same = assertNotNull(h.session.sampleBeforeWhiteBalance(0.1f, 0.1f))
        assertContentEquals(before, same)

        // Stages before white balance do: one stop of exposure doubles it.
        h.session.applyEdits("exposure", tinted.copy(color = ColorAdjustments(exposureStops = 1f)))
        val brighter = assertNotNull(h.session.sampleBeforeWhiteBalance(0.5f, 0.5f))
        for (c in 0..2) assertEquals(before[c] * 2f, brighter[c], 1e-4f)

        // The solved recipe neutralises the sampled patch.
        val pick = assertNotNull(
            WhiteBalanceModel.neutralFor(brighter[0], brighter[1], brighter[2])
        )
        val g = WhiteBalanceModel.gains(pick)
        val r = brighter[0] * g[0]
        val gg = brighter[1] * g[1]
        val b = brighter[2] * g[2]
        assertEquals(1f, r / gg, 0.003f)
        assertEquals(1f, b / gg, 0.003f)
    }

    @Test
    fun `white balance sample is null without a document`() = runSessionTest { h ->
        assertNull(h.session.sampleBeforeWhiteBalance(0.5f, 0.5f))
    }

    private fun layerEdits(vararg ids: String) = PhotoEdits(
        layers = ids.map { id ->
            PhotoImageLayer(
                id = id,
                name = "layer-$id",
                source = MediaAsset.Image("src-$id", "ref://$id", PixelSize(4, 2))
            )
        }.toPersistentList()
    )

    @Test
    fun `addImageLayer inserts a top layer and the preview composites it`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()

            h.session.addImageLayer("layer")
            assertTrue(h.session.state.value.importingLayer)
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.importingLayer)
            assertFalse(state.layerImportError)
            val layers = state.document?.layers
            assertEquals(1, layers?.size)
            val layer = layers!!.single()
            assertEquals("layer-1", layer.id)
            assertEquals("layer-source-1", layer.source.id)
            assertEquals("layer", layer.source.reference)
            // The import lands as one committed global step — no open
            // tool transaction, no draft.
            assertFalse(state.toolEditing)
            assertFalse(state.isDrafting)
            assertTrue(state.canUndo)
            // The composite preview is the white layer everywhere.
            val preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
            }
            // The layer raster only lived for the preview decode.
            assertTrue(h.media.rasters.getValue("layer").closed)
        }

    @Test
    fun `the same reference may be layered twice on a shared source`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2)
            h.session.open("a")
            advanceUntilIdle()

            h.session.addImageLayer("layer")
            advanceUntilIdle()
            h.session.addImageLayer("layer")
            advanceUntilIdle()

            val layers = h.session.state.value.document!!.layers
            assertEquals(2, layers.size)
            assertEquals("layer-1", layers[0].id)
            assertEquals("layer-2", layers[1].id)
            // The duplicate reuses the loaded immutable source — one decode,
            // one frame, one source id shared by both layers.
            assertSame(layers[0].source, layers[1].source)
            assertEquals("layer-source-1", layers[0].source.id)
            assertEquals(1, h.media.opened.count { it == "layer" })
        }

    @Test
    fun `cancelLayerImport discards an in-flight decode`() = runSessionTest { h ->
        h.media.add("a", 4, 2)
        h.media.add("layer", 4, 2)
        h.media.slowOpen = "layer"
        h.media.slowOpenGate = CompletableDeferred()
        h.session.open("a")
        advanceUntilIdle()

        h.session.addImageLayer("layer")
        advanceUntilIdle()
        assertTrue(h.session.state.value.importingLayer)

        h.session.cancelLayerImport()
        assertFalse(h.session.state.value.importingLayer)
        h.media.slowOpenGate?.complete(Unit)
        advanceUntilIdle()

        val state = h.session.state.value
        assertTrue(state.document?.layers?.isEmpty() == true)
        assertFalse(state.layerImportError)
        // The decode never delivered a raster to the session — a raster
        // the port never returned is the port's to clean up, so nothing
        // is asserted about its close.
    }

    @Test
    fun `a source switch discards an in-flight layer import`() = runSessionTest { h ->
        h.media.add("a", 4, 2)
        h.media.add("b", 4, 2)
        h.media.add("layer", 4, 2)
        h.media.slowOpen = "layer"
        h.media.slowOpenGate = CompletableDeferred()
        h.session.open("a")
        advanceUntilIdle()

        h.session.addImageLayer("layer")
        advanceUntilIdle()
        assertTrue(h.session.state.value.importingLayer)

        h.session.open("b")
        h.media.slowOpenGate?.complete(Unit)
        advanceUntilIdle()

        val state = h.session.state.value
        assertEquals("b", state.document?.source?.reference)
        assertTrue(state.document?.layers?.isEmpty() == true)
        assertFalse(state.importingLayer)
        // The stale decode published nothing into the new source's map.
        assertFalse(state.layerImportError)
    }

    @Test
    fun `the layer cap flags an import error and mutates nothing`() = runSessionTest { h ->
        h.media.add("a", 4, 2)
        h.media.add("layer", 4, 2)
        h.session.open("a")
        advanceUntilIdle()
        val full = layerEdits(*Array(16) { "l$it" })
        h.session.applyEdits("fill", full)
        assertEquals(16, h.session.state.value.document?.layers?.size)

        h.session.addImageLayer("layer")
        advanceUntilIdle()

        val state = h.session.state.value
        assertTrue(state.layerImportError)
        assertFalse(state.importingLayer)
        assertEquals(16, state.document?.layers?.size)
        // The cap is checked before any media work.
        assertFalse(h.media.opened.contains("layer"))
    }

    @Test
    fun `a layer import is one global step that undoes and redoes`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()

            h.session.addImageLayer("layer")
            advanceUntilIdle()
            var state = h.session.state.value
            assertFalse(state.toolEditing)
            assertFalse(state.isDrafting)
            assertEquals(1, state.document?.layers?.size)
            assertTrue(state.canUndo)

            // Undo drops the layer; the decoded frame survives in the
            // session's reference cache so redo re-aliases it without a
            // new decode.
            h.session.undo()
            advanceUntilIdle()
            assertTrue(
                h.session.state.value.document?.layers?.isEmpty() == true
            )
            assertTrue(h.session.state.value.canRedo)

            h.session.redo()
            advanceUntilIdle()
            state = h.session.state.value
            assertEquals(1, state.document?.layers?.size)
            val preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
            }
            assertEquals(1, h.media.opened.count { it == "layer" })
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `a layer import is refused while a tool transaction is open`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2)
            h.session.open("a")
            advanceUntilIdle()

            h.session.beginToolEdit("Light")
            assertTrue(h.session.state.value.toolEditing)

            // The Layers flow never sneaks a recipe step under an open
            // transaction — nothing is decoded and nothing is inserted.
            h.session.addImageLayer("layer")
            advanceUntilIdle()
            assertTrue(
                h.session.state.value.document?.layers?.isEmpty() == true
            )
            assertFalse(h.session.state.value.importingLayer)
            assertFalse(h.session.state.value.layerImportError)
            assertFalse(h.media.opened.contains("layer"))

            // An open interaction draft on the document history refuses
            // the import the same way.
            h.session.cancelToolEdit()
            h.session.beginEdit("exposure")
            assertTrue(h.session.state.value.isDrafting)
            h.session.addImageLayer("layer")
            advanceUntilIdle()
            assertTrue(
                h.session.state.value.document?.layers?.isEmpty() == true
            )
            assertFalse(h.media.opened.contains("layer"))
            h.session.cancelEdit()
        }

    @Test
    fun `removing a layer drops its source frame and restores the base preview`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()
            h.session.addImageLayer("layer")
            advanceUntilIdle()

            val edits = h.session.state.value.document!!.let {
                PhotoEdits(color = it.adjustments, geometry = it.geometry)
            }
            h.session.applyEdits("remove", edits)
            advanceUntilIdle()

            val state = h.session.state.value
            assertTrue(state.document?.layers?.isEmpty() == true)
            val preview = state.preview
            assertNotNull(preview)
            // Base is the flat grey fixture — the layer frame is gone.
            for (i in 0 until 8) {
                assertEquals(0xFF202020.toInt(), preview.component(i), "pixel $i")
            }
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `undo then redo restores a dropped layer frame without an error`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()
            h.session.addImageLayer("layer")
            advanceUntilIdle()

            // Undo removes the layer from the recipe; reconciliation drops
            // the now-unreferenced frame — the pixels survive only in the
            // session's reference cache.
            h.session.undo()
            advanceUntilIdle()
            var state = h.session.state.value
            assertTrue(state.document?.layers?.isEmpty() == true)
            var preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFF202020.toInt(), preview.component(i), "pixel $i")
            }

            // Redo re-references the source id: the cached frame re-aliases
            // and the composite renders again — no 'no loaded frame' error.
            h.session.redo()
            advanceUntilIdle()
            state = h.session.state.value
            assertEquals(1, state.document?.layers?.size)
            preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
            }
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `removing a layer then undoing restores the composite`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()
            h.session.addImageLayer("layer")
            advanceUntilIdle()

            val base = h.session.state.value.document!!.let {
                PhotoEdits(color = it.adjustments, geometry = it.geometry)
            }
            h.session.applyEdits("remove", base)
            advanceUntilIdle()
            assertTrue(
                h.session.state.value.document?.layers?.isEmpty() == true
            )

            // Undo brings the layer back together with its decoded frame.
            h.session.undo()
            advanceUntilIdle()
            val state = h.session.state.value
            assertEquals(1, state.document?.layers?.size)
            val preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
            }
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `duplicating a layer reuses one frame for both source parameters`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("layer", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
            h.session.open("a")
            advanceUntilIdle()
            h.session.addImageLayer("layer")
            advanceUntilIdle()

            val layer = h.session.state.value.document!!.layers.single()
            h.session.applyEdits(
                "duplicate",
                h.session.state.value.document!!.toEdits().copy(
                    layers = persistentListOf(layer, layer.copy(id = "layer-2"))
                )
            )
            advanceUntilIdle()

            val state = h.session.state.value
            assertEquals(2, state.document?.layers?.size)
            assertEquals(
                layer.source.id,
                state.document?.layers?.get(1)?.source?.id
            )
            // Both placements resolve the single loaded frame — still
            // white everywhere, and no extra decode of the reference.
            val preview = state.preview
            assertNotNull(preview)
            for (i in 0 until 8) {
                assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
            }
            assertEquals(1, h.media.opened.count { it == "layer" })
            assertTrue(h.errors.isEmpty())
        }

    @Test
    fun `reordering layers changes which one wins the composite`() = runSessionTest { h ->
        h.media.add("a", 4, 2)
        h.media.add("white", 4, 2, previewArgb = 0xFFFFFFFF.toInt())
        h.media.add("black", 4, 2, previewArgb = 0xFF000000.toInt())
        h.session.open("a")
        advanceUntilIdle()
        h.session.addImageLayer("white")
        advanceUntilIdle()
        h.session.addImageLayer("black")
        advanceUntilIdle()

        var preview = h.session.state.value.preview
        assertNotNull(preview)
        // Black is on top: the whole canvas reads black.
        for (i in 0 until 8) {
            assertEquals(0xFF000000.toInt(), preview.component(i), "pixel $i")
        }

        val document = h.session.state.value.document!!
        h.session.applyEdits(
            "reorder",
            document.toEdits().copy(
                layers = document.layers.reversed().toPersistentList()
            )
        )
        advanceUntilIdle()

        preview = h.session.state.value.preview
        assertNotNull(preview)
        for (i in 0 until 8) {
            assertEquals(0xFFFFFFFF.toInt(), preview.component(i), "pixel $i")
        }
        // Both source frames are still referenced — nothing was dropped.
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun `restored layers decode on open and a missing one fails explicitly`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.media.add("ref://one", 4, 2)
            h.session.open("a", restoredEdits = layerEdits("one"))
            advanceUntilIdle()

            val state = h.session.state.value
            assertFalse(state.opening)
            val layer = state.document?.layers?.single()
            assertEquals("one", layer?.id)
            assertEquals("src-one", layer?.source?.id)
            assertTrue(h.media.rasters.getValue("ref://one").closed)

            // A mismatched actual size fails the whole open — no silent
            // omit; the unreadable layer joins the recovery's missing
            // list so the user can relink or discard, never a flat drop.
            h.media.add("b", 4, 2)
            h.media.add("ref://two", 8, 8)
            h.session.open("b", restoredEdits = layerEdits("two"))
            advanceUntilIdle()
            val failed = h.session.state.value
            assertNull(failed.document)
            assertNull(failed.error)
            assertContentEquals(
                listOf("ref://two"),
                failed.recovery?.missingReferences
            )
            assertTrue(h.errors.isNotEmpty())
        }

    @Test
    fun `missing layer media surfaces recovery and retry replays`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.session.open("a", restoredEdits = layerEdits("one"))
            advanceUntilIdle()
            val blocked = h.session.state.value
            assertNull(blocked.document)
            assertContentEquals(
                listOf("ref://one"),
                blocked.recovery?.missingReferences
            )

            // The file comes back — the retry replays the same recipe.
            h.media.add("ref://one", 4, 2)
            h.session.retryRestore()
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.recovery)
            assertEquals("one", state.document?.layers?.single()?.id)
        }

    @Test
    fun `discardRestore opens the source clean`() = runSessionTest { h ->
        h.media.add("a", 4, 2)
        h.session.open("a", restoredEdits = layerEdits("one"))
        advanceUntilIdle()
        assertNotNull(h.session.state.value.recovery)

        h.session.discardRestore()
        advanceUntilIdle()
        val state = h.session.state.value
        assertNull(state.recovery)
        assertEquals("a", state.document?.source?.reference)
        assertTrue(state.document?.layers?.isEmpty() == true)
        assertFalse(state.isDirty)
    }

    @Test
    fun `relinkRestore swaps the missing reference and lands the recipe`() =
        runSessionTest { h ->
            h.media.add("a", 4, 2)
            h.session.open("a", restoredEdits = layerEdits("one"))
            advanceUntilIdle()
            assertContentEquals(
                listOf("ref://one"),
                h.session.state.value.recovery?.missingReferences
            )

            h.media.add("ref://fixed", 4, 2)
            h.session.relinkRestore("ref://one", "ref://fixed")
            advanceUntilIdle()
            val state = h.session.state.value
            assertNull(state.recovery)
            val layer = state.document?.layers?.single()
            // The layer keeps its id and transform — only the source
            // reference moved to the relinked file.
            assertEquals("one", layer?.id)
            assertEquals("ref://fixed", layer?.source?.reference)
        }
}
