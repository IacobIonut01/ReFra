package com.dot.gallery.editor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The look recipe's invariants: amount stays a finite unit blend, and
 * neutrality is either axis — no look selected or a zero blend.
 */
class PhotoLookTest {

    @Test
    fun `default look is None at full blend`() {
        val look = PhotoLook()
        assertEquals(PhotoLookId.None, look.id)
        assertEquals(1f, look.amount)
        assertTrue(look.isNeutral)
    }

    @Test
    fun `amount stays finite inside the unit range`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoLook(PhotoLookId.Clean, -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLook(PhotoLookId.Clean, 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLook(PhotoLookId.Clean, Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLook(PhotoLookId.Clean, Float.POSITIVE_INFINITY)
        }
        PhotoLook(PhotoLookId.Clean, 0f)
        PhotoLook(PhotoLookId.Clean, 1f)
    }

    @Test
    fun `neutrality is None or a zero blend`() {
        assertTrue(PhotoLook(PhotoLookId.None, 1f).isNeutral)
        assertTrue(PhotoLook(PhotoLookId.None, 0f).isNeutral)
        assertTrue(PhotoLook(PhotoLookId.Cinematic, 0f).isNeutral)
        assertFalse(PhotoLook(PhotoLookId.Mono, 0.5f).isNeutral)
        assertFalse(PhotoLook(PhotoLookId.WarmFade, 1f).isNeutral)
    }

    @Test
    fun `a look makes finishing non-neutral`() {
        assertTrue(PhotoFinishing().isNeutral)
        assertFalse(
            PhotoFinishing(look = PhotoLook(PhotoLookId.Cool)).isNeutral
        )
        // A neutral look leaves the rest of the stage's verdict alone.
        assertTrue(
            PhotoFinishing(look = PhotoLook(PhotoLookId.None, 0.4f))
                .isNeutral
        )
    }
}
