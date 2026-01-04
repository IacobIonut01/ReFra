package com.dot.gallery.feature_node.presentation.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection

fun Modifier.safeSystemGesturesPadding(
    onlyRight: Boolean = false,
    onlyLeft: Boolean = false
): Modifier = composed {
    val gesturePadding = WindowInsets.systemGestures.asPaddingValues()
    if (onlyRight) {
        padding(end = gesturePadding.calculateEndPadding(LocalLayoutDirection.current))
    } else if (onlyLeft) {
        padding(start = gesturePadding.calculateStartPadding(LocalLayoutDirection.current))
    } else {
        padding(
            start = gesturePadding.calculateStartPadding(LocalLayoutDirection.current),
            end = gesturePadding.calculateEndPadding(LocalLayoutDirection.current)
        )
    }
}

fun Modifier.verticalFadingEdge(percentage: Float) = this.fadingEdge(
    Brush.verticalGradient(
        0f to Color.Transparent,
        percentage to Color.Red,
        1f - percentage to Color.Red,
        1f to Color.Transparent
    )
)

fun Modifier.horizontalFadingEdge(percentage: Float) = this.fadingEdge(
    Brush.horizontalGradient(
        0f to Color.Transparent,
        percentage to Color.Red,
        1f - percentage to Color.Red,
        1f to Color.Transparent
    )
)

fun Modifier.fadingEdge(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

/**
 * Conditionally applies a [Modifier] based on a [condition].
 */
fun Modifier.maybeApply(condition: Boolean, modifier: Modifier): Modifier {
    return if (condition) this.then(modifier) else this
}