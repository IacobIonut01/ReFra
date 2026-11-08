package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.graph.EffectDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoSpatialEffectsTest {

    // ---- PhotoDetails ---------------------------------------------------

    @Test
    fun `details enforces the authored domains`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(texture = -1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(texture = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(clarity = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(sharpening = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(sharpening = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoDetails(noiseReduction = 1.1f)
        }
        assertTrue(PhotoDetails().isNeutral)
        assertFalse(PhotoDetails(texture = 0.1f).isNeutral)
        assertFalse(PhotoDetails(clarity = -0.1f).isNeutral)
        assertFalse(PhotoDetails(sharpening = 0.1f).isNeutral)
        assertFalse(PhotoDetails(noiseReduction = 0.1f).isNeutral)
    }

    // ---- PhotoLensBlur --------------------------------------------------

    @Test
    fun `lens blur enforces field domains and strength gates neutrality`() {
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(strength = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(strength = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(transition = 0.04f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(transition = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(highlightBokeh = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(centerX = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(centerY = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoLensBlur(angleDegrees = 181f)
        }
        assertTrue(PhotoLensBlur().isNeutral)
        // A shaped-but-zero blur draws nothing — strength is the gate.
        assertTrue(
            PhotoLensBlur(
                mode = LensBlurMode.Linear, transition = 0.9f,
                centerX = 0.1f, angleDegrees = 45f
            ).isNeutral
        )
        assertFalse(PhotoLensBlur(strength = 0.1f).isNeutral)
    }

    // ---- PhotoSpatialEffects + halo -------------------------------------

    @Test
    fun `spatial neutrality requires both stages neutral`() {
        assertTrue(PhotoSpatialEffects().isNeutral)
        assertFalse(
            PhotoSpatialEffects(
                details = PhotoDetails(clarity = 0.2f)
            ).isNeutral
        )
        assertFalse(
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(strength = 0.2f)
            ).isNeutral
        )
    }

    @Test
    fun `required halo tracks the active stages`() {
        assertEquals(0, PhotoSpatialEffects().requiredHalo)
        // Details alone reads its 3x3 neighbourhood.
        assertEquals(
            1,
            PhotoSpatialEffects(
                details = PhotoDetails(texture = 0.5f)
            ).requiredHalo
        )
        // The blur disk reaches round(strength * 16) — ceil, because a
        // fractional maximum still reads its outer taps.
        assertEquals(
            8,
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(strength = 0.5f)
            ).requiredHalo
        )
        assertEquals(
            16,
            PhotoSpatialEffects(
                lensBlur = PhotoLensBlur(strength = 1f)
            ).requiredHalo
        )
        // A combined recipe composes the margins: the blur's reach plus
        // the Details 3x3's own row beyond the blurred taps.
        assertEquals(
            17,
            PhotoSpatialEffects(
                details = PhotoDetails(texture = 0.5f),
                lensBlur = PhotoLensBlur(strength = 1f)
            ).requiredHalo
        )
        assertEquals(
            PhotoSpatialEffects.MAX_HALO,
            EffectDescriptor.PhotoSpatial.haloPixels
        )
        assertEquals(
            9,
            PhotoSpatialEffects(
                details = PhotoDetails(texture = 0.5f),
                lensBlur = PhotoLensBlur(strength = 0.5f)
            ).requiredHalo
        )
    }

    @Test
    fun `spatial survives edits and document conversion`() {
        val spatial = PhotoSpatialEffects(
            details = PhotoDetails(texture = 0.3f),
            lensBlur = PhotoLensBlur(
                mode = LensBlurMode.Linear, strength = 0.5f,
                angleDegrees = 30f
            )
        )
        val edits = PhotoEdits(spatial = spatial)
        val document = edits.toDocument(
            MediaAsset.Image("img", "ref", PixelSize(4, 4))
        )
        assertEquals(spatial, document.spatial)
        assertEquals(edits, document.toEdits())
        assertEquals(spatial, document.toEdits().spatial)
    }
}
