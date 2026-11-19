package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.render.photo.PhotoExportResult
import com.dot.gallery.editor.ui.icons.Layers

/**
 * The shared inputs of the workbench compositions — the large Simple
 * workspace and every Studio form: the session/canvas wiring plus the
 * callbacks their headers, docks and panels route through.
 */
internal class PhotoDesktopArgs(
    val state: PhotoEditorState,
    val actions: PhotoEditorActions,
    val workspace: EditorWorkspaceState,
    val toolUiState: PhotoToolUiState,
    val viewport: EditorViewportState,
    val bitmap: ImageBitmap?,
    val originalBitmap: ImageBitmap?,
    val imageDescription: String,
    val imageCapable: Boolean,
    val shaderDrawing: Boolean,
    val shaderRenderer: PhotoShaderRenderer?,
    val shaderEdits: PhotoEdits?,
    val shaderImageSize: PixelSize?,
    val onGpuFrameFailed: (PhotoShaderRenderer) -> Unit,
    val canvasFocusRequester: FocusRequester,
    val histogramPrefs: PhotoHistogramPrefs,
    val toolEntries: List<EditorToolEntry<PhotoEditorState, PhotoEditorActions>>,
    val toolMenu: PhotoToolMenuState,
    val onSelectTool: (EditorToolId) -> Unit,
    val onRequestClose: () -> Unit,
    val onOpenExport: ((PhotoExportResult) -> Unit)?,
    val onCanvasRefocus: () -> Unit,
    val autoDock: PhotoAutoDock
)

/**
 * The contextual tool overlay painted over the fit image box — the crop
 * window, the white-balance picker, the lens-blur handles and the
 * composition tools' layer overlays. Compare states paint nothing: the
 * split is the read-out, not an editing surface. Shared by the large
 * Simple workspace and every Studio form.
 */
@Composable
internal fun BoxScope.PhotoToolImageOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    workspace: EditorWorkspaceState,
    toolUiState: PhotoToolUiState,
    viewport: EditorViewportState,
    onCanvasRefocus: () -> Unit
) {
    when {
        viewport.compareBefore || viewport.compareSplit -> Unit
        workspace.panel == EditorWorkspacePanel.Layers ->
            PhotoLayerSelectionOverlay(
                layout = layout,
                state = state,
                selectedLayerId = workspace.selectedLayerId
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            (workspace.activeTool == EditorToolIds.PhotoCrop ||
                workspace.activeTool == EditorToolIds.PhotoPerspective) ->
            EditorCropWindow(
                layout = layout,
                state = state,
                actions = actions,
                upright = state.info?.uprightSize,
                onCommit = {
                    actions.applyToolEdit()
                    onCanvasRefocus()
                }
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            photoToolOwnsWhiteBalancePick(workspace.activeTool) &&
            toolUiState.whiteBalancePicking ->
            WhiteBalancePickOverlay(
                layout = layout,
                state = state,
                actions = actions,
                toolUiState = toolUiState,
                viewport = viewport
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool == EditorToolIds.PhotoLensBlur ->
            LensBlurOverlay(
                layout = layout,
                state = state,
                actions = actions
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool in RETOUCH_CANVAS_TOOLS ->
            RetouchCanvasOverlay(
                layout = layout,
                state = state,
                actions = actions,
                toolUiState = toolUiState,
                tool = workspace.activeTool,
                viewport = viewport
            )
        workspace.panel == EditorWorkspacePanel.Edit &&
            workspace.activeTool in CONTEXTUAL_PHOTO_TOOLS ->
            LayerToolCanvasOverlay(
                layout = layout,
                state = state,
                actions = actions,
                workspace = workspace,
                toolUiState = toolUiState,
                tool = workspace.activeTool
            )
    }
}
