/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.timeline.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.dot.gallery.core.presentation.components.SetupButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.MediaTypeFilter
import com.dot.gallery.feature_node.domain.model.TimelineFilter
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimelineFilterSheet(
    sheetState: AppBottomSheetState,
    currentFilter: TimelineFilter,
    availableYears: List<Int>,
    availableAlbums: List<Album>,
    onApply: (TimelineFilter) -> Unit,
) {
    val scope = rememberCoroutineScope()

    if (sheetState.isVisible) {
        var filter by remember(currentFilter) { mutableStateOf(currentFilter) }

        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch { sheetState.hide() }
            },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Scrollable filter content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    // Title
                    Text(
                        text = stringResource(R.string.filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Type section
                    FilterSectionHeader(stringResource(R.string.filter_type))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        FilterChip(
                            label = stringResource(R.string.filter_all),
                            selected = filter.mediaType == MediaTypeFilter.ALL,
                            onClick = { filter = filter.copy(mediaType = MediaTypeFilter.ALL) }
                        )
                        FilterChip(
                            label = stringResource(R.string.photos),
                            selected = filter.mediaType == MediaTypeFilter.PHOTOS,
                            onClick = { filter = filter.copy(mediaType = MediaTypeFilter.PHOTOS) }
                        )
                        FilterChip(
                            label = stringResource(R.string.videos),
                            selected = filter.mediaType == MediaTypeFilter.VIDEOS,
                            onClick = { filter = filter.copy(mediaType = MediaTypeFilter.VIDEOS) }
                        )
                    }

                    // Status section
                    FilterSectionHeader(stringResource(R.string.filter_status))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        FilterChip(
                            label = stringResource(R.string.favorites),
                            selected = filter.favoritesOnly,
                            onClick = { filter = filter.copy(favoritesOnly = !filter.favoritesOnly) }
                        )
                    }

                    // Time section
                    if (availableYears.isNotEmpty()) {
                        FilterSectionHeader(stringResource(R.string.filter_time))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            availableYears.forEach { year ->
                                FilterChip(
                                    label = year.toString(),
                                    selected = year in filter.selectedYears,
                                    onClick = {
                                        val newYears = filter.selectedYears.toMutableSet()
                                        if (year in newYears) newYears.remove(year) else newYears.add(year)
                                        filter = filter.copy(selectedYears = newYears)
                                    }
                                )
                            }
                        }
                    }

                    // Source (Albums) section
                    if (availableAlbums.isNotEmpty()) {
                        FilterSectionHeader(stringResource(R.string.filter_source))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            availableAlbums.forEach { album ->
                                FilterChip(
                                    label = album.label,
                                    selected = album.id in filter.selectedAlbumIds,
                                    onClick = {
                                        val newIds = filter.selectedAlbumIds.toMutableSet()
                                        if (album.id in newIds) newIds.remove(album.id) else newIds.add(album.id)
                                        filter = filter.copy(selectedAlbumIds = newIds)
                                    }
                                )
                            }
                        }
                    }
                }

                // Pinned bottom buttons
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetupButton(
                        modifier = Modifier.weight(1f),
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        text = stringResource(R.string.filter_reset),
                        onClick = { filter = TimelineFilter() }
                    )
                    SetupButton(
                        modifier = Modifier.weight(1f),
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        text = stringResource(R.string.filter_apply),
                        onClick = {
                            onApply(filter)
                            scope.launch { sheetState.hide() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "chipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipContent"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "chipBorder"
    )

    val shape = RoundedCornerShape(100)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
