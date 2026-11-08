package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where an editor workspace opens instead of its home state: a tool to
 * select ([tool], an [EditorToolIds] value), a contextual [panel], or one
 * of the overlays. Hosts that deep-link into a surface provide it through
 * [LocalEditorLaunchPose]; the default (null) opens the editor as usual.
 *
 * The pose is applied once per source, after the document has opened, and
 * a tool goes through the same selection path a tap on its cell takes, so
 * the tool begins its edit exactly as it would for the user.
 */
@Immutable
data class EditorLaunchPose(
    val tool: String? = null,
    val panel: EditorWorkspacePanel? = null,
    val catalogueOpen: Boolean = false,
    val pinEditorOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val focusMode: Boolean = false
)

val LocalEditorLaunchPose = staticCompositionLocalOf<EditorLaunchPose?> { null }
