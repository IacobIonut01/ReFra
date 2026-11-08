/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RawOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.galleryFloatingSurface
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.search_fill_query
import com.dot.gallery.feature.classifier.generated.resources.search_remove_from_history
import com.dot.gallery.feature.classifier.generated.resources.search_smart_off_action
import com.dot.gallery.feature.classifier.generated.resources.search_smart_off_body
import com.dot.gallery.feature.classifier.generated.resources.search_smart_off_dismiss
import com.dot.gallery.feature.classifier.generated.resources.search_smart_off_title
import com.dot.gallery.feature.classifier.generated.resources.search_sort_label
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_hours
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_minutes
import com.dot.gallery.feature.classifier.generated.resources.smart_scan_eta_under_minute
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_preparing
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_progress
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_title
import com.dot.gallery.feature.classifier.generated.resources.smart_search_index_waiting
import com.dot.gallery.feature.classifier.generated.resources.sort_by_date
import com.dot.gallery.feature.classifier.generated.resources.sort_most_accurate
import com.dot.gallery.feature.classifier.ui.components.CategoryCarouselItem
import com.dot.gallery.feature.classifier.ui.components.CategoryCover
import com.dot.gallery.feature.classifier.ui.components.CategoryProgressCard
import com.dot.gallery.ui.theme.ComponentSize
import com.dot.gallery.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** Horizontal padding shared by every search section, matching the search bar's inset. */
internal val SearchSectionPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal)

