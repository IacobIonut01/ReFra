package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

@Composable
fun AlphaBar(
    modifier: Modifier = Modifier,
    currentColor: Color,
    isSupportingPanel: Boolean,
    setColor: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pressOffset = remember { mutableStateOf(Offset.Zero) }

    val initialAlpha = remember(currentColor) {
        currentColor.alpha
    }

    Canvas(
        modifier = modifier
            .then(
                if (isSupportingPanel) Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                else Modifier
                    .height(32.dp)
                    .fillMaxWidth()
            )
            .clip(RoundedCornerShape(20))
            .emitDragGesture(interactionSource)
    ) {
        val drawScopeSize = size

        if (drawScopeSize.width > 0 && drawScopeSize.height > 0) {
            val gradient = if (isSupportingPanel) {
                Brush.verticalGradient(
                    colors = listOf(
                        currentColor.copy(alpha = 0f),
                        currentColor.copy(alpha = 1f)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    colors = listOf(
                        currentColor.copy(alpha = 0f),
                        currentColor.copy(alpha = 1f)
                    )
                )
            }

            drawRoundRect(
                brush = gradient,
                topLeft = Offset.Zero,
                size = drawScopeSize,
                cornerRadius = CornerRadius(20f, 20f)
            )

            val initialOffset = if (isSupportingPanel) {
                initialAlpha * drawScopeSize.height
            } else {
                initialAlpha * drawScopeSize.width
            }
            pressOffset.value = if (isSupportingPanel) {
                Offset(0f, initialOffset)
            } else {
                Offset(initialOffset, 0f)
            }

            fun pointToAlpha(point: Float): Float {
                val size = if (isSupportingPanel) drawScopeSize.height else drawScopeSize.width
                val pos = point.coerceIn(0f..size)
                return pos / size
            }

            scope.collectForPress(interactionSource) { pressPosition ->
                val pressPos = if (isSupportingPanel) pressPosition.y else pressPosition.x
                val selectedAlpha = pointToAlpha(pressPos)
                pressOffset.value = if (isSupportingPanel) {
                    Offset(0f, pressPos)
                } else {
                    Offset(pressPos, 0f)
                }
                setColor(selectedAlpha)
            }

            drawRoundRect(
                color = Color.White,
                topLeft = if (isSupportingPanel) {
                    Offset(0f, pressOffset.value.y - 8.dp.toPx())
                } else {
                    Offset(pressOffset.value.x - 8.dp.toPx(), 0f)
                },
                size = if (isSupportingPanel) {
                    Size(size.width, size.width / 3)
                } else {
                    Size(size.height / 3, size.height)
                },
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
fun SaturationBar(
    modifier: Modifier = Modifier,
    currentColor: Color,
    isSupportingPanel: Boolean,
    setColor: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pressOffset = remember { mutableStateOf(Offset.Zero) }

    val hsv = remember(currentColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(currentColor.toArgb(), hsv)
        hsv
    }

    val initialSaturation = remember(hsv) { hsv[1] }
    val currentHue = remember(hsv) { hsv[0] }
    val currentAlpha = remember(currentColor) { currentColor.alpha }

    Canvas(
        modifier = modifier
            .then(
                if (isSupportingPanel) Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                else Modifier
                    .height(32.dp)
                    .fillMaxWidth()
            )
            .clip(RoundedCornerShape(20))
            .emitDragGesture(interactionSource)
    ) {
        val drawScopeSize = size

        if (drawScopeSize.width > 0 && drawScopeSize.height > 0) {
            val bitmap = Bitmap.createBitmap(
                size.width.toInt(),
                size.height.toInt(),
                Bitmap.Config.ARGB_8888
            )
            val saturationCanvas = android.graphics.Canvas(bitmap)
            val saturationPanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            val saturationColors = IntArray(
                if (isSupportingPanel) saturationPanel.height().toInt() else saturationPanel.width().toInt()
            )
            for (i in saturationColors.indices) {
                saturationColors[i] = AndroidColor.HSVToColor(
                    (currentAlpha * 255).toInt(),
                    floatArrayOf(currentHue, i / (if (isSupportingPanel) saturationPanel.height() else saturationPanel.width()), 1f)
                )
            }
            val linePaint = Paint()
            linePaint.strokeWidth = 0F
            for (i in saturationColors.indices) {
                linePaint.color = saturationColors[i]
                if (isSupportingPanel) {
                    saturationCanvas.drawLine(0f, i.toFloat(), saturationPanel.right, i.toFloat(), linePaint)
                } else {
                    saturationCanvas.drawLine(i.toFloat(), 0F, i.toFloat(), saturationPanel.bottom, linePaint)
                }
            }
            drawBitmap(bitmap = bitmap, panel = saturationPanel)

            val initialOffset = if (isSupportingPanel) {
                initialSaturation * drawScopeSize.height
            } else {
                initialSaturation * drawScopeSize.width
            }
            pressOffset.value = if (isSupportingPanel) {
                Offset(0f, initialOffset)
            } else {
                Offset(initialOffset, 0f)
            }

            fun pointToSaturation(point: Float): Float {
                val size = if (isSupportingPanel) saturationPanel.height() else saturationPanel.width()
                val pos = point.coerceIn(0f..size)
                return pos / size
            }

            scope.collectForPress(interactionSource) { pressPosition ->
                val pressPos = if (isSupportingPanel) pressPosition.y else pressPosition.x
                val selectedSaturation = pointToSaturation(pressPos)
                pressOffset.value = if (isSupportingPanel) {
                    Offset(0f, pressPos)
                } else {
                    Offset(pressPos, 0f)
                }
                setColor(selectedSaturation)
            }

            drawRoundRect(
                color = Color.White,
                topLeft = if (isSupportingPanel) {
                    Offset(0f, pressOffset.value.y - 8.dp.toPx())
                } else {
                    Offset(pressOffset.value.x - 8.dp.toPx(), 0f)
                },
                size = if (isSupportingPanel) {
                    Size(size.width, size.width / 3)
                } else {
                    Size(size.height / 3, size.height)
                },
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

@Composable
fun HueBar(
    modifier: Modifier = Modifier,
    currentColor: Color,
    isSupportingPanel: Boolean,
    supportingBarSize: Dp = 48.dp,
    nonSupportingBarSize: Dp = 32.dp,
    enabled: Boolean = true,
    setColor: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pressOffset = remember { mutableStateOf(Offset.Zero) }

    val hsv = remember(currentColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(currentColor.toArgb(), hsv)
        hsv
    }

    val currentSaturation = remember(hsv) { hsv[1] }
    val initialHue = remember(hsv) { hsv[0] }
    val currentAlpha = remember(currentColor, enabled) { currentColor.alpha * if (enabled) 1f else 0.2f }

    Canvas(
        modifier = Modifier
            .then(
                if (isSupportingPanel) Modifier
                    .width(supportingBarSize)
                    .fillMaxHeight()
                else Modifier
                    .height(nonSupportingBarSize)
                    .fillMaxWidth()
            )
            .then(modifier)
            .clip(RoundedCornerShape(20))
            .emitDragGesture(interactionSource)
    ) {
        val drawScopeSize = size

        if (drawScopeSize.width > 0 && drawScopeSize.height > 0) {
            val bitmap = createBitmap(size.width.toInt(), size.height.toInt())
            val hueCanvas = android.graphics.Canvas(bitmap)
            val huePanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            val hueColors = IntArray(
                if (isSupportingPanel) huePanel.height().toInt() else huePanel.width().toInt()
            )
            var hue = 0f
            for (i in hueColors.indices) {
                hueColors[i] = AndroidColor.HSVToColor(
                    (currentAlpha * 255).toInt(),
                    floatArrayOf(hue, currentSaturation, 1f)
                )
                hue += 360f / hueColors.size
            }
            val linePaint = Paint()
            linePaint.strokeWidth = 0F
            for (i in hueColors.indices) {
                linePaint.color = hueColors[i]
                if (isSupportingPanel) {
                    hueCanvas.drawLine(0f, i.toFloat(), huePanel.right, i.toFloat(), linePaint)
                } else {
                    hueCanvas.drawLine(i.toFloat(), 0F, i.toFloat(), huePanel.bottom, linePaint)
                }
            }
            drawBitmap(bitmap = bitmap, panel = huePanel)

            val initialOffset = if (isSupportingPanel) {
                initialHue / 360f * drawScopeSize.height
            } else {
                initialHue / 360f * drawScopeSize.width
            }
            pressOffset.value = if (isSupportingPanel) {
                Offset(0f, initialOffset)
            } else {
                Offset(initialOffset, 0f)
            }

            fun pointToHue(point: Float): Float {
                val size = if (isSupportingPanel) huePanel.height() else huePanel.width()
                val pos = point.coerceIn(0f..size)
                return pos * 360f / size
            }

            scope.collectForPress(interactionSource) { pressPosition ->
                val pressPos = if (isSupportingPanel) pressPosition.y else pressPosition.x
                val selectedHue = pointToHue(pressPos)
                pressOffset.value = if (isSupportingPanel) {
                    Offset(0f, pressPos)
                } else {
                    Offset(pressPos, 0f)
                }
                if (enabled) setColor(selectedHue)
            }

            if (enabled) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = if (isSupportingPanel) {
                        Offset(0f, pressOffset.value.y - 8.dp.toPx())
                    } else {
                        Offset(pressOffset.value.x - 8.dp.toPx(), 0f)
                    },
                    size = if (isSupportingPanel) {
                        Size(size.width, size.width / 3)
                    } else {
                        Size(size.height / 3, size.height)
                    },
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

fun CoroutineScope.collectForPress(
    interactionSource: InteractionSource,
    setOffset: (Offset) -> Unit
) {
    launch {
        interactionSource.interactions.collect { interaction ->
            (interaction as? PressInteraction.Press)
                ?.pressPosition
                ?.let(setOffset)
        }
    }
}

private fun Modifier.emitDragGesture(
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        detectDragGestures { input, _ ->
            scope.launch {
                interactionSource.emit(PressInteraction.Press(input.position))
            }
        }
    }.clickable(interactionSource, null) {
    }
}

private fun DrawScope.drawBitmap(
    bitmap: Bitmap,
    panel: RectF
) {
    drawIntoCanvas {
        it.nativeCanvas.drawBitmap(
            bitmap,
            null,
            panel.toRect(),
            null
        )
    }
}