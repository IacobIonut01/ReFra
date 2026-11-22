package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.MediaAsset
import com.dot.gallery.editor.core.model.NormalizedCrop
import com.dot.gallery.editor.core.model.PhotoCropShape
import com.dot.gallery.editor.core.model.PhotoDocument
import com.dot.gallery.editor.core.model.PhotoExportFormat
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.render.photo.losesTransparency
import com.dot.gallery.editor.render.photo.transparentExportFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorGeometryTest {

    @Test
    fun squareCropOnLandscapeCropsWidthCentred() {
        // 4:3 landscape (1600x1200) -> 1:1 keeps full height, trims width.
        val crop = centeredCrop(PixelSize(1600, 1200), 1.0, quarterTurns = 0)
        assertEquals(0.0, crop.top, 1e-9)
        assertEquals(1.0, crop.bottom, 1e-9)
        assertEquals(0.125, crop.left, 1e-9)
        assertEquals(0.875, crop.right, 1e-9)
    }

    @Test
    fun rotatedCropComputesPreRotationAspect() {
        // 1600x1200 with one quarter turn displays 4:3 only if the stored
        // crop is 900x1200 (3:4 pre-rotation).
        val crop = centeredCrop(PixelSize(1600, 1200), 4.0 / 3.0, quarterTurns = 1)
        val croppedWidth = (crop.right - crop.left) * 1600
        val croppedHeight = (crop.bottom - crop.top) * 1200
        assertEquals(900.0, croppedWidth, 1e-6)
        assertEquals(1200.0, croppedHeight, 1e-6)
        // Displayed aspect after rotation is within pixel rounding of 4:3.
        val displayed = croppedHeight / croppedWidth
        assertTrue(abs(displayed - 4.0 / 3.0) < 0.001)
    }

    @Test
    fun portraitCropOnWideTargetCropsHeight() {
        // 1200x1600 portrait -> 16:9 keeps full width, trims height.
        val crop = centeredCrop(PixelSize(1200, 1600), 16.0 / 9.0, quarterTurns = 0)
        assertEquals(0.0, crop.left, 1e-9)
        assertEquals(1.0, crop.right, 1e-9)
        val croppedHeight = (crop.bottom - crop.top) * 1600
        assertEquals(675.0, croppedHeight, 1e-6)
    }

    @Test
    fun invalidAspectRejected() {
        assertFailsWith<IllegalArgumentException> {
            centeredCrop(PixelSize(100, 100), 0.0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            centeredCrop(PixelSize(100, 100), -1.0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            centeredCrop(PixelSize(100, 100), Double.NaN, 0)
        }
    }

    @Test
    fun signedDecimalFormats() {
        assertEquals("+0.00", formatSignedDecimal(0f))
        assertEquals("+0.75", formatSignedDecimal(0.75f))
        assertEquals("-1.25", formatSignedDecimal(-1.25f))
        assertEquals("+10.00", formatSignedDecimal(10f))
        assertEquals("-10.00", formatSignedDecimal(-10f))
    }

    @Test
    fun unsignedDecimalFormats() {
        assertEquals("1.00", formatDecimal(1f))
        assertEquals("0.50", formatDecimal(0.5f))
        assertEquals("4.00", formatDecimal(4f))
    }

    @Test
    fun zoomLabelFormats() {
        assertEquals("1.0×", formatZoom(1f))
        assertEquals("2.0×", formatZoom(2f))
        assertEquals("1.5×", formatZoom(1.5f))
    }

    @Test
    fun displayedCropRectRoundTripsEveryTurnAndFlip() {
        val crop = NormalizedCrop(
            left = 0.1, top = 0.2, right = 0.7, bottom = 0.9
        )
        for (turns in 0..3) {
            for (flipH in listOf(false, true)) {
                for (flipV in listOf(false, true)) {
                    val displayed = displayedCropRect(
                        crop, turns, flipH, flipV
                    )
                    val back = cropFromDisplayed(
                        displayed, turns, flipH, flipV
                    )
                    assertEquals(crop.left, back.left, 1e-9)
                    assertEquals(crop.top, back.top, 1e-9)
                    assertEquals(crop.right, back.right, 1e-9)
                    assertEquals(crop.bottom, back.bottom, 1e-9)
                }
            }
        }
    }

    @Test
    fun quarterTurnSwapsTheDisplayedAxes() {
        // turns=1 is clockwise (PhotoMapping: display x = 1-v, y = u) —
        // the upright crop's left column lands on the display's top row.
        val crop = NormalizedCrop(
            left = 0.1, top = 0.2, right = 0.7, bottom = 0.9
        )
        val d = displayedCropRect(crop, 1, false, false)
        assertEquals(1.0 - 0.9, d[0], 1e-9)
        assertEquals(0.1, d[1], 1e-9)
        assertEquals(1.0 - 0.2, d[2], 1e-9)
        assertEquals(0.7, d[3], 1e-9)
    }

    @Test
    fun cropPresetDerivesFromThePixelRatio() {
        val upright = PixelSize(800, 600)
        // An untouched, whole-frame crop is the Free default.
        assertEquals(
            CropAspectPreset.Free to false,
            CropAspectPreset.derive(PhotoGeometry(), upright)
        )
        // 480x600 = 0.8 → 4:5 in portrait; a centred square → 1:1.
        assertEquals(
            CropAspectPreset.FourFive to true,
            CropAspectPreset.derive(
                PhotoGeometry(crop = NormalizedCrop(0.2, 0.0, 0.8, 1.0)),
                upright
            )
        )
        assertEquals(
            CropAspectPreset.Square,
            CropAspectPreset.derive(
                PhotoGeometry(crop = centeredCrop(upright, 1.0, 0)),
                upright
            ).first
        )
        // 16:9 landscape and its 9:16 reciprocal.
        assertEquals(
            CropAspectPreset.Wide to false,
            CropAspectPreset.derive(
                PhotoGeometry(crop = centeredCrop(upright, 16.0 / 9.0, 0)), upright
            )
        )
        assertEquals(
            CropAspectPreset.Wide to true,
            CropAspectPreset.derive(
                PhotoGeometry(crop = centeredCrop(upright, 9.0 / 16.0, 0)), upright
            )
        )
        // An arbitrary ratio is Free.
        assertEquals(
            CropAspectPreset.Free,
            CropAspectPreset.derive(
                PhotoGeometry(crop = NormalizedCrop(0.1, 0.1, 0.33, 0.9)), upright
            ).first
        )
    }

    @Test
    fun presetRatiosAndTheirReciprocals() {
        assertEquals(null, CropAspectPreset.Free.ratio(800.0, 600.0, false))
        assertEquals(4.0 / 3.0, CropAspectPreset.Original.ratio(800.0, 600.0, false)!!, 1e-12)
        assertEquals(3.0 / 4.0, CropAspectPreset.Original.ratio(800.0, 600.0, true)!!, 1e-12)
        assertEquals(0.8, CropAspectPreset.FourFive.ratio(800.0, 600.0, true)!!, 1e-12)
        assertEquals(1.25, CropAspectPreset.FourFive.ratio(800.0, 600.0, false)!!, 1e-12)
        assertEquals("2:3", CropAspectPreset.ThreeTwo.ratioLabel(true))
        assertEquals("16:9", CropAspectPreset.Wide.ratioLabel(false))
    }

    @Test
    fun degreesFormatShowsOneDecimal() {
        assertEquals("-1.2°", formatDegrees(-1.2f))
        assertEquals("+45.0°", formatDegrees(45f))
        assertEquals("0.0°", formatDegrees(0f))
    }

    @Test
    fun trailingZeroTrimKeepsSignificantZeros() {
        assertEquals("1", trimTrailingZeros(1f))
        assertEquals("-10", trimTrailingZeros(-10f))
        assertEquals("2.5", trimTrailingZeros(2.5f))
        // The exponent's zero is significant — trimming to "1.0E-1" would
        // silently change the entered value by nine orders of magnitude.
        assertEquals("1.0E-10", trimTrailingZeros(1e-10f))
        assertEquals("1.0E-20", trimTrailingZeros(1e-20f))
    }

    @Test
    fun circleCoverageKeepsTheInscribedDiscAndClearsTheCorners() {
        val shape = PhotoCropShape.Circle
        assertEquals(1f, shape.coverage(50, 50, 100, 100))
        assertEquals(0f, shape.coverage(0, 0, 100, 100))
        assertEquals(0f, shape.coverage(99, 99, 100, 100))
        // The edge midpoints sit on the circle, so they stay almost whole.
        assertTrue(shape.coverage(0, 50, 100, 100) > 0.4f)
        assertTrue(shape.coverage(50, 0, 100, 100) > 0.4f)
    }

    @Test
    fun roundedSquareClearsOnlyTheCorners() {
        val shape = PhotoCropShape.RoundedSquare
        assertEquals(0f, shape.coverage(0, 0, 100, 100))
        assertEquals(1f, shape.coverage(0, 50, 100, 100))
        assertEquals(1f, shape.coverage(50, 99, 100, 100))
        // Inside the corner radius (20 px) but past the arc: kept.
        assertEquals(1f, shape.coverage(10, 10, 100, 100))
    }

    @Test
    fun rectangleCoversEveryPixel() {
        assertEquals(1f, PhotoCropShape.Rectangle.coverage(0, 0, 100, 100))
        assertFalse(PhotoCropShape.Rectangle.isShaped)
    }

    @Test
    fun shapedPresetsLockSquareAndCarryTheirShape() {
        for (preset in listOf(CropAspectPreset.Circle, CropAspectPreset.RoundedSquare)) {
            assertTrue(preset.isFixedRatio)
            assertTrue(preset.isSquare, "$preset has no orientation")
            assertEquals(1.0, preset.ratio(800.0, 600.0, portrait = false)!!, 1e-12)
            assertEquals(1.0, preset.ratio(800.0, 600.0, portrait = true)!!, 1e-12)
            assertTrue(preset.shape.isShaped)
        }
        assertEquals(PhotoCropShape.Circle, CropAspectPreset.Circle.shape)
        assertEquals(PhotoCropShape.RoundedSquare, CropAspectPreset.RoundedSquare.shape)
        assertEquals(PhotoCropShape.Rectangle, CropAspectPreset.Square.shape)
    }

    @Test
    fun aShapedCropDerivesItsShapedPreset() {
        val upright = PixelSize(800, 600)
        val circle = PhotoGeometry(
            crop = NormalizedCrop(0.125, 0.0, 0.875, 1.0),
            shape = PhotoCropShape.Circle
        )
        assertEquals(CropAspectPreset.Circle, CropAspectPreset.derive(circle, upright).first)
        val rounded = circle.copy(shape = PhotoCropShape.RoundedSquare)
        assertEquals(CropAspectPreset.RoundedSquare, CropAspectPreset.derive(rounded, upright).first)
        // The same square crop without a shape is the plain 1:1 preset.
        val square = circle.copy(shape = PhotoCropShape.Rectangle)
        assertEquals(CropAspectPreset.Square, CropAspectPreset.derive(square, upright).first)
    }

    @Test
    fun aRememberedShapeIsNotReappliedWhenTheToolOpens() {
        val ui = PhotoToolUiState()
        val fit = ui.seedPreset(PhotoGeometry(), PixelSize(800, 600), CropAspectPreset.Circle)
        assertFalse(fit, "opening the crop tool must not make the photo transparent")
        assertEquals(CropAspectPreset.Free, ui.cropPreset)
    }

    @Test
    fun aTransparentSaveAsksBeforeLosingItsAlpha() {
        val source = MediaAsset.Image("base", "file:///pic.jpg", PixelSize(800, 600))
        val plain = PhotoDocument(source)
        val shaped = PhotoDocument(source, geometry = PhotoGeometry(shape = PhotoCropShape.Circle))
        // An opaque photo saves wherever it was going.
        assertFalse(losesTransparency(plain, null, sourceKeepsAlpha = false))
        assertFalse(losesTransparency(plain, PhotoExportFormat.JPEG, sourceKeepsAlpha = false))
        // A shaped crop of a JPEG source cannot keep the source's container...
        assertTrue(losesTransparency(shaped, null, sourceKeepsAlpha = false))
        assertTrue(losesTransparency(shaped, PhotoExportFormat.JPEG, sourceKeepsAlpha = false))
        assertTrue(losesTransparency(shaped, PhotoExportFormat.HEIF, sourceKeepsAlpha = false))
        // ...but PNG, WebP and a PNG source's own container keep alpha.
        assertFalse(losesTransparency(shaped, PhotoExportFormat.PNG, sourceKeepsAlpha = false))
        assertFalse(losesTransparency(shaped, PhotoExportFormat.WEBP, sourceKeepsAlpha = false))
        assertFalse(losesTransparency(shaped, null, sourceKeepsAlpha = true))
        // A caller that skips the question still never writes an opaque copy.
        assertEquals(
            PhotoExportFormat.PNG,
            transparentExportFormat(shaped, PhotoExportFormat.JPEG, sourceKeepsAlpha = false)
        )
        assertEquals(
            PhotoExportFormat.WEBP,
            transparentExportFormat(shaped, PhotoExportFormat.WEBP, sourceKeepsAlpha = false)
        )
        assertNull(transparentExportFormat(shaped, null, sourceKeepsAlpha = true))
    }
}
