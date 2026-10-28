/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.workers

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

/**
 * JPEG rotation used to decode, rotate and re-encode the pixels, which dropped every EXIF tag
 * (capture date, GPS, camera) and ignored an orientation the camera had already set — so a
 * sideways-stored photo did not visibly turn. It now turns the EXIF orientation losslessly.
 */
@RunWith(AndroidJUnit4::class)
class RotateExifOrientationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun jpeg(orientation: Int? = null): File {
        val file = tmp.newFile("probe.jpg")
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:15 15:42:30")
            setAttribute(ExifInterface.TAG_MAKE, "ReFra Test Lab")
            setLatLong(44.4268, 26.1025)
            orientation?.let { setAttribute(ExifInterface.TAG_ORIENTATION, "$it") }
            saveAttributes()
        }
        return file
    }

    private fun orientationOf(file: File) =
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    @Test
    fun rotationKeepsMetadataAndPixels() {
        val file = jpeg()
        val scanBefore = file.readBytes().let { it.copyOfRange(it.size - 64, it.size) }

        assertTrue(rotateExifOrientation(file, 90))

        val exif = ExifInterface(file.absolutePath)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientationOf(file))
        assertEquals("2026:01:15 15:42:30", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("ReFra Test Lab", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals(44.4268, exif.latLong!![0], 1e-4)
        // Compressed image data is untouched: no re-encode happened.
        assertArrayEquals(scanBefore, file.readBytes().let { it.copyOfRange(it.size - 64, it.size) })
    }

    @Test
    fun rotationComposesWithCameraOrientation() {
        val file = jpeg(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        assertTrue(rotateExifOrientation(file, 90))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, orientationOf(file))

        assertTrue(rotateExifOrientation(file, 180))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, orientationOf(file))
    }

    @Test
    fun nonJpegIsRejected() {
        val file = tmp.newFile("not-a.jpg").apply { writeText("nope") }
        assertFalse(rotateExifOrientation(file, 90))
    }
}
