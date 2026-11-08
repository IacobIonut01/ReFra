package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * V4 floating chrome: tinted 12 dp backdrop blur without an outline.
 * When the host's
 * [EditorUiPreferences.blurEnabled] is off the same geometry renders as an
 * opaque surface — only the material changes, never the layout.
 */
@Composable
internal fun EditorFloatingPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .chromeBlur(shape),
        content = content
    )
}

/**
 * The sheet-blur material — a wider 24 dp backdrop blur under a heavier
 * surface tint for large floating surfaces. The photo workspace supplies
 * Haze's downsampled Performance policy. With blur disabled the surface stays
 * opaque at identical geometry.
 */
@Composable
internal fun Modifier.editorSheetBlur(
    shape: Shape,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    blurRadius: androidx.compose.ui.unit.Dp = 24.dp
): Modifier {
    val prefs = LocalEditorUiPreferences.current
    val haze = LocalEditorHazeState.current
    val style = remember(baseColor, blurRadius) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            noiseFactor(0f)
            colorEffects(
                listOf(HazeColorEffect.tint(baseColor.copy(alpha = 0.82f)))
            )
            fallbackColorEffect(
                HazeColorEffect.tint(baseColor)
            )
        }
    }
    return this
        .clip(shape)
        .background(baseColor, shape)
        .then(
            if (prefs.blurEnabled) {
                Modifier.hazeBlur(
                    input = HazeInput.Backdrop(haze),
                    style = style
                )
            } else {
                Modifier
            }
        )
}

/** Inherit the sheet's blur instead of resampling the undimmed canvas per cell. */
@Composable
internal fun Modifier.editorSheetItemSurface(
    shape: Shape,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
): Modifier = clip(shape).background(
    if (LocalEditorUiPreferences.current.blurEnabled) baseColor.copy(alpha = 0.6f)
    else baseColor
)

/**
 * Softens a horizontal scroller's ends: a short gradient of the card
 * colour fades content out instead of cutting it off at a hard edge.
 *
 * Declare it *before* `horizontalScroll` so the fade is part of the
 * scroller's viewport and stays pinned to the edges; after the scroll
 * modifier it would be drawn into the scrolling content and slide away
 * with it. Pass the strip's own content [width] inset: at a resting end
 * the gradient then lands on that padding and never dims a fully visible
 * item, while a cut item at the opposite end is still softened.
 */
internal fun Modifier.softHorizontalEdges(
    color: Color,
    width: androidx.compose.ui.unit.Dp = 20.dp
): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx().coerceAtMost(size.width / 2f)
    if (w <= 0f) return@drawWithContent
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(color, Color.Transparent),
            startX = 0f,
            endX = w
        ),
        size = Size(w, size.height)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color),
            startX = size.width - w,
            endX = size.width
        ),
        topLeft = Offset(size.width - w, 0f),
        size = Size(w, size.height)
    )
}
