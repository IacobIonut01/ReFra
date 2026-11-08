/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberedDerivedState


@Composable
fun OptionSheet(
    state: AppBottomSheetState,
    onDismiss: (() -> Unit)? = null,
    headerContent: @Composable (ColumnScope.() -> Unit)? = null,
    style: OptionLayoutStyle = OptionLayoutStyle.Column,
    vararg optionList: SnapshotStateList<OptionItem>
) {
    ExpressiveSheet(
        sheetState = state,
        onDismissRequest = { onDismiss?.invoke() },
    ) {
        headerContent?.invoke(this)
        optionList.forEachIndexed { index, list ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            OptionLayout(
                modifier = Modifier.fillMaxWidth(),
                style = style,
                optionList = list
            )
        }
    }
}

fun LazyListScope.SettingsOptionLayout(
    modifier: Modifier = Modifier,
    optionList: List<SettingsEntity>,
    slimLayout: Boolean = false,
    swipeToDismiss: Boolean = false,
    itemKey: ((Int, SettingsEntity) -> Any)? = { _, item -> item.toString() },
    onDismiss: ((SettingsEntity) -> Unit)? = null
) {
    itemsIndexed(
        items = optionList,
        key = itemKey
    ) { index, item ->
        val position: Position = remember(index, item) {
            when (index) {
                0 -> {
                    if (optionList.size == 1) Position.Alone
                    else Position.Top
                }

                optionList.lastIndex -> {
                    if (optionList[(index - 1).coerceAtLeast(0)] is SettingsEntity.Header) {
                        Position.Alone
                    } else Position.Bottom
                }

                else -> {
                    val previous = optionList[(index - 1).coerceAtLeast(0)]
                    val next = optionList[(index + 1).coerceAtMost(optionList.lastIndex)]
                    if (previous is SettingsEntity.Header && next is SettingsEntity.Header) {
                        Position.Alone
                    } else if (previous is SettingsEntity.Header) {
                        Position.Top
                    } else if (next is SettingsEntity.Header) {
                        Position.Bottom
                    } else {
                        Position.Middle
                    }
                }
            }
        }
        val newItem = remember(item) {
            when (item) {
                is SettingsEntity.Preference -> item.copy(
                    screenPosition = position,
                )

                is SettingsEntity.SwitchPreference -> item.copy(
                    screenPosition = position
                )

                is SettingsEntity.SeekPreference -> item.copy(
                    screenPosition = position
                )

                else -> item
            }
        }
        if (swipeToDismiss && newItem !is SettingsEntity.Header) {
            val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
                SwipeToDismissBoxValue.Settled,
                SwipeToDismissBoxDefaults.positionalThreshold
            )

            SwipeToDismissBox(
                state = swipeToDismissBoxState,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    // Rows are width-capped on large screens while this background spans the
                    // list, so it is drawn only while a swipe is actually revealing it.
                    if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                        return@SwipeToDismissBox
                    }
                    val shape by rememberedDerivedState(position) {
                        when (position) {
                            Position.Alone -> RoundedCornerShape(24.dp)
                            Position.Bottom -> RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 8.dp,
                                bottomStart = 24.dp,
                                bottomEnd = 24.dp
                            )

                            Position.Middle -> RoundedCornerShape(
                                topStart = 8.dp,
                                topEnd = 8.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 8.dp
                            )

                            Position.Top -> RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = 8.dp,
                                bottomEnd = 8.dp
                            )
                        }
                    }
                    val paddingModifier by rememberedDerivedState(position) {
                        when (position) {
                            Position.Alone -> Modifier.padding(bottom = 16.dp)
                            Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
                            Position.Middle -> Modifier.padding(vertical = 1.dp)
                            Position.Top -> Modifier.padding(bottom = 1.dp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(paddingModifier)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = shape
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                },
                onDismiss = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        onDismiss?.invoke(newItem)
                    }
                },
                content = {
                    SettingsItem(
                        modifier = modifier.animateItem(),
                        item = newItem,
                        slimLayout = slimLayout,
                        tintIcon = newItem.iconUri == null
                    )
                }
            )
        } else {
            SettingsItem(
                modifier = modifier.animateItem(),
                item = newItem,
                slimLayout = slimLayout,
                tintIcon = newItem.iconUri == null
            )
        }
    }
}
