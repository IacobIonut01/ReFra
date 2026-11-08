package com.dot.gallery.editor.desktop

import com.dot.gallery.editor.core.graph.EffectParameters
import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.io.NativeColorBackend
import com.dot.gallery.editor.io.NativeColorEffect
import com.dot.gallery.editor.render.BasicColorEffect
import com.dot.gallery.editor.render.LinearRgbaFrame
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The production JNI path on desktop: the jar-bundled `refra_editor_jni`
 * (built by stageNativeRuntime, loaded via DesktopEditorNativeLoader's
 * digest-pinned cache) must report ABI 2 and match the Kotlin `color.basic`
 * reference on the same analytic grid as the standalone conformance test —
 * colour within `2e-5 * max(1, |reference|)`, alpha bit-exact, neutral
 * bit-exact. A loader that cannot resolve the resource falls back to Kotlin
 * with a single diagnostic.
 */
class NativeColorEffectTest {

    private val errors = CopyOnWriteArrayList<Throwable>()

    @Test
    fun packagedKernelMatchesKotlinOnParameterGrid() = runTest {
        val loader = DesktopEditorNativeLoader()
        val effect = NativeColorEffect(loader) { errors += it }
        val shades =
            floatArrayOf(-0.25f, 0f, 1e-10f, 0.0031308f, 0.18f, 0.5f, 1f, 4f)
        val pixels = FloatArray(64) { index ->
            if (index % 4 == 3) (index / 4 % 4) / 3f
            else shades[(index / 4 + index % 4 * 3) % shades.size]
        }
        val source = LinearRgbaFrame(PixelSize(4, 4), pixels)
        val reference = BasicColorEffect()

        suspend fun assertParity(params: ColorAdjustments, label: String = "") {
            val expected = reference
                .render(listOf(source), EffectParameters.Color(params))
                .copyPixels()
            val actual = effect
                .render(listOf(source), EffectParameters.Color(params))
                .copyPixels()
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

        for (exposure in listOf(-10f, -2.5f, -1f, 0f, 0.5f, 1f, 2.25f, 10f)) {
            for (contrast in listOf(0f, 0.5f, 1f, 2f, 4f)) {
                for (saturation in listOf(0f, 0.5f, 1f, 2f, 4f)) {
                    assertParity(ColorAdjustments(exposure, contrast, saturation))
                }
            }
        }
        // v2 tonal parameters: the 81 corner/neutral combinations at a
        // neutral base, then each individual extreme over a mixed recipe.
        val corners = listOf(-1f, 0f, 1f)
        for (hi in corners) {
            for (sh in corners) {
                for (wh in corners) {
                    for (bl in corners) {
                        assertParity(
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
                tonal.copy(
                    exposureStops = 0.5f, contrast = 1.2f, saturation = 0.7f
                ),
                label
            )
        }
        assertEquals(
            NativeColorBackend.Native, effect.backend,
            "packaged library did not load — backend is ${effect.backend}"
        )
        assertTrue(errors.isEmpty(), "load failures reported: $errors")
        // Proof the loaded image is the staged resource copy, not a
        // system-installed library.
        val status = loader.loadStatus
        assertNotNull(status, "loader recorded no status")
        assertTrue(
            status.contains("refra-editor-native/"),
            "load came from outside the digest-pinned cache: $status"
        )
        assertTrue(
            status.contains("sha256="),
            "status lacks the content digest: $status"
        )
    }

    @Test
    fun neutralRoundTripIsBitExact() = runTest {
        val effect = NativeColorEffect(DesktopEditorNativeLoader()) {
            errors += it
        }
        val pixels = floatArrayOf(
            -0f, Float.MAX_VALUE, 1e-30f, 0f,
            1e-10f, -1e-10f, 3e-10f, 0.75f
        )
        val source = LinearRgbaFrame(PixelSize(2, 1), pixels)
        val actual = effect
            .render(listOf(source), EffectParameters.Color(ColorAdjustments()))
            .copyPixels()
        assertContentEquals(
            pixels.map { it.toRawBits() }, actual.map { it.toRawBits() },
            "neutral native render changed the bits"
        )
        assertEquals(NativeColorBackend.Native, effect.backend)
    }

    @Test
    fun missingLoaderFallsBackOnce() = runTest {
        val effect = NativeColorEffect(
            loader = {
                throw java.io.IOException("no packaged resource for this host")
            },
            onUnavailable = { errors += it }
        )
        val source = LinearRgbaFrame(
            PixelSize(2, 1),
            floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1f, 0.5f, 0.25f, 0.5f)
        )
        val params = EffectParameters.Color(ColorAdjustments(0.5f, 2f, 0.5f))
        val expected = BasicColorEffect().render(listOf(source), params).copyPixels()
        repeat(2) {
            val actual = effect.render(listOf(source), params).copyPixels()
            for (i in actual.indices) {
                val tolerance = 2e-5f * max(1f, abs(expected[i]))
                assertTrue(
                    abs(actual[i] - expected[i]) <= tolerance,
                    "fallback render diverged at component $i"
                )
            }
        }
        assertEquals(NativeColorBackend.KotlinFallback, effect.backend)
        assertEquals(1, errors.size, "unavailable reported ${errors.size} times")
    }
}
