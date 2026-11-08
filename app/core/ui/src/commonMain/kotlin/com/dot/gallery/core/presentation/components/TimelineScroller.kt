package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.timeline_month_items
import com.dot.gallery.core.ui.generated.resources.timeline_summary_separator
import com.dot.gallery.domain.util.getUri
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.zIndex
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MosaicDisplayItem
import com.dot.gallery.domain.model.TimelineDateSource
import com.dot.gallery.domain.model.timestampFor
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.scrollbar.InternalLazyVerticalGridScrollbar
import com.dot.gallery.scrollbar.ScrollbarLayoutSide
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.date.todayLocalDate
import org.jetbrains.compose.resources.painterResource
import com.dot.gallery.scrollbar.ScrollbarSelectionActionable
import com.dot.gallery.scrollbar.ScrollbarSelectionMode
import com.dot.gallery.scrollbar.ScrollbarSettings
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.ic_scroll_arrow



/**
 * Precomputed, binary-searchable mapping from a [androidx.compose.foundation.lazy.grid.LazyGridState]
 * item index to a human readable timeline label (month, plus year for past years).
 *
 * Built once per [mappedData] change instead of walking the list / formatting dates every
 * frame, which is what made the old indicator stutter during fast scroll.
 */
@Stable
class MonthSegments (
    private val starts: IntArray,
    private val labels: Array<String>,
    /** `year * 100 + month` of each segment (see [monthKeyOf]). */
    private val keys: IntArray = IntArray(starts.size),
) {
    /** Item indices where each month begins; the scrollbar uses these to snap drags to months. */
    val snapIndices: IntArray get() = starts

    fun labelAt(index: Int): String? = segmentAt(index)?.let { labels[it] }

    fun keyAt(index: Int): Int? = segmentAt(index)?.let { keys[it] }

    /** The item index a month starts at, or null when the month isn't in this grid. */
    fun startOf(monthKey: Int): Int? {
        val segment = keys.indexOf(monthKey)
        return if (segment >= 0) starts[segment] else null
    }

    /** Each year with the item index of its first month, in grid order. */
    fun yearStarts(): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var lastYear = Int.MIN_VALUE
        for (i in keys.indices) {
            val year = keys[i] / 100
            if (year != lastYear) {
                lastYear = year
                result.add(year to starts[i])
            }
        }
        return result
    }

    private fun segmentAt(index: Int): Int? {
        if (starts.isEmpty()) return null
        var lo = 0
        var hi = starts.size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= index) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}

/**
 * Builds month segments over an abstract list of grid entries, in the grid's own
 * item-index space. [leadingItemCount] accounts for any non-timeline items rendered
 * before the timeline entries (e.g. the `aboveGrid` "What's New" card), so the snap
 * indices and label lookups line up with [LazyGridState.firstVisibleItemIndex] and
 * [LazyGridState.scrollToItem].
 *
 * @param count number of timeline entries (excluding [leadingItemCount]).
 * @param isHeader whether the entry at the local index is a date/section header.
 * @param timestampSecAt representative timestamp (seconds) of a non-header entry.
 */
private inline fun buildMonthSegments(
    count: Int,
    leadingItemCount: Int,
    isHeader: (Int) -> Boolean,
    timestampSecAt: (Int) -> Long,
): MonthSegments {
    val starts = ArrayList<Int>()
    val labels = ArrayList<String>()
    val keys = ArrayList<Int>()
    var lastMonthKey = Int.MIN_VALUE
    var lastHeaderIndex = -1
    var prevWasHeader = false
    var lastStartLocal = -1
    for (i in 0 until count) {
        if (isHeader(i)) {
            // Anchor to the first header of a consecutive run, so a big month divider
            // (which precedes the day header) becomes the snap target rather than the day header.
            if (!prevWasHeader) lastHeaderIndex = i
            prevWasHeader = true
            continue
        }
        prevWasHeader = false
        val timestamp = timestampSecAt(i)
        val date = timestamp.epochSecToLocalDate()
        val key = date.year * 100 + date.monthNumber
        if (key != lastMonthKey) {
            lastMonthKey = key
            val label = "${timestamp.formatEpochSec("LLLL")} ${date.year}"
            // Anchor the segment to the header introducing the month when present,
            // so the label flips exactly as the month divider scrolls into view.
            val startLocal = if (lastHeaderIndex > lastStartLocal) lastHeaderIndex else i
            lastStartLocal = startLocal
            starts.add(startLocal + leadingItemCount)
            labels.add(label)
            keys.add(key)
        }
    }
    return MonthSegments(starts.toIntArray(), labels.toTypedArray(), keys.toIntArray())
}

