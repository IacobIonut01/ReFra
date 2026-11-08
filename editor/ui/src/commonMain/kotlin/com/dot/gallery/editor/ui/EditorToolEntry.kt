package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable

/**
 * A registered editor tool: its [descriptor] plus the composable that
 * renders its controls into the contextual panel. The registry lists only
 * tools whose backing session behaviour actually exists — the catalogue is
 * built straight from this list, so a new tool is added here without
 * touching the workspace layout.
 */
internal class EditorToolEntry<S, A>(
    val descriptor: EditorToolDescriptor,
    val tag: String,
    /**
     * Whether the tool can open in the current state — evaluated by the
     * catalogue/rail so a contextual tool (e.g. layer transform with no
     * image layer) paints disabled and refuses selection. Defaults to
     * always available.
     */
    val available: (S) -> Boolean = { true },
    /**
     * Whether the tool appears in the catalogue/rail at all — contextual-
     * only tools (the layer mask brush) register here so dispatch and the
     * tool title resolve, but are reached through their parent surface.
     */
    val catalogued: Boolean = true,
    /**
     * Compact surface anatomy. Photo entries explicitly select the V4
     * sheet; other registries may keep an inline inspector.
     */
    val presentation: EditorToolPresentation = EditorToolPresentation.Inline,
    val content: @Composable (S, A) -> Unit
)
