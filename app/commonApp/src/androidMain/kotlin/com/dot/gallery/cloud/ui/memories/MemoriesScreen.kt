/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import com.dot.gallery.core.presentation.components.ExpressiveEmptyState
import com.dot.gallery.domain.model.StoryCard
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_memories
import com.dot.gallery.generated.resources.cloud_memories_empty
import com.dot.gallery.generated.resources.cloud_memories_empty_summary
import com.dot.gallery.generated.resources.cloud_memories_memory
import com.dot.gallery.generated.resources.cloud_memories_earlier
import com.dot.gallery.generated.resources.cloud_memories_on_this_day
import com.dot.gallery.generated.resources.cloud_memories_years_ago_count
import com.dot.gallery.generated.resources.cloud_person_photo_count
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.util.getUri
import org.jetbrains.compose.resources.pluralStringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.hazeSource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    /** Plays the page's memories in the story viewer, starting at [initialCardId]. */
    onOpenStory: (cards: List<StoryCard>, initialCardId: Long) -> Unit = { _, _ -> },
) {
    val viewModel = injectViewModel<MemoriesViewModel> {memoriesViewModel}
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = LocalHazeState.current
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                GalleryPageTopBar(
                    title = stringResource(Res.string.cloud_memories),
                    count = state.memories.size.takeIf { it > 0 }?.toString(),
                    hazeState = hazeState,
                    scrolled = rememberScrolledUnderTopBar(gridState),
                )
            }
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        // The page's only haze source, around every branch.
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        when {
            state.isLoading -> {
                LoadingMedia(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            state.memories.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpressiveEmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(Res.string.cloud_memories_empty),
                        body = stringResource(Res.string.cloud_memories_empty_summary),
                    )
                }
            }
            else -> {
                MemoriesGrid(
                    memories = state.memories,
                    onOpenMemory = { memory ->
                        val memories = state.memories
                        scope.launch { onOpenStory(viewModel.storyCards(memories), viewModel.storyCardId(memory)) }
                    },
                    today = remember { LocalDate.now() },
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDir),
                        end = innerPadding.calculateEndPadding(layoutDir),
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 32.dp
                    ),
                    gridState = gridState,
                )
            }
        }
        }

        // With nothing to show the empty state already explains where memories come from.
        state.error?.takeIf { state.memories.isNotEmpty() }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * The memories page body: today's "On this day" memories as a row of tall cover cards, then
 * every earlier memory as a two-column grid of square covers with the year below.
 */
@Composable
internal fun MemoriesGrid(
    memories: List<MemoryEntry>,
    today: LocalDate,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenMemory: (MemoryEntry) -> Unit = {},
) {
    val (onThisDay, earlier) = remember(memories) { memories.partition { it.onThisDay } }
    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onThisDay.isNotEmpty()) {
            item(key = "on_this_day_label", span = { GridItemSpan(maxLineSpan) }) {
                MemoriesSectionLabel(stringResource(Res.string.cloud_memories_on_this_day), onThisDay.size)
            }
            item(key = "on_this_day_row", span = { GridItemSpan(maxLineSpan) }) {
                // Bleed the row through the grid's side padding so cards scroll edge to edge.
                LazyRow(
                    modifier = Modifier.layout { measurable, constraints ->
                        val bleed = 32.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minWidth + bleed,
                                maxWidth = constraints.maxWidth + bleed,
                            )
                        )
                        layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(onThisDay, key = { it.key }) { memory ->
                        OnThisDayCard(memory = memory, today = today, onClick = { onOpenMemory(memory) })
                    }
                }
            }
        }
        if (earlier.isNotEmpty()) {
            item(key = "earlier_label", span = { GridItemSpan(maxLineSpan) }) {
                MemoriesSectionLabel(stringResource(Res.string.cloud_memories_earlier), earlier.size)
            }
            gridItems(earlier, key = { it.key }) { memory ->
                MemoryTile(memory = memory, today = today, onClick = { onOpenMemory(memory) })
            }
        }
    }
}

@Composable
private fun MemoriesSectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun MemoryCover(memory: MemoryEntry, modifier: Modifier = Modifier) {
    val cover = memory.media.firstOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier.fillMaxSize(),
                model = cover.getUri(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                signature = cover,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PhotoAlbum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun OnThisDayCard(memory: MemoryEntry, today: LocalDate, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(164.dp)
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        MemoryCover(memory = memory, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = memoryTitle(memory, today),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    memory.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    stringResource(Res.string.cloud_person_photo_count, memory.assetCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTile(memory: MemoryEntry, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // Tighter bottom corners so the press ripple never cuts into the caption.
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MemoryCover(
            memory = memory,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (memory.year > 0) memory.year.toString() else stringResource(Res.string.cloud_memories_memory),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = listOfNotNull(
                memoryTitle(memory, today).takeIf { memory.year > 0 },
                stringResource(Res.string.cloud_person_photo_count, memory.assetCount),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** "3 years ago" for dated memories, otherwise the memory type in words. */
@Composable
private fun memoryTitle(memory: MemoryEntry, today: LocalDate): String =
    if (memory.year > 0) {
        val years = (today.year - memory.year).coerceAtLeast(0)
        pluralStringResource(Res.plurals.cloud_memories_years_ago_count, years, years)
    } else {
        memory.typeLabel
    }
