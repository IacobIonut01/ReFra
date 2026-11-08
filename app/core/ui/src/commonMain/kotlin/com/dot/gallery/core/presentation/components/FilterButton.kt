/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HorizontalSplit
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.AlbumViewType
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.OrderType
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.toggle_view_type

@Composable
private fun SortSegment(
    shape: Shape,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
fun FilterButton(
    modifier: Modifier = Modifier,
    filterOptions: Array<FilterOption> = emptyArray(),
    viewType: AlbumViewType,
    onViewTypeChange: (AlbumViewType) -> Unit,
    /** Replaces the leading view toggle; the toggle then joins the trailing controls. */
    title: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    var lastSort by rememberPreference(SettingsKeys.albumLastSort)
    var expanded by remember { mutableStateOf(false) }
    val selectedFilter by remember(lastSort) { mutableStateOf(filterOptions.first { it.filterKind == lastSort.kind }) }
    var order: OrderType by remember(lastSort) { mutableStateOf(lastSort.orderType) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val viewToggle: @Composable () -> Unit = {
                IconButton(
                    onClick = {
                        if (viewType == AlbumViewType.GRID) {
                            onViewTypeChange(AlbumViewType.LIST)
                        } else {
                            onViewTypeChange(AlbumViewType.GRID)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (viewType == AlbumViewType.GRID) Icons.Outlined.GridView else Icons.Outlined.HorizontalSplit,
                        contentDescription = stringResource(Res.string.toggle_view_type),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (title != null) title() else viewToggle()

            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // A connected group: outer corners fully round, inner corners tight.
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        SortSegment(
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                            onClick = { expanded = true },
                        ) {
                            Text(
                                modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                text = stringResource(selectedFilter.titleRes)
                            )
                        }
                        SortSegment(
                            shape = RoundedCornerShape(4.dp),
                            onClick = {
                                order = if (order == OrderType.Ascending) OrderType.Descending else OrderType.Ascending
                                lastSort = lastSort.copy(orderType = order)
                            },
                        ) {
                            Icon(
                                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                                imageVector = if (order == OrderType.Descending) {
                                    Icons.Outlined.KeyboardDoubleArrowDown
                                } else Icons.Outlined.KeyboardDoubleArrowUp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = null
                            )
                        }
                        SortSegment(
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp),
                            onClick = {
                                onViewTypeChange(
                                    if (viewType == AlbumViewType.GRID) AlbumViewType.LIST else AlbumViewType.GRID
                                )
                            },
                        ) {
                            Icon(
                                modifier = Modifier.padding(start = 8.dp, end = 12.dp).size(20.dp),
                                imageVector = if (viewType == AlbumViewType.GRID) Icons.Outlined.GridView else Icons.Outlined.HorizontalSplit,
                                contentDescription = stringResource(Res.string.toggle_view_type),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    actions?.invoke(this)
                }
            } else Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .clickable {
                            expanded = true
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    text = stringResource(selectedFilter.titleRes)
                )
                IconButton(
                    onClick = {
                        order = if (order == OrderType.Ascending) OrderType.Descending else OrderType.Ascending
                        lastSort = lastSort.copy(orderType = order)
                    }
                ) {
                    Icon(
                        imageVector = remember(selectedFilter) {
                            if (order == OrderType.Descending)
                                Icons.Outlined.KeyboardDoubleArrowDown
                            else Icons.Outlined.KeyboardDoubleArrowUp
                        },
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
        ) {
            DropdownMenu(
                modifier = Modifier.align(Alignment.TopEnd),
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filterOptions.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(filter.titleRes)) },
                        onClick = {
                            lastSort = lastSort.copy(kind = filter.filterKind)
                        },
                        trailingIcon = {
                            if (lastSort.kind == filter.filterKind) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
