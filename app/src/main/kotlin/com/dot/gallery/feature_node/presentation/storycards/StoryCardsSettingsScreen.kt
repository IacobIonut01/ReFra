/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.Settings.Misc.rememberStoryCardsConfig
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerAutoAdvance
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerDuration
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.StoryCardType
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCardsSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    var config by rememberStoryCardsConfig()
    var autoAdvance by rememberStoryViewerAutoAdvance()
    var duration by rememberStoryViewerDuration()

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Drag-to-reorder state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.story_cards_settings_title)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        title = stringResource(R.string.story_cards_enabled),
                        summary = stringResource(R.string.story_cards_enabled_summary),
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
                    title = stringResource(R.string.story_cards_order_title),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Card Type Items (drag-to-reorder) ──
            itemsIndexed(
                items = config.cardOrder,
                key = { _, type -> "card_${type.name}" }
            ) { index, type ->
                val isEnabled = type !in config.disabledTypes
                val position = cardItemPosition(index, config.cardOrder.size)
                val isDragged = draggingIndex == index

                CardTypeListItem(
                    type = type,
                    isEnabled = isEnabled && config.enabled,
                    position = position,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onDragStart = {
                        draggingIndex = index
                        dragOffsetY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { delta ->
                        dragOffsetY += delta
                        val swapThreshold = itemHeightPx * 0.5f
                        if (dragOffsetY > swapThreshold && draggingIndex < config.cardOrder.lastIndex) {
                            val list = config.cardOrder.toMutableList()
                            val item = list.removeAt(draggingIndex)
                            list.add(draggingIndex + 1, item)
                            config = config.copy(cardOrder = list)
                            draggingIndex++
                            dragOffsetY -= itemHeightPx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                            val list = config.cardOrder.toMutableList()
                            val item = list.removeAt(draggingIndex)
                            list.add(draggingIndex - 1, item)
                            config = config.copy(cardOrder = list)
                            draggingIndex--
                            dragOffsetY += itemHeightPx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
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
                    title = stringResource(R.string.story_viewer_settings_title),
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
                        title = stringResource(R.string.story_viewer_auto_advance),
                        summary = stringResource(R.string.story_viewer_auto_advance_summary),
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
                        title = stringResource(R.string.story_viewer_duration),
                        summary = stringResource(R.string.story_viewer_duration_summary, duration),
                        minValue = 3f,
                        currentValue = duration.toFloatOrNull() ?: 5f,
                        maxValue = 10f,
                        step = 1,
                        seekSuffix = "s",
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
    isEnabled: Boolean,
    position: Position,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer

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

    Box(
        modifier = Modifier
            .then(paddingModifier)
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = elevation
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, offset ->
                        change.consume()
                        currentOnDrag(offset.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
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
                            if (isEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    )

                    // Label + description
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = type.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isEnabled) 1f else 0.38f
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Toggle
                    Switch(
                        checked = isEnabled,
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
        modifier = modifier
    )
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
    }

private val StoryCardType.displayName: String
    get() = when (this) {
        StoryCardType.MEMORIES -> "Memories"
        StoryCardType.ALBUMS -> "Albums"
        StoryCardType.CATEGORIES -> "Categories"
        StoryCardType.LOCATIONS -> "Locations"
        StoryCardType.FAVORITES -> "Favorites"
    }

private val StoryCardType.description: String
    get() = when (this) {
        StoryCardType.MEMORIES -> "Photos from this day in previous years"
        StoryCardType.ALBUMS -> "Highlighted and pinned albums"
        StoryCardType.CATEGORIES -> "AI-classified photo categories"
        StoryCardType.LOCATIONS -> "Photos organized by location"
        StoryCardType.FAVORITES -> "Your favorite photos"
    }
