package com.dot.gallery.feature.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing

/** One "Order by" choice of the timeline controls, as a grouped radio row. */
@Composable
fun TimelineSortOption(
    title: String,
    summary: String,
    testTag: String,
    selected: Boolean,
    position: Position = Position.Alone,
    onClick: () -> Unit
) {
    SheetOptionRow(
        title = title,
        summary = summary,
        position = position,
        trailing = SheetRowTrailing.Radio(selected),
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    )
}
