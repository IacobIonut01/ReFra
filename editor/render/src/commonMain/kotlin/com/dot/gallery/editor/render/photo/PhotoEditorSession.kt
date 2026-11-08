package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.history.EditHistory
import com.dot.gallery.editor.core.model.MAX_PHOTO_IMAGE_LAYERS
import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoImageLayer
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.core.model.toDocument
import com.dot.gallery.editor.render.CpuFrameRenderer
import com.dot.gallery.editor.render.FrameSource
import com.dot.gallery.editor.render.LinearRgbaFrame
import com.dot.gallery.editor.render.PreviewScheduler
import com.dot.gallery.editor.render.PreviewState
import com.dot.gallery.editor.render.RenderRequest
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.render.renderGraph
import com.dot.gallery.editor.render.toLinearFrame
import com.dot.gallery.editor.render.toSrgbPixels
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-facing controller for the photo editor: owns the open/decode/render/
 * export pipeline behind one [StateFlow] of [PhotoEditorState].
 *
 * All public functions are confined to the owning thread (typically the UI
 * thread); state they can compute synchronously — controls, history flags —
 * is published before they return, and every write to [state] happens on the
 * owner. Decode, rendering, PNG encoding and I/O run on [workerDispatcher]
 * and report back through conflated [MutableStateFlow]s, so a busy worker
 * never interleaves a stale write with a synchronous edit.
 *
 * Preview rendering goes through a [PreviewScheduler] (latest-request-wins,
 * one render in flight, superseded work cancelled). The decoded upright
 * preview frames are held in one atomic immutable map keyed by source id —
 * the base plus every loaded layer source — so moving a slider re-renders
 * through the graph and never re-decodes. [open] while exporting or
 * mid-draft is ignored; a superseding [open] invalidates the previous
 * request via a generation counter so a slow backend can never publish
 * into the new document.
 *
 * [addImageLayer] decodes one more source on the worker dispatcher and
 * inserts it at the top of the recipe's layer list as one immediate
 * global history step — only from an idle document, never inside a tool
 * transaction. Stale or cancelled imports publish nothing. Restored
 * documents open and decode every referenced layer atomically — a
 * missing or mismatched layer source fails the open explicitly rather
 * than silently omitting it.
 *
 * Tool transactions: [beginToolEdit] opens an outer draft plus a private
 * [EditHistory] the tool's interactions live in. While a tool owns the
 * draft, begin/update/commit/cancel/apply/undo/redo all target the local
 * history — Undo rewinds one interaction, not the whole tool — and every
 * effective local change is mirrored into the open outer draft so the
 * document, preview and dirty flag track the live recipe. [applyToolEdit]
 * commits the outer draft once (one global undo step, none for a no-op
 * tool); [cancelToolEdit] restores the pre-tool recipe without a step.
 * [PhotoEditorState.isDrafting] reports the outer draft for the whole tool
 * session, [PhotoEditorState.interactionActive] only the in-flight
 * gesture/dialog.
 *
 * Exports are single-flight and export the *committed* recipe (a draft in
 * progress is not a saveable state — [exportCopy] is ignored while
 * drafting). The media port's `onPublished` callback is the commit receipt:
 * a successful publication is reported as success even when cancellation
 * prevents the export call from returning normally. [cancelExport] marks
 * [PhotoEditorState.exportCancelling] but keeps `exporting` true until the
 * job actually finishes, so a second export can never overlap cleanup.
 */
