/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.domain.date.epochSecToLocalDate
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.ui.theme.GalleryChrome
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.LocalHazePerformanceMode

/**
 * Header of a detail page (place, category, album, collection, album group): a 120 dp cover
 * with 40 dp corners, the name in headlineMedium, a stats line and a row of [actions] built from
 * [GalleryDetailAction]. It scrolls with the content; once the name passes under the top bar,
 * [GalleryDetailTopBar] takes over the title.
 */
@Composable
fun GalleryDetailHeader(
    title: String,
    stats: String?,
    cover: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        cover()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 6.dp)
                .then(
                    if (onTitleClick != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button, onClick = onTitleClick)
                    } else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { heading() },
            )
            titleTrailing?.invoke()
        }
        if (!stats.isNullOrBlank()) {
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp),
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

/** The 120 dp rounded cover of a [GalleryDetailHeader]; [fallbackIcon] shows when [model] is null. */
@Composable
fun GalleryDetailCover(
    model: Any?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    signature: Any? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier.size(120.dp),
                model = model,
                contentScale = ContentScale.Crop,
                contentDescription = contentDescription,
                signature = signature,
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** A 48 dp tonal pill under a [GalleryDetailHeader]: leading icon and label. */
@Composable
fun GalleryDetailAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * Top bar of a detail page. At rest it shows only the round buttons on the page colour, since
 * the header below carries the name. Once [collapsed], the name and [count] move into the bar
 * and it turns into the transparent blur bar over the content, blurred in
 * [HazePerformanceMode.Performance] because photo grids scroll under it.
 *
 * The bar's buttons form one segmented group, like the viewer's and timeline's. Once
 * [collapsed], [collapsedActions] (build each with [GalleryPageTopBarAction]) fade in at the
 * front of that group, and fade out again below the threshold; [actions] always show.
 */
@Composable
fun GalleryDetailTopBar(
    title: String,
    collapsed: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    count: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    collapsedActions: List<@Composable () -> Unit> = emptyList(),
    actions: (@Composable () -> Unit)? = null,
) {
    val hasActions = actions != null || collapsedActions.isNotEmpty()
    CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
        GalleryPageTopBar(
            title = if (collapsed) title else "",
            count = count?.takeIf { collapsed },
            modifier = modifier,
            onBack = onBack,
            navigationIcon = navigationIcon ?: Icons.AutoMirrored.Filled.ArrowBack,
            hazeState = hazeState,
            scrolled = collapsed,
            actions = if (hasActions) {
                {
                    GallerySegmentedActionGroup(segmentSize = GalleryChrome.ToolbarButtonSize) {
                        collapsedActions.forEach { action ->
                            AnimatedVisibility(
                                visible = collapsed,
                                enter = fadeIn(tween(DetailActionsFadeMillis)),
                                exit = fadeOut(tween(DetailActionsFadeMillis)),
                            ) { action() }
                        }
                        actions?.invoke()
                    }
                }
            } else null,
        )
    }
}

/** Duration of the bar actions' fade in and out. */
private const val DetailActionsFadeMillis = 400

/** Height of [GalleryDetailTopBar] including the status bar: the top padding of the content. */
@Composable
fun galleryDetailTopBarHeight(): Dp {
    val density = LocalDensity.current
    val statusBar = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    return statusBar + GalleryChrome.ToolbarButtonSize + GalleryChrome.ToolbarVerticalPadding * 2
}

/**
 * True once the header's name has scrolled under the top bar: the header is the grid's first
 * item, so this is any later first item or an offset past [threshold].
 */
@Composable
fun rememberDetailHeaderCollapsed(gridState: LazyGridState, threshold: Dp = 160.dp): State<Boolean> {
    val thresholdPx = with(LocalDensity.current) { threshold.roundToPx() }
    return remember(gridState, thresholdPx) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
}

/** [rememberDetailHeaderCollapsed] for a page whose header is the first item of a lazy list. */
@Composable
fun rememberDetailHeaderCollapsed(listState: LazyListState, threshold: Dp = 160.dp): State<Boolean> {
    val thresholdPx = with(LocalDensity.current) { threshold.roundToPx() }
    return remember(listState, thresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
}

/**
 * Capture span of [media] for a detail header's stats line: "Jun 2025", "Jun – Aug 2025" or
 * "Jun 2019 – Oct 2026". Null when there is nothing dated.
 */
fun mediaDateRangeLabel(media: List<Media>): String? {
    val stamps = media.asSequence().map { it.definedTimestamp }.filter { it > 0 }
    val first = stamps.minOrNull() ?: return null
    val last = stamps.maxOrNull() ?: return null
    return dateRangeLabel(first, last)
}

/** [mediaDateRangeLabel] for two epoch-second timestamps. */
fun dateRangeLabel(firstEpochSec: Long, lastEpochSec: Long): String {
    val start = firstEpochSec.epochSecToLocalDate()
    val end = lastEpochSec.epochSecToLocalDate()
    return when {
        start.year == end.year && start.month == end.month -> lastEpochSec.formatEpochSec("MMM yyyy")
        start.year == end.year ->
            "${firstEpochSec.formatEpochSec("MMM")} – ${lastEpochSec.formatEpochSec("MMM yyyy")}"
        else -> "${firstEpochSec.formatEpochSec("MMM yyyy")} – ${lastEpochSec.formatEpochSec("MMM yyyy")}"
    }
}
