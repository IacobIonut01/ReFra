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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetSectionLabel
import com.dot.gallery.core.presentation.components.sheetRowPosition
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
import com.dot.gallery.feature.timeline.generated.resources.file_name_summary
import com.dot.gallery.feature.timeline.generated.resources.filter_all
import com.dot.gallery.feature.timeline.generated.resources.filter_display
import com.dot.gallery.feature.timeline.generated.resources.filter_reset
import com.dot.gallery.feature.timeline.generated.resources.filter_source
import com.dot.gallery.feature.timeline.generated.resources.filter_status
import com.dot.gallery.feature.timeline.generated.resources.filter_time
import com.dot.gallery.feature.timeline.generated.resources.filter_type
import com.dot.gallery.feature.timeline.generated.resources.modified_time_summary
import com.dot.gallery.feature.timeline.generated.resources.order_by
import com.dot.gallery.feature.timeline.generated.resources.photos
import com.dot.gallery.feature.timeline.generated.resources.remember_filters
import com.dot.gallery.feature.timeline.generated.resources.remember_filters_summary
import com.dot.gallery.feature.timeline.generated.resources.show_date_headers
import com.dot.gallery.feature.timeline.generated.resources.show_date_headers_summary
import com.dot.gallery.feature.timeline.generated.resources.sort_ascending
import com.dot.gallery.feature.timeline.generated.resources.sort_by_date_modified
import com.dot.gallery.feature.timeline.generated.resources.sort_by_file_name
import com.dot.gallery.feature.timeline.generated.resources.sort_descending
import com.dot.gallery.feature.timeline.generated.resources.sort_name_ascending
import com.dot.gallery.feature.timeline.generated.resources.sort_name_descending
import com.dot.gallery.feature.timeline.generated.resources.timeline_controls
import com.dot.gallery.feature.timeline.generated.resources.videos
import org.jetbrains.compose.resources.stringResource

const val TIMELINE_FILTER_CHIP_VISUAL_TAG = "TimelineFilterChipVisual"
const val TIMELINE_CAPTURE_SORT_TAG = "TimelineCaptureSort"
const val TIMELINE_MODIFIED_SORT_TAG = "TimelineModifiedSort"
const val TIMELINE_NAME_SORT_TAG = "TimelineNameSort"
const val TIMELINE_DATE_HEADERS_TAG = "TimelineDateHeaders"
const val TIMELINE_REMEMBER_FILTERS_TAG = "TimelineRememberFilters"

/** The order every media page starts with, and what Reset goes back to. */
val DefaultTimelineSort = LastSort(OrderType.Descending, FilterKind.DATE)

/** True when the page shows something other than everything, newest first. */
fun timelineControlsActive(filter: TimelineFilter, sort: LastSort): Boolean =
    filter.isActive || sort != DefaultTimelineSort

/**
 * Sort and filter sheet of a media page. Filter and date header changes are reported as they are
 * tapped. The order is kept in the sheet and passed to [onSortCommit] once, when the sheet
 * closes, and only if it changed: the order is a saved preference and resorting reloads the page.
 */
@Composable
fun TimelineFilterSheet(
    sheetState: AppBottomSheetState,
    filter: TimelineFilter,
    sort: LastSort,
    availableYears: List<Int>,
    availableAlbums: List<Album>,
    indexProgress: Int?,
    onFilterChange: (TimelineFilter) -> Unit,
    onSortCommit: (LastSort) -> Unit,
    title: String = stringResource(Res.string.timeline_controls),
    showFavoritesFilter: Boolean = true,
    showNameSort: Boolean = false,
    dateHeaders: Boolean? = null,
    onDateHeadersChange: (Boolean) -> Unit = {},
    rememberFilters: Boolean? = null,
    onRememberFiltersChange: (Boolean) -> Unit = {},
) {
    if (!sheetState.isVisible) return
    val draftSort = remember(sort) { mutableStateOf(sort) }
    val latestDraft by rememberUpdatedState(draftSort)
    val latestSort by rememberUpdatedState(sort)
    val latestCommit by rememberUpdatedState(onSortCommit)
    DisposableEffect(Unit) {
        onDispose { sortToCommit(latestSort, latestDraft.value)?.let(latestCommit) }
    }
    ExpressiveSheet(sheetState = sheetState, paneTitle = title) {
        TimelineFilterSheetContent(
            filter = filter,
            sort = draftSort.value,
            availableYears = availableYears,
            availableAlbums = availableAlbums.map { it.id to it.label },
            indexProgress = indexProgress,
            onFilterChange = onFilterChange,
            onSortChange = { draftSort.value = it },
            onReset = {
                onFilterChange(TimelineFilter())
                draftSort.value = DefaultTimelineSort
            },
            title = title,
            showFavoritesFilter = showFavoritesFilter,
            showNameSort = showNameSort,
            dateHeaders = dateHeaders,
            onDateHeadersChange = onDateHeadersChange,
            rememberFilters = rememberFilters,
            onRememberFiltersChange = onRememberFiltersChange,
        )
    }
}

