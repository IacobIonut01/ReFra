package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compact sheets follow Light's value/ruler, tabs, then expanded settings rhythm. */
@Composable
internal fun PhotoParameterControls(
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    details: @Composable () -> Unit
) {
    val sheetExpanded = LocalPhotoToolSheetExpanded.current
    Column(modifier.fillMaxWidth()) {
        if (sheetExpanded == null) tabs()
        controls()
        if (sheetExpanded != null) tabs()
        if (sheetExpanded != false) details()
    }
}
