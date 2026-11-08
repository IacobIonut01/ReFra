package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PhotoWarp] validation: every field rejects non-finite and out-of-range
 * values atomically at construction, and [PhotoWarp.isNeutral] detects the
 * bit-exact pass-through.
 */
class PhotoWarpTest {

    @Test
    fun `default warp is neutral`() {
        assertTrue(PhotoWarp().isNeutral)
        assertFalse(PhotoWarp(rotationDegrees = 1f).isNeutral)
        assertFalse(PhotoWarp(verticalPerspective = 1f).isNeutral)
        assertFalse(PhotoWarp(horizontalPerspective = 1f).isNeutral)
        assertFalse(PhotoWarp(scale = 1.5f).isNeutral)
    }

    @Test
    fun `range edges are accepted`() {
        PhotoWarp(
            rotationDegrees = -45f,
            verticalPerspective = -100f,
            horizontalPerspective = 100f,
            scale = 1f
        )
        PhotoWarp(rotationDegrees = 45f, scale = 2f)
    }

    @Test
    fun `out-of-range values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(rotationDegrees = -45.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(rotationDegrees = 45.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(verticalPerspective = -100.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(verticalPerspective = 100.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(horizontalPerspective = -100.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(horizontalPerspective = 100.1f)
        }
        assertFailsWith<IllegalArgumentException> { PhotoWarp(scale = 0.99f) }
        assertFailsWith<IllegalArgumentException> { PhotoWarp(scale = 2.01f) }
    }

    @Test
    fun `non-finite values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(rotationDegrees = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(verticalPerspective = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(horizontalPerspective = Float.NEGATIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> { PhotoWarp(scale = Float.NaN) }
        assertFailsWith<IllegalArgumentException> {
            PhotoWarp(scale = Float.POSITIVE_INFINITY)
        }
    }
}
