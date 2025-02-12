/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RepeatOnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                action()
            }
        }
    }
}

fun <T> MutableState<T>.update(newState: T) {
    value = newState
}

fun MediaRepository.mediaFlowWithType(
    albumId: Long,
    allowedMedia: AllowedMedia
): Flow<Resource<List<UriMedia>>> =
    (if (albumId != -1L) {
        getMediaByAlbumIdWithType(albumId, allowedMedia)
    } else {
        getMediaByType(allowedMedia)
    }).flowOn(Dispatchers.IO).conflate()

fun MediaRepository.mediaFlow(albumId: Long, target: String?): Flow<Resource<List<UriMedia>>> =
    (if (albumId != -1L) {
        getMediaByAlbumId(albumId)
    } else if (!target.isNullOrEmpty()) {
        when (target) {
            Constants.Target.TARGET_FAVORITES -> getFavorites(mediaOrder = MediaOrder.Default)
            Constants.Target.TARGET_TRASH -> getTrashed()
            else -> getMedia()
        }
    } else {
        getMedia()
    })

fun <T : Media> Flow<Resource<List<T>>>.mapMedia(
    albumId: Long,
    groupByMonth: Boolean = false,
    withMonthHeader: Boolean = true,
    updateDatabase: () -> Unit,
    defaultDateFormat: String,
    extendedDateFormat: String,
    weeklyDateFormat: String
) = map {
    updateDatabase()
    mapMediaToItem(
        data = it.data ?: emptyList(),
        error = it.message ?: "",
        albumId = albumId,
        groupByMonth = groupByMonth,
        withMonthHeader = withMonthHeader,
        defaultDateFormat = defaultDateFormat,
        extendedDateFormat = extendedDateFormat,
        weeklyDateFormat = weeklyDateFormat
    )
}

suspend fun <T : Media> MutableStateFlow<MediaState<T>>.collectMedia(
    data: List<T>,
    error: String,
    albumId: Long,
    groupByMonth: Boolean = false,
    withMonthHeader: Boolean = true,
    defaultDateFormat: String,
    extendedDateFormat: String,
    weeklyDateFormat: String
) = withContext(Dispatchers.IO) {
    emit(
        mapMediaToItem(
            data = data,
            error = error,
            albumId = albumId,
            groupByMonth = groupByMonth,
            withMonthHeader = withMonthHeader,
            defaultDateFormat = defaultDateFormat,
            extendedDateFormat = extendedDateFormat,
            weeklyDateFormat = weeklyDateFormat
        )
    )
}

suspend fun <T : Media> mapMediaToItem(
    data: List<T>,
    error: String,
    albumId: Long,
    groupByMonth: Boolean = false,
    withMonthHeader: Boolean = true,
    defaultDateFormat: String,
    extendedDateFormat: String,
    weeklyDateFormat: String
) = withContext(Dispatchers.IO) {
    val mappedData = mutableListOf<MediaItem<T>>()
    val mappedDataWithMonthly = mutableListOf<MediaItem<T>>()
    val monthHeaderList: MutableSet<String> = mutableSetOf()
    val headers = mutableListOf<MediaItem.Header<T>>()

    val groupedData = data.groupBy {
        if (groupByMonth) {
            it.definedTimestamp.getMonth()
        } else {
            it.definedTimestamp.getDate(
                /** Localized in composition */
                stringToday = "Today",
                stringYesterday = "Yesterday",
                format = defaultDateFormat,
                extendedFormat = extendedDateFormat,
                weeklyFormat = weeklyDateFormat
            )
        }
    }
    groupedData.forEach { (date, data) ->
        val dateHeader = MediaItem.Header<T>("header_$date", date, data.map { it.id }.toSet())
        headers.add(dateHeader)
        val groupedMedia = data.map {
            MediaItem.MediaViewItem("media_${it.id}_${it.label}", it)
        }
        if (groupByMonth) {
            mappedData.add(dateHeader)
            mappedData.addAll(groupedMedia)
            mappedDataWithMonthly.add(dateHeader)
            mappedDataWithMonthly.addAll(groupedMedia)
        } else {
            val month = getMonth(
                defaultFormat = defaultDateFormat,
                extendedFormat = extendedDateFormat,
                date = date
            )
            if (month.isNotEmpty() && !monthHeaderList.contains(month)) {
                monthHeaderList.add(month)
                if (withMonthHeader && mappedDataWithMonthly.isNotEmpty()) {
                    mappedDataWithMonthly.add(
                        MediaItem.Header(
                            "header_big_${month}_${data.size}",
                            month,
                            data.map { it.id }.toSet()
                        )
                    )
                }
            }
            mappedData.add(dateHeader)
            if (withMonthHeader) {
                mappedDataWithMonthly.add(dateHeader)
            }
            mappedData.addAll(groupedMedia)
            if (withMonthHeader) {
                mappedDataWithMonthly.addAll(groupedMedia)
            }
        }
    }
    MediaState(
        isLoading = false,
        error = error,
        media = data,
        headers = headers,
        mappedMedia = mappedData,
        mappedMediaWithMonthly = if (withMonthHeader) mappedDataWithMonthly else emptyList(),
        dateHeader = data.dateHeader(albumId)
    )
}

private fun List<Media>.dateHeader(albumId: Long): String =
    if (albumId != -1L && isNotEmpty()) {
        val startDate: DateExt = last().definedTimestamp.getDateExt()
        val endDate: DateExt = first().definedTimestamp.getDateExt()
        getDateHeader(startDate, endDate)
    } else ""
