/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.header_today
import com.dot.gallery.core.ui.generated.resources.header_yesterday
import com.dot.gallery.core.ui.generated.resources.timeline_month_photos
import com.dot.gallery.core.ui.generated.resources.timeline_month_videos
import com.dot.gallery.core.ui.generated.resources.timeline_open_months
import com.dot.gallery.core.ui.generated.resources.timeline_open_place
import com.dot.gallery.core.ui.generated.resources.timeline_places_and_more
import com.dot.gallery.core.ui.generated.resources.timeline_places_two
import com.dot.gallery.core.ui.generated.resources.timeline_play_month_recap
import com.dot.gallery.core.ui.generated.resources.timeline_select_month
import com.dot.gallery.core.ui.generated.resources.timeline_summary_separator
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.domain.date.todayLocalDate
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.model.isBigHeaderKey
import com.dot.gallery.domain.util.getUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Text and icons drawn on photos read the same in every theme. */
private val OnMedia = Color.White
private val MediaScrim = Color.Black.copy(alpha = 0.62f)
private val MediaBadge = Color.Black.copy(alpha = 0.4f)

/** Cache key matching the grid cells', so card thumbnails reuse their decoded bitmaps. */
fun Media.thumbnailSignature(): String =
    "$id:$definedTimestamp:$size:$mimeType:$label:$path"

/** "248 photos · 12 videos", leaving out a kind the month has none of. */
@Composable
fun monthSummaryText(month: TimelineMonth): String {
    val photos = pluralStringResource(Res.plurals.timeline_month_photos, month.photoCount, month.photoCount)
    val videos = pluralStringResource(Res.plurals.timeline_month_videos, month.videoCount, month.videoCount)
    return when {
        month.videoCount == 0 -> photos
        month.photoCount == 0 -> videos
        else -> stringResource(Res.string.timeline_summary_separator, photos, videos)
    }
}

/** "Lisbon", "Lisbon, Sintra" or "Lisbon, Sintra and 2 more". */
@Composable
fun placesText(places: List<TimelinePlace>): String? = when (places.size) {
    0 -> null
    1 -> places[0].city
    2 -> stringResource(Res.string.timeline_places_two, places[0].city, places[1].city)
    else -> pluralStringResource(
        Res.plurals.timeline_places_and_more,
        places.size - 2,
        places.size - 2,
        places[0].city,
        places[1].city,
    )
}

/**
 * A grid header on any media grid. On the main timeline (with [LocalTimelineDecorations]) month
 * headers become a [TimelineMonthCard] or a [TimelineMonthCompactHeader] and day headers name
 * their place; elsewhere it is the plain [MediaItemHeader].
 */
