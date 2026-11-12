package com.dot.gallery.feature.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.presentation.components.GalleryDetailAction
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.MediaTypeFilter
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.timeline.generated.resources.Res
import com.dot.gallery.feature.timeline.generated.resources.sort_and_filter
import org.jetbrains.compose.resources.stringResource

/** Keeps a page's filter across navigation, such as opening the viewer and coming back. */
val TimelineFilterSaver: Saver<TimelineFilter, Any> = listSaver(
    save = { encodeTimelineFilter(it) },
    restore = { decodeTimelineFilter(it) },
)

fun encodeTimelineFilter(filter: TimelineFilter): List<Any> = listOf(
    filter.mediaType.name,
    filter.favoritesOnly,
    ArrayList(filter.selectedYears),
    ArrayList(filter.selectedAlbumIds),
)

fun decodeTimelineFilter(values: List<Any?>): TimelineFilter = TimelineFilter(
    mediaType = MediaTypeFilter.entries.firstOrNull { it.name == values.getOrNull(0) } ?: MediaTypeFilter.ALL,
    favoritesOnly = values.getOrNull(1) as? Boolean ?: false,
    selectedYears = (values.getOrNull(2) as? List<*>)?.filterIsInstance<Int>()?.toSet().orEmpty(),
    selectedAlbumIds = (values.getOrNull(3) as? List<*>)?.filterIsInstance<Long>()?.toSet().orEmpty(),
)

/** Albums worth offering as a Source on a page holding [media]: those it has items from. */
fun sourceAlbumsFor(albums: List<Album>, media: List<UriMedia>): List<Album> {
    val albumIds = media.mapTo(HashSet()) { it.albumID }
    return albums.filterableOnTimeline()
        .filter { album -> album.sourceAlbumIds.any { it in albumIds } }
        .sortedBy { it.label }
}

/** Drops selected Source albums that are no longer offered, so no hidden filter stays on. */
@Composable
fun KeepFilterAlbumsAvailable(
    albums: List<Album>,
    filter: TimelineFilter,
    onFilterChange: (TimelineFilter) -> Unit,
) {
    val currentFilter by rememberUpdatedState(filter)
    val currentOnChange by rememberUpdatedState(onFilterChange)
    LaunchedEffect(albums) {
        val supported = currentFilter.selectedAlbumIds.intersect(albums.mapTo(hashSetOf()) { it.id })
        if (supported != currentFilter.selectedAlbumIds) {
            currentOnChange(currentFilter.copy(selectedAlbumIds = supported))
        }
    }
}

/**
 * [filter] applied to the media of a page other than the timeline, regrouped with the same date
 * formats and grouping settings the media distributor used for [mediaState].
 */
@Composable
fun rememberFilteredMediaPageState(
    mediaState: State<MediaState<UriMedia>>,
    filter: TimelineFilter,
    albums: List<Album>,
    albumId: Long = -1L,
): State<MediaState<UriMedia>> {
    val distributor = LocalMediaDistributor.current
    val dateFormats by distributor.dateFormatsFlow.collectAsState()
    val settings by distributor.settingsFlow.collectAsState()
    val groupSimilarMedia by distributor.groupSimilarMedia.collectAsState()
    val enabledGroupTypes by distributor.enabledGroupTypes.collectAsState()
    val albumSourceIdsById = remember(albums) {
        albums.associate { album -> album.id to album.sourceAlbumIds.toSet() }
    }
    return rememberFilteredTimelineState(
        mediaState = mediaState,
        timelineFilter = filter,
        albumSourceIdsById = albumSourceIdsById,
        dateFormats = dateFormats,
        groupByMonth = settings?.groupTimelineByMonth == true,
        groupByYear = settings?.groupTimelineByYear == true,
        groupSimilarMedia = groupSimilarMedia,
        enabledGroupTypes = enabledGroupTypes,
        albumId = albumId,
    )
}

/** Title of the sort and filter sheet on pages other than the timeline. */
@Composable
fun mediaPageControlsTitle(): String = stringResource(Res.string.sort_and_filter)

/** Top bar button that opens a page's sort and filter sheet; tinted while any control is set. */
@Composable
fun MediaPageFilterAction(active: Boolean, onClick: () -> Unit) {
    GalleryPageTopBarAction(
        icon = Icons.Outlined.FilterList,
        contentDescription = stringResource(Res.string.sort_and_filter),
        onClick = onClick,
        tint = if (active) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
}

/** Detail header pill that opens a page's sort and filter sheet. */
@Composable
fun MediaPageFilterPill(onClick: () -> Unit) {
    GalleryDetailAction(
        label = stringResource(Res.string.sort_and_filter),
        icon = Icons.Outlined.FilterList,
        onClick = onClick,
    )
}
