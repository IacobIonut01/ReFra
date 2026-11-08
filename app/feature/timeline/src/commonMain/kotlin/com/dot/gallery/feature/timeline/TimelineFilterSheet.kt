package com.dot.gallery.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetSectionLabel
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.MediaTypeFilter
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.feature.timeline.generated.resources.Res
import com.dot.gallery.feature.timeline.generated.resources.capture_time
import com.dot.gallery.feature.timeline.generated.resources.capture_time_indexing
import com.dot.gallery.feature.timeline.generated.resources.capture_time_summary
import com.dot.gallery.feature.timeline.generated.resources.favorites
import com.dot.gallery.feature.timeline.generated.resources.filter_all
import com.dot.gallery.feature.timeline.generated.resources.filter_apply
import com.dot.gallery.feature.timeline.generated.resources.filter_reset
import com.dot.gallery.feature.timeline.generated.resources.filter_source
import com.dot.gallery.feature.timeline.generated.resources.filter_status
import com.dot.gallery.feature.timeline.generated.resources.filter_time
import com.dot.gallery.feature.timeline.generated.resources.filter_type
import com.dot.gallery.feature.timeline.generated.resources.modified_time_summary
import com.dot.gallery.feature.timeline.generated.resources.order_by
import com.dot.gallery.feature.timeline.generated.resources.photos
import com.dot.gallery.feature.timeline.generated.resources.sort_ascending
import com.dot.gallery.feature.timeline.generated.resources.sort_by_date_modified
import com.dot.gallery.feature.timeline.generated.resources.sort_descending
import com.dot.gallery.feature.timeline.generated.resources.timeline_controls
import com.dot.gallery.feature.timeline.generated.resources.videos
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val TIMELINE_FILTER_CHIP_VISUAL_TAG = "TimelineFilterChipVisual"
const val TIMELINE_CAPTURE_SORT_TAG = "TimelineCaptureSort"
const val TIMELINE_MODIFIED_SORT_TAG = "TimelineModifiedSort"

@Composable
fun TimelineFilterSheet(
    sheetState: AppBottomSheetState,
    currentFilter: TimelineFilter,
    currentSort: LastSort,
    availableYears: List<Int>,
    availableAlbums: List<Album>,
    indexProgress: Int?,
    onApply: (TimelineFilter, LastSort) -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (!sheetState.isVisible) return
    var filter by remember(currentFilter) { mutableStateOf(currentFilter) }
    var sort by remember(currentSort) { mutableStateOf(currentSort) }
    ExpressiveSheet(sheetState = sheetState, paneTitle = stringResource(Res.string.timeline_controls)) {
        TimelineFilterSheetContent(
            filter = filter,
            sort = sort,
            availableYears = availableYears,
            availableAlbums = availableAlbums.map { it.id to it.label },
            indexProgress = indexProgress,
            onFilterChange = { filter = it },
            onSortChange = { sort = it },
            onReset = {
                filter = TimelineFilter()
                sort = LastSort(OrderType.Descending, FilterKind.DATE)
            },
            onApply = {
                onApply(filter, sort)
                scope.launch { sheetState.hide() }
            },
        )
    }
}

