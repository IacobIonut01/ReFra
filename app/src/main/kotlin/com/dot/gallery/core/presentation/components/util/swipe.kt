package com.dot.gallery.core.presentation.components.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager
import kotlin.math.roundToInt

@Composable
fun Modifier.swipe(
    enabled: Boolean = true,
    onOffset: (IntOffset) -> Unit = {},
    onSwipeDown: () -> Unit
): Modifier {
    var delta by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val feedbackManager = rememberFeedbackManager()
    var isVibrating by remember { mutableStateOf(false) }
    val animatedDelta by animateFloatAsState(
        label = "animatedDelta",
        targetValue = if (isDragging) delta else 0f,
        animationSpec = spring()
    )
    return this then Modifier
        .pointerInput(enabled) {
            if (enabled) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isVibrating = false
                        isDragging = true
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0f || delta > 0f) {
                            delta += dragAmount
                            delta = delta.coerceIn(0f, 400f)
                            if (!isVibrating && delta == 400f) {
                                feedbackManager.vibrate()
                                isVibrating = true
                            }
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        isVibrating = false
                        isDragging = false
                        if (delta == 400f) {
                            onSwipeDown()
                        }
                        delta = 0f
                    },
                    onDragCancel = {
                        isVibrating = false
                        isDragging = false
                        delta = 0f
                    }
                )
            }
        }
        .offset {
            IntOffset(0, if (isDragging) delta.roundToInt() else animatedDelta.roundToInt()).also(onOffset)
        }
}