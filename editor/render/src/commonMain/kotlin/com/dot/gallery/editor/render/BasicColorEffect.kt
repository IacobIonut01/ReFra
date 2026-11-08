package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectDescriptor
import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import kotlinx.coroutines.ensureActive

/**
 * CPU implementation of `color.basic` v2: ordered exposure, the tonal
 * operation (highlights/shadows/whites/blacks as a shared luminance
 * offset), contrast around the 0.18 pivot, then Rec.709-luma saturation on
 * linear-light straight-alpha RGBA. Alpha passes through unchanged and
 * scene colour is never clamped.
 *
 * Neutral stages perform no arithmetic; a fully neutral adjustment preserves
 * input bits exactly (including -0 and very small values that the arithmetic
 * would round away).
 * Cancellation is checked per pixel row; a computation that would produce a
 * non-finite component fails rather than emitting corrupt colour.
 */
class BasicColorEffect : FrameEffect {

    override val descriptor: EffectDescriptor = EffectDescriptor.ColorBasic

    override suspend fun render(
        inputs: List<LinearRgbaFrame>,
        parameters: EffectParameters
    ): LinearRgbaFrame {
        if (inputs.size != 1) {
            throw UnsupportedOperationException(
                "color.basic takes exactly 1 input, got ${inputs.size}"
            )
        }
        val color = parameters as? EffectParameters.Color
            ?: throw UnsupportedOperationException(
                "color.basic requires EffectParameters.Color, got $parameters"
            )
        return apply(inputs.single(), color.adjustments)
    }

    private suspend fun apply(
        frame: LinearRgbaFrame,
        adjustments: ColorAdjustments
    ): LinearRgbaFrame {
        if (adjustments.exposureStops == 0f &&
            adjustments.isToneNeutral() &&
            adjustments.contrast == 1f &&
            adjustments.saturation == 1f
        ) {
            return frame
        }
        val output = FloatArray(frame.componentCount)
        val exposureGain = 2.0.pow(adjustments.exposureStops.toDouble()).toFloat()
        val toneActive = !adjustments.isToneNeutral()
        val width = frame.size.width
        val componentsPerRow = width * COMPONENTS_PER_PIXEL
        for (y in 0 until frame.size.height) {
            coroutineContext.ensureActive()
            val rowStart = y * componentsPerRow
            for (x in 0 until width) {
                val i = rowStart + x * COMPONENTS_PER_PIXEL
                var r = frame.component(i)
                var g = frame.component(i + 1)
                var b = frame.component(i + 2)

                if (exposureGain != 1f) {
                    r *= exposureGain
                    g *= exposureGain
                    b *= exposureGain
                }
                if (toneActive) {
                    val y2 = LUMA_R * r + LUMA_G * g + LUMA_B * b
                    val delta = toneOffset(
                        y2,
                        adjustments.highlights,
                        adjustments.shadows,
                        adjustments.whites,
                        adjustments.blacks
                    )
                    r += delta
                    g += delta
                    b += delta
                }
                if (adjustments.contrast != 1f) {
                    r = (r - CONTRAST_PIVOT) * adjustments.contrast + CONTRAST_PIVOT
                    g = (g - CONTRAST_PIVOT) * adjustments.contrast + CONTRAST_PIVOT
                    b = (b - CONTRAST_PIVOT) * adjustments.contrast + CONTRAST_PIVOT
                }
                if (adjustments.saturation != 1f) {
                    val luma = LUMA_R * r + LUMA_G * g + LUMA_B * b
                    r = luma + (r - luma) * adjustments.saturation
                    g = luma + (g - luma) * adjustments.saturation
                    b = luma + (b - luma) * adjustments.saturation
                }

                if (!r.isFinite() || !g.isFinite() || !b.isFinite()) {
                    throw IllegalStateException(
                        "color.basic produced non-finite colour at pixel ($x, $y)"
                    )
                }
                output[i] = r
                output[i + 1] = g
                output[i + 2] = b
                output[i + 3] = frame.component(i + 3)
            }
        }
        return LinearRgbaFrame.fromOwned(frame.size, output)
    }

    private companion object {
        const val COMPONENTS_PER_PIXEL = 4
        const val CONTRAST_PIVOT = 0.18f
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}