/**
 * Stateless body of [TimelineFilterSheet]: order options as a grouped radio list, every other
 * filter as a chip row, then Reset and Apply. [availableAlbums] are id and label pairs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.TimelineFilterSheetContent(
    filter: TimelineFilter,
    sort: LastSort,
    availableYears: List<Int>,
    availableAlbums: List<Pair<Long, String>>,
    indexProgress: Int?,
    onFilterChange: (TimelineFilter) -> Unit,
    onSortChange: (LastSort) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.timeline_controls), modifier = Modifier.padding(bottom = 0.dp))

    SheetSectionLabel(stringResource(Res.string.order_by))
    TimelineSortOption(
        title = stringResource(Res.string.capture_time),
        summary = stringResource(Res.string.capture_time_summary),
        testTag = TIMELINE_CAPTURE_SORT_TAG,
        selected = sort.kind == FilterKind.DATE,
        position = Position.Top,
        onClick = { onSortChange(sort.copy(kind = FilterKind.DATE)) }
    )
    TimelineSortOption(
        title = stringResource(Res.string.sort_by_date_modified),
        summary = stringResource(Res.string.modified_time_summary),
        testTag = TIMELINE_MODIFIED_SORT_TAG,
        selected = sort.kind == FilterKind.DATE_MODIFIED,
        position = Position.Bottom,
        onClick = { onSortChange(sort.copy(kind = FilterKind.DATE_MODIFIED)) }
    )
    if (indexProgress != null) {
        Text(
            text = stringResource(Res.string.capture_time_indexing, indexProgress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
    }
    ChipRow(modifier = Modifier.padding(top = 4.dp)) {
        TimelineFilterChip(
            label = stringResource(Res.string.sort_descending),
            selected = sort.orderType == OrderType.Descending,
            role = Role.RadioButton,
            onClick = { onSortChange(sort.copy(orderType = OrderType.Descending)) }
        )
        TimelineFilterChip(
            label = stringResource(Res.string.sort_ascending),
            selected = sort.orderType == OrderType.Ascending,
            role = Role.RadioButton,
            onClick = { onSortChange(sort.copy(orderType = OrderType.Ascending)) }
        )
    }

    SheetSectionLabel(stringResource(Res.string.filter_type))
    ChipRow {
        TimelineFilterChip(
            label = stringResource(Res.string.filter_all),
            selected = filter.mediaType == MediaTypeFilter.ALL,
            onClick = { onFilterChange(filter.copy(mediaType = MediaTypeFilter.ALL)) }
        )
        TimelineFilterChip(
            label = stringResource(Res.string.photos),
            selected = filter.mediaType == MediaTypeFilter.PHOTOS,
            onClick = { onFilterChange(filter.copy(mediaType = MediaTypeFilter.PHOTOS)) }
        )
        TimelineFilterChip(
            label = stringResource(Res.string.videos),
            selected = filter.mediaType == MediaTypeFilter.VIDEOS,
            onClick = { onFilterChange(filter.copy(mediaType = MediaTypeFilter.VIDEOS)) }
        )
    }

    SheetSectionLabel(stringResource(Res.string.filter_status))
    ChipRow {
        TimelineFilterChip(
            label = stringResource(Res.string.favorites),
            selected = filter.favoritesOnly,
            onClick = { onFilterChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
        )
    }

    if (availableYears.isNotEmpty()) {
        SheetSectionLabel(stringResource(Res.string.filter_time))
        ChipRow {
            availableYears.forEach { year ->
                TimelineFilterChip(
                    label = year.toString(),
                    selected = year in filter.selectedYears,
                    onClick = {
                        val years = filter.selectedYears.toMutableSet()
                        if (year in years) years.remove(year) else years.add(year)
                        onFilterChange(filter.copy(selectedYears = years))
                    }
                )
            }
        }
    }

    if (availableAlbums.isNotEmpty()) {
        SheetSectionLabel(stringResource(Res.string.filter_source))
        ChipRow {
            availableAlbums.forEach { (id, label) ->
                TimelineFilterChip(
                    label = label,
                    selected = id in filter.selectedAlbumIds,
                    onClick = {
                        val ids = filter.selectedAlbumIds.toMutableSet()
                        if (id in ids) ids.remove(id) else ids.add(id)
                        onFilterChange(filter.copy(selectedAlbumIds = ids))
                    }
                )
            }
        }
    }

    SheetActions {
        SheetButton(
            text = stringResource(Res.string.filter_reset),
            onClick = onReset,
            style = SheetButtonStyle.Tonal,
            modifier = Modifier.weight(1f),
        )
        SheetButton(
            text = stringResource(Res.string.filter_apply),
            onClick = onApply,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) { content() }
}
