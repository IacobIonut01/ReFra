/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.RequestInitialSettingsFocus
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.core.presentation.components.ActionZone
import com.dot.gallery.core.presentation.components.SelectionAction
import com.dot.gallery.core.presentation.components.SelectionSheetConfig
import com.dot.gallery.core.presentation.components.rememberSelectionSheetConfig
import com.dot.gallery.core.presentation.components.rememberShowSelectionTitles
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_bar
import com.dot.gallery.feature.settings.generated.resources.action_bar_fill
import com.dot.gallery.feature.settings.generated.resources.action_bar_fill_summary
import com.dot.gallery.feature.settings.generated.resources.middle_actions
import com.dot.gallery.feature.settings.generated.resources.minimum_one_action
import com.dot.gallery.feature.settings.generated.resources.reset_to_defaults
import com.dot.gallery.feature.settings.generated.resources.selection_actions
import com.dot.gallery.feature.settings.generated.resources.selection_actions_description
import com.dot.gallery.feature.settings.generated.resources.top_actions
import com.dot.gallery.feature.settings.generated.resources.top_actions_right_aligned
import com.dot.gallery.feature.settings.generated.resources.top_actions_right_aligned_summary
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSelectionActionsContent() {
    var config by rememberSelectionSheetConfig()
    val sanitizedConfig = remember(config) { config.sanitized() }

    val initialFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    RequestInitialSettingsFocus(
        focusRequester = initialFocusRequester,
        prepareFocus = { listState.scrollToItem(3) },
    )

    var showAddTopSheet by rememberSaveable { mutableStateOf(false) }
    var showAddMiddleSheet by rememberSaveable { mutableStateOf(false) }
    var showAddBottomSheet by rememberSaveable { mutableStateOf(false) }

    // Drag-to-reorder state
    var draggingZone by remember { mutableStateOf<ActionZone?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Approximate item height for swap threshold
    val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.selection_actions),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current).settingsFocusGroup(),
            userScrollEnabled = draggingZone == null,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Live Preview ──
            item(key = "preview") {
                Box(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionSheetPreview(config = sanitizedConfig)
                }
            }

            // ── Description ──
            item(key = "description") {
                Text(
                    text = stringResource(Res.string.selection_actions_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                )
            }

            // ── Reset to Defaults ──
            item(key = "reset") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = { config = SelectionSheetConfig() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(Res.string.reset_to_defaults))
                    }
                }
            }

            // ── Top Actions Header ──
            item(key = "top_header") {
                ActionEditorSectionHeader(
                    title = stringResource(Res.string.top_actions),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Right-align toggle ──
            item(key = "top_right_align") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.top_actions_right_aligned),
                        summary = stringResource(Res.string.top_actions_right_aligned_summary),
                        isChecked = sanitizedConfig.topActionsRightAligned,
                        onCheck = { checked ->
                            config = config.copy(topActionsRightAligned = checked)
                        },
                        screenPosition = Position.Alone
                    ),
                    modifier = Modifier
                        .focusRequester(initialFocusRequester)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // ── Top Actions List ──
            itemsIndexed(
                items = sanitizedConfig.topActions,
                key = { _, action -> "top_${action.name}" }
            ) { index, action ->
                val isLocked = action == SelectionAction.CLOSE
                val position = actionEditorPosition(index, sanitizedConfig.topActions.size)
                val isDragged = draggingZone == ActionZone.TOP && draggingIndex == index
                ActionEditorListItem(
                    entry = ActionEditorEntry(
                        icon = action.icon,
                        label = stringResource(action.labelRes),
                        description = stringResource(action.descriptionRes),
                    ),
                    position = position,
                    isLocked = isLocked,
                    canRemove = !isLocked,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onDragStart = {
                        if (!isLocked) {
                            draggingZone = ActionZone.TOP
                            draggingIndex = index
                            dragOffsetY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { delta ->
                        if (draggingZone == ActionZone.TOP) {
                            dragOffsetY += delta
                            val swapThreshold = itemHeightPx * 0.5f
                            if (dragOffsetY > swapThreshold && draggingIndex < sanitizedConfig.topActions.lastIndex) {
                                val list = sanitizedConfig.topActions.toMutableList()
                                val item = list.removeAt(draggingIndex)
                                list.add(draggingIndex + 1, item)
                                config = config.copy(topActions = list)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                val fromIdx = draggingIndex
                                if (sanitizedConfig.topActions[fromIdx] != SelectionAction.CLOSE &&
                                    sanitizedConfig.topActions[fromIdx - 1] != SelectionAction.CLOSE
                                ) {
                                    val list = sanitizedConfig.topActions.toMutableList()
                                    val item = list.removeAt(fromIdx)
                                    list.add(fromIdx - 1, item)
                                    config = config.copy(topActions = list)
                                    draggingIndex--
                                    dragOffsetY += itemHeightPx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingZone = null
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onMoveUp = if (!isLocked && index > 0 && sanitizedConfig.topActions[index - 1] != SelectionAction.CLOSE) {
                        {
                            val list = sanitizedConfig.topActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index - 1, item)
                            config = config.copy(topActions = list)
                        }
                    } else null,
                    onMoveDown = if (!isLocked && index < sanitizedConfig.topActions.lastIndex) {
                        {
                            val list = sanitizedConfig.topActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index + 1, item)
                            config = config.copy(topActions = list)
                        }
                    } else null,
                    onRemove = {
                        val list = sanitizedConfig.topActions.toMutableList()
                        list.removeAt(index)
                        config = config.copy(topActions = list)
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Add Top Action Button ──
            item(key = "add_top") {
                val availableTop = remember(sanitizedConfig) {
                    SelectionAction.entries
                        .filter { it.zone == ActionZone.TOP && it !in sanitizedConfig.topActions }
                }
                if (availableTop.isNotEmpty()) {
                    ActionEditorAddButton(
                        onClick = { showAddTopSheet = true },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Middle Actions Header ──
            item(key = "middle_header") {
                ActionEditorSectionHeader(
                    title = stringResource(Res.string.middle_actions),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Middle Actions List ──
            itemsIndexed(
                items = sanitizedConfig.middleActions,
                key = { _, action -> "middle_${action.name}" }
            ) { index, action ->
                val position = actionEditorPosition(index, sanitizedConfig.middleActions.size)
                val isDragged = draggingZone == ActionZone.MIDDLE && draggingIndex == index
                ActionEditorListItem(
                    entry = ActionEditorEntry(
                        icon = action.icon,
                        label = stringResource(action.labelRes),
                        description = stringResource(action.descriptionRes),
                    ),
                    position = position,
                    isLocked = false,
                    canRemove = true,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onDragStart = {
                        draggingZone = ActionZone.MIDDLE
                        draggingIndex = index
                        dragOffsetY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { delta ->
                        if (draggingZone == ActionZone.MIDDLE) {
                            dragOffsetY += delta
                            val swapThreshold = itemHeightPx * 0.5f
                            if (dragOffsetY > swapThreshold && draggingIndex < sanitizedConfig.middleActions.lastIndex) {
                                val list = sanitizedConfig.middleActions.toMutableList()
                                val item = list.removeAt(draggingIndex)
                                list.add(draggingIndex + 1, item)
                                config = config.copy(middleActions = list)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                val list = sanitizedConfig.middleActions.toMutableList()
                                val item = list.removeAt(draggingIndex)
                                list.add(draggingIndex - 1, item)
                                config = config.copy(middleActions = list)
                                draggingIndex--
                                dragOffsetY += itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        draggingZone = null
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onMoveUp = if (index > 0) {
                        {
                            val list = sanitizedConfig.middleActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index - 1, item)
                            config = config.copy(middleActions = list)
                        }
                    } else null,
                    onMoveDown = if (index < sanitizedConfig.middleActions.lastIndex) {
                        {
                            val list = sanitizedConfig.middleActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index + 1, item)
                            config = config.copy(middleActions = list)
                        }
                    } else null,
                    onRemove = {
                        val list = sanitizedConfig.middleActions.toMutableList()
                        list.removeAt(index)
                        config = config.copy(middleActions = list)
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Add Middle Action Button ──
            item(key = "add_middle") {
                val availableMiddle = remember(sanitizedConfig) {
                    SelectionAction.entries
                        .filter { it.zone == ActionZone.MIDDLE && it !in sanitizedConfig.middleActions }
                }
                if (availableMiddle.isNotEmpty()) {
                    ActionEditorAddButton(
                        onClick = { showAddMiddleSheet = true },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Bottom Actions Header ──
            item(key = "bottom_header") {
                ActionEditorSectionHeader(
                    title = stringResource(Res.string.action_bar),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Fill action bar toggle ──
            item(key = "bottom_fill") {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = stringResource(Res.string.action_bar_fill),
                        summary = stringResource(Res.string.action_bar_fill_summary),
                        isChecked = sanitizedConfig.fillActionBar,
                        onCheck = { checked ->
                            config = config.copy(fillActionBar = checked)
                        },
                        screenPosition = Position.Alone
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // ── Bottom Actions List ──
            itemsIndexed(
                items = sanitizedConfig.bottomActions,
                key = { _, action -> "bottom_${action.name}" }
            ) { index, action ->
                val position = actionEditorPosition(index, sanitizedConfig.bottomActions.size)
                val isDragged = draggingZone == ActionZone.BOTTOM && draggingIndex == index
                ActionEditorListItem(
                    entry = ActionEditorEntry(
                        icon = action.icon,
                        label = stringResource(action.labelRes),
                        description = stringResource(action.descriptionRes),
                    ),
                    position = position,
                    isLocked = false,
                    canRemove = sanitizedConfig.bottomActions.size > 1,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onDragStart = {
                        draggingZone = ActionZone.BOTTOM
                        draggingIndex = index
                        dragOffsetY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { delta ->
                        if (draggingZone == ActionZone.BOTTOM) {
                            dragOffsetY += delta
                            val swapThreshold = itemHeightPx * 0.5f
                            if (dragOffsetY > swapThreshold && draggingIndex < sanitizedConfig.bottomActions.lastIndex) {
                                val list = sanitizedConfig.bottomActions.toMutableList()
                                val item = list.removeAt(draggingIndex)
                                list.add(draggingIndex + 1, item)
                                config = config.copy(bottomActions = list)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                val list = sanitizedConfig.bottomActions.toMutableList()
                                val item = list.removeAt(draggingIndex)
                                list.add(draggingIndex - 1, item)
                                config = config.copy(bottomActions = list)
                                draggingIndex--
                                dragOffsetY += itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        draggingZone = null
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onMoveUp = if (index > 0) {
                        {
                            val list = sanitizedConfig.bottomActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index - 1, item)
                            config = config.copy(bottomActions = list)
                        }
                    } else null,
                    onMoveDown = if (index < sanitizedConfig.bottomActions.lastIndex) {
                        {
                            val list = sanitizedConfig.bottomActions.toMutableList()
                            val item = list.removeAt(index)
                            list.add(index + 1, item)
                            config = config.copy(bottomActions = list)
                        }
                    } else null,
                    onRemove = {
                        if (sanitizedConfig.bottomActions.size > 1) {
                            val list = sanitizedConfig.bottomActions.toMutableList()
                            list.removeAt(index)
                            config = config.copy(bottomActions = list)
                        }
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Add Bottom Action Button ──
            item(key = "add_bottom") {
                val availableBottom = remember(sanitizedConfig) {
                    SelectionAction.entries
                        .filter { it.zone == ActionZone.BOTTOM && it !in sanitizedConfig.bottomActions }
                }
                if (availableBottom.isNotEmpty()) {
                    ActionEditorAddButton(
                        onClick = { showAddBottomSheet = true },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }

    // ── Add Action Sheets ──
    if (showAddTopSheet) {
        val availableTop = remember(sanitizedConfig) {
            SelectionAction.entries
                .filter { it.zone == ActionZone.TOP && it !in sanitizedConfig.topActions }
        }
        ActionEditorAddSheet(
            actions = availableTop,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.copy(topActions = config.topActions + action)
                showAddTopSheet = false
            },
            onDismiss = { showAddTopSheet = false }
        )
    }

    if (showAddMiddleSheet) {
        val availableMiddle = remember(sanitizedConfig) {
            SelectionAction.entries
                .filter { it.zone == ActionZone.MIDDLE && it !in sanitizedConfig.middleActions }
        }
        ActionEditorAddSheet(
            actions = availableMiddle,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.copy(middleActions = config.middleActions + action)
                showAddMiddleSheet = false
            },
            onDismiss = { showAddMiddleSheet = false }
        )
    }

    if (showAddBottomSheet) {
        val availableBottom = remember(sanitizedConfig) {
            SelectionAction.entries
                .filter { it.zone == ActionZone.BOTTOM && it !in sanitizedConfig.bottomActions }
        }
        ActionEditorAddSheet(
            actions = availableBottom,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.copy(bottomActions = config.bottomActions + action)
                showAddBottomSheet = false
            },
            onDismiss = { showAddBottomSheet = false }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Live Preview — exact same dimensions/styling as SelectionSheet
// Uses solid background (non-blur fallback)
// ────────────────────────────────────────────────────────────────────────────────

@Composable
fun SelectionSheetPreview(
    modifier: Modifier = Modifier,
    config: SelectionSheetConfig,
    showTitlesOverride: Boolean? = null,
    count: Int = 3,
    selectAllActive: Boolean = false,
    infoVisible: Boolean = true,
    onClose: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onAction: ((SelectionAction) -> Unit)? = null,
) {
    val showTitlesSetting by rememberShowSelectionTitles()
    val showTitles = showTitlesOverride ?: showTitlesSetting
    val tintColor = MaterialTheme.colorScheme.onSurface
    val barSurfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val pillShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top row: addon pills — matching SelectionAddon, scrollable with fade
        val topScrollState = rememberScrollState()
        val rightAligned = config.topActionsRightAligned
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeWidth = 32.dp.toPx()
                    if (topScrollState.canScrollForward) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = size.width - fadeWidth,
                                endX = size.width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            if (rightAligned) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CLOSE stays on the left
                    if (SelectionAction.CLOSE in config.topActions) {
                        Row(
                            modifier = Modifier
                                .background(
                                    color = barSurfaceColor,
                                    shape = pillShape
                                )
                                .clip(pillShape)
                                .clickable(enabled = onClose != null) { onClose?.invoke() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Outlined.Close,
                                colorFilter = ColorFilter.tint(tintColor),
                                contentDescription = null
                            )
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = tintColor
                            )
                        }
                    }
                    // Remaining actions pushed to the right, scrollable
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(topScrollState),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        config.topActions.forEach { action ->
                            if (action != SelectionAction.CLOSE &&
                                (action != SelectionAction.INFO || infoVisible)
                            ) {
                                TopActionPreviewPill(
                                    action = action,
                                    tintColor = tintColor,
                                    barSurfaceColor = barSurfaceColor,
                                    pillShape = pillShape,
                                    count = count,
                                    selectAllActive = selectAllActive,
                                    onClick = when (action) {
                                        SelectionAction.SELECT_ALL -> onSelectAll
                                        else -> onAction?.let { handler -> ({ handler(action) }) }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(topScrollState),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    config.topActions.forEach { action ->
                        if (action != SelectionAction.INFO || infoVisible) {
                            TopActionPreviewPill(
                                action = action,
                                tintColor = tintColor,
                                barSurfaceColor = barSurfaceColor,
                                pillShape = pillShape,
                                count = count,
                                selectAllActive = selectAllActive,
                                onClick = when (action) {
                                    SelectionAction.CLOSE -> onClose
                                    SelectionAction.SELECT_ALL -> onSelectAll
                                    else -> onAction?.let { handler -> ({ handler(action) }) }
                                },
                            )
                        }
                    }
                }
            }
        }
        // Middle: full-width pill buttons
        config.middleActions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = barSurfaceColor,
                        shape = pillShape
                    )
                    .clip(pillShape)
                    .clickable(enabled = onAction != null) { onAction?.invoke(action) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    modifier = Modifier.size(24.dp),
                    imageVector = action.icon,
                    colorFilter = ColorFilter.tint(tintColor),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(action.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = tintColor,
                    maxLines = 1,
                )
            }
        }
        // Bottom: action bar — matching SelectionBarColumn, scrollable
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = barSurfaceColor,
                    shape = pillShape
                )
                .clip(pillShape)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.bottomActions.isEmpty()) {
                Text(
                    text = stringResource(Res.string.minimum_one_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tintColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                config.bottomActions.forEach { action ->
                    Column(
                        modifier = Modifier
                            .clickable(enabled = onAction != null) { onAction?.invoke(action) }
                            .then(
                                if (config.fillActionBar) Modifier.weight(1f)
                                else Modifier.defaultMinSize(
                                    minWidth = if (showTitles) 80.dp else 64.dp
                                )
                            )
                            .defaultMinSize(
                                minHeight = if (showTitles) 80.dp else 64.dp
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .padding(top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            imageVector = action.icon,
                            colorFilter = ColorFilter.tint(tintColor),
                            contentDescription = null,
                            modifier = Modifier.height(32.dp)
                        )
                        if (showTitles) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(action.labelRes),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tintColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopActionPreviewPill(
    action: SelectionAction,
    tintColor: Color,
    barSurfaceColor: Color,
    pillShape: RoundedCornerShape,
    count: Int = 3,
    selectAllActive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // The select-all pill mirrors SelectAllAddon's active pose: Deselect icon,
    // clear-selection label, primary-container surface.
    val isSelectAll = action == SelectionAction.SELECT_ALL && selectAllActive
    val pillSurfaceColor = if (isSelectAll) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        barSurfaceColor
    }
    val pillContentColor = if (isSelectAll) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        tintColor
    }
    Row(
        modifier = Modifier
            .background(
                color = pillSurfaceColor,
                shape = pillShape
            )
            .clip(pillShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            imageVector = if (isSelectAll) Icons.Outlined.Deselect else action.icon,
            colorFilter = ColorFilter.tint(pillContentColor),
            contentDescription = null
        )
        Text(
            text = when (action) {
                SelectionAction.CLOSE -> count.toString()
                else -> if (isSelectAll) {
                    // commonApp's clear_selection string is module-internal here.
                    "Remove All"
                } else {
                    stringResource(action.labelRes)
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = pillContentColor
        )
    }
}
