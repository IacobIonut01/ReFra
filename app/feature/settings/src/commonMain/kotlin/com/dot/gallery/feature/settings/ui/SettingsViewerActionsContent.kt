/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.core.ui.mediaview.ViewerActionBarConfig
import com.dot.gallery.core.ui.mediaview.ViewerActionPlacement
import com.dot.gallery.core.ui.mediaview.ViewerQuickAction
import com.dot.gallery.core.ui.mediaview.rememberViewerActionBarConfig
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_delete
import com.dot.gallery.feature.settings.generated.resources.minimum_one_action
import com.dot.gallery.feature.settings.generated.resources.reset_to_defaults
import com.dot.gallery.feature.settings.generated.resources.viewer_action_bar
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_description
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_list_header
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_slot_empty
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_slot_next_to_back
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_slot_next_to_info
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_top_bar_header
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Editor for the media viewer's quick actions: the bottom pill plus the two
 * configurable top-bar slots next to Back and Info. Actions live in exactly one
 * place; pick which actions are shown, drag the bottom ones into order, and
 * preview the result. Rendered by `feature.viewer`'s `MediaViewQuickBottomBar`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsViewerActionsContent(supportsFavorites: Boolean = true, supportsCast: Boolean = false) {
    var config by rememberViewerActionBarConfig()
    val sanitizedConfig = remember(config) { config.sanitized() }
    // Favorite needs MediaStore favorites (API 30+) and Cast needs the INTERNET
    // permission; unsupported actions are neither listed nor offered, so they
    // cannot be configured.
    fun ViewerQuickAction.isSupported() =
        (supportsFavorites || this != ViewerQuickAction.FAVORITE) &&
            (supportsCast || this != ViewerQuickAction.CAST)
    val displayActions = remember(sanitizedConfig, supportsFavorites, supportsCast) {
        sanitizedConfig.actions.filter { it.isSupported() }
    }
    val topStartAction = sanitizedConfig.topStart?.takeIf { it.isSupported() }
    val topEndAction = sanitizedConfig.topEnd?.takeIf { it.isSupported() }
    val topSlotActions = remember(topStartAction, topEndAction) {
        setOfNotNull(topStartAction, topEndAction)
    }

    val listState = rememberLazyListState()

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var topStartSheet by rememberSaveable { mutableStateOf(false) }
    var topEndSheet by rememberSaveable { mutableStateOf(false) }

    // A slot picker offers every supported action except the slot's current one
    // and the sole remaining bottom action — moving that one up would empty the
    // bottom bar, which `withTopSlot` refuses anyway.
    fun availableForTopSlot(current: ViewerQuickAction?): List<ViewerQuickAction> =
        ViewerQuickAction.entries
            .filter { it.isSupported() }
            .filter { it != current }
            .filter { it != displayActions.singleOrNull() }

    fun availableForBottom(): List<ViewerQuickAction> =
        ViewerQuickAction.entries
            .filter { it.isSupported() }
            .filter { it !in displayActions }
            .filter { it !in topSlotActions }

    // Drag-to-reorder state
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Approximate item height for swap threshold
    val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    fun moveAction(from: Int, to: Int) {
        val list = displayActions.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        config = config.copy(actions = list)
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.viewer_action_bar),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current).settingsFocusGroup(),
            userScrollEnabled = draggingIndex < 0,
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
                ViewerActionBarPreview(
                    actions = displayActions,
                    topStart = topStartAction,
                    topEnd = topEndAction,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                )
            }

            // ── Description ──
            item(key = "description") {
                Text(
                    text = stringResource(Res.string.viewer_actions_description),
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
                        onClick = { config = ViewerActionBarConfig() }
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

            // ── Top bar slots ──
            item(key = "top_bar_header") {
                ActionEditorSectionHeader(
                    title = stringResource(Res.string.viewer_actions_top_bar_header),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }
            item(key = "top_slot_start") {
                ViewerTopSlotRow(
                    title = stringResource(Res.string.viewer_actions_slot_next_to_back),
                    action = topStartAction,
                    position = Position.Top,
                    onClick = { topStartSheet = true },
                    onClear = {
                        config = config.withTopSlot(ViewerActionPlacement.TOP_START, null)
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }
            item(key = "top_slot_end") {
                ViewerTopSlotRow(
                    title = stringResource(Res.string.viewer_actions_slot_next_to_info),
                    action = topEndAction,
                    position = Position.Bottom,
                    onClick = { topEndSheet = true },
                    onClear = {
                        config = config.withTopSlot(ViewerActionPlacement.TOP_END, null)
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Actions header ──
            item(key = "actions_header") {
                ActionEditorSectionHeader(
                    title = stringResource(Res.string.viewer_actions_list_header),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // ── Actions list ──
            itemsIndexed(
                items = displayActions,
                key = { _, action -> "viewer_${action.name}" }
            ) { index, action ->
                val position = actionEditorPosition(index, displayActions.size)
                val isDragged = draggingIndex == index
                ActionEditorListItem(
                    entry = ActionEditorEntry(
                        icon = action.icon,
                        label = stringResource(action.labelRes),
                        description = stringResource(action.descriptionRes),
                    ),
                    position = position,
                    isLocked = false,
                    canRemove = displayActions.size > 1,
                    isDragging = isDragged,
                    dragOffset = if (isDragged) dragOffsetY else 0f,
                    onDragStart = {
                        draggingIndex = index
                        dragOffsetY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { delta ->
                        if (draggingIndex >= 0) {
                            dragOffsetY += delta
                            val swapThreshold = itemHeightPx * 0.5f
                            if (dragOffsetY > swapThreshold &&
                                draggingIndex < displayActions.lastIndex
                            ) {
                                moveAction(draggingIndex, draggingIndex + 1)
                                draggingIndex++
                                dragOffsetY -= itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -swapThreshold && draggingIndex > 0) {
                                moveAction(draggingIndex, draggingIndex - 1)
                                draggingIndex--
                                dragOffsetY += itemHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        draggingIndex = -1
                        dragOffsetY = 0f
                    },
                    onMoveUp = if (index > 0) {
                        { moveAction(index, index - 1) }
                    } else null,
                    onMoveDown = if (index < displayActions.lastIndex) {
                        { moveAction(index, index + 1) }
                    } else null,
                    onRemove = {
                        if (displayActions.size > 1) {
                            config = config.copy(
                                actions = displayActions
                                    .toMutableList()
                                    .apply { removeAt(index) }
                            )
                        }
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                )
            }

            // ── Add action ──
            item(key = "add_action") {
                val available = remember(
                    displayActions, topSlotActions, supportsFavorites, supportsCast
                ) {
                    availableForBottom()
                }
                if (available.isNotEmpty()) {
                    ActionEditorAddButton(
                        onClick = { showAddSheet = true },
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

    if (showAddSheet) {
        val available = remember(
            displayActions, topSlotActions, supportsFavorites, supportsCast
        ) {
            availableForBottom()
        }
        ActionEditorAddSheet(
            actions = available,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.copy(actions = config.actions + action)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    if (topStartSheet) {
        val available = remember(
            displayActions, topStartAction, supportsFavorites, supportsCast
        ) {
            availableForTopSlot(topStartAction)
        }
        ActionEditorAddSheet(
            actions = available,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.withTopSlot(ViewerActionPlacement.TOP_START, action)
                topStartSheet = false
            },
            onDismiss = { topStartSheet = false }
        )
    }

    if (topEndSheet) {
        val available = remember(
            displayActions, topEndAction, supportsFavorites, supportsCast
        ) {
            availableForTopSlot(topEndAction)
        }
        ActionEditorAddSheet(
            actions = available,
            entry = { action ->
                ActionEditorEntry(
                    icon = action.icon,
                    label = stringResource(action.labelRes),
                    description = stringResource(action.descriptionRes),
                )
            },
            onActionSelected = { action ->
                config = config.withTopSlot(ViewerActionPlacement.TOP_END, action)
                topEndSheet = false
            },
            onDismiss = { topEndSheet = false }
        )
    }
}

/**
 * Approximates the viewer's chrome on a dark viewer background: a mock top bar
 * with the two configurable slots inside the Back/Info pills, and the bottom
 * quick-action pill. Actions that the bar would currently hide are left out so
 * the preview matches what the user sees.
 */
@Composable
private fun ViewerActionBarPreview(
    actions: List<ViewerQuickAction>,
    topStart: ViewerQuickAction?,
    topEnd: ViewerQuickAction?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF15161A))
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Mock top bar: [Back | start] pinned left, [end | Info] pinned right.
            // Empty slots take no segment, like the real segmented group.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewPill {
                    PreviewSegment(Icons.AutoMirrored.Outlined.ArrowBack)
                    if (topStart != null) {
                        PreviewDivider()
                        PreviewSegment(topStart.icon)
                    }
                }
                PreviewPill {
                    if (topEnd != null) {
                        PreviewSegment(topEnd.icon)
                        PreviewDivider()
                    }
                    PreviewSegment(Icons.Outlined.Info)
                }
            }
            if (actions.isEmpty()) {
                Text(
                    text = stringResource(Res.string.minimum_one_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            } else {
                // A long bar scrolls inside the card instead of running past its edges.
                PreviewPill(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) {
                            PreviewDivider()
                        }
                        PreviewSegment(action.icon)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .then(modifier)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RowScope.PreviewSegment(icon: ImageVector) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun PreviewDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

/**
 * One configurable top-bar slot row: shows the slot's current action (icon,
 * label and description) or an empty state. Tapping opens the action picker;
 * the delete control clears the slot without returning the action to the
 * bottom list. Styled like [ActionEditorListItem] but fixed — slots cannot be
 * dragged or reordered.
 */
@Composable
private fun ViewerTopSlotRow(
    title: String,
    action: ViewerQuickAction?,
    position: Position,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = when (position) {
        Position.Alone -> RoundedCornerShape(24.dp)
        Position.Top -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        )
        Position.Middle -> RoundedCornerShape(8.dp)
        Position.Bottom -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
    }
    val paddingModifier = when (position) {
        Position.Alone -> Modifier.padding(bottom = 16.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }
    Box(
        modifier = modifier.then(paddingModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = action?.icon ?: Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp),
                    tint = if (action != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = action?.let {
                            "${stringResource(it.labelRes)} — ${stringResource(it.descriptionRes)}"
                        } ?: stringResource(Res.string.viewer_actions_slot_empty),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (action != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, stringResource(Res.string.action_delete))
                    }
                }
            }
        }
    }
}
