/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.logging.LogLevel
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.dev_range_1h
import com.dot.gallery.feature.settings.generated.resources.dev_range_24h
import com.dot.gallery.feature.settings.generated.resources.dev_range_7d
import com.dot.gallery.feature.settings.generated.resources.dev_range_all
import com.dot.gallery.feature.settings.generated.resources.dev_range_custom

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogFiltersCard(
    filters: SessionLogsFilters,
    onToggleLevel: (LogLevel) -> Unit,
    onRange: (SessionLogsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogLevel.entries.forEach { level ->
                    GalleryFilterChip(
                        label = level.name.lowercase()
                            .replaceFirstChar { it.titlecase() },
                        selected = level in filters.levels,
                        onClick = { onToggleLevel(level) }
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val ranges = listOf(
                    SessionLogsRange.H1 to Res.string.dev_range_1h,
                    SessionLogsRange.D1 to Res.string.dev_range_24h,
                    SessionLogsRange.D7 to Res.string.dev_range_7d,
                    SessionLogsRange.ALL to Res.string.dev_range_all,
                    SessionLogsRange.CUSTOM to Res.string.dev_range_custom,
                )
                ranges.forEach { (range, label) ->
                    GalleryFilterChip(
                        label = stringResource(label),
                        selected = filters.range == range,
                        onClick = { onRange(range) }
                    )
                }
            }
        }
    }
}
