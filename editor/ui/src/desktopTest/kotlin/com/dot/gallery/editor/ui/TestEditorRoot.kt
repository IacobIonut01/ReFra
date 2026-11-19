package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

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
    // The histogram pill is off by default in the app; the suite keeps
    // it on so the histogram tests still reach it. The one-time ruler hint
    // is marked seen so it never shifts the measured layouts.
    val settingsStore = remember {
        EditorSettingsStore().apply {
            update { it.copy(showHistogram = true, rulerHintSeen = true) }
        }
    }
    CompositionLocalProvider(
        LocalWorkbenchTooltipsEnabled provides false,
        LocalEditorSettingsStore provides settingsStore
    ) {
        EditorTheme(darkTheme = darkTheme, content = content)
    }
}
