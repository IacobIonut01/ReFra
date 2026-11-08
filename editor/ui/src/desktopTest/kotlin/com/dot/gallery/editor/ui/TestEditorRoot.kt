package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Test root: the standalone [EditorTheme] with workbench tooltips off. A
 * tooltip popup is a second subscene — injected pointer moves can dispose it
 * while the render loop still holds a queued redraw for it
 * ("RootNodeOwner is already disposed"). Production keeps tooltips; tests
 * only lose the hover label, never the control's semantics.
 */
@Composable
internal fun TestEditorRoot(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalWorkbenchTooltipsEnabled provides false) {
        EditorTheme(darkTheme = darkTheme, content = content)
    }
}
