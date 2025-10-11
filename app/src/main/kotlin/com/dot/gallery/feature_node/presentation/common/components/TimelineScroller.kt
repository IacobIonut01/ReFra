package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings.Misc.rememberDefaultDateFormat
import com.dot.gallery.core.Settings.Misc.rememberExtendedDateFormat
import com.dot.gallery.core.Settings.Misc.rememberWeeklyDateFormat
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.getDate
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarLayoutSide
import my.nanihadesuka.compose.ScrollbarSelectionActionable
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun <T : Media> rememberScrollbarSettings(
    headers: SnapshotStateList<MediaItem.Header<T>>,
): ScrollbarSettings {
    val enabled by remember(headers) { derivedStateOf { headers.size > 3 } }
    return remember(headers, enabled) {
        ScrollbarSettings.Default.copy(
            enabled = enabled,
            side = ScrollbarLayoutSide.End,
            selectionMode = ScrollbarSelectionMode.Thumb,
            selectionActionable = ScrollbarSelectionActionable.WhenVisible,
            scrollbarPadding = 0.dp,
            thumbThickness = 24.dp,
            thumbUnselectedColor = Color.Transparent,
            thumbSelectedColor = Color.Transparent,
            hideDisplacement = 0.dp
        )
    }
}

@Composable
fun <T : Media> TimelineScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    mappedData: SnapshotStateList<MediaItem<T>>,
    headers: SnapshotStateList<MediaItem.Header<T>>,
    settings: ScrollbarSettings = rememberScrollbarSettings(headers),
    content: @Composable () -> Unit
) {
    if (!settings.enabled) content()
    else Box {
        content()
        InternalLazyVerticalGridScrollbar(
            state = state,
            settings = settings,
            modifier = modifier,
            indicatorContent = { index, isSelected ->
                val stringToday = stringResource(R.string.header_today)
                val stringYesterday = stringResource(R.string.header_yesterday)
                val defaultDateFormat by rememberDefaultDateFormat()
                val weeklyDateFormat by rememberWeeklyDateFormat()
                val extendedDateFormat by rememberExtendedDateFormat()

                val firstItemIndexOnScreen by rememberedDerivedState {
                    state.firstVisibleItemIndex
                }

                // Nearest previous (or if none, next) header lookup.
                val currentDate by rememberedDerivedState(mappedData, firstItemIndexOnScreen) {
                    // 1. Try walk backwards to find the most recent header.
                    var i = firstItemIndexOnScreen
                    while (i >= 0) {
                        when (val item = mappedData.getOrNull(i)) {
                            is MediaItem.Header<*> -> return@rememberedDerivedState item.text
                            else -> { /* Ignore other item types. */ }
                        }
                        i--
                    }
                    // 2. If no previous header, search forward (rare edge case).
                    var j = firstItemIndexOnScreen + 1
                    while (j < mappedData.size) {
                        when (val item = mappedData.getOrNull(j)) {
                            is MediaItem.Header<*> -> return@rememberedDerivedState item.text
                            else -> { /* Ignore other item types. */ }
                        }
                        j++
                    }
                    // 3. Fallback: format the first visible media item if present.
                    val fallback = mappedData.getOrNull(firstItemIndexOnScreen)
                    when (fallback) {
                        is MediaItem.MediaViewItem -> fallback.media.timestamp.getDate(
                            format = defaultDateFormat,
                            weeklyFormat = weeklyDateFormat,
                            extendedFormat = extendedDateFormat,
                            stringToday = stringToday,
                            stringYesterday = stringYesterday
                        )
                        is MediaItem.Header<*> -> fallback.text
                        else -> null
                    }
                }
                val isScrolling by rememberedDerivedState(state) { state.isScrollInProgress }
                val offset by animateDpAsState(
                    targetValue = if (isScrolling || isSelected) 24.dp else 72.dp,
                    label = "thumbOffset"
                )
                Row(
                    modifier = Modifier
                        .offset {
                            IntOffset(offset.roundToPx(), 0)
                        }
                        .zIndex(5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !currentDate.isNullOrEmpty() && isSelected,
                        enter = enterAnimation(250),
                        exit = exitAnimation(1000)
                    ) {
                        Text(
                            text = currentDate.toString(),
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
