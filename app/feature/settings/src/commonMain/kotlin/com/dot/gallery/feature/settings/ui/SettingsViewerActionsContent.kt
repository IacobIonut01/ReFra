/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.core.ui.mediaview.ViewerActionBarConfig
import com.dot.gallery.core.ui.mediaview.ViewerQuickAction
import com.dot.gallery.core.ui.mediaview.rememberViewerActionBarConfig
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.minimum_one_action
import com.dot.gallery.feature.settings.generated.resources.reset_to_defaults
import com.dot.gallery.feature.settings.generated.resources.viewer_action_bar
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_description
import com.dot.gallery.feature.settings.generated.resources.viewer_actions_list_header
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Editor for the media viewer's bottom quick-action pill: pick which actions are
 * shown, drag them into order, and preview the result. The stored order is
 * applied by `feature.viewer`'s `MediaViewQuickBottomBar`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsViewerActionsContent(supportsFavorites: Boolean = true) {
    var config by rememberViewerActionBarConfig()
    val sanitizedConfig = remember(config) { config.sanitized() }
    // Favorite needs MediaStore favorites (API 30+); on older devices it is neither
    // listed nor offered, so it cannot be configured.
    val displayActions = remember(sanitizedConfig, supportsFavorites) {
        sanitizedConfig.actions.filter { supportsFavorites || it != ViewerQuickAction.FAVORITE }
    }

    val listState = rememberLazyListState()

    var showAddSheet by rememberSaveable { mutableStateOf(false) }

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
                val available = remember(displayActions, supportsFavorites) {
                    ViewerQuickAction.entries
                        .filter { supportsFavorites || it != ViewerQuickAction.FAVORITE }
                        .filter { it !in displayActions }
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
        val available = remember(displayActions, supportsFavorites) {
            ViewerQuickAction.entries
                .filter { supportsFavorites || it != ViewerQuickAction.FAVORITE }
                .filter { it !in displayActions }
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
}

/**
 * Approximates the viewer's segmented quick-action pill on a dark viewer
 * background. Actions that the bar would currently hide are left out so the
 * preview matches what the user sees.
 */
@Composable
private fun ViewerActionBarPreview(
    actions: List<ViewerQuickAction>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF15161A))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (actions.isEmpty()) {
            Text(
                text = stringResource(Res.string.minimum_one_action),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                    }
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
