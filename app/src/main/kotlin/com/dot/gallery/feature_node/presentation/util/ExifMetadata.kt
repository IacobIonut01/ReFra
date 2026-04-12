/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import androidx.exifinterface.media.ExifInterface
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

class ExifMetadata(exifInterface: ExifInterface) {
    val manufacturerName: String? =
        exifInterface.getAttribute(ExifInterface.TAG_MAKE)
    val modelName: String? =
        exifInterface.getAttribute(ExifInterface.TAG_MODEL)
    val apertureValue: Double =
        exifInterface.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, 0.0)

    val focalLength: Double =
        exifInterface.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
    val isoValue: Int =
        exifInterface.getAttributeInt(ExifInterface.TAG_ISO_SPEED, 0)
    val imageWidth: Int =
        exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)
    val imageHeight: Int =
        exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)
    val imageMp: String
        get() {
            val roundingMP = DecimalFormat("#.#").apply { roundingMode = RoundingMode.DOWN }
            return roundingMP.format(imageWidth * imageHeight / 1024000.0)
        }

    val imageDescription: String? =
        exifInterface.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)

    val lensDescription: String?
        get() {
            return if (!manufacturerName.isNullOrEmpty() && !modelName.isNullOrEmpty() && apertureValue != 0.0) {
                "$manufacturerName $modelName - f/$apertureValue - $imageMp MP"
            } else null
        }

    /**
     * 0 - latitude
     * 1 - longitude
     */
    val gpsLatLong: DoubleArray? =
        exifInterface.latLong

    val formattedCords: String?
        get() = if (gpsLatLong != null) String.format(
            Locale.getDefault(), "%.3f, %.3f", gpsLatLong[0], gpsLatLong[1]
        ) else null

}