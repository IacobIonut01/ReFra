package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.render.SrgbPixels

/**
 * Everything [PhotoEditorContent] can ask of a session, as plain callbacks —
 * the content composable never sees the session, a Context or any I/O.
 * [PhotoEditorScreen] fills this from a live [com.dot.gallery.editor.render.photo.PhotoEditorSession];
 * tests and previews fill it with recorders.
 */
data class PhotoEditorActions(
    val beginEdit: (String) -> Unit,
    val updateEdits: (PhotoEdits) -> Unit,
    val commitEdit: () -> Unit,
    val cancelEdit: () -> Unit,
    val applyEdits: (String, PhotoEdits) -> Unit,
    val undo: () -> Unit,
    val redo: () -> Unit,
    val reset: () -> Unit,
    /** Writes a copy in the source's own container. */
    val saveCopy: () -> Unit,
    /**
     * Writes a copy in an explicit [PhotoExportFormat]. The Save menu lists
     * exactly [exportFormats]; the no-op default keeps previews and
     * tests without an exporter compilable.
     */
    val exportCopy: (PhotoExportFormat) -> Unit = {},
    /** The explicit containers this host can write (empty: source only). */
    val exportFormats: Set<PhotoExportFormat> = emptySet(),
    val cancelExport: () -> Unit,
    val retryOpen: () -> Unit,
    val retryPreview: () -> Unit,
    val clearError: () -> Unit,
    val close: () -> Unit,
    /**
     * Pauses/resumes the CPU preview pipeline while a runtime-shader
     * preview owns the canvas. The content keeps it in sync with renderer
     * availability; a no-op default suits pure-state previews.
     */
    val setCpuPreviewEnabled: (Boolean) -> Unit = {},
    /**
     * Toggles the crop tool's preview mode: while on, the canvas draws the
     * recipe with its crop dropped (full source behind the crop window)
     * and crop drags queue no preview renders. No-op default suits
     * previews/tests without a live session.
     */
    val setCropPreviewMode: (Boolean) -> Unit = {},
    /** Reports a runtime-shader failure so the host can fall back to CPU. */
    val reportPreviewFallback: (Throwable) -> Unit = {},
    /**
     * Tool transaction seam: [beginToolEdit] opens the tool's outer draft
     * plus its local undo history (idempotent while the tool owns the
     * draft), [applyToolEdit] lands the tool's work as one global undo
     * step, [cancelToolEdit] discards it all. No-op defaults keep tests
     * and previews that never open a tool compilable.
     */
    val beginToolEdit: (String) -> Unit = {},
    val applyToolEdit: () -> Unit = {},
    val cancelToolEdit: () -> Unit = {},
    /**
     * The Layers surface's image picker seam: the host (Android document
     * picker, desktop file dialog) launches its real media chooser and
     * feeds the picked reference to the session — the shared UI never
     * touches platform APIs. No-op default keeps previews/tests
     * compilable.
     */
    val requestAddImage: () -> Unit = {},
    /** Cancels an in-flight layer import decode. */
    val cancelLayerImport: () -> Unit = {},
    /**
     * The blocked-restore seam: [retryRecovery] re-probes every missing
     * reference, [discardRecovery] abandons the pending recipe and opens
     * clean, and [requestRelink] asks the host to pick a replacement for
     * one missing reference — the host feeds it back to
     * [com.dot.gallery.editor.render.photo.PhotoEditorSession.relinkRestore].
     * No-op defaults keep previews/tests compilable.
     */
    val retryRecovery: () -> Unit = {},
    val discardRecovery: () -> Unit = {},
    val requestRelink: (String) -> Unit = {},
    /**
     * The neutral-point picker's sampling seam: the straight-linear
     * `(r, g, b)` reaching white balance around a normalized output point
     * (see [com.dot.gallery.editor.render.photo.PhotoEditorSession.sampleBeforeWhiteBalance]),
     * or null when nothing can be sampled. The null default keeps
     * previews/tests without a live session compilable — the picker then
     * reports that the spot cannot be balanced.
     */
    val sampleBeforeWhiteBalance: suspend (Float, Float) -> FloatArray? =
        { _, _ -> null },
    /**
     * The pixels reaching the tone curves, for their histogram and Auto (see
     * [com.dot.gallery.editor.render.photo.PhotoEditorSession.renderCurvesInput]).
     * Null falls back to the original preview, so previews/tests need no session.
     */
    val renderCurvesInput: suspend () -> SrgbPixels? = { null },
    /** A small render of a RAW under a develop, for Develop's option tiles. */
    val renderDevelopThumbnail: suspend (PhotoRawDevelop) -> SrgbPixels? = { null }
)
