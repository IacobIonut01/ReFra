/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.ui.theme.GalleryGlass
import kotlin.math.abs
import kotlin.math.roundToLong

/** Outer corner of a group in the viewer info sheet. */
val ViewerSheetOuterCorner: Dp = 24.dp

/** Corner between two rows of the same group. */
val ViewerSheetInnerCorner: Dp = 8.dp

/** Gap between the rows of one group. */
val ViewerSheetGroupGap: Dp = 2.dp

/** Corner radii of one cell, in the order a [RoundedCornerShape] takes them. */
data class ViewerSheetCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
) {
    fun toShape(): Shape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
    )
}

/** Where row [index] of [count] sits inside its group. */
fun viewerSheetPosition(index: Int, count: Int): Position = when {
    count <= 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/** Grouped-row corners: 24 dp on the group's outer edge, 8 dp between rows. */
fun viewerSheetCorners(position: Position): ViewerSheetCorners {
    val outer = ViewerSheetOuterCorner
    val inner = ViewerSheetInnerCorner
    return when (position) {
        Position.Alone -> ViewerSheetCorners(outer, outer, outer, outer)
        Position.Top -> ViewerSheetCorners(outer, outer, inner, inner)
        Position.Middle -> ViewerSheetCorners(inner, inner, inner, inner)
        Position.Bottom -> ViewerSheetCorners(inner, inner, outer, outer)
    }
}

/**
 * Corners of a cell in the two-column action grid. Only the grid's four outer corners are
 * rounded; a lone cell in the last row spans it and takes both bottom corners.
 */
fun actionGridCellCorners(
    row: Int,
    rowCount: Int,
    column: Int,
    spansRow: Boolean,
): ViewerSheetCorners {
    val outer = ViewerSheetOuterCorner
    val inner = ViewerSheetInnerCorner
    val isFirstRow = row == 0
    val isLastRow = row == rowCount - 1
    val isStart = column == 0
    val isEnd = column == 1 || spansRow
    return ViewerSheetCorners(
        topStart = if (isFirstRow && isStart) outer else inner,
        topEnd = if (isFirstRow && isEnd) outer else inner,
        bottomEnd = if (isLastRow && isEnd) outer else inner,
        bottomStart = if (isLastRow && isStart) outer else inner,
    )
}

/** "51.3217° N, 116.1860° W": four decimals, hemisphere letters instead of signs. */
fun formatCoordinates(latitude: Double, longitude: Double): String {
    fun part(value: Double, positive: Char, negative: Char): String {
        val scaled = (abs(value) * 10_000).roundToLong()
        val decimals = (scaled % 10_000).toString().padStart(4, '0')
        return "${scaled / 10_000}.$decimals° ${if (value < 0) negative else positive}"
    }
    return "${part(latitude, 'N', 'S')}, ${part(longitude, 'E', 'W')}"
}

/**
 * Fill of a row, cell or card inside the info sheet. With blur on it is the translucent
 * `glass/row` tint over the sheet's single blur; rows never blur again. With blur off the sheet
 * paints `surface` and rows `surfaceContainer`.
 */
@Composable
fun viewerSheetRowColor(): Color =
    if (LocalMediaViewerVisualPolicy.current.allowBlur) GalleryGlass.row
    else MaterialTheme.colorScheme.surfaceContainer

/** Rows of one group, 2 dp apart. */
@Composable
fun ViewerSheetGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ViewerSheetGroupGap),
    ) { content() }
}

/** Small label above a group ("Details", "Actions", "People"). */
@Composable
fun ViewerSheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
    )
}

/**
 * One row of the info sheet: a plain 24 dp glyph, title and summary, and optional trailing
 * content. Long-pressing copies the summary unless [onLongClick] is given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerSheetRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    position: Position = Position.Alone,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    summaryMaxLines: Int = 2,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = viewerSheetCorners(position).toShape()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val longClick = onLongClick
        ?: summary?.takeIf { it.isNotEmpty() }?.let { text ->
            { clipboardManager.setText(AnnotatedString(text)) }
        }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(viewerSheetRowColor())
            .then(
                if (onClick != null || longClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { onClick?.invoke() },
                        onLongClick = longClick,
                    )
                } else Modifier
            )
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.heightIn(min = 40.dp),
                contentAlignment = Alignment.Center,
            ) { trailing() }
        }
    }
}

/** Trailing glyph of a row that navigates or opens something (chevron, pencil). */
@Composable
fun ViewerSheetTrailingIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 48 dp round tonal button sitting on the sheet, such as the edit-date button. */
@Composable
fun ViewerSheetIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(viewerSheetRowColor()),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 28 dp chip under the date (RAW extension, Encrypted, Cloud). */
@Composable
fun ViewerSheetChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
