/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.TimelineMonth
import com.dot.gallery.core.presentation.components.thumbnailSignature
import com.dot.gallery.domain.date.todayLocalDate
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.timeline.generated.resources.Res
import com.dot.gallery.feature.timeline.generated.resources.story_item_count
import com.dot.gallery.feature.timeline.generated.resources.timeline_months_close
import com.dot.gallery.feature.timeline.generated.resources.timeline_months_this_month
import com.dot.gallery.feature.timeline.generated.resources.timeline_months_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every month of the timeline as a 2×2 collage tile, grouped by year. Opened by pinching out past
 * the widest grid or tapping the floating date; a tap on a tile jumps the grid to that month, and
 * a pinch in or the close button goes back.
 */
@Composable
fun TimelineMonthsOverview(
    months: List<TimelineMonth>,
    topPadding: Dp,
    bottomPadding: Dp,
    onMonthClick: (TimelineMonth) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val close by rememberUpdatedState(onClose)
    val byYear = remember(months) { months.groupBy { it.year } }
    val today = remember { todayLocalDate() }
    val currentKey = today.year * 100 + today.monthNumber
    val currentYear = today.year
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size >= 2) zoom *= event.calculateZoom()
                    } while (event.changes.any { it.pressed })
                    if (zoom > 1.15f) close()
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = topPadding + 56.dp, bottom = bottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            byYear.forEach { (year, yearMonths) ->
                item(key = "year_$year", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val count = yearMonths.sumOf { it.itemCount }
                        Text(
                            text = pluralStringResource(Res.plurals.story_item_count, count, count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                items(yearMonths, key = { it.key }) { month ->
                    MonthTile(
                        month = month,
                        title = month.title(currentYear),
                        isCurrent = month.key == currentKey,
                        onClick = { onMonthClick(month) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = topPadding)
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.timeline_months_close))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(Res.string.timeline_months_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MonthTile(
    month: TimelineMonth,
    title: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = title, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            val photos = month.highlights
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0..1) {
                            val media = photos.getOrNull(row * 2 + col)
                                ?: photos.getOrNull((row * 2 + col) % photos.size.coerceAtLeast(1))
                            TilePhoto(media, Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val items = pluralStringResource(Res.plurals.story_item_count, month.itemCount, month.itemCount)
            val place = month.places.firstOrNull()?.city
            val detail = listOfNotNull(
                stringResource(Res.string.timeline_months_this_month).takeIf { isCurrent },
                items,
                place,
            ).joinToString(" · ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TilePhoto(media: Media?, modifier: Modifier) {
    if (media == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        return
    }
    LocalMediaImageRenderer.current.RenderImage(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        model = media.getUri(),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        signature = media.thumbnailSignature(),
    )
}
