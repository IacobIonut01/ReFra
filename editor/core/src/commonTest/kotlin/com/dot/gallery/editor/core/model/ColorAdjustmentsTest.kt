package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ColorAdjustmentsTest {

    @Test
    fun `defaults are the identity`() {
        ColorAdjustments()
        ColorAdjustments(0f, 1f, 1f)
    }

    @Test
    fun `out-of-range and non-finite values are rejected`() {
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(exposureStops = 10.5f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(exposureStops = -10.5f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(exposureStops = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            ColorAdjustments(exposureStops = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(contrast = -0.1f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(contrast = 4.5f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(saturation = -1f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(saturation = Float.NaN) }
    }

    @Test
    fun `tonal parameters are finite in minus one to one`() {
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(highlights = -1.01f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(highlights = 1.01f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(highlights = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(shadows = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(whites = -1.01f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(whites = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(blacks = 1.01f) }
        assertFailsWith<IllegalArgumentException> { ColorAdjustments(blacks = Float.NaN) }
    }

    @Test
    fun `boundary values are accepted`() {
        ColorAdjustments(exposureStops = -10f, contrast = 0f, saturation = 0f)
        ColorAdjustments(exposureStops = 10f, contrast = 4f, saturation = 4f)
        ColorAdjustments(
            highlights = -1f, shadows = 1f, whites = -1f, blacks = 1f
        )
    }
}
