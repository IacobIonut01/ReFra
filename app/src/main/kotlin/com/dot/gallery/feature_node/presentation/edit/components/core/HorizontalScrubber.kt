package com.dot.gallery.feature_node.presentation.edit.components.core

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.horizontalFadingEdge
import com.dot.gallery.ui.theme.GalleryTheme
import kotlin.math.roundToInt

@Composable
fun HorizontalScrubber(
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
    spacerWidth: Dp = 3.dp,
    normalWidth: Dp = 1.dp,
    normalHeight: Dp = 10.dp,
    arrowHeight: Dp = 18.dp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    normalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
    highlightedColor: Color = MaterialTheme.colorScheme.onSurface,
    arrowColor: Color = MaterialTheme.colorScheme.inversePrimary,
    @FloatRange(from = 0.0, to = 1.0)
    horizontalFade: Float = 0.3f,
    minValue: Float = -40f,
    maxValue: Float = 40f,
    defaultValue: Float = 0f,
    currentValue: Float = defaultValue,
    displayValue: (Float) -> String = { (it * 10f).roundToInt().toString() },
    onValueChanged: (isScrolling: Boolean, newValue: Float) -> Unit
) {
    require(minValue < maxValue) { "minValue($minValue) should be less than maxValue($maxValue)" }
    require(defaultValue in minValue..maxValue) { "defaultValue should be between minValue and maxValue" }
    val clampedCurrentValue = currentValue.coerceIn(minValue, maxValue)
    require(horizontalFade in 0f..1f) { "horizontalFade should be between 0f and 1f" }
    require(maxValue > 0) { "maxValue should be greater than 0" }
    if (allowNegative) {
        require(minValue < 0) { "minValue should be less than 0" }
    } else {
        require(minValue >= 0) { "minValue should be greater than or equal to 0" }
    }
    var currentValueInternal by rememberSaveable { mutableFloatStateOf(clampedCurrentValue) }
    val view = LocalView.current
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = displayValue(currentValueInternal),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally)
        )
        BoxWithConstraints(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth()
        ) {
            val density = LocalDensity.current
            val scrubberPadding = remember(constraints) { (constraints.maxWidth / density.density) / 2 }

            val steps = remember(allowNegative) { if (allowNegative) 200 else 100 }
            val middleIndex = remember(steps) {
                if (allowNegative) steps / 2 else 0
            }

            /**
             * Range 0 (left) - 100 (middle) - 200 (right)
             */
            /**
             * Range 0 (left) - 100 (middle) - 200 (right)
             */
            val state = rememberLazyListState(
                initialFirstVisibleItemIndex = if (allowNegative) {
                    if (clampedCurrentValue >= defaultValue) {
                        middleIndex + (clampedCurrentValue / maxValue * middleIndex).roundToInt()
                    } else {
                        middleIndex - (clampedCurrentValue / minValue * middleIndex).roundToInt()
                    }
                } else {
                    (clampedCurrentValue * steps.toFloat() / maxValue).roundToInt()
                }
            )

            val defaultIndex = remember(allowNegative, middleIndex, defaultValue, minValue, maxValue, steps) {
                if (allowNegative) {
                    if (defaultValue >= 0f) {
                        middleIndex + (defaultValue / maxValue * middleIndex).roundToInt()
                    } else {
                        middleIndex - (defaultValue / minValue * middleIndex).roundToInt()
                    }
                } else {
                    (defaultValue * steps.toFloat() / maxValue).roundToInt()
                }
            }
            val snapThreshold = 3
            LaunchedEffect(state) {
                snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }.collect { (index, isScrollInProgress) ->
                    fun value(raw: Int): Float = raw.toFloat() / (if (allowNegative) middleIndex.toFloat() else steps.toFloat())
                    currentValueInternal = if (index < middleIndex) minValue - minValue * value(index)
                    else if (index > middleIndex) value(index) * maxValue - (if (allowNegative) maxValue else 0f)
                    else if (allowNegative) 0f
                    else minValue
                    onValueChanged(isScrollInProgress, currentValueInternal)

                    if (!isScrollInProgress && index != defaultIndex
                        && index in (defaultIndex - snapThreshold)..(defaultIndex + snapThreshold)
                    ) {
                        state.animateScrollToItem(defaultIndex)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            LaunchedEffect(clampedCurrentValue) {
                if (clampedCurrentValue != currentValueInternal) {
                    state.animateScrollToItem(
                        index = if (allowNegative) {
                            if (clampedCurrentValue >= defaultValue) {
                                middleIndex + (clampedCurrentValue / maxValue * middleIndex).roundToInt()
                            } else {
                                middleIndex - (clampedCurrentValue / minValue * middleIndex).roundToInt()
                            }
                        } else {
                            (clampedCurrentValue * steps.toFloat() / maxValue).roundToInt()
                        }
                    )
                }
            }
            LazyRow(
                state = state,
                modifier = Modifier
                    .wrapContentWidth()
                    .horizontalFadingEdge(horizontalFade),
                contentPadding = PaddingValues(horizontal = scrubberPadding.dp),
                verticalAlignment = Alignment.Bottom,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
            ) {
                repeat(if (allowNegative) 10 else 5) {
                    item {
                        Spacer(
                            modifier = Modifier
                                .height(if (it == if (allowNegative) 5 else 0) arrowHeight else normalHeight)
                                .width(normalWidth)
                                .background(highlightedColor)
                        )
                    }
                    item {
                        Spacer(
                            modifier = Modifier
                                .height(normalHeight)
                                .width(spacerWidth)
                        )
                    }
                    repeat(9) {
                        item {
                            Spacer(
                                modifier = Modifier
                                    .height(normalHeight)
                                    .width(normalWidth)
                                    .background(normalColor)
                            )
                        }
                        item {
                            Spacer(
                                modifier = Modifier
                                    .height(normalHeight)
                                    .width(spacerWidth)
                            )
                        }
                    }
                }
                item {
                    Spacer(
                        modifier = Modifier
                            .height(normalHeight)
                            .width(normalWidth)
                            .background(highlightedColor)
                    )
                }
            }

            /**
             * Selection Arrow
             */

            /**
             * Selection Arrow
             */
            Spacer(
                modifier = Modifier
                    .height(arrowHeight)
                    .width(normalWidth)
                    .background(arrowColor)
            )
        }
    }
}

@Preview
@Composable
private fun HorizontalScrubberPreview2() {
    GalleryTheme(darkTheme = true) {
        Surface(color = Color.Black) {
            Column {

                HorizontalScrubber(
                    currentValue = -4f,
                    defaultValue = 0f,
                    maxValue = 3f,
                    minValue = -5f,
                    displayValue = { (it * 100).roundToInt().toString() }
                ) { _, _ ->

                }

                HorizontalScrubber(
                    allowNegative = false,
                    currentValue = 2f,
                    minValue = 0f,
                    maxValue = 4f,
                    defaultValue = 0f,
                    displayValue = { (it * 100).roundToInt().toString() }
                ) { _, _ ->

                }
            }
        }
    }
}