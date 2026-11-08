package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Conformance comparison between the Kotlin `color.basic` implementation and
 * the standalone C kernel, run through the test-only JNI bridge. Colour
 * components use a mixed absolute/relative tolerance
 * (`2e-5 * max(1, |reference|)`) — floating-point conformance, not visual or
 * HDR certification; alpha and the fully-neutral case are bit-exact.
 */
class NativeColorParityTest {

    @Test
    fun nativeKernelMatchesKotlinOnParameterGrid() = runTest {
        val shades = floatArrayOf(-0.25f, 0f, 1e-10f, 0.0031308f, 0.18f, 0.5f, 1f, 4f)
        val pixels = FloatArray(64) { index ->
            if (index % 4 == 3) (index / 4 % 4) / 3f
            else shades[(index / 4 + index % 4 * 3) % shades.size]
        }
        val source = LinearRgbaFrame(PixelSize(4, 4), pixels)
        val reference = BasicColorEffect()
        for (exposure in listOf(-10f, -2.5f, -1f, 0f, 0.5f, 1f, 2.25f, 10f)) {
            for (contrast in listOf(0f, 0.5f, 1f, 2f, 4f)) {
                for (saturation in listOf(0f, 0.5f, 1f, 2f, 4f)) {
                    val params = ColorAdjustments(exposure, contrast, saturation)
                    assertParity(reference, source, pixels, params)
                }
            }
        }
        // The v2 tonal parameters: all 81 corner/neutral combinations at a
        // neutral exposure/contrast/saturation base, then each individual
        // tonal extreme over a mixed base.
        val corners = listOf(-1f, 0f, 1f)
        for (hi in corners) {
            for (sh in corners) {
                for (wh in corners) {
                    for (bl in corners) {
                        assertParity(
                            reference, source, pixels,
                            ColorAdjustments(
                                highlights = hi, shadows = sh,
                                whites = wh, blacks = bl
                            )
                        )
                    }
                }
            }
        }
        for ((label, tonal) in listOf(
            "highlights" to ColorAdjustments(highlights = 1f),
            "highlights-" to ColorAdjustments(highlights = -1f),
            "shadows" to ColorAdjustments(shadows = 1f),
            "shadows-" to ColorAdjustments(shadows = -1f),
            "whites" to ColorAdjustments(whites = 1f),
            "whites-" to ColorAdjustments(whites = -1f),
            "blacks" to ColorAdjustments(blacks = 1f),
            "blacks-" to ColorAdjustments(blacks = -1f)
        )) {
            assertParity(
                reference, source, pixels,
                tonal.copy(
                    exposureStops = 0.5f, contrast = 1.2f, saturation = 0.7f
                ),
                label
            )
        }
        assertContentEquals(
            source.copyPixels(), pixels, "native bridge mutated source"
        )
    }

    private suspend fun assertParity(
        reference: BasicColorEffect,
        source: LinearRgbaFrame,
        pixels: FloatArray,
        params: ColorAdjustments,
        label: String = ""
    ) {
        val expected = reference
            .render(listOf(source), EffectParameters.Color(params))
            .copyPixels()
        val actual = FloatArray(pixels.size)
        assertEquals(
            0,
            NativeColorTestBridge.apply(
                pixels, actual, 4, 4,
                params.exposureStops, params.contrast, params.saturation,
                params.highlights, params.shadows, params.whites, params.blacks
            ),
            "native rejection for $params $label"
        )
        for (i in actual.indices) {
            if (i % 4 == 3) {
                assertEquals(
                    expected[i].toRawBits(), actual[i].toRawBits(),
                    "alpha $i $params $label"
                )
            } else {
                val tolerance = 2e-5f * max(1f, abs(expected[i]))
                assertTrue(
                    actual[i].isFinite() &&
                        abs(actual[i] - expected[i]) <= tolerance,
                    "component $i $params $label: native=${actual[i]}, " +
                        "kotlin=${expected[i]}, tolerance=$tolerance"
                )
            }
        }
    }

    @Test
    fun neutralNativeRoundTripIsBitExact() {
        val source = floatArrayOf(
            -0f, Float.MAX_VALUE, 1e-30f, 0f,
            1e-10f, -1e-10f, 3e-10f, 0.75f
        )
        val before = source.map { it.toRawBits() }
        val target = FloatArray(source.size)
        assertEquals(
            0, NativeColorTestBridge.apply(
                source, target, 2, 1, 0f, 1f, 1f, 0f, 0f, 0f, 0f
            )
        )
        assertEquals(before, target.map { it.toRawBits() })
        assertEquals(before, source.map { it.toRawBits() })
    }

    @Test
    fun nativeValidationLeavesDestinationUntouched() {
        val source = floatArrayOf(
            0.5f, 0.5f, 0.5f, 1f,
            Float.NaN, 0f, 0f, 1f
        )
        val target = FloatArray(8) { 7f }
        assertEquals(
            1, NativeColorTestBridge.apply(
                source, target, 2, 1, 0f, 1f, 1f, 0f, 0f, 0f, 0f
            )
        )
        assertContentEquals(FloatArray(8) { 7f }, target)
        assertEquals(
            1, NativeColorTestBridge.apply(
                floatArrayOf(0f), target, 2, 1, 0f, 1f, 1f, 0f, 0f, 0f, 0f
            )
        )
        assertContentEquals(FloatArray(8) { 7f }, target)
        // Every new v2 parameter is validated before any write.
        val clean = floatArrayOf(0.5f, 0.5f, 0.5f, 1f, 0.25f, 0.25f, 0.25f, 1f)
        for (slot in 0..3) {
            val bad = FloatArray(4) { 0f }.also { it[slot] = Float.NaN }
            assertEquals(
                1, NativeColorTestBridge.apply(
                    clean, target, 2, 1,
                    0f, 1f, 1f, bad[0], bad[1], bad[2], bad[3]
                ),
                "NaN tonal parameter $slot was not rejected"
            )
            assertContentEquals(
                FloatArray(8) { 7f }, target,
                "rejected call $slot still wrote to the destination"
            )
        }
    }
}
