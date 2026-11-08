package com.dot.gallery.editor.android

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The production JNI path on-device: the packaged `librefra_editor_jni.so`
 * must load, report ABI 2 and match the Kotlin `color.basic` reference on the
 * same analytic grid as the standalone conformance test — colour within
 * `2e-5 * max(1, |reference|)`, alpha bit-exact. A loader that cannot resolve
 * the library falls back to Kotlin exactly once, still rendering correctly.
 */
@RunWith(AndroidJUnit4::class)
class NativeColorEffectAndroidTest {

    private val errors = CopyOnWriteArrayList<Throwable>()

    @Test
    fun packagedKernelMatchesKotlinOnParameterGrid() = runBlocking {
        val shades =
            floatArrayOf(-0.25f, 0f, 1e-10f, 0.0031308f, 0.18f, 0.5f, 1f, 4f)
        val pixels = FloatArray(64) { index ->
            if (index % 4 == 3) (index / 4 % 4) / 3f
            else shades[(index / 4 + index % 4 * 3) % shades.size]
        }
        val source = LinearRgbaFrame(PixelSize(4, 4), pixels)
        val reference = BasicColorEffect()
        val effect = NativeColorEffect(AndroidEditorNativeLoader()) {
            errors += it
        }

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
                        "alpha $i $params $label",
                        expected[i].toRawBits(), actual[i].toRawBits()
                    )
                } else {
                    val tolerance = 2e-5f * max(1f, abs(expected[i]))
                    assertTrue(
                        "component $i $params $label: native=${actual[i]}, " +
                            "kotlin=${expected[i]}, tolerance=$tolerance",
                        actual[i].isFinite() &&
                            abs(actual[i] - expected[i]) <= tolerance
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
            "packaged library did not load — backend is ${effect.backend}",
            NativeColorBackend.Native, effect.backend
        )
        assertTrue("load failures reported: $errors", errors.isEmpty())
    }

    @Test
    fun missingLoaderFallsBackOnce() = runBlocking {
        val effect = NativeColorEffect(
            loader = { throw UnsatisfiedLinkError("no refra_editor_jni") },
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
                    "fallback render diverged at component $i",
                    abs(actual[i] - expected[i]) <= tolerance
                )
            }
        }
        assertEquals(NativeColorBackend.KotlinFallback, effect.backend)
        assertEquals(
            "unavailable reported ${errors.size} times", 1, errors.size
        )
    }
}
