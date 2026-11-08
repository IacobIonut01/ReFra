package com.dot.gallery.editor.core.project

import com.dot.gallery.editor.core.model.ColorAdjustments
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoRawColourSpace
import com.dot.gallery.editor.core.model.PhotoRawDemosaic
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoRawHighlights
import com.dot.gallery.editor.core.model.PhotoRawNoiseReduction
import com.dot.gallery.editor.core.model.PhotoRawWhiteBalance
import com.dot.gallery.editor.core.model.isDevelopOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoRawDevelopCodecTest {

    @Test
    fun everyDevelopFieldSurvivesARoundTrip() {
        val develop = PhotoRawDevelop(
            whiteBalance = PhotoRawWhiteBalance.Custom,
            temperatureKelvin = 6100f,
            tint = 0.06f,
            exposure = 0.3f,
            brightness = -0.5f,
            contrast = 0.25f,
            shadows = 0.24f,
            highlights = -0.18f,
            sharpen = 0.5f,
            saturation = 0.08f,
            vibrance = -0.1f,
            noiseReduction = PhotoRawNoiseReduction.Medium,
            highlightRecovery = PhotoRawHighlights.Rebuild,
            colourSpace = PhotoRawColourSpace.ProPhoto,
            demosaic = PhotoRawDemosaic.Dcb,
            halfSize = true
        )
        val decoded = PhotoProjectCodec.decode(
            PhotoProjectCodec.encode(PhotoProjectSnapshot(PhotoEdits(develop = develop)))
        )
        assertEquals(develop, decoded.edits.develop)
    }

    @Test
    fun toneFieldsShareADecodeAndDecodeFieldsDoNot() {
        val base = PhotoRawDevelop(exposure = 1f)
        assertTrue(base.sharesBaseWith(base.copy(contrast = 0.5f, saturation = -0.5f, sharpen = 1f)))
        assertFalse(base.sharesBaseWith(base.copy(exposure = 1.5f)))
        assertFalse(base.sharesBaseWith(base.copy(whiteBalance = PhotoRawWhiteBalance.Auto)))
        assertFalse(base.sharesBaseWith(base.copy(demosaic = PhotoRawDemosaic.Vng)))
        assertFalse(base.sharesBaseWith(base.copy(colourSpace = PhotoRawColourSpace.Wide)))
    }

    @Test
    fun onlyAPureDevelopIsTiffSafe() {
        assertTrue(PhotoEdits().isDevelopOnly())
        assertTrue(PhotoEdits(develop = PhotoRawDevelop(exposure = 1f)).isDevelopOnly())
        assertFalse(
            PhotoEdits(
                color = ColorAdjustments(exposureStops = 0.5f),
                develop = PhotoRawDevelop(exposure = 1f)
            ).isDevelopOnly()
        )
    }

    @Test
    fun outOfRangeValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> { PhotoRawDevelop(exposure = 4f) }
        assertFailsWith<IllegalArgumentException> { PhotoRawDevelop(temperatureKelvin = 100f) }
        assertFailsWith<IllegalArgumentException> { PhotoRawDevelop(sharpen = -1f) }
    }
}
