package com.dot.gallery.feature.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.MediaTypeFilter
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.timestampFor
import com.dot.gallery.domain.util.MediaGroupType
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.util.mapMediaToItem

fun resolveTimelineAlbumSourceIds(
    selectedAlbumIds: Set<Long>,
    sourceIdsByAlbumId: Map<Long, Set<Long>>,
): Set<Long> = selectedAlbumIds.flatMapTo(hashSetOf()) { albumId ->
    sourceIdsByAlbumId[albumId].orEmpty()
}

fun <T> filterTimelineItems(
    items: List<T>,
    filter: TimelineFilter,
    selectedAlbumSourceIds: Set<Long>,
    isImage: (T) -> Boolean,
    isVideo: (T) -> Boolean,
    isFavorite: (T) -> Boolean,
    timestampSeconds: (T) -> Long,
    albumId: (T) -> Long,
): List<T> = items.filter { item ->
    val typeMatch = when (filter.mediaType) {
        MediaTypeFilter.ALL -> true
        MediaTypeFilter.PHOTOS -> isImage(item)
        MediaTypeFilter.VIDEOS -> isVideo(item)
    }
    val favoriteMatch = !filter.favoritesOnly || isFavorite(item)
    val yearMatch = filter.selectedYears.isEmpty() ||
        timestampSeconds(item).epochSecToLocalDate().year in filter.selectedYears
    val albumMatch = filter.selectedAlbumIds.isEmpty() ||
        selectedAlbumSourceIds.isEmpty() || albumId(item) in selectedAlbumSourceIds
    typeMatch && favoriteMatch && yearMatch && albumMatch
}

fun List<Album>.filterableOnTimeline(): List<Album> = filterNot { album ->
    album.isLocked || album.relativePath.startsWith("cloud/")
}

/** Sorted distinct capture years for the media set — feeds the filter sheet's year chips. */
@Composable
fun rememberAvailableTimelineYears(
    mediaState: State<MediaState<UriMedia>>,
): List<Int> {
    val years by rememberedDerivedState(mediaState.value) {
        mediaState.value.media.mapTo(mutableSetOf()) { media ->
            media.timestampFor(mediaState.value.dateSource).epochSecToLocalDate().year
        }.sortedDescending()
    }
    return years
}

/**
 * Applies the active [TimelineFilter] and regroups the media list into [MediaState] items.
 * When no filter is active the source state passes through untouched.
 */
@Composable
fun rememberFilteredTimelineState(
    mediaState: State<MediaState<UriMedia>>,
    timelineFilter: TimelineFilter,
    albumSourceIdsById: Map<Long, Set<Long>>,
    dateFormats: Triple<String, String, String>,
    groupByMonth: Boolean,
    groupByYear: Boolean,
    groupSimilarMedia: Boolean,
    enabledGroupTypes: Set<MediaGroupType>,
    albumId: Long = -1L,
): State<MediaState<UriMedia>> = produceState(
    initialValue = if (timelineFilter.isActive) {
        MediaState(
            error = mediaState.value.error,
            isLoading = mediaState.value.error.isEmpty(),
        )
    } else mediaState.value,
    mediaState.value,
    timelineFilter,
    albumSourceIdsById,
    dateFormats,
    groupByMonth,
    groupByYear,
    groupSimilarMedia,
    enabledGroupTypes,
    albumId,
) {
    val source = mediaState.value
    if (!timelineFilter.isActive) {
        value = source
        return@produceState
    }
    val selectedAlbumSourceIds = resolveTimelineAlbumSourceIds(
        selectedAlbumIds = timelineFilter.selectedAlbumIds,
        sourceIdsByAlbumId = albumSourceIdsById,
    )
    val filtered = filterTimelineItems(
        items = source.media,
        filter = timelineFilter,
        selectedAlbumSourceIds = selectedAlbumSourceIds,
        isImage = { it.isImage },
        isVideo = { it.isVideo },
        isFavorite = { it.isFavorite },
        timestampSeconds = { it.timestampFor(source.dateSource) },
        albumId = { it.albumID },
    )
    val filteredIds = filtered.mapTo(HashSet(filtered.size)) { it.id }
    value = mapMediaToItem(
        data = filtered,
        error = source.error,
        albumId = albumId,
        groupByMonth = groupByMonth,
        groupByYear = groupByYear,
        groupSimilarMedia = groupSimilarMedia,
        enabledGroupTypes = enabledGroupTypes,
        cloudBackups = source.cloudBackups.filterKeys { it in filteredIds },
        defaultDateFormat = dateFormats.first,
        extendedDateFormat = dateFormats.second,
        weeklyDateFormat = dateFormats.third,
        dateSource = source.dateSource,
    ).copy(isLoading = source.isLoading)
}