/**
 * Month segments for a plain (1:1) grid whose items map directly to [mappedData].
 */
@Composable
fun <T : Media> rememberMonthSegments(
    mappedData: List<MediaItem<T>>,
    leadingItemCount: Int = 0,
    dateSource: TimelineDateSource = TimelineDateSource.CAPTURE_TIME,
): MonthSegments {
    return remember(mappedData, leadingItemCount, dateSource) {
        buildMonthSegments(
            count = mappedData.size,
            leadingItemCount = leadingItemCount,
            isHeader = { mappedData[it] is MediaItem.Header<*> },
            timestampSecAt = {
                (mappedData[it] as MediaItem.MediaViewItem<T>).media.timestampFor(dateSource)
            },
        )
    }
}

/**
 * Month segments for the mosaic grid, whose [androidx.compose.foundation.lazy.grid.LazyGridState]
 * indexes the *combined* mosaic tiles rather than [mappedData]. The segments must live in that
 * tile-index space, otherwise the indicator label and the visible content diverge (the tiles
 * collapse several media into one grid item, so the two index spaces have different scales).
 */
@Composable
fun <T : Media> rememberMosaicMonthSegments(
    mappedData: List<MediaItem<T>>,
    columns: Int,
    allowHeaders: Boolean,
    leadingItemCount: Int = 0,
    dateSource: TimelineDateSource = TimelineDateSource.CAPTURE_TIME,
): MonthSegments {
    val displayItems = remember(mappedData, allowHeaders, columns) {
        val items = if (allowHeaders) mappedData
        else mappedData.fastFilter { it is MediaItem.MediaViewItem<*> }
        buildMosaicDisplayItems(items, columns)
    }
    return remember(displayItems, leadingItemCount, dateSource) {
        buildMonthSegments(
            count = displayItems.size,
            leadingItemCount = leadingItemCount,
            isHeader = { displayItems[it] is MosaicDisplayItem.HeaderItem },
            timestampSecAt = { idx ->
                when (val item = displayItems[idx]) {
                    is MosaicDisplayItem.BigTileItem -> item.mediaItem.media.timestampFor(dateSource)
                    is MosaicDisplayItem.QuadTileItem -> item.mediaItems.first().media.timestampFor(dateSource)
                    is MosaicDisplayItem.PairTileItem -> item.mediaItems.first().media.timestampFor(dateSource)
                    is MosaicDisplayItem.SingleItem -> item.mediaItem.media.timestampFor(dateSource)
                    is MosaicDisplayItem.HeaderItem -> 0L
                }
            },
        )
    }
}

fun timelineScrollbarSettings(enabled: Boolean): ScrollbarSettings =
    ScrollbarSettings.Default.copy(
        enabled = enabled,
        side = ScrollbarLayoutSide.End,
        selectionMode = ScrollbarSelectionMode.Thumb,
        selectionActionable = ScrollbarSelectionActionable.WhenVisible,
        scrollbarPadding = 0.dp,
        indicatorOffset = 24.dp,
        thumbThickness = 24.dp,
        thumbUnselectedColor = Color.Transparent,
        thumbSelectedColor = Color.Transparent,
        hideDelayMillis = 2_000,
        hideDisplacement = 48.dp
    )

@Composable
fun <T : Media> rememberScrollbarSettings(
    headers: List<MediaItem.Header<T>>,
): ScrollbarSettings {
    val enabled by remember(headers) { derivedStateOf { headers.size > 3 } }
    return remember(headers, enabled) { timelineScrollbarSettings(enabled) }
}

