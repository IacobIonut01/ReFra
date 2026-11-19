package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * What a slider's track band paints: a colour ramp or one of the tonal
 * brushes.
 */
internal sealed interface EditorSliderTrack {
    data class Gradient(val colors: List<Color>) : EditorSliderTrack

    enum class Tonal : EditorSliderTrack {
        Brightness, Exposure, Contrast, Highlights, Shadows, Whites, Blacks, Hue,
        Saturation, Vibrance
    }
}

/** The tonal brushes' left→right ramps across [startX]..[endX]; Contrast, Saturation and Vibrance paint their own layers. */
internal fun tonalTrackBrush(
    kind: EditorSliderTrack.Tonal,
    startX: Float,
    endX: Float
): Brush? = when (kind) {
    EditorSliderTrack.Tonal.Brightness -> Brush.horizontalGradient(
        listOf(Color(0xFF000000), Color(0xFFFFFFFF)), startX, endX
    )
    EditorSliderTrack.Tonal.Exposure -> Brush.horizontalGradient(
        0f to Color(0xFF08080A),
        0.5f to Color(0xFF808080),
        0.85f to Color(0xFFEBEBEB),
        1f to Color(0xFFFFFFFF),
        startX = startX,
        endX = endX
    )
    EditorSliderTrack.Tonal.Highlights -> Brush.horizontalGradient(
        listOf(Color(0xFF78787A), Color(0xFFFFFFFF)), startX, endX
    )
    EditorSliderTrack.Tonal.Shadows -> Brush.horizontalGradient(
        listOf(Color(0xFF000000), Color(0xFF969698)), startX, endX
    )
    EditorSliderTrack.Tonal.Whites -> Brush.horizontalGradient(
        listOf(Color(0xFFB8B8BA), Color(0xFFFFFFFF)), startX, endX
    )
    EditorSliderTrack.Tonal.Blacks -> Brush.horizontalGradient(
        listOf(Color(0xFF000000), Color(0xFF58585A)), startX, endX
    )
    EditorSliderTrack.Tonal.Hue -> Brush.horizontalGradient(
        (0..6).map { Color.hsv(it * 60f, 0.85f, 0.95f) }, startX, endX
    )
    EditorSliderTrack.Tonal.Contrast -> null
    EditorSliderTrack.Tonal.Saturation -> null
    EditorSliderTrack.Tonal.Vibrance -> null
}

/**
 * Paints [kind] into the rect at ([left], [top]) sized [width]×[height] —
 * the caller clips to the band's rounded ends first.
 */
internal fun DrawScope.drawTonalTrack(
    kind: EditorSliderTrack.Tonal,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    if (kind == EditorSliderTrack.Tonal.Contrast) {
        // The top half falls to black, the bottom half rises to white.
        val mid = top + height / 2f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF808080), Color(0xFF000000)), left, left + width
            ),
            topLeft = Offset(left, top),
            size = Size(width, mid - top)
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF808080), Color(0xFFFFFFFF)), left, left + width
            ),
            topLeft = Offset(left, mid),
            size = Size(width, top + height - mid)
        )
        return
    }
    if (kind == EditorSliderTrack.Tonal.Saturation || kind == EditorSliderTrack.Tonal.Vibrance) {
        // A hue rainbow with a neutral grey laid over it: Saturation fades
        // the grey out to the right, Vibrance keeps the left end muted.
        val end = left + width
        val grey = Color(0xFF8C8C8C)
        drawRect(
            brush = Brush.horizontalGradient(
                (0..6).map { Color.hsv(it * 60f, 0.9f, 0.92f) }, left, end
            ),
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
        val overlay = if (kind == EditorSliderTrack.Tonal.Saturation) {
            Brush.horizontalGradient(
                listOf(grey, grey.copy(alpha = 0f)), left, end
            )
        } else {
            Brush.horizontalGradient(
                0f to grey.copy(alpha = 0.6f),
                0.7f to grey.copy(alpha = 0f),
                1f to grey.copy(alpha = 0f),
                startX = left,
                endX = end
            )
        }
        drawRect(
            brush = overlay,
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
        return
    }
    tonalTrackBrush(kind, left, left + width)?.let { brush ->
        drawRect(
            brush = brush,
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
    }
}