@Composable
fun <T : Media> TimelineHeaderItem(
    header: MediaItem.Header<T>,
    mediaState: State<MediaState<T>>,
    selectedMedia: State<Set<Long>>,
    isSelectionActive: Boolean,
    allowSelection: Boolean,
    bigHeaders: Boolean,
    onMediaClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selector = LocalMediaSelector.current
    val feedbackManager = rememberFeedbackManager()
    val scope = rememberCoroutineScope()
    val decorations = LocalTimelineDecorations.current
    val month = remember(header.key, decorations?.index) {
        if (header.key.isBigHeaderKey) decorations?.index?.monthForHeader(header.key) else null
    }
    val selectionIds = month?.mediaIds ?: header.data
    var isChecked by rememberSaveable(header.key) { mutableStateOf(false) }
    if (allowSelection) {
        LaunchedEffect(isSelectionActive) {
            isChecked = isChecked && isSelectionActive
        }
        LaunchedEffect(selectedMedia.value.size, selectionIds) {
            isChecked = withContext(Dispatchers.Default) {
                val present = mediaState.value.media.filter { it.id in selectionIds }
                present.isNotEmpty() && present.all { it.id in selectedMedia.value }
            }
        }
    }
    val toggleSelection: () -> Unit = {
        if (allowSelection) {
            feedbackManager.vibrate()
            scope.launch {
                val select = !isChecked
                isChecked = select
                val ids = mediaState.value.media.map { it.id }.filter { it in selectionIds }
                if (select) selector.addToSelection(ids) else selector.removeFromSelection(ids)
            }
        }
    }
    val onOpenPlace = decorations?.onOpenPlace?.takeIf { decorations.headerPlaces }

    if (month != null && decorations != null) {
        val title = month.title(todayLocalDate().year)
        if (decorations.monthHighlights && month.itemCount >= MONTH_HIGHLIGHT_MIN_ITEMS &&
            month.highlights.isNotEmpty()
        ) {
            TimelineMonthCard(
                month = month,
                title = title,
                showPlaces = decorations.headerPlaces,
                selectionActive = isSelectionActive && allowSelection,
                isChecked = isChecked,
                onToggleSelection = toggleSelection.takeIf { allowSelection },
                onOpenMedia = { media ->
                    @Suppress("UNCHECKED_CAST")
                    onMediaClick(media as T)
                },
                onPlay = decorations.onPlayMonth?.takeIf { decorations.monthRecaps }?.let { play -> { play(month) } },
                onPlaceClick = onOpenPlace,
                modifier = modifier,
            )
        } else {
            TimelineMonthCompactHeader(
                month = month,
                title = title,
                bigHeaderOnly = bigHeaders,
                showPlaces = decorations.headerPlaces,
                onPlaceClick = onOpenPlace,
                modifier = modifier,
            )
        }
        return
    }

    val stringToday = stringResource(Res.string.header_today)
    val stringYesterday = stringResource(Res.string.header_yesterday)
    val showAsBig = header.key.isBigHeaderKey || bigHeaders
    val place = remember(header.key, decorations) {
        if (decorations?.headerPlaces == true && !showAsBig) decorations.index.placeForHeader(header.key) else null
    }
    val checkedState = remember { mutableStateOf(false) }.apply { value = isChecked }
    MediaItemHeader(
        modifier = modifier,
        date = remember(header.text, stringToday, stringYesterday) {
            header.text
                .replace("Today", stringToday)
                .replace("Yesterday", stringYesterday)
        },
        showAsBig = showAsBig,
        bigHeaderOnly = bigHeaders,
        isChecked = checkedState,
        place = place?.city,
        onPlaceClick = if (place != null && onOpenPlace != null) {
            { onOpenPlace(place) }
        } else null,
        onChecked = toggleSelection,
    )
}

/**
 * A month header with a hero photo and two highlights, the month's counts and places, and a recap
 * button. A long press selects the whole month; while selecting, a tap toggles it.
 */
