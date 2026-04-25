package com.dot.gallery.feature_node.presentation.edit.components.core

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature_node.presentation.util.verticalFadingEdge
import com.dot.gallery.ui.theme.GalleryTheme
import kotlin.math.roundToInt

@Composable
fun VerticalScrubber(
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
    spacerHeight: Dp = 4.dp,
    normalWidth: Dp = 16.dp,
    normalHeight: Dp = 1.dp,
    arrowWidth: Dp = 32.dp,
    textSize: TextUnit = 18.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    normalColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
    highlightedColor: Color = MaterialTheme.colorScheme.onSurface,
    arrowColor: Color = MaterialTheme.colorScheme.inversePrimary,
    @FloatRange(from = 0.0, to = 1.0)
    verticalFade: Float = 0.3f,
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
    require(verticalFade in 0f..1f) { "horizontalFade should be between 0f and 1f" }
    require(maxValue > 0) { "maxValue should be greater than 0" }
    if (allowNegative) {
        require(minValue < 0) { "minValue should be less than 0" }
    } else {
        require(minValue >= 0) { "minValue should be greater than or equal to 0" }
    }
    var currentValueInternal by rememberSaveable { mutableFloatStateOf(clampedCurrentValue) }
    Row(
        modifier = modifier.fillMaxHeight()
    ) {
        Text(
            text = displayValue(currentValueInternal),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontSize = textSize,
            modifier = Modifier
                .padding(end = 16.dp)
                .align(Alignment.CenterVertically)
                .defaultMinSize(minWidth = 32.dp)
        )
        BoxWithConstraints(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxHeight()
        ) {
            val density = LocalDensity.current
            val scrubberPadding = remember(constraints) { (constraints.maxHeight / density.density) / 2 }

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

            LaunchedEffect(state) {
                snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }.collect { (index, isScrollInProgress) ->
                    fun value(raw: Int): Float = raw.toFloat() / (if (allowNegative) middleIndex.toFloat() else steps.toFloat())
                    currentValueInternal = if (index < middleIndex) minValue - minValue * value(index)
                    else if (index > middleIndex) value(index) * maxValue - (if (allowNegative) maxValue else 0f)
                    else if (allowNegative) 0f
                    else minValue
                    onValueChanged(isScrollInProgress, currentValueInternal)
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
            LazyColumn(
                state = state,
                modifier = Modifier
                    .width(arrowWidth)
                    .verticalFadingEdge(verticalFade),
                contentPadding = PaddingValues(vertical = scrubberPadding.dp),
                horizontalAlignment = Alignment.End,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
            ) {
                repeat(if (allowNegative) 10 else 5) {
                    item {
                        Spacer(
                            modifier = Modifier
                                .width(if (it == if (allowNegative) 5 else 0) arrowWidth else normalWidth)
                                .height(normalHeight)
                                .background(highlightedColor)
                        )
                    }
                    item {
                        Spacer(
                            modifier = Modifier
                                .width(normalWidth)
                                .height(spacerHeight)
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
                                    .width(normalWidth)
                                    .height(spacerHeight)
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
                    .width(arrowWidth)
                    .height(normalHeight)
                    .background(arrowColor)
            )
        }
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun HorizontalScrubberPreview2() {
    GalleryTheme(darkTheme = true) {
        Surface(color = Color.Black) {
            Column {

                VerticalScrubber(
                    currentValue = 0f,
                    defaultValue = 0f,
                    maxValue = 3f,
                    minValue = -5f,
                    displayValue = { (it * 100).roundToInt().toString() }
                ) { _, _ ->

                }


            }
        }
    }
}