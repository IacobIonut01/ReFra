/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.core.Position
import com.dot.gallery.core.ui.components.rememberSettingsFocusState
import com.dot.gallery.core.ui.components.settingsFocusTarget
import com.dot.gallery.core.ui.util.rememberedDerivedState
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.action_delete
import com.dot.gallery.feature.settings.generated.resources.action_locked
import com.dot.gallery.feature.settings.generated.resources.add_action
import com.dot.gallery.feature.settings.generated.resources.move_down
import com.dot.gallery.feature.settings.generated.resources.move_up
import com.dot.gallery.feature.settings.generated.resources.no_actions_available

/** Resolved display data for one row in an action editor. */
internal data class ActionEditorEntry(
    val icon: ImageVector,
    val label: String,
    val description: String,
)

internal fun actionEditorPosition(index: Int, size: Int): Position = when {
    size == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == size - 1 -> Position.Bottom
    else -> Position.Middle
}

// ────────────────────────────────────────────────────────────────────────────────
// Action row — matches SettingsItem design: animated corner radii, Position-based
// shapes, surfaceContainer bg, swipe-to-delete, drag handle
// ────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionEditorListItem(
    entry: ActionEditorEntry,
    position: Position,
    isLocked: Boolean,
    canRemove: Boolean = true,
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val focusState = rememberSettingsFocusState()

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
                topStart = fullCornerRadius,
                topEnd = fullCornerRadius,
                bottomStart = normalCornerRadius,
                bottomEnd = normalCornerRadius
            )
            Position.Middle -> RoundedCornerShape(normalCornerRadius)
            Position.Bottom -> RoundedCornerShape(
                topStart = normalCornerRadius,
                topEnd = normalCornerRadius,
                bottomStart = fullCornerRadius,
                bottomEnd = fullCornerRadius
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

    val dragModifier = if (!isLocked) {
        Modifier.pointerInput(Unit) {
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
    } else Modifier

    val itemContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = modifier
                    .settingsFocusTarget(focusState)
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(
                        color = if (focusState.hasFocus) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            backgroundColor
                        }
                    )
                    .border(
                        width = if (focusState.hasFocus) 2.dp else 0.dp,
                        color = if (focusState.hasFocus) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = shape,
                    )
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

                    // Action icon
                    Image(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )

                    // Label + description
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (onMoveUp != null) {
                        IconButton(onClick = onMoveUp) {
                            Icon(Icons.Outlined.KeyboardArrowUp, stringResource(Res.string.move_up))
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(onClick = onMoveDown) {
                            Icon(Icons.Outlined.KeyboardArrowDown, stringResource(Res.string.move_down))
                        }
                    }
                    if (!isLocked && canRemove) {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, stringResource(Res.string.action_delete))
                        }
                    }

                    // Locked indicator
                    if (isLocked) {
                        Icon(
                            Icons.Outlined.Lock, stringResource(Res.string.action_locked),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    // Outer wrapper: drag gesture sits above SwipeToDismissBox so it isn't intercepted
    Box(
        modifier = Modifier
            .then(paddingModifier)
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = elevation
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .then(dragModifier)
    ) {
        if (!isLocked && canRemove && !isDragging) {
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { totalDistance -> totalDistance * 0.4f }
            )
            LaunchedEffect(dismissState.targetValue) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    onRemove()
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = true,
                content = { itemContent() }
            )
        } else {
            itemContent()
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Add Action Button — matches SettingsItem Alone position style
// ────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ActionEditorAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Add, null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.add_action),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Section Header — matches SettingsEntity.Header style
// ────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ActionEditorSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

// ────────────────────────────────────────────────────────────────────────────────
// Add Action Bottom Sheet — picker for available actions
// ────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ActionEditorAddSheet(
    actions: List<T>,
    entry: @Composable (T) -> ActionEditorEntry,
    onActionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(Res.string.add_action),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            if (actions.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_actions_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                actions.forEachIndexed { index, action ->
                    ActionEditorAddSheetItem(
                        entry = entry(action),
                        position = actionEditorPosition(index, actions.size),
                        onClick = { onActionSelected(action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionEditorAddSheetItem(
    entry: ActionEditorEntry,
    position: Position,
    onClick: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer

    val fullCornerRadius = 24.dp
    val normalCornerRadius = 8.dp

    val shape = when (position) {
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

    val paddingModifier = when (position) {
        Position.Alone -> Modifier.padding(bottom = 0.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(paddingModifier)
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(color = backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                imageVector = entry.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
