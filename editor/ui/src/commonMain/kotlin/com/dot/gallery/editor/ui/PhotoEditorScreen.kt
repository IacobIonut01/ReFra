package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.core.model.isDevelopOnly
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.render.photo.PhotoEditorSession
import com.dot.gallery.editor.render.photo.PhotoExportResult

/**
 * The session-bound editor screen: collects [PhotoEditorSession.state] and
 * hands [PhotoEditorContent] a stable, memoized [PhotoEditorActions] built
 * from the session's methods. [onClose] means the user finished (saved or
 * discarded); [onRetryOpen] re-triggers the host's open pipeline after an
 * open failure. [onOpenExport] adds an "Open" affordance to the saved-copy
 * strip — pass null on hosts that cannot open the exported file.
 *
 * Callers wrap this in their theme (GalleryTheme on Android, [EditorTheme]
 * standalone). The session must be opened and closed by the host.
 *
 * [closeRequest] is a host-driven close signal (a desktop window's X, for
 * example): incrementing it runs the same draft/export/dirty close flow as
 * the Back button, so nothing closes without consent.
 *
 * [renderPreference] selects the preview backend: [PhotoRenderPreference.Auto]
 * lets the canvas use the platform runtime shader (falling back to CPU when
 * unavailable or failed); [PhotoRenderPreference.Cpu] pins the reference
 * CPU pipeline. Saving a copy is always CPU either way.
 *
 * [onRequestAddImage] is the Layers surface's picker seam: the host launches
 * its real media chooser and feeds the picked reference to
 * [PhotoEditorSession.addImageLayer]. The default no-op leaves the Add
 * affordances gated but reachable for hosts that cannot pick.
 *
 * [onRequestRelink] is the recovery seam's picker: a blocked restore lists
 * each missing reference with a Relink action; the host picks a
 * replacement and calls [PhotoEditorSession.relinkRestore] — the session
 * owns the replay and re-reports whatever is still missing.
 */
@Composable
fun PhotoEditorScreen(
    session: PhotoEditorSession,
    onClose: () -> Unit,
    onRetryOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExport: ((PhotoExportResult) -> Unit)? = null,
    closeRequest: Int = 0,
    renderPreference: PhotoRenderPreference = PhotoRenderPreference.Auto,
    onRequestAddImage: () -> Unit = {},
    onRequestRelink: (String) -> Unit = {}
) {
    val state by session.state.collectAsState()
    val rawExportOpen = remember(session) { mutableStateOf(false) }
    val settingsStore = rememberEditorSettingsStore()
    val actions = remember(
        session, onClose, onRetryOpen, onRequestAddImage, onRequestRelink, settingsStore
    ) {
        PhotoEditorActions(
            beginEdit = session::beginEdit,
            updateEdits = session::updateEdits,
            commitEdit = session::commitEdit,
            cancelEdit = session::cancelEdit,
            applyEdits = session::applyEdits,
            undo = session::undo,
            redo = session::redo,
            reset = session::reset,
            // A RAW has no container of its own to follow, so its save asks
            // unless the settings name one; other photos follow the source
            // or the stored copy format.
            saveCopy = {
                val current = session.state.value
                val decision = resolveSaveFormat(
                    settings = settingsStore.settings,
                    isRaw = current.info?.isRaw == true,
                    tiffAllowed = current.document?.toEdits()?.isDevelopOnly() == true,
                    supported = session.exportFormats
                )
                when (decision) {
                    SaveFormatDecision.Ask -> rawExportOpen.value = true
                    is SaveFormatDecision.Write -> session.exportCopy(decision.format)
                }
            },
            exportCopy = { format -> session.exportCopy(format) },
            exportFormats = session.exportFormats,
            cancelExport = session::cancelExport,
            retryOpen = onRetryOpen,
            retryPreview = session::retryPreview,
            clearError = session::clearError,
            close = onClose,
            setCpuPreviewEnabled = session::setCpuPreviewEnabled,
            setCropPreviewMode = session::setCropPreviewMode,
            reportPreviewFallback = session::reportPreviewFallback,
            beginToolEdit = session::beginToolEdit,
            applyToolEdit = session::applyToolEdit,
            cancelToolEdit = session::cancelToolEdit,
            requestAddImage = onRequestAddImage,
            cancelLayerImport = session::cancelLayerImport,
            retryRecovery = session::retryRestore,
            discardRecovery = session::discardRestore,
            requestRelink = onRequestRelink,
            renderCurvesInput = { session.renderCurvesInput() },
            renderDevelopThumbnail = { session.renderDevelopThumbnail(it) },
            sampleBeforeWhiteBalance = { x, y ->
                session.sampleBeforeWhiteBalance(x, y)
            }
        )
    }
    // The workbench chrome is owned here so every host — GalleryTheme on
    // Android, EditorTheme standalone, tests — gets the neutral studio
    // palette while keeping the host's accent and typography.
    EditorWorkspaceTheme {
        val readerFactory = remember(session) { session::openImageReader }
        // The host may persist user looks; without one the presets live
        // for this screen's lifetime.
        val providedPresetStore = LocalEditorLookPresetStore.current
        val localPresetStore = remember { EditorLookPresetStore() }
        val lookPresetStore = providedPresetStore ?: localPresetStore
        CompositionLocalProvider(
            LocalPhotoImageReaderFactory provides readerFactory,
            LocalEditorLookPresetStore provides lookPresetStore,
            LocalEditorSettingsStore provides settingsStore
        ) {
            PhotoEditorContent(
                state = state,
                actions = actions,
                modifier = modifier,
                onOpenExport = onOpenExport,
                closeRequest = closeRequest,
                renderPreference = renderPreference
            )
            if (rawExportOpen.value) {
                PhotoRawExportSheet(
                    allowTiff = state.document?.toEdits()?.isDevelopOnly() == true,
                    onSelect = { format ->
                        rawExportOpen.value = false
                        session.exportCopy(format)
                    },
                    onDismiss = { rawExportOpen.value = false }
                )
            }
        }
    }
}