@Composable
fun <T : Media> TimelineScroller(
    state: LazyGridState,
    segments: MonthSegments,
    modifier: Modifier = Modifier,
    headers: List<MediaItem.Header<T>>,
    settings: ScrollbarSettings = rememberScrollbarSettings(headers),
    snapScrollOffset: Int = 0,
    content: @Composable () -> Unit
) {
    val decorations = LocalTimelineDecorations.current
    val signals = decorations?.signals
    val pendingJump = signals?.pendingMonthJump
    LaunchedEffect(pendingJump, segments) {
        val key = pendingJump ?: return@LaunchedEffect
        if (segments.snapIndices.isEmpty()) return@LaunchedEffect
        segments.startOf(key)?.let { state.scrollToItem(it) }
        signals.pendingMonthJump = null
    }
    if (!settings.enabled) content()
    else Box {
        content()
        val feedbackManager = rememberFeedbackManager()
        var held by remember { mutableStateOf(false) }
        DisposableEffect(signals) {
            onDispose { signals?.isScrubbing = false }
        }
        val yearStarts = remember(segments) { segments.yearStarts() }
        AnimatedVisibility(
            visible = held && yearStarts.size > 1,
            modifier = Modifier.matchParentSize().then(modifier),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ScrollerYearRail(yearStarts = yearStarts, totalItems = { state.layoutInfo.totalItemsCount })
        }
        InternalLazyVerticalGridScrollbar(
            state = state,
            settings = settings,
            modifier = modifier,
            snapIndices = segments.snapIndices,
            snapScrollOffset = snapScrollOffset,
            onSnap = { feedbackManager.vibrate() },
            indicatorContent = { index, isSelected ->
                LaunchedEffect(isSelected) {
                    held = isSelected
                    signals?.isScrubbing = isSelected
                }
                // Resolved via a precomputed binary-searchable segment table, so there is no
                // per-frame list walk or date formatting during fast scroll.
                val currentLabel = remember(index, segments) { segments.labelAt(index) }
                val month = remember(index, segments, decorations?.index) {
                    segments.keyAt(index)?.let { decorations?.index?.month(it) }
                }
                Row(
                    modifier = Modifier.zIndex(5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = !currentLabel.isNullOrEmpty() && isSelected,
                        enter = enterAnimation(250),
                        exit = exitAnimation(1000)
                    ) {
                        ScrubLabel(label = currentLabel.orEmpty(), month = month)
                    }
                    ScrollerHandle(held = isSelected)
                }
            }
        )
    }
}

/** The scroller's grip: a tonal half pill at rest, primary and larger while held. */
@Composable
private fun ScrollerHandle(held: Boolean) {
    val width by animateDpAsState(if (held) 48.dp else 40.dp, label = "scrollerHandleWidth")
    val height by animateDpAsState(if (held) 64.dp else 56.dp, label = "scrollerHandleHeight")
    val container by animateColorAsState(
        if (held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "scrollerHandleContainer",
    )
    val tint by animateColorAsState(
        if (held) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "scrollerHandleTint",
    )
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
            .background(container)
            .padding(start = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_scroll_arrow),
            contentDescription = null,
            tint = tint
        )
    }
}

/** "October 2026", with the month's highlight and item count when the timeline knows them. */
@Composable
private fun ScrubLabel(label: String, month: TimelineMonth?) {
    val hero = month?.highlights?.firstOrNull()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = if (hero != null) 6.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hero != null) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                model = hero.getUri(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                signature = hero.thumbnailSignature(),
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            if (month != null) {
                val items = pluralStringResource(Res.plurals.timeline_month_items, month.itemCount, month.itemCount)
                val place = month.places.firstOrNull()?.city
                Text(
                    text = if (place != null) stringResource(Res.string.timeline_summary_separator, items, place) else items,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The track and year chips shown while the handle is held. Each year sits where its first month
 * starts, as a share of the grid's items, which is how the handle maps a drag to the grid.
 */
@Composable
private fun ScrollerYearRail(yearStarts: List<Pair<Int, Int>>, totalItems: () -> Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val chipHeight = 24.dp
        val available = maxHeight - chipHeight
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        val total = totalItems().coerceAtLeast(1)
        // Years whose starts sit closer than a chip's height would overlap; the newer one stays.
        var lastOffset = -chipHeight
        yearStarts.forEach { (year, start) ->
            val offset = available * (start.toFloat() / total).coerceIn(0f, 1f)
            if (offset < lastOffset + chipHeight + 4.dp) return@forEach
            lastOffset = offset
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = offset)
                    .padding(end = 14.dp)
                    .height(chipHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
