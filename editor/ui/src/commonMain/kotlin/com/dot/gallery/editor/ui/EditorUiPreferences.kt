package com.dot.gallery.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Host-supplied editor UI preferences. Today this is only the floating-
 * chrome material: when [blurEnabled] is false every blurred surface falls
 * back to an opaque fill with identical geometry — the media image itself
 * is never affected either way. Gallery hosts supply their `allowBlur`
 * preference; the desktop workbench exposes it as a View-menu toggle.
 */
@Immutable
data class EditorUiPreferences(
    val blurEnabled: Boolean = true
)

val LocalEditorUiPreferences =
    staticCompositionLocalOf { EditorUiPreferences() }

/**
 * The workspace's shared [HazeState]: the content area registers as the
 * backdrop source and floating chrome samples it. One instance per editor
 * composition, provided at the workspace root.
 */
internal val LocalEditorHazeState = compositionLocalOf { HazeState() }
