package com.dot.gallery.editor.render.photo

import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.render.SrgbPixels
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Immutable snapshot of everything the photo editor UI binds to. Published
 * synchronously by [PhotoEditorSession] on its owning thread; I/O, decode and
 * render work happens off that thread and only lands here when current.
 */
data class PhotoEditorState(
    val opening: Boolean = false,
    val info: PhotoInfo? = null,
    val document: PhotoDocument? = null,
    val preview: SrgbPixels? = null,
    val originalPreview: SrgbPixels? = null,
    /**
     * Decoded upright previews keyed by layer id — one entry per image
     * layer currently in the document, so the Layers surface paints real
     * thumbnails without a second decode. Duplicate layers share the same
     * pixels instance under their own ids.
     */
    val layerPreviews: PersistentMap<String, SrgbPixels> = persistentMapOf(),
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val exportCancelling: Boolean = false,
    val exportProgress: Float? = null,
    /**
     * The outer document history holds an open draft — an ordinary gesture
     * or a whole open tool transaction. Saves, persistence and export
     * receipts stay protected for the full draft.
     */
    val isDrafting: Boolean = false,
    /**
     * A tool currently owns the outer draft — its edits live in the tool's
     * local history and land as one global step on Apply. Save stays gated
     * by [isDrafting]; undo/redo flags address tool interactions.
     */
    val toolEditing: Boolean = false,
    /**
     * A single interaction draft is open — a slider gesture or the numeric
     * dialog. Inside a tool this is the tool-local draft; with no tool open
     * it equals [isDrafting]. Only an active interaction locks sibling
     * controls.
     */
    val interactionActive: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isDirty: Boolean = false,
    /** A layer import decode is in flight; sibling mutations are gated. */
    val importingLayer: Boolean = false,
    /**
     * The last layer import failed or hit the layer cap — an explicit
     * signal the Layers surface binds; cleared by the next attempt,
     * [com.dot.gallery.editor.render.photo.PhotoEditorSession.clearError]
     * or a source switch.
     */
    val layerImportError: Boolean = false,
    val error: String? = null,
    /** Which action failed; drives the UI's fixed localized message. */
    val errorKind: PhotoErrorKind? = null,
    /**
     * A project restore is blocked on missing media — the saved recipe
     * is held pending and the recovery surface offers Retry/Discard/
     * Relink; nothing renders behind a half-restored document.
     */
    val recovery: PhotoRecovery? = null,
    val exported: PhotoExportResult? = null,
    /** The immutable recipe that actually produced [preview], not a pending edit. */
    val previewDocument: PhotoDocument? = null,
    /**
     * The document the canvas should draw, when it differs from [document]:
     * while the crop tool's preview mode is on this is the live recipe with
     * `geometry.crop` reset to the full frame, so the whole source shows
     * behind the crop window. Null means the canvas draws [document].
     */
    val displayDocument: PhotoDocument? = null
)
