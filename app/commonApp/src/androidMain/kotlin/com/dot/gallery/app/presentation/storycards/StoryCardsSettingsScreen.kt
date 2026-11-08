/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.storycards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.core.Settings.Misc.rememberStoryCardsConfig
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerAutoAdvance
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerDuration
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.story_cards_enabled
import com.dot.gallery.generated.resources.story_cards_enabled_summary
import com.dot.gallery.generated.resources.story_cards_order_title
import com.dot.gallery.generated.resources.story_cards_settings_title
import com.dot.gallery.generated.resources.story_move_down
import com.dot.gallery.generated.resources.story_move_up
import com.dot.gallery.generated.resources.story_seconds_short
import com.dot.gallery.generated.resources.story_type_albums
import com.dot.gallery.generated.resources.story_type_albums_description
import com.dot.gallery.generated.resources.story_type_categories
import com.dot.gallery.generated.resources.story_type_categories_description
import com.dot.gallery.generated.resources.story_type_cloud_memories
import com.dot.gallery.generated.resources.story_type_cloud_memories_description
import com.dot.gallery.generated.resources.story_type_favorites
import com.dot.gallery.generated.resources.story_type_favorites_description
import com.dot.gallery.generated.resources.story_type_locations
import com.dot.gallery.generated.resources.story_type_locations_description
import com.dot.gallery.generated.resources.story_type_memories
import com.dot.gallery.generated.resources.story_type_memories_description
import com.dot.gallery.generated.resources.story_viewer_auto_advance
import com.dot.gallery.generated.resources.story_viewer_auto_advance_summary
import com.dot.gallery.generated.resources.story_viewer_duration
import com.dot.gallery.generated.resources.story_viewer_duration_summary
import com.dot.gallery.generated.resources.story_viewer_settings_title
import org.jetbrains.compose.resources.StringResource
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCardsSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    var config by rememberStoryCardsConfig()
    var autoAdvance by rememberStoryViewerAutoAdvance()
    var duration by rememberStoryViewerDuration()
    var draftOrder by remember(config.normalizedOrder) { mutableStateOf(config.normalizedOrder) }

    val title = stringResource(Res.string.story_cards_settings_title)
    val listState = rememberLazyListState()

    // Drag-to-reorder state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<StoryCardType, Int>() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fallbackItemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    fun updateOrder(from: Int, to: Int, persist: Boolean) {
        val updated = moveStoryCardType(draftOrder, from, to)
        if (updated == draftOrder) return
        draftOrder = updated
        if (persist) config = config.copy(cardOrder = updated)
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            GalleryPageTopBar(
                title = title,
                onBack = onNavigateBack,
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
            userScrollEnabled = draggingIndex == -1,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Master Toggle ──
            item(key = "master_toggle") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.story_cards_enabled),
                        summary = stringResource(Res.string.story_cards_enabled_summary),
                        isChecked = config.enabled,
                        onCheck = { config = config.copy(enabled = it) },
                        screenPosition = Position.Alone
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            // ── Card Types Header ──
            item(key = "card_types_header") {
                SectionHeader(
                    title = stringResource(Res.string.story_cards_order_title),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Card Type Items (drag-to-reorder) ──
            itemsIndexed(
                items = draftOrder,
                key = { _, type -> "card_${type.name}" }
            ) { index, type ->
                val isChecked = type !in config.disabledTypes
                val position = cardItemPosition(index, draftOrder.size)
                val isDragged = draggingIndex == index

                CardTypeListItem(
                    type = type,
                    isChecked = isChecked,
                    enabled = config.enabled,
                    position = position,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onHeightChanged = { rowHeights[type] = it },
                    onDragStart = {
                        draggingIndex = index
                        dragOffsetY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { delta ->
                        dragOffsetY += delta
                        val currentHeight = rowHeights[type]?.toFloat() ?: fallbackItemHeightPx
                        if (draggingIndex < draftOrder.lastIndex) {
                            val nextType = draftOrder[draggingIndex + 1]
                            val nextHeight = rowHeights[nextType]?.toFloat() ?: fallbackItemHeightPx
                            if (dragOffsetY > (currentHeight + nextHeight) * 0.5f) {
                                updateOrder(draggingIndex, draggingIndex + 1, persist = false)
                                draggingIndex++
                                dragOffsetY -= nextHeight
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        if (draggingIndex > 0) {
                            val previousType = draftOrder[draggingIndex - 1]
                            val previousHeight = rowHeights[previousType]?.toFloat() ?: fallbackItemHeightPx
                            if (dragOffsetY < -(currentHeight + previousHeight) * 0.5f) {
                                updateOrder(draggingIndex, draggingIndex - 1, persist = false)
                                draggingIndex--
                                dragOffsetY += previousHeight
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        if (draftOrder != config.normalizedOrder) {
                            config = config.copy(cardOrder = draftOrder)
                        }
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        draftOrder = config.normalizedOrder
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onMoveUp = if (index > 0 && config.enabled) {
                        { updateOrder(index, index - 1, persist = true) }
                    } else null,
                    onMoveDown = if (index < draftOrder.lastIndex && config.enabled) {
                        { updateOrder(index, index + 1, persist = true) }
                    } else null,
                    onToggle = { checked ->
                        val newDisabled = if (checked) {
                            config.disabledTypes - type
                        } else {
                            config.disabledTypes + type
                        }
                        config = config.copy(disabledTypes = newDisabled)
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Viewer Settings Header ──
            item(key = "viewer_header") {
                SectionHeader(
                    title = stringResource(Res.string.story_viewer_settings_title),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // ── Auto-Advance ──
            item(key = "auto_advance") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.story_viewer_auto_advance),
                        summary = stringResource(Res.string.story_viewer_auto_advance_summary),
                        isChecked = autoAdvance,
                        onCheck = { autoAdvance = it },
                        screenPosition = Position.Top
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Duration ──
            item(key = "duration") {
                SettingsItem(
                    item = SettingsEntity.SeekPreference(
                        title = stringResource(Res.string.story_viewer_duration),
                        summary = stringResource(Res.string.story_viewer_duration_summary, duration),
                        enabled = autoAdvance,
                        minValue = 3f,
                        currentValue = duration.toFloatOrNull()?.coerceIn(3f, 10f) ?: 5f,
                        maxValue = 10f,
                        step = 1,
                        seekSuffix = stringResource(Res.string.story_seconds_short),
                        onSeek = { duration = it.toInt().toString() },
                        screenPosition = Position.Bottom
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Card Type List Item — drag-to-reorder + toggle, matching SettingsItem style
// ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardTypeListItem(
    type: StoryCardType,
    isChecked: Boolean,
    enabled: Boolean,
    position: Position,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onHeightChanged: (Int) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val displayName = stringResource(type.displayNameRes)
    val description = stringResource(type.descriptionRes)
    val moveUpLabel = stringResource(Res.string.story_move_up, displayName)
    val moveDownLabel = stringResource(Res.string.story_move_down, displayName)
    val moveActions = buildList {
        onMoveUp?.let { action ->
            add(CustomAccessibilityAction(moveUpLabel) {
                action()
                true
            })
        }
        onMoveDown?.let { action ->
            add(CustomAccessibilityAction(moveDownLabel) {
                action()
                true
            })
        }
    }
    val contentActive = enabled && isChecked

    val fullCornerRadius by animateDpAsState(
        targetValue = 24.dp,
        label = "fullCornerRadius"
    )
    val normalCornerRadius by animateDpAsState(
        targetValue = 8.dp,
        label = "normalCornerRadius"
    )

    val shape by rememberedDerivedState(position, fullCornerRadius, normalCornerRadius) {
        when (position) {
            Position.Alone -> RoundedCornerShape(fullCornerRadius)
            Position.Top -> RoundedCornerShape(
                topStart = fullCornerRadius, topEnd = fullCornerRadius,
                bottomStart = normalCornerRadius, bottomEnd = normalCornerRadius
            )
            Position.Middle -> RoundedCornerShape(normalCornerRadius)
            Position.Bottom -> RoundedCornerShape(
                topStart = normalCornerRadius, topEnd = normalCornerRadius,
                bottomStart = fullCornerRadius, bottomEnd = fullCornerRadius
            )
        }
    }

    val paddingModifier = when (position) {
        Position.Alone -> Modifier.padding(bottom = 16.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 8f else 0f,
        label = "dragElevation"
    )

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Box(
        modifier = Modifier
            .then(paddingModifier)
            .onSizeChanged { onHeightChanged(it.height) }
            .zIndex(if (isDragging) 1f else 0f)
            .semantics(mergeDescendants = true) { customActions = moveActions }
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = elevation
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentOnDragStart() },
                        onDrag = { change, offset ->
                            change.consume()
                            currentOnDrag(offset.y)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() }
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(color = backgroundColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle
                    Icon(
                        Icons.Outlined.DragHandle, null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        tint = if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )

                    // Type icon
                    Image(
                        imageVector = type.settingsIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        colorFilter = ColorFilter.tint(
                            if (contentActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    )

                    // Label + description
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (contentActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (contentActive) 1f else 0.38f
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Toggle
                    Switch(
                        checked = isChecked,
                        enabled = enabled,
                        onCheckedChange = onToggle
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Section Header
// ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() }
    )
}

internal fun moveStoryCardType(
    order: List<StoryCardType>,
    from: Int,
    to: Int,
): List<StoryCardType> {
    if (from !in order.indices || to !in order.indices || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

private fun cardItemPosition(index: Int, size: Int): Position {
    return when {
        size == 1 -> Position.Alone
        index == 0 -> Position.Top
        index == size - 1 -> Position.Bottom
        else -> Position.Middle
    }
}

private val StoryCardType.settingsIcon: ImageVector
    get() = when (this) {
        StoryCardType.MEMORIES -> Icons.Outlined.History
        StoryCardType.ALBUMS -> Icons.Outlined.PhotoAlbum
        StoryCardType.CATEGORIES -> Icons.Outlined.ImageSearch
        StoryCardType.LOCATIONS -> Icons.Outlined.LocationOn
        StoryCardType.FAVORITES -> Icons.Outlined.Favorite
        StoryCardType.CLOUD_MEMORIES -> Icons.Outlined.Cloud
    }

private val StoryCardType.displayNameRes: StringResource
    get() = when (this) {
        StoryCardType.MEMORIES -> Res.string.story_type_memories
        StoryCardType.ALBUMS -> Res.string.story_type_albums
        StoryCardType.CATEGORIES -> Res.string.story_type_categories
        StoryCardType.LOCATIONS -> Res.string.story_type_locations
        StoryCardType.FAVORITES -> Res.string.story_type_favorites
        StoryCardType.CLOUD_MEMORIES -> Res.string.story_type_cloud_memories
    }

private val StoryCardType.descriptionRes: StringResource
    get() = when (this) {
        StoryCardType.MEMORIES -> Res.string.story_type_memories_description
        StoryCardType.ALBUMS -> Res.string.story_type_albums_description
        StoryCardType.CATEGORIES -> Res.string.story_type_categories_description
        StoryCardType.LOCATIONS -> Res.string.story_type_locations_description
        StoryCardType.FAVORITES -> Res.string.story_type_favorites_description
        StoryCardType.CLOUD_MEMORIES -> Res.string.story_type_cloud_memories_description
    }