class PhotoEditorSession(
    scope: CoroutineScope,
    private val media: PhotoMediaAccess,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onError: (Throwable) -> Unit
) {

    private val sessionJob = SupervisorJob(scope.coroutineContext[Job])
    private val sessionScope =
        CoroutineScope(scope.coroutineContext.minusKey(Job) + sessionJob)

    private val _state = MutableStateFlow(PhotoEditorState())
    val state: StateFlow<PhotoEditorState> = _state.asStateFlow()

    /**
     * Thread-safe snapshot operation: opens independent owned readers without
     * consulting mutable session state or borrowing preview/export handles.
     */
    suspend fun openImageReader(document: PhotoDocument): PhotoImageReader =
        PhotoImageReader.open(media, document, workerDispatcher)

    /** Tagged progress so a stale export cannot write into a newer state. */
    private data class ExportProgressUpdate(val generation: Long, val value: Float)

    /** One decoded open: base preview, every source frame and layer thumbs. */
    private data class OpenedSource(
        val info: PhotoInfo,
        val preview: SrgbPixels,
        val frames: Map<String, LinearRgbaFrame>,
        val layerPreviews: Map<String, SrgbPixels>
    )

    // Owner-thread confined.
    private var history: EditHistory<PhotoEdits>? = null

    /**
     * The open tool's private history — interactions (gestures, numeric
     * entries, resets) are local steps until Apply lands them on [history]
     * as one entry. Null while no tool owns the draft.
     */
    private var toolHistory: EditHistory<PhotoEdits>? = null
    private var source: MediaAsset.Image? = null
    private var openJob: Job? = null
    private var exportJob: Job? = null
    private var layerImportJob: Job? = null
    private var openGeneration = 0L
    private var requestGeneration = 0L
    private var exportGeneration = 0L
    private var closed = false
    private var cpuPreviewEnabled = true

    /**
     * Crop-preview mode: the canvas shows the full uncropped recipe behind
     * the crop window. While on, [requestPreview] renders
     * [displayDocumentFor] of the live recipe and dedups on it — a crop
     * rectangle drag rewrites only `geometry.crop`, which the display
     * document drops, so the drag queues no renders at all.
     */
    private var cropPreviewMode = false

    /** The display document the last crop-mode render was submitted for. */
    private var lastCropDisplayDocument: PhotoDocument? = null

    /** The document the in-flight or last-submitted preview render was for. */
    private var lastSubmittedDocument: PhotoDocument? = null

    /**
     * A restore waiting on missing media: the recipe and the source it
     * belongs to, retained so [retryRestore], [discardRestore] and
     * [relinkRestore] can replay it without the host re-supplying state.
     */
    private var pendingRestoreEdits: PhotoEdits? = null
    private var pendingRestoreReference: String? = null

    /** The layer references that could not be opened or decoded. */
    private class MissingMediaException(val references: List<String>) :
        Exception("missing layer sources: $references")

    /**
     * Decoded upright preview frames keyed by source id — the base plus
     * every loaded layer source — replaced atomically, never mutated.
     */
    private val sourceFrames = MutableStateFlow<Map<String, LinearRgbaFrame>>(emptyMap())

    /**
     * Layer frames keyed by source *reference*, kept for the session's
     * lifetime: undo/redo can restore a layer whose source id was already
     * dropped from [sourceFrames], and the decoded pixels re-alias to the
     * restored id without re-decoding. Cleared on source open/close.
     */
    private var layerFrameCacheByReference: Map<String, LinearRgbaFrame> = emptyMap()

    /**
     * Decoded upright layer thumbnails keyed by layer id, kept for the
     * session's lifetime beside [layerFrameCacheByReference] — undo/redo
     * restores a removed row's thumbnail without re-decoding, and a
     * duplicate aliases the same pixels under its own id. Cleared on
     * source open/close; published filtered to the live document.
     */
    private var layerPreviewCache: Map<String, SrgbPixels> = emptyMap()
    private val exportProgressUpdates =
        MutableStateFlow<ExportProgressUpdate?>(null)

    private val frameSource = object : FrameSource {
        override suspend fun read(sourceId: String): LinearRgbaFrame {
            // One atomic read of the whole map — ids and frames never tear.
            return sourceFrames.value[sourceId]
                ?: throw IllegalStateException("no loaded frame for source '$sourceId'")
        }
    }

    private val previewScheduler =
        PreviewScheduler(sessionScope, CpuFrameRenderer(frameSource), workerDispatcher)

    init {
        sessionScope.launch {
            previewScheduler.state.collectLatest { preview ->
                when (preview) {
                    PreviewState.Idle ->
                        _state.value = _state.value.copy(rendering = false)
                    is PreviewState.Rendering ->
                        // Only a current-generation render lights the
                        // spinner — a request superseded by a CPU-disable
                        // or a newer recipe must not leave rendering=true.
                        if (preview.request.revision == requestGeneration) {
                            _state.value = _state.value.copy(rendering = true)
                        }
                    is PreviewState.Ready -> {
                        val pixels = withContext(workerDispatcher) {
                            preview.frame.toSrgbPixels()
                        }
                        if (preview.request.revision == requestGeneration && !closed) {
                            _state.value =
                                _state.value.copy(
                                    preview = pixels,
                                    previewDocument = lastSubmittedDocument
                                        ?: _state.value.document,
                                    rendering = false
                                )
                        }
                    }
                    is PreviewState.Failed -> {
                        if (preview.request.revision == requestGeneration && !closed) {
                            onError(preview.error)
                            _state.value = _state.value.copy(
                                rendering = false,
                                error = userFacingMessage(
                                    preview.error, "Could not render preview"
                                ),
                                errorKind = PhotoErrorKind.Preview
                            )
                        }
                    }
                }
            }
        }
        sessionScope.launch {
            // Worker threads only write to this flow; the public state moves
            // exclusively here on the owner thread.
            exportProgressUpdates.collect { update ->
                if (update != null && update.generation == exportGeneration &&
                    !closed && _state.value.exporting
                ) {
                    _state.value = _state.value.copy(exportProgress = update.value)
                }
            }
        }
    }

    /**
     * Opens [reference]; ignored while exporting or while a bare gesture
     * draft is open. An open tool's transaction belongs to the old source —
     * it is discarded before the switch, never carried across sources.
     */
    fun open(reference: String, restoredEdits: PhotoEdits? = null) {
        if (closed || _state.value.exporting) return
        if (history?.isDrafting == true) {
            if (toolHistory == null) return
            history?.cancel()
            toolHistory = null
        }
        openJob?.cancel()
        layerImportJob?.cancel()
        val generation = ++openGeneration
        requestGeneration += 1L // invalidate in-flight preview publication
        history = null
        toolHistory = null // no draft crosses sources
        source = null
        sourceFrames.value = emptyMap()
        layerFrameCacheByReference = emptyMap()
        layerPreviewCache = emptyMap()
        cropPreviewMode = false
        lastCropDisplayDocument = null
        lastSubmittedDocument = null
        developJob?.cancel()
        developJob = null
        developedRecipe = PhotoRawDevelop()
        // The pending pair rides the open: a missing-media failure can
        // then offer recovery without the host re-supplying the recipe.
        pendingRestoreEdits = restoredEdits
        pendingRestoreReference = reference
        _state.value = PhotoEditorState(opening = true)
        openJob = sessionScope.launch {
            try {
                // Open, decode and close inside one context hop: a superseded
                // open still runs the finally and closes every raster before
                // the cancelled resume out of withContext can abandon them.
                // Restored layers decode in the same hop: a missing or
                // mismatched layer source fails the whole open explicitly.
                val decoded = withContext(workerDispatcher) {
                    val rasters = ArrayList<PhotoRaster>()
                    try {
                        val raster = media.open(reference)
                        rasters += raster
                        val preview = raster.readPreview(PREVIEW_MAX_EDGE)
                        val frames = LinkedHashMap<String, LinearRgbaFrame>()
                        frames[raster.info.reference] = preview.toLinearFrame()
                        val layerPreviews = LinkedHashMap<String, SrgbPixels>()
                        // Every missing or unreadable layer source is
                        // collected, never dropped — the whole open fails
                        // afterwards with the complete list for recovery.
                        val missing = ArrayList<String>()
                        for (layer in restoredEdits?.layers.orEmpty()) {
                            try {
                                val layerRaster =
                                    media.open(layer.source.reference)
                                rasters += layerRaster
                                require(
                                    layerRaster.info.uprightSize ==
                                        layer.source.size
                                ) {
                                    "layer '${layer.id}' source reports " +
                                        "${layerRaster.info.uprightSize.width}x" +
                                        "${layerRaster.info.uprightSize.height}, " +
                                        "recipe expects " +
                                        "${layer.source.size.width}x" +
                                        "${layer.source.size.height}"
                                }
                                val layerPreview =
                                    layerRaster.readPreview(PREVIEW_MAX_EDGE)
                                frames[layer.source.id] =
                                    layerPreview.toLinearFrame()
                                layerPreviews[layer.id] = layerPreview
                            } catch (e: Exception) {
                                missing += layer.source.reference
                            }
                        }
                        if (missing.isNotEmpty()) {
                            throw MissingMediaException(missing)
                        }
                        OpenedSource(
                            raster.info, preview, frames.toMap(), layerPreviews
                        )
                    } finally {
                        for (raster in rasters) raster.close()
                    }
                }
                val (info, preview, frames, layerPreviews) = decoded
                if (generation != openGeneration || closed) return@launch
                val asset = MediaAsset.Image(
                    id = info.reference,
                    reference = info.reference,
                    size = info.uprightSize
                )
                // The checkpoint is the clean recipe: a restored recipe is an
                // ordinary undoable step, so reopening with saved edits still
                // reads dirty until an export or an explicit save.
                val fresh = EditHistory(PhotoEdits())
                if (restoredEdits != null) {
                    fresh.execute("Restore edits") { restoredEdits }
                }
                history = fresh
                source = asset
                sourceFrames.value = frames
                // Restored layers also seed the reference cache so
                // undo/redo can re-alias a dropped source id later.
                layerFrameCacheByReference =
                    restoredEdits?.layers.orEmpty().mapNotNull { layer ->
                        frames[layer.source.id]
                            ?.let { layer.source.reference to it }
                    }.toMap()
                layerPreviewCache = layerPreviews
                val edits = fresh.current
                _state.value = PhotoEditorState(
                    info = info,
                    document = edits.toDocument(asset),
                    preview = preview,
                    previewDocument = PhotoDocument(asset),
                    originalPreview = preview,
                    layerPreviews = layerPreviews.toPersistentMap(),
                    isDirty = fresh.isDirty,
                    canUndo = fresh.canUndo,
                    canRedo = fresh.canRedo,
                    isDrafting = fresh.isDrafting
                )
                // The restore landed — nothing is left pending recovery.
                pendingRestoreEdits = null
                if (edits != PhotoEdits()) requestPreview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == openGeneration && !closed) {
                    onError(e)
                    if (e is MissingMediaException) {
                        // Missing layer media is recovery, not an open
                        // error — the recipe stays pending behind it.
                        _state.value = _state.value.copy(
                            opening = false,
                            recovery = PhotoRecovery(
                                e.references.toPersistentList()
                            )
                        )
                    } else {
                        _state.value = _state.value.copy(
                            opening = false,
                            error = userFacingMessage(
                                e, "Could not open photo"
                            ),
                            errorKind = errorKindFor(e, PhotoErrorKind.Open)
                        )
                    }
                }
                return@launch
            }
            if (generation == openGeneration && !closed && _state.value.opening) {
                _state.value = _state.value.copy(opening = false)
            }
        }
    }

    /**
     * Surfaces a persisted-state *format* failure — a project file that
     * could not be decoded at all. The opened document is untouched; the
     * error is the fixed Open message, never the raw payload.
     */
    fun reportRestoreFailure(error: Throwable) {
        if (closed) return
        onError(error)
        _state.value = _state.value.copy(
            error = userFacingMessage(error, "Could not open photo"),
            errorKind = errorKindFor(error, PhotoErrorKind.Open)
        )
    }

    /** Re-probes every missing reference and replays the pending restore. */
    fun retryRestore() {
        val edits = pendingRestoreEdits ?: return
        val reference = pendingRestoreReference ?: return
        open(reference, edits)
    }

    /**
     * Abandons the pending recipe and opens the source clean — the
     * user's explicit choice to lose the persisted edit.
     */
    fun discardRestore() {
        pendingRestoreEdits = null
        val reference = pendingRestoreReference ?: return
        open(reference)
    }

    /**
     * Replaces one missing [missing] reference with the host-picked
     * [replacement] in the pending recipe and replays the restore.
     * Other missing references stay listed — the recovery re-reports
     * whatever still cannot be opened.
     */
    fun relinkRestore(missing: String, replacement: String) {
        val edits = pendingRestoreEdits ?: return
        val reference = pendingRestoreReference ?: return
        val relinked = edits.copy(
            layers = edits.layers.map { layer ->
                if (layer.source.reference == missing) {
                    layer.copy(
                        source = layer.source.copy(reference = replacement)
                    )
                } else {
                    layer
                }
            }.toPersistentList()
        )
        open(reference, relinked)
    }

    /**
     * The history the current interaction writes to: the open tool's local
     * history while a tool owns the outer draft, [history] otherwise.
     */
    private fun interactionHistory(): EditHistory<PhotoEdits>? =
        toolHistory ?: history

    /**
     * Mirrors a finished local step into the open outer draft so the
     * published document, dirty flag and preview track the tool's live
     * recipe. A no-op when no tool is open or the value already matches.
     */
    private fun syncToolDraft() {
        val tool = toolHistory ?: return
        val h = history ?: return
        if (h.isDrafting && h.current != tool.current) {
            h.update(tool.current)
        }
    }

    /**
     * Lets a tool take over the document history: an outer draft opens and
     * a private [EditHistory] starts from the committed recipe, so the
     * tool's interactions undo/redo locally until [applyToolEdit] lands
     * them as one global step. Idempotent while a tool is already open;
     * ignored with no document or mid-gesture.
     */
    fun beginToolEdit(label: String) {
        val h = history ?: return
        if (closed || toolHistory != null || h.isDrafting) return
        h.begin(label)
        toolHistory = EditHistory(h.current)
        publishControls()
    }

    /**
     * Lands the tool's local history as a single undoable step on the outer
     * history and releases the draft. A no-op tool — or one whose work
     * cancelled back to the pre-tool recipe — records nothing and keeps the
     * redo stack. An interaction still in flight (a held gesture, an open
     * numeric dialog) blocks Apply entirely — callers must commit or cancel
     * it first, so a live gesture is never silently swallowed or dropped.
     */
    fun applyToolEdit() {
        val h = history ?: return
        val tool = toolHistory ?: return
        if (closed || tool.isDrafting) return
        toolHistory = null
        if (h.isDrafting) h.commit()
        publishControls()
    }

    /**
     * Discards the whole tool transaction — every local interaction — and
     * restores the recipe captured at [beginToolEdit]; no global undo step
     * is recorded. The pre-tool preview is re-requested.
     */
    fun cancelToolEdit() {
        val h = history ?: return
        if (toolHistory == null) return
        if (closed) return
        toolHistory = null
        if (h.isDrafting) h.cancel()
        publishControls()
        requestPreview()
    }

    /** Opens an interaction draft (a gesture or the numeric dialog). */
    fun beginEdit(label: String) {
        val ih = interactionHistory() ?: return
        if (closed) return
        ih.begin(label)
        publishControls()
    }

    /** Replaces the live interaction draft; ignored with no draft open. */
    fun updateEdits(edits: PhotoEdits) {
        val ih = interactionHistory() ?: return
        if (closed || !ih.isDrafting) return
        val revision = ih.revision
        ih.update(edits)
        if (ih.revision == revision) return
        syncToolDraft()
        publishControls()
        requestPreview()
    }

    /** Lands the open interaction draft as one step on its history. */
    fun commitEdit() {
        val ih = interactionHistory() ?: return
        if (closed || !ih.isDrafting) return
        ih.commit()
        syncToolDraft()
        publishControls()
    }

    /** Discards the open interaction draft and restores its preview. */
    fun cancelEdit() {
        val ih = interactionHistory() ?: return
        if (closed || !ih.isDrafting) return
        ih.cancel()
        syncToolDraft()
        publishControls()
        requestPreview()
    }

    /** Applies [edits] as a single undoable step; ignored mid-interaction. */
    fun applyEdits(label: String, edits: PhotoEdits) {
        val ih = interactionHistory() ?: return
        if (closed || ih.isDrafting) return
        if (ih.execute(label) { edits }) {
            syncToolDraft()
            publishControls()
            requestPreview()
        }
    }

    fun undo() {
        val ih = interactionHistory() ?: return
        if (closed || ih.isDrafting) return
        if (ih.undo()) {
            syncToolDraft()
            publishControls()
            requestPreview()
        }
    }

    fun redo() {
        val ih = interactionHistory() ?: return
        if (closed || ih.isDrafting) return
        if (ih.redo()) {
            syncToolDraft()
            publishControls()
            requestPreview()
        }
    }

    /**
     * Returns to the clean recipe as one undoable step — one local step
     * while a tool is open. The saved checkpoint is untouched, so reset
     * stays dirty relative to the last save and [undo] restores the
     * pre-reset edits.
     */
    fun reset() {
        val ih = interactionHistory() ?: return
        if (closed || ih.isDrafting) return
        if (ih.execute("Reset") { PhotoEdits() }) {
            syncToolDraft()
            publishControls()
            requestPreview()
        }
    }

    /**
     * Imports [reference] as a new image layer inserted at the top of the
     * recipe. From the idle Layers surface the import is one immediate
     * global history step — never an invisible tool transaction — so it
     * requires no open tool transaction, no open interaction draft and no
     * open/export/in-flight import; any of those rejects the request.
     *
     * The decode runs on the worker dispatcher; only the owner thread
     * mutates the source map and history. A superseding [open],
     * [cancelLayerImport] (or close), or a transaction opened mid-decode
     * invalidates the import — a stale or cancelled decode publishes
     * nothing. The layer source carries a fresh composition-local id
     * (`layer-source-N`) distinct from every current id; a reference the
     * composition already holds shares its decoded source and frame.
     * [MAX_PHOTO_IMAGE_LAYERS] is a hard cap — hitting it flags
     * [PhotoEditorState.layerImportError] and mutates nothing.
     */
    fun addImageLayer(reference: String, name: String? = null) {
        val h = history ?: return
        val state = _state.value
        if (closed || state.exporting || state.opening || state.importingLayer ||
            h.isDrafting || toolHistory != null
        ) {
            return
        }
        if (h.current.layers.size >= MAX_PHOTO_IMAGE_LAYERS) {
            _state.value = state.copy(layerImportError = true)
            return
        }
        // A reference already loaded in the composition shares its decoded
        // source: the new layer gets a fresh layer id but reuses the
        // existing immutable source id and frame — no second decode, so
        // nothing goes through the importing flag.
        val shared = h.current.layers
            .firstOrNull { it.source.reference == reference }
        if (shared != null) {
            val (layerId, _) = nextLayerIds(h.current)
            val layer = PhotoImageLayer(
                id = layerId,
                name = name?.takeIf { it.isNotBlank() } ?: shared.name,
                source = shared.source
            )
            layerPreviewCache[shared.id]?.let { pixels ->
                layerPreviewCache = layerPreviewCache + (layerId to pixels)
            }
            commitNewLayer(layer)
            return
        }
        _state.value = _state.value.copy(
            importingLayer = true,
            layerImportError = false
        )
        val generation = openGeneration
        layerImportJob = sessionScope.launch {
            try {
                val decoded = withContext(workerDispatcher) {
                    val raster = media.open(reference)
                    try {
                        raster.info to raster.readPreview(PREVIEW_MAX_EDGE)
                    } finally {
                        raster.close()
                    }
                }
                val (info, layerPreview) = decoded
                if (generation != openGeneration || closed) return@launch
                // A tool transaction or gesture draft that opened mid-
                // decode owns the history now — the import drops without
                // touching the document.
                if (h.isDrafting || toolHistory != null) {
                    _state.value = _state.value.copy(importingLayer = false)
                    return@launch
                }
                val edits = h.current
                if (edits.layers.size >= MAX_PHOTO_IMAGE_LAYERS) {
                    _state.value = _state.value.copy(
                        importingLayer = false,
                        layerImportError = true
                    )
                    return@launch
                }
                // Composition-local ids, unique across layer ids, layer
                // source ids and the base source id.
                val (layerId, sourceId) = nextLayerIds(edits)
                val layer = PhotoImageLayer(
                    id = layerId,
                    name = name?.takeIf { it.isNotBlank() } ?: info.displayName,
                    source = MediaAsset.Image(
                        id = sourceId,
                        reference = reference,
                        size = info.uprightSize
                    )
                )
                // The frame lands before the recipe references it, so the
                // republished document always resolves its sources; the
                // reference cache keeps it alive for later undo/redo
                // re-aliasing.
                val frame = layerPreview.toLinearFrame()
                layerFrameCacheByReference =
                    layerFrameCacheByReference + (reference to frame)
                layerPreviewCache = layerPreviewCache + (layerId to layerPreview)
                sourceFrames.value = sourceFrames.value + (layer.source.id to frame)
                commitNewLayer(layer)
                _state.value = _state.value.copy(importingLayer = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == openGeneration && !closed) {
                    onError(e)
                    _state.value = _state.value.copy(
                        importingLayer = false,
                        layerImportError = true
                    )
                }
            }
        }
    }

    /** First free `layer-N`/`layer-source-N` pair across the composition. */
    private fun nextLayerIds(edits: PhotoEdits): Pair<String, String> {
        val used = HashSet<String>(edits.layers.size * 2 + 1)
        source?.let { used += it.id }
        for (layer in edits.layers) {
            used += layer.id
            used += layer.source.id
        }
        var n = 1
        while ("layer-$n" in used || "layer-source-$n" in used) n += 1
        return "layer-$n" to "layer-source-$n"
    }

    /** Inserts [layer] on top as one committed global history step. */
    private fun commitNewLayer(layer: PhotoImageLayer) {
        val h = history ?: return
        h.execute("Add image layer") {
            it.copy(layers = it.layers.adding(layer))
        }
        publishControls()
        requestPreview()
    }

    /**
     * Cancels an in-flight [addImageLayer] decode; the flag clears
     * immediately and nothing is inserted.
     */
    fun cancelLayerImport() {
        if (closed || !_state.value.importingLayer) return
        layerImportJob?.cancel()
        _state.value = _state.value.copy(importingLayer = false)
    }

    /**
     * Gates the CPU preview pipeline. While a runtime-shader preview owns
     * the canvas, [enabled] = false invalidates any in-flight publication
     * and stops new preview renders — slider gestures then cost no CPU work
     * at all. Re-enabling renders the current recipe straight away. The
     * document, history and export paths are unaffected: saving a copy is
     * always the CPU strip pipeline either way.
     */
    fun setCpuPreviewEnabled(enabled: Boolean) {
        if (closed || enabled == cpuPreviewEnabled) return
        cpuPreviewEnabled = enabled
        if (enabled) {
            requestPreview()
        } else {
            requestGeneration += 1L
            _state.value = _state.value.copy(rendering = false)
        }
    }

    /**
     * Toggles the crop tool's preview mode. While on, the published
     * [PhotoEditorState.displayDocument] is the live recipe with
     * `geometry.crop` reset to the full frame — the whole source shows
     * behind the crop overlay — and the CPU preview renders that document
     * instead of the recipe. Crop drags change nothing else, so they are
     * deduplicated out of the render queue; the recipe document, history
     * and export paths are unaffected. Toggling either way re-renders
     * immediately.
     */
    fun setCropPreviewMode(enabled: Boolean) {
        if (closed || enabled == cropPreviewMode) return
        cropPreviewMode = enabled
        lastCropDisplayDocument = null
        publishControls()
        requestPreview()
    }

    /**
     * The document the canvas draws in crop-preview mode: [document] with
     * the crop window dropped. Null while the mode is off — the canvas
     * then draws the recipe document itself.
     */
    private fun displayDocumentFor(document: PhotoDocument): PhotoDocument? =
        if (cropPreviewMode) {
            // Markup and the border sit on the output frame, so they step
            // aside while the crop tool shows the whole source.
            document.copy(
                geometry = document.geometry.copy(crop = NormalizedCrop()),
                retouch = document.retouch.copy(
                    markup = PhotoMarkup(),
                    matte = null,
                    effects = document.retouch.effects.copy(borderWidth = 0f)
                )
            )
        } else {
            null
        }

    /**
     * The last pre-white-balance render the neutral-point picker sampled,
     * keyed by its document and the decoded source map it read.
     * Owner-thread confined; a pick session's taps and drags reuse one
     * render until the recipe below white balance or a source changes.
     */
    private var whiteBalanceSampleCache: WhiteBalanceSample? = null

    private class WhiteBalanceSample(
        val document: PhotoDocument,
        val frames: Map<String, LinearRgbaFrame>,
        val frame: LinearRgbaFrame
    )

    /**
     * Averages the straight-linear colour that reaches the white-balance
     * stage around the normalized output point ([x], [y]) of the current
     * recipe — every stage before finishing applied (colour, geometry and
     * warp, layers, spatial), none of finishing, so the result is what
     * [com.dot.gallery.editor.core.model.WhiteBalanceModel.neutralFor]
     * must neutralise. The patch is a square of side `2r + 1` preview
     * pixels, `r` being [radiusFraction] of the shorter output edge (at
     * least one pixel); transparent pixels are skipped. Returns
     * `(r, g, b)` or null without a document or opaque samples. Renders
     * on the worker dispatcher through the CPU graph regardless of which
     * backend draws the canvas.
     */
    suspend fun sampleBeforeWhiteBalance(
        x: Float,
        y: Float,
        radiusFraction: Float = 0.004f
    ): FloatArray? {
        if (closed || !x.isFinite() || !y.isFinite()) return null
        val document = _state.value.document ?: return null
        val frames = sourceFrames.value
        val base = frames[document.source.id] ?: return null
        val key = document.copy(finishing = PhotoFinishing())
        val frame = whiteBalanceSampleCache
            ?.takeIf { it.frames === frames && it.document == key }
            ?.frame
            ?: run {
                val canvas = PhotoMapping(
                    base.size, orientation = 1, key.geometry
                ).outputSize
                val renderer = CpuFrameRenderer(object : FrameSource {
                    override suspend fun read(sourceId: String): LinearRgbaFrame =
                        frames[sourceId] ?: throw IllegalStateException(
                            "no loaded frame for source '$sourceId'"
                        )
                })
                val rendered = withContext(workerDispatcher) {
                    renderer.render(RenderRequest(0L, key.renderGraph(canvas)))
                }
                if (closed) return null
                whiteBalanceSampleCache = WhiteBalanceSample(key, frames, rendered)
                rendered
            }
        return withContext(workerDispatcher) {
            averagePatch(frame, x, y, radiusFraction)
        }
    }

    private fun averagePatch(
        frame: LinearRgbaFrame,
        x: Float,
        y: Float,
        radiusFraction: Float
    ): FloatArray? {
        val w = frame.size.width
        val h = frame.size.height
        val cx = (x.coerceIn(0f, 1f) * w).toInt().coerceIn(0, w - 1)
        val cy = (y.coerceIn(0f, 1f) * h).toInt().coerceIn(0, h - 1)
        val r = maxOf(1, (minOf(w, h) * radiusFraction).toInt())
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        var n = 0
        for (py in maxOf(0, cy - r)..minOf(h - 1, cy + r)) {
            for (px in maxOf(0, cx - r)..minOf(w - 1, cx + r)) {
                val i = (py * w + px) * LinearRgbaFrame.COMPONENTS_PER_PIXEL
                if (frame.component(i + 3) <= 0f) continue
                sr += frame.component(i)
                sg += frame.component(i + 1)
                sb += frame.component(i + 2)
                n++
            }
        }
        if (n == 0) return null
        return floatArrayOf((sr / n).toFloat(), (sg / n).toFloat(), (sb / n).toFloat())
    }

    private var curvesInputCache: CurvesInput? = null

    private class CurvesInput(
        val document: PhotoDocument,
        val frames: Map<String, LinearRgbaFrame>,
        val pixels: SrgbPixels
    )

    /**
     * The current recipe rendered up to the tone curves: every stage before
     * finishing plus white balance and colour grade, display-encoded like the
     * curve axes. Editing a curve leaves the result valid, so the histogram
     * holds still under a drag without re-rendering. Null without a document.
     */
    suspend fun renderCurvesInput(): SrgbPixels? {
        if (closed) return null
        val document = _state.value.document ?: return null
        val frames = sourceFrames.value
        val base = frames[document.source.id] ?: return null
        val key = document.copy(
            finishing = PhotoFinishing(
                whiteBalance = document.finishing.whiteBalance,
                grade = document.finishing.grade
            )
        )
        curvesInputCache
            ?.takeIf { it.frames === frames && it.document == key }
            ?.let { return it.pixels }
        val canvas = PhotoMapping(base.size, orientation = 1, key.geometry).outputSize
        val renderer = CpuFrameRenderer(object : FrameSource {
            override suspend fun read(sourceId: String): LinearRgbaFrame =
                frames[sourceId] ?: throw IllegalStateException(
                    "no loaded frame for source '$sourceId'"
                )
        })
        val pixels = withContext(workerDispatcher) {
            renderer.render(RenderRequest(0L, key.renderGraph(canvas))).toSrgbPixels()
        }
        if (closed) return null
        curvesInputCache = CurvesInput(key, frames, pixels)
        return pixels
    }

    /**
     * The runtime-shader preview reported a failure: record it through
     * [onError] and hand the canvas back to the CPU pipeline, which renders
     * the same recipe — never a user-facing error on its own.
     */
    fun reportPreviewFallback(error: Throwable) {
        if (closed) return
        onError(error)
        setCpuPreviewEnabled(true)
    }

    /** Re-renders the preview for the current document, e.g. after a failure. */
    fun retryPreview() {
        if (closed || _state.value.document == null) return
        _state.value = _state.value.copy(error = null, errorKind = null)
        requestPreview()
    }

    fun clearError() {
        if (closed) return
        _state.value = _state.value.copy(
            error = null,
            errorKind = null,
            layerImportError = false
        )
    }

    /**
     * The containers this session can export explicitly (see
     * [PhotoMediaAccess.supportedExportFormats]); the source-container
     * "save copy" is always available on top of these.
     */
    val exportFormats: Set<PhotoExportFormat>
        get() = media.supportedExportFormats

    /**
     * Saves the committed recipe as a new copy in [format], or in the
     * source's own container when [format] is null. Ignored while another
     * export is running (including while it winds down) or while a draft is
     * open — a tool transaction's outer draft blocks export too, so an
     * unapplied tool recipe is never written. An export started before the
     * tool opened still lands its commit receipt mid-draft (see below).
     * Success marks the exported snapshot as the saved checkpoint —
     * edits that landed during the export stay dirty. Cancellation before
     * publication publishes no result and no error; a commit receipt that
     * already fired still lands as success. [PhotoEditorState.exported]
     * always names the last published copy — a failed or cancelled export
     * never erases an earlier success.
     */
    fun exportCopy(format: PhotoExportFormat? = null) {
        val h = history ?: return
        if (closed || _state.value.exporting || h.isDrafting) return
        val snapshotEdits = h.current
        val snapshot = documentFor(snapshotEdits) ?: return
        val generation = ++exportGeneration
        // Commit receipt: the port fires it exactly once after the file is
        // durably published, even if the export call never returns.
        val receipt = MutableStateFlow<PhotoExportResult?>(null)
        exportOutcome.value = null
        // `exported` is deliberately retained: it is the last *published*
        // copy, and a cancelled/failed second export must not erase it —
        // hosts return it as the result on close.
        _state.value = _state.value.copy(exporting = true, exportProgress = 0f)
        val job = sessionScope.launch(start = CoroutineStart.LAZY) {
            var failure: Throwable? = null
            try {
                withContext(workerDispatcher) {
                    media.export(
                        snapshot,
                        // Only PNG keeps a removed background transparent.
                        format = if (snapshot.retouch.hasTransparency) {
                            PhotoExportFormat.PNG
                        } else {
                            format
                        },
                        onPublished = { receipt.value = it },
                        onProgress = { value ->
                            exportProgressUpdates.value =
                                ExportProgressUpdate(generation, value)
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
            exportOutcome.value = ExportOutcome(generation, failure)
        }
        exportJob = job
        job.invokeOnCompletion {
            // Fires even for a job cancelled before its first dispatch — the
            // cleanup below is what always clears the exporting flag.
            sessionScope.launch {
                finishExport(generation, snapshotEdits, receipt.value)
            }
        }
        job.start()
    }

    /** What the export job recorded; read only by the owner-side cleanup. */
    private data class ExportOutcome(
        val generation: Long,
        val failure: Throwable?
    )

    private val exportOutcome = MutableStateFlow<ExportOutcome?>(null)

    private fun finishExport(
        generation: Long,
        snapshotEdits: PhotoEdits,
        receipt: PhotoExportResult?
    ) {
        if (closed || generation != exportGeneration) return
        val outcome = exportOutcome.value?.takeIf { it.generation == generation }
        _state.value = _state.value.copy(
            exporting = false,
            exportCancelling = false,
            exportProgress = null
        )
        when {
            // A commit receipt outranks cancellation and even a trailing
            // error: the file exists, so the UI must learn about it.
            receipt != null -> {
                outcome?.failure?.let(onError)
                // The receipt is a checkpoint on the outer history — legal
                // mid-tool-draft — then flags republish through the same
                // seam so tool-local undo state is never overwritten.
                history?.markSaved(snapshotEdits)
                _state.value = _state.value.copy(exported = receipt)
                publishControls()
            }
            outcome?.failure != null -> {
                onError(outcome.failure)
                _state.value = _state.value.copy(
                    error = userFacingMessage(
                        outcome.failure, "Could not save copy"
                    ),
                    errorKind = errorKindFor(outcome.failure, PhotoErrorKind.Export)
                )
            }
            // else: cancelled (or superseded) before publication — no
            // result, no error.
        }
    }

    /**
     * Requests export cancellation. `exporting` stays true while the job
     * winds down — only [PhotoEditorState.exportCancelling] changes now —
     * so a new export cannot overlap the old one's cleanup.
     */
    fun cancelExport() {
        if (closed || !_state.value.exporting) return
        _state.value = _state.value.copy(exportCancelling = true)
        exportJob?.cancel()
    }

    /** Cancels all session-owned work and resets state; the caller's scope lives on. */
    fun close() {
        media.releaseDevelopCache()
        if (closed) return
        closed = true
        // Bump every guard first: no in-flight callback can republish.
        openGeneration += 1L
        requestGeneration += 1L
        exportGeneration += 1L
        layerImportJob?.cancel()
        history = null
        toolHistory = null
        source = null
        sourceFrames.value = emptyMap()
        layerFrameCacheByReference = emptyMap()
        layerPreviewCache = emptyMap()
        cropPreviewMode = false
        lastCropDisplayDocument = null
        lastSubmittedDocument = null
        developJob?.cancel()
        developJob = null
        developedRecipe = PhotoRawDevelop()
        whiteBalanceSampleCache = null
        previewScheduler.close()
        sessionJob.cancel()
        _state.value = PhotoEditorState()
    }

    private fun documentFor(edits: PhotoEdits): PhotoDocument? {
        val asset = source ?: return null
        return edits.toDocument(asset)
    }

    private fun publishControls() {
        val h = history ?: return
        val document = documentFor(h.current) ?: return
        reconcileSourceFrames(document)
        val tool = toolHistory
        val interactions = interactionHistory() ?: h
        val layerIds = HashSet<String>(document.layers.size)
        for (layer in document.layers) layerIds += layer.id
        _state.value = _state.value.copy(
            document = document,
            displayDocument = displayDocumentFor(document),
            layerPreviews = layerPreviewCache
                .filterKeys { it in layerIds }
                .toPersistentMap(),
            // Undo/redo speak the interaction history: inside a tool that
            // is the tool-local stack (one gesture at a time), otherwise
            // the document history.
            canUndo = interactions.canUndo,
            canRedo = interactions.canRedo,
            isDirty = h.isDirty,
            isDrafting = h.isDrafting,
            toolEditing = tool != null,
            interactionActive = tool?.isDrafting ?: h.isDrafting
        )
    }

    /**
     * Keeps the source map in lockstep with the recipe: frames stay for
     * the base and every referenced layer source (duplicate layers share
     * the one frame under their common source id) and any source id no
     * longer referenced is dropped. Undo/redo can restore a layer whose
     * frame was already dropped — the decoded pixels survive in the
     * reference-keyed cache and re-alias to whatever source id the recipe
     * now asks for, so one immutable frame can serve several source ids.
     */
    private fun reconcileSourceFrames(document: PhotoDocument) {
        val frames = sourceFrames.value
        if (frames.isEmpty()) return
        val needed = HashSet<String>(document.layers.size + 1)
        needed += document.source.id
        for (layer in document.layers) needed += layer.source.id
        var next = frames
        for (layer in document.layers) {
            if (layer.source.id !in next) {
                val cached = layerFrameCacheByReference[layer.source.reference]
                if (cached != null) next = next + (layer.source.id to cached)
            }
        }
        if (next.keys != needed) next = next.filterKeys { it in needed }
        if (next != frames) sourceFrames.value = next
    }

    /** The develop the source frame currently shows; owner-thread confined. */
    private var developedRecipe = PhotoRawDevelop()
    private var developJob: Job? = null

    /**
     * Brings the source frame in line with the recipe's develop. Decoding runs off
     * the owner thread and only the newest request lands, so a slider drag settles
     * on its last value; undo and redo come through the same path.
     */
    private fun syncDevelop(document: PhotoDocument) {
        if (_state.value.info?.isRaw != true) return
        if (document.develop == developedRecipe) {
            developJob?.cancel()
            developJob = null
            return
        }
        developJob?.cancel()
        val target = document.develop
        val generation = openGeneration
        developJob = sessionScope.launch {
            delay(DEVELOP_DEBOUNCE_MS)
            val frame = try {
                withContext(workerDispatcher) {
                    media.developPreview(
                        document.source.reference, target, PREVIEW_MAX_EDGE
                    )?.toLinearFrame()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                _state.value = _state.value.copy(
                    error = userFacingMessage(e, "Could not develop this photo"),
                    errorKind = errorKindFor(e, PhotoErrorKind.Preview)
                )
                null
            } ?: return@launch
            if (generation != openGeneration || closed) return@launch
            developedRecipe = target
            sourceFrames.value = sourceFrames.value + (document.source.id to frame)
            lastCropDisplayDocument = null
            requestPreview()
        }
    }

    /**
     * A small render of the source under [develop], for the Develop tool's option
     * tiles. It never disturbs the preview's decode.
     */
    suspend fun renderDevelopThumbnail(develop: PhotoRawDevelop): SrgbPixels? {
        val reference = source?.reference ?: return null
        return withContext(workerDispatcher) {
            media.developPreview(reference, develop, DEVELOP_THUMBNAIL_EDGE, reuseBase = false)
        }
    }

    private fun requestPreview() {
        val document = _state.value.document ?: return
        syncDevelop(document)
        // While the shader backend owns the canvas, edits still publish but
        // queue no CPU renders — the shader redraws from the recipe.
        if (closed || !cpuPreviewEnabled) return
        val displayDocument = displayDocumentFor(document) ?: document
        // Crop drags rewrite only the recipe's crop; the display document
        // drops it, so identical display requests are not re-rendered.
        if (cropPreviewMode && displayDocument == lastCropDisplayDocument) return
        lastCropDisplayDocument = displayDocument.takeIf { cropPreviewMode }
        requestGeneration += 1L
        lastSubmittedDocument = displayDocument
        // Layer placement must see the canvas this render produces: the
        // geometry output at the decoded preview resolution.
        val canvas = sourceFrames.value[document.source.id]?.let { frame ->
            PhotoMapping(
                frame.size, orientation = 1, displayDocument.geometry
            ).outputSize
        }
        try {
            previewScheduler.submit(
                RenderRequest(requestGeneration, displayDocument.renderGraph(canvas))
            )
        } catch (e: IllegalStateException) {
            onError(e)
            _state.value = _state.value.copy(
                rendering = false,
                error = userFacingMessage(e, "Could not render preview"),
                errorKind = PhotoErrorKind.Preview
            )
        }
    }

    /**
     * Fixed action strings for the UI; internal exception details go to
     * [onError] instead. Only our own capability errors carry a message that
     * is safe to show.
     */
    private fun userFacingMessage(error: Throwable, action: String): String =
        (error as? UnsupportedPhotoException)?.message?.takeIf { it.isNotBlank() }
            ?: action

    /** Capability failures get their own kind so the UI can phrase them safely. */
    private fun errorKindFor(error: Throwable, action: PhotoErrorKind): PhotoErrorKind =
        when (error) {
            is PhotoTooLargeException -> PhotoErrorKind.TooLarge
            is UnsupportedPhotoException -> PhotoErrorKind.Unsupported
            else -> action
        }

    private companion object {
        private const val DEVELOP_DEBOUNCE_MS = 80L
        private const val DEVELOP_THUMBNAIL_EDGE = 200
        const val PREVIEW_MAX_EDGE = 1024
    }
}
