package com.dot.gallery.editor.ui

/**
 * Which pane the contextual area (compact bottom panel / wide inspector)
 * is showing: the selected tool's controls, the Source pane (original media
 * and recipe/source information), the Edits pane (current edit list) or
 * the Layers workspace (the composition's layer list and actions).
 */
enum class EditorWorkspacePanel {
    Edit,
    Source,
    Edits,
    Layers
}
