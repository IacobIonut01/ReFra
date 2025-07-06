/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppBottomSheetState(
    skipPartiallyExpanded: Boolean = true,
    skipHiddenState: Boolean = false,
    positionalThreshold: Dp = 56.dp,
    velocityThreshold: Dp = 125.dp,
): AppBottomSheetState {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val density = LocalDensity.current
    val positionalThresholdToPx = { with(density) { positionalThreshold.toPx() } }
    val velocityThresholdToPx = { with(density) { velocityThreshold.toPx() } }
    return rememberSaveable(saver = AppBottomSheetState.Saver(
        positionalThreshold = positionalThresholdToPx,
        velocityThreshold = velocityThresholdToPx,
        skipHiddenState = skipHiddenState
    )) {
        AppBottomSheetState(sheetState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class AppBottomSheetState(
    val sheetState: SheetState
) {

    var isVisible by mutableStateOf(false)
        private set

    internal constructor(sheetState: SheetState, isVisible: Boolean) : this(sheetState) {
        this.isVisible = isVisible
    }

    suspend fun show() {
        if (!isVisible) {
            isVisible = true
            delay(10)
            sheetState.show()
        }
    }

    suspend fun hide() {
        if (isVisible) {
            sheetState.hide()
            delay(10)
            isVisible = false
        }
    }

    companion object {
        fun Saver(
            skipPartiallyExpanded: Boolean = true,
            confirmValueChange: (SheetValue) -> Boolean = { true },
            positionalThreshold: () -> Float,
            velocityThreshold: () -> Float,
            skipHiddenState: Boolean
        ) = Saver<AppBottomSheetState, Pair<SheetValue, Boolean>>(
            save = { Pair(it.sheetState.currentValue, it.isVisible) },
            restore = { savedValue ->
                AppBottomSheetState(
                    SheetState(
                        skipPartiallyExpanded,
                        positionalThreshold,
                        velocityThreshold,
                        savedValue.first,
                        confirmValueChange,
                        skipHiddenState,
                    ),
                    savedValue.second
                )
            }
        )
    }
}