/** Section title with an optional baseline count and a trailing text action. */
@Composable
internal fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentSize.MinimumTouchTarget)
            .padding(start = Spacing.Large, end = 12.dp, top = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            if (count != null) {
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Horizontally scrolling row of chips or cards inset like the rest of the page. */
@Composable
internal fun SearchHorizontalRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = Spacing.Small,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = SearchSectionPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Recent search chip. Tapping runs it again; long-pressing removes it from history. An
 * image search shows its photo instead of the history glyph.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchHistoryChip(
    label: String,
    icon: ImageVector?,
    thumbnailUri: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeLabel = stringResource(Res.string.search_remove_from_history)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRemove,
                onLongClickLabel = removeLabel,
                role = Role.Button,
            )
            .padding(start = if (thumbnailUri != null) 6.dp else 12.dp, end = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (thumbnailUri != null) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                model = thumbnailUri,
                contentScale = ContentScale.Crop,
                contentDescription = null,
                signature = thumbnailUri,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Long history entries are clipped so one chip can't fill the whole row.
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

/** Tonal quick suggestion chip (Screenshots, Videos…) with a leading glyph. */
@Composable
internal fun SearchSuggestionChip(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 12.dp else Spacing.Medium, end = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** Plain filter chip used for camera models and "Or try" terms. */
@Composable
internal fun SearchFilterChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Category cover with its name and photo count underneath. */
@Composable
internal fun SearchCategoryTile(
    category: CategoryCarouselItem,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        CategoryCover(
            media = category.thumbnailMedia,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Column(modifier = Modifier.padding(horizontal = Spacing.ExtraSmall)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Photo card for a place with its name over a bottom scrim. */
@Composable
internal fun SearchPlaceCard(
    location: LocationMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(148.dp)
            .height(184.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        LocalMediaImageRenderer.current.RenderImage(
            modifier = Modifier.fillMaxSize(),
            model = location.media.getUri(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
            signature = location.media,
        )
        // The label sits on the photo, so it stays white in both themes like the album overlays.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = location.city?.takeIf { it.isNotBlank() } ?: location.location,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            location.country?.takeIf { it.isNotBlank() && !location.city.isNullOrBlank() }?.let { country ->
                Text(
                    text = country,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One entry of a two-column [SearchTypeGrid]. */
internal data class SearchTypeEntry(
    val key: String,
    val title: String,
    val count: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Two-column grid of tonal tiles: an icon disc, a title and a count. */
@Composable
internal fun SearchTypeGrid(entries: List<SearchTypeEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SearchSectionPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                row.forEach { entry ->
                    SearchTypeTile(entry = entry, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchTypeTile(entry: SearchTypeEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = entry.onClick)
            .padding(horizontal = 12.dp, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchIconDisc(icon = entry.icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 40 dp primary-container disc holding a glyph. */
@Composable
internal fun SearchIconDisc(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
    }
}

/** Glyph shown for a media-type, mode or grouping key from the search ViewModel. */
internal fun searchTypeIcon(key: String): ImageVector = when {
    key.startsWith("video") -> Icons.Outlined.Movie
    key == "image/gif" -> Icons.Outlined.Gif
    key.startsWith("image/x-") || key == "image/dng" -> Icons.Outlined.PhotoCamera
    key == "night_mode" -> Icons.Outlined.DarkMode
    key == "panorama" -> Icons.Outlined.Panorama
    key == "photosphere" -> Icons.Outlined.Vrpano
    key == "long_exposure" -> Icons.Outlined.ShutterSpeed
    key == "motion_photo" -> Icons.Outlined.MotionPhotosOn
    key == "bursts" -> Icons.Outlined.BurstMode
    key == "raw_jpg" -> Icons.Outlined.RawOn
    key == "edits" -> Icons.Outlined.Edit
    else -> Icons.Outlined.Image
}

/** Leading slot of a [SearchSuggestionRow]: a glyph on a tonal circle. */
@Composable
internal fun SearchRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Leading slot of a [SearchSuggestionRow]: a photo, rounded square for collections. */
@Composable
internal fun SearchRowImage(model: Any?, signature: Any?, shape: Shape = RoundedCornerShape(12.dp)) {
    LocalMediaImageRenderer.current.RenderImage(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        model = model,
        contentScale = ContentScale.Crop,
        contentDescription = null,
        signature = signature,
    )
}

/**
 * Suggestion list row: leading glyph or photo, title and subtitle, and an optional arrow that
 * copies the suggestion into the field without searching.
 */
@Composable
internal fun SearchSuggestionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFill: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.Medium, end = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        leading()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.Small)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onFill != null) {
            IconButton(onClick = onFill) {
                Icon(
                    imageVector = Icons.Outlined.NorthWest,
                    contentDescription = stringResource(Res.string.search_fill_query),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(Spacing.Small))
        }
    }
}

/**
 * Frosted dock over the results: the sort toggle for relevance searches and the result count.
 * Uses the same floating surface as the navigation dock so the grid shows through it.
 */
@Composable
internal fun SearchResultsDock(
    countLabel: String,
    showSort: Boolean,
    sortByRelevance: Boolean,
    onSortChange: (relevance: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = modifier
            .then(galleryFloatingSurface(shape))
            .heightIn(min = 64.dp)
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (showSort) {
            val sortLabel = stringResource(Res.string.search_sort_label)
            Row(
                modifier = Modifier.semantics { contentDescription = sortLabel },
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                SearchDockSegment(
                    label = stringResource(Res.string.sort_most_accurate),
                    selected = sortByRelevance,
                    onClick = { onSortChange(true) },
                )
                SearchDockSegment(
                    label = stringResource(Res.string.sort_by_date),
                    selected = !sortByRelevance,
                    onClick = { onSortChange(false) },
                )
            }
        }
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun SearchDockSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "searchDockSegment",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .height(ComponentSize.MinimumTouchTarget)
            .clip(CircleShape)
            .background(container)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(start = if (selected) 12.dp else Spacing.Medium, end = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
private fun searchEtaLabel(estimatedRemainingMillis: Long): String {
    val totalMinutes = (estimatedRemainingMillis / 60_000L).coerceAtLeast(0L)
    if (totalMinutes < 1) return stringResource(Res.string.smart_scan_eta_under_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(Res.string.smart_scan_eta_hours, hours, minutes)
    } else {
        stringResource(Res.string.smart_scan_eta_minutes, totalMinutes)
    }
}

/** Smart search indexing progress, shown at the top of every search state while it runs. */
@Composable
internal fun SearchIndexStatusCard(state: SearchIndexerState, modifier: Modifier = Modifier) {
    val running = state.status == SmartScanStatus.RUNNING
    CategoryProgressCard(
        icon = Icons.Outlined.AutoAwesome,
        title = stringResource(Res.string.smart_search_index_title),
        summary = when {
            running && state.total > 0 -> stringResource(
                Res.string.smart_search_index_progress,
                state.processed,
                state.total,
                state.stageNumber,
                state.stageCount
            )
            running -> stringResource(Res.string.smart_search_index_preparing, state.stageNumber, state.stageCount)
            else -> stringResource(Res.string.smart_search_index_waiting, state.stageNumber, state.stageCount)
        },
        progress = if (running && state.total > 0) state.progress else null,
        note = state.estimatedRemainingMillis?.let { searchEtaLabel(it) },
        modifier = modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Small),
    )
}

/** Offer shown when the Smart search model is missing; [onTurnOn] opens its setup. */
@Composable
internal fun SmartSearchPromoCard(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Small)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = Spacing.Medium, end = Spacing.Medium, top = Spacing.Medium, bottom = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchIconDisc(
                icon = Icons.Outlined.AutoAwesome,
                container = MaterialTheme.colorScheme.surface,
                content = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = stringResource(Res.string.search_smart_off_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(Res.string.search_smart_off_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.search_smart_off_dismiss))
            }
            Button(onClick = onTurnOn) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(stringResource(Res.string.search_smart_off_action))
            }
        }
    }
}

/** Expressive loading indicator with the search glyph and a status line. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchLoadingState(status: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(96.dp))
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Placeholder tiles shown under the loading indicator while a search runs. */
@Composable
internal fun SearchSkeletonGrid(modifier: Modifier = Modifier) {
    val shades = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.surfaceContainerLow,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SearchSectionPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        shades.forEach { shade ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shade)
                    )
                }
            }
        }
    }
}

/** Centred icon tile, title, body and optional actions for empty and error states. */
@Composable
internal fun SearchMessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.ExtraLarge, end = Spacing.ExtraLarge, top = 88.dp, bottom = Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(28.dp),
            color = iconContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
        actions()
    }
}
