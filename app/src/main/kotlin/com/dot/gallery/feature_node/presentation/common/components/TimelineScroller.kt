package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.zIndex
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MosaicDisplayItem
import com.dot.gallery.feature_node.presentation.util.getCurrentAndroid
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import com.dot.gallery.scrollbar.InternalLazyVerticalGridScrollbar
import com.dot.gallery.scrollbar.ScrollbarLayoutSide
import com.dot.gallery.scrollbar.ScrollbarSelectionActionable
import com.dot.gallery.scrollbar.ScrollbarSelectionMode
import com.dot.gallery.scrollbar.ScrollbarSettings
import java.util.Calendar

/**
 * Precomputed, binary-searchable mapping from a [androidx.compose.foundation.lazy.grid.LazyGridState]
 * item index to a human readable timeline label (month, plus year for past years).
 *
 * Built once per [mappedData] change instead of walking the list / formatting dates every
 * frame, which is what made the old indicator stutter during fast scroll.
 */
@Stable
class MonthSegments internal constructor(
    private val starts: IntArray,
    private val labels: Array<String>,
) {
    /** Item indices where each month begins; the scrollbar uses these to snap drags to months. */
    val snapIndices: IntArray get() = starts

    fun labelAt(index: Int): String? {
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
        return labels[result]
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
    val locale = ComposeLocale.getCurrentAndroid()
    val cal = Calendar.getInstance(locale)
    val currentYear = Calendar.getInstance(locale).get(Calendar.YEAR)
    val starts = ArrayList<Int>()
    val labels = ArrayList<String>()
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
        cal.timeInMillis = timestampSecAt(i) * 1000L
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val key = year * 100 + month
        if (key != lastMonthKey) {
            lastMonthKey = key
            val monthName = cal.getDisplayName(
                Calendar.MONTH, Calendar.LONG_FORMAT, locale
            ).orEmpty()
            val label = if (year != currentYear) "$monthName $year" else monthName
            // Anchor the segment to the header introducing the month when present,
            // so the label flips exactly as the month divider scrolls into view.
            val startLocal = if (lastHeaderIndex > lastStartLocal) lastHeaderIndex else i
            lastStartLocal = startLocal
            starts.add(startLocal + leadingItemCount)
            labels.add(label)
        }
    }
    return MonthSegments(starts.toIntArray(), labels.toTypedArray())
}

/**
 * Month segments for a plain (1:1) grid whose items map directly to [mappedData].
 */
@Composable
fun <T : Media> rememberMonthSegments(
    mappedData: List<MediaItem<T>>,
    leadingItemCount: Int = 0,
): MonthSegments {
    return remember(mappedData, leadingItemCount) {
        buildMonthSegments(
            count = mappedData.size,
            leadingItemCount = leadingItemCount,
            isHeader = { mappedData[it] is MediaItem.Header<*> },
            timestampSecAt = {
                (mappedData[it] as MediaItem.MediaViewItem<T>).media.definedTimestamp
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
): MonthSegments {
    val displayItems = remember(mappedData, allowHeaders, columns) {
        val items = if (allowHeaders) mappedData
        else mappedData.fastFilter { it is MediaItem.MediaViewItem<*> }
        buildMosaicDisplayItems(items, columns)
    }
    return remember(displayItems, leadingItemCount) {
        buildMonthSegments(
            count = displayItems.size,
            leadingItemCount = leadingItemCount,
            isHeader = { displayItems[it] is MosaicDisplayItem.HeaderItem },
            timestampSecAt = { idx ->
                when (val item = displayItems[idx]) {
                    is MosaicDisplayItem.BigTileItem -> item.mediaItem.media.definedTimestamp
                    is MosaicDisplayItem.QuadTileItem -> item.mediaItems.first().media.definedTimestamp
                    is MosaicDisplayItem.PairTileItem -> item.mediaItems.first().media.definedTimestamp
                    is MosaicDisplayItem.SingleItem -> item.mediaItem.media.definedTimestamp
                    is MosaicDisplayItem.HeaderItem -> 0L
                }
            },
        )
    }
}

internal fun timelineScrollbarSettings(enabled: Boolean): ScrollbarSettings =
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
    if (!settings.enabled) content()
    else Box {
        content()
        val feedbackManager = rememberFeedbackManager()
        InternalLazyVerticalGridScrollbar(
            state = state,
            settings = settings,
            modifier = modifier,
            snapIndices = segments.snapIndices,
            snapScrollOffset = snapScrollOffset,
            onSnap = { feedbackManager.vibrate() },
            indicatorContent = { index, isSelected ->
                // Immich-style label: month name, with the year appended for past years.
                // Resolved via a precomputed binary-searchable segment table, so there is no
                // per-frame list walk or date formatting during fast scroll.
                val currentLabel = remember(index, segments) { segments.labelAt(index) }
                Row(
                    modifier = Modifier.zIndex(5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = !currentLabel.isNullOrEmpty() && isSelected,
                        enter = enterAnimation(250),
                        exit = exitAnimation(1000)
                    ) {
                        Text(
                            text = currentLabel.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(100)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = RoundedCornerShape(
                                    topStartPercent = 100,
                                    bottomStartPercent = 100
                                )
                            )
                            .padding(vertical = 2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_scroll_arrow),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
        )
    }
}
