package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.editor.render.photo.PhotoEditorState

internal const val TAG_CATEGORY_PERSPECTIVE = "editor.category.perspective"

/**
 * The legacy Perspective tool id. Keystone correction now lives in Crop &
 * straighten 2 (Straighten · Vertical · Horizontal share one ruler under a
 * fixed crop frame), so this entry opens that tool on its Vertical tab —
 * restored workspaces, pins and callers holding the old id keep working.
 */
@Composable
internal fun PerspectiveToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    CropToolContent(state, actions, modifier, initialTab = CropTab.Vertical)
}