/** The order to save when the sheet closes, or null when it is unchanged. */
fun sortToCommit(saved: LastSort, draft: LastSort): LastSort? = draft.takeIf { it != saved }

/**
 * Stateless body of [TimelineFilterSheet]: order options as a grouped radio list, every other
 * filter as a chip row, an optional date headers switch, an optional "Remember filters" switch
 * (null hides it), then Reset. [availableAlbums] are id and label pairs; an empty list hides the
 * Source section.
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
    title: String = stringResource(Res.string.timeline_controls),
    showFavoritesFilter: Boolean = true,
    showNameSort: Boolean = false,
    dateHeaders: Boolean? = null,
    onDateHeadersChange: (Boolean) -> Unit = {},
    rememberFilters: Boolean? = null,
    onRememberFiltersChange: (Boolean) -> Unit = {},
) {
    SheetHeader(title = title, modifier = Modifier.padding(bottom = 0.dp))

    SheetSectionLabel(stringResource(Res.string.order_by))
    val sortOptionCount = if (showNameSort) 3 else 2
    TimelineSortOption(
        title = stringResource(Res.string.capture_time),
        summary = stringResource(Res.string.capture_time_summary),
        testTag = TIMELINE_CAPTURE_SORT_TAG,
        selected = sort.kind == FilterKind.DATE,
        position = sheetRowPosition(0, sortOptionCount),
        onClick = { onSortChange(sort.copy(kind = FilterKind.DATE)) }
    )
    TimelineSortOption(
        title = stringResource(Res.string.sort_by_date_modified),
        summary = stringResource(Res.string.modified_time_summary),
        testTag = TIMELINE_MODIFIED_SORT_TAG,
        selected = sort.kind == FilterKind.DATE_MODIFIED,
        position = sheetRowPosition(1, sortOptionCount),
        onClick = { onSortChange(sort.copy(kind = FilterKind.DATE_MODIFIED)) }
    )
    if (showNameSort) {
        TimelineSortOption(
            title = stringResource(Res.string.sort_by_file_name),
            summary = stringResource(Res.string.file_name_summary),
            testTag = TIMELINE_NAME_SORT_TAG,
            selected = sort.kind == FilterKind.NAME,
            position = sheetRowPosition(2, sortOptionCount),
            onClick = { onSortChange(sort.copy(kind = FilterKind.NAME)) }
        )
    }
    if (indexProgress != null) {
        Text(
            text = stringResource(Res.string.capture_time_indexing, indexProgress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
    }
    val byName = sort.kind == FilterKind.NAME
    ChipRow(modifier = Modifier.padding(top = 4.dp)) {
        TimelineFilterChip(
            label = stringResource(if (byName) Res.string.sort_name_descending else Res.string.sort_descending),
            selected = sort.orderType == OrderType.Descending,
            role = Role.RadioButton,
            onClick = { onSortChange(sort.copy(orderType = OrderType.Descending)) }
        )
        TimelineFilterChip(
            label = stringResource(if (byName) Res.string.sort_name_ascending else Res.string.sort_ascending),
            selected = sort.orderType == OrderType.Ascending,
            role = Role.RadioButton,
            onClick = { onSortChange(sort.copy(orderType = OrderType.Ascending)) }
        )
    }

    if (dateHeaders != null) {
        SheetSectionLabel(stringResource(Res.string.filter_display))
        SheetOptionRow(
            title = stringResource(Res.string.show_date_headers),
            summary = stringResource(Res.string.show_date_headers_summary),
            trailing = SheetRowTrailing.Switch(dateHeaders),
            onClick = { onDateHeadersChange(!dateHeaders) },
            modifier = Modifier.testTag(TIMELINE_DATE_HEADERS_TAG),
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

    if (showFavoritesFilter) {
        SheetSectionLabel(stringResource(Res.string.filter_status))
        ChipRow {
            TimelineFilterChip(
                label = stringResource(Res.string.favorites),
                selected = filter.favoritesOnly,
                onClick = { onFilterChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
            )
        }
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

    if (rememberFilters != null) {
        SheetOptionRow(
            title = stringResource(Res.string.remember_filters),
            summary = stringResource(Res.string.remember_filters_summary),
            trailing = SheetRowTrailing.Switch(rememberFilters),
            onClick = { onRememberFiltersChange(!rememberFilters) },
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(TIMELINE_REMEMBER_FILTERS_TAG),
        )
    }

    SheetActions {
        SheetButton(
            text = stringResource(Res.string.filter_reset),
            onClick = onReset,
            style = SheetButtonStyle.Tonal,
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
