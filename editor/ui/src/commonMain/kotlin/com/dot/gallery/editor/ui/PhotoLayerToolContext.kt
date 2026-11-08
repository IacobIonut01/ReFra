package com.dot.gallery.editor.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The contextual bridge between the layer-aware surfaces — the Layers
 * list, the composition tools and the canvas overlays — and the
 * workspace state they share. [selectedLayerId] is the workspace's own
 * selection (null = base); [selectLayer] writes it; [requestAddImage]
 * raises the host's image picker; [openTool] selects a registered tool
 * through the same path a catalogue cell takes, so the outgoing tool
 * transaction is settled before the next opens; [enterFocusMode] raises
 * the focus canvas so a contextual tool (the mask brush) keeps its
 * pointer surface and its pending Cancel/Apply.
 */
@Stable
internal class PhotoLayerToolContext(
    val selectedLayerId: String?,
    val selectLayer: (String?) -> Unit,
    val requestAddImage: () -> Unit,
    val openTool: (EditorToolId) -> Unit,
    val enterFocusMode: () -> Unit
)

/** Present around the layer-aware panels; null in detached previews. */
internal val LocalPhotoLayerToolContext =
    staticCompositionLocalOf<PhotoLayerToolContext?> { null }
