package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

/**
 * The declarative half of a registered editor tool: identity, label, icon
 * and catalogue grouping. Descriptors carry no behaviour — pairing with the
 * tool's content composable happens in the per-editor registry.
 */
data class EditorToolDescriptor(
    val id: EditorToolId,
    val title: StringResource,
    val icon: ImageVector,
    val group: EditorToolGroup
)