@Composable
fun TimelineMonthCard(
    month: TimelineMonth,
    title: String,
    showPlaces: Boolean,
    selectionActive: Boolean,
    isChecked: Boolean,
    onToggleSelection: (() -> Unit)?,
    onOpenMedia: (Media) -> Unit,
    onPlay: (() -> Unit)?,
    onPlaceClick: ((TimelinePlace) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val selected = selectionActive && isChecked
    val cardScale by animateFloatAsState(if (selected) 0.95f else 1f, label = "monthCardScale")
    val cardCorner by animateDpAsState(if (selected) 32.dp else 28.dp, label = "monthCardCorner")
    val borderWidth by animateDpAsState(if (selected) 3.dp else 0.dp, label = "monthCardBorder")
    val shape = RoundedCornerShape(cardCorner)
    val renderer = LocalMediaImageRenderer.current
    val selectLabel = stringResource(Res.string.timeline_select_month, title)

    @Composable
    fun Highlight(media: Media, modifier: Modifier) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .combinedClickable(
                    onClickLabel = media.label,
                    onLongClickLabel = selectLabel,
                    onClick = { if (selectionActive) onToggleSelection?.invoke() else onOpenMedia(media) },
                    onLongClick = onToggleSelection,
                )
        ) {
            renderer.RenderImage(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                model = media.getUri(),
                contentScale = ContentScale.Crop,
                contentDescription = media.label,
                signature = media.thumbnailSignature(),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .aspectRatio(358f / 236f)
                .scale(cardScale)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(borderWidth, MaterialTheme.colorScheme.primary, shape),
        ) {
            val highlights = month.highlights
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Highlight(highlights[0], Modifier.weight(2f))
                if (highlights.size > 1) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Highlight(highlights[1], Modifier.fillMaxWidth().weight(1f))
                        if (highlights.size > 2) {
                            Highlight(highlights[2], Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.64f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MediaScrim)))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = OnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = monthSummaryText(month),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnMedia.copy(alpha = 0.85f),
                    maxLines = 1,
                )
                val places = if (showPlaces) placesText(month.places) else null
                if (places != null) {
                    val top = month.places.first()
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (onPlaceClick != null && !selectionActive) Modifier.clickable(
                                    onClickLabel = stringResource(Res.string.timeline_open_place, top.city),
                                ) { onPlaceClick(top) } else Modifier
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = OnMedia,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = places,
                            style = MaterialTheme.typography.labelLarge,
                            color = OnMedia,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = onPlay != null && !selectionActive,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MediaBadge)
                        .clickable(onClickLabel = stringResource(Res.string.timeline_play_month_recap, title)) {
                            onPlay?.invoke()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(Res.string.timeline_play_month_recap, title),
                        tint = OnMedia,
                    )
                }
            }
            AnimatedVisibility(
                visible = selectionActive,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MediaBadge)
                        .clickable(onClickLabel = selectLabel) { onToggleSelection?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isChecked) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(OnMedia))
                    }
                    Icon(
                        imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = selectLabel,
                        tint = if (isChecked) MaterialTheme.colorScheme.primary else OnMedia,
                    )
                }
            }
        }
    }
}

/**
 * The month header for small months, or with highlights turned off: the month name as before,
 * with a line of counts and places under it.
 */
@Composable
fun TimelineMonthCompactHeader(
    month: TimelineMonth,
    title: String,
    bigHeaderOnly: Boolean,
    showPlaces: Boolean,
    onPlaceClick: ((TimelinePlace) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 80.dp, bottom = if (bigHeaderOnly) 16.dp else 0.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthSummaryText(month),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val places = if (showPlaces) placesText(month.places) else null
            if (places != null) {
                val top = month.places.first()
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (onPlaceClick != null) Modifier.clickable(
                                onClickLabel = stringResource(Res.string.timeline_open_place, top.city),
                            ) { onPlaceClick(top) } else Modifier
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = places,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The timeline's sticky date as a floating pill. It hides while the scroller is held, since the
 * scroller shows the month itself, and a tap opens the months view.
 */
@Composable
fun TimelineFloatingDate(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp,
    decorations: TimelineDecorations,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
) {
    val onClick = decorations.onOpenMonths
    // A month header near the top names the month itself, and the sticky text still holds the
    // day before it, so the pill steps aside until the header scrolls away.
    val monthHeaderOnTop by remember(gridState) {
        androidx.compose.runtime.derivedStateOf {
            val info = gridState.layoutInfo
            val limit = info.viewportStartOffset + info.viewportSize.height / 3
            info.visibleItemsInfo.any { it.key.isBigHeaderKey && it.offset.y < limit }
        }
    }
    AnimatedVisibility(
        visible = !decorations.signals.isScrubbing && text.isNotEmpty() && !monthHeaderOnTop,
        modifier = modifier.fillMaxWidth().padding(top = topPadding + 12.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (onClick != null) Modifier.clickable(
                            onClickLabel = stringResource(Res.string.timeline_open_months),
                            onClick = onClick,
                        ) else Modifier
                    )
                    .padding(start = 16.dp, end = if (onClick != null) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
