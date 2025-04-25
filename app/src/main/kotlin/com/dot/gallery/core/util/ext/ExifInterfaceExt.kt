/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import android.util.Size
import androidx.exifinterface.media.ExifInterface

private const val DEFAULT_VALUE_INT = -1
private const val DEFAULT_VALUE_DOUBLE = -1.0

fun ExifInterface.updateImageDescription(string: String) {
    setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, string)
}

fun ExifInterface.deleteMetadata() {
    val tags = arrayOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE
    )
    deleteAttributes(tags)
}

fun ExifInterface.deleteGpsMetadata() {
    val tags = arrayOf(
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF
    )
    deleteAttributes(tags)
}

private fun ExifInterface.deleteAttributes(
    tags: Array<String>
) {
    tags.forEach { tag ->
        runCatching {
            setAttribute(tag, null)
        }
    }
}

fun ExifInterface.getAttributeInt(tag: String) =
    getAttributeInt(tag, DEFAULT_VALUE_INT).takeIf {
        it != DEFAULT_VALUE_INT
    }

fun ExifInterface.getAttributeDouble(tag: String): Double? =
    getAttributeDouble(tag, DEFAULT_VALUE_DOUBLE).takeIf {
        it != DEFAULT_VALUE_DOUBLE
    }

val ExifInterface.artist
    get() = getAttribute(ExifInterface.TAG_ARTIST)

val ExifInterface.apertureValue
    get() = getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE)

val ExifInterface.copyright
    get() = getAttribute(ExifInterface.TAG_COPYRIGHT)

val ExifInterface.exposureTime
    get() = getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME)

val ExifInterface.isoSpeed
    get() = getAttributeInt(ExifInterface.TAG_ISO_SPEED)

val ExifInterface.focalLength
    get() = getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH)

val ExifInterface.make
    get() = getAttribute(ExifInterface.TAG_MAKE)

val ExifInterface.model
    get() = getAttribute(ExifInterface.TAG_MODEL)

val ExifInterface.pixelXDimension
    get() = getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION)

val ExifInterface.pixelYDimension
    get() = getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION)

val ExifInterface.size
    get() = pixelXDimension?.let { x -> pixelYDimension?.let { y -> Size(x, y) } }

val ExifInterface.software
    get() = getAttribute(ExifInterface.TAG_SOFTWARE)

var ExifInterface.userComment
    get() = getAttribute(ExifInterface.TAG_USER_COMMENT)
    set(value) {
        setAttribute(ExifInterface.TAG_USER_COMMENT, value)
    }

val ExifInterface.isSupportedFormatForSavingAttributes: Boolean
    get() {
        val mimeType = ExifInterface::class.java.getDeclaredField("mMimeType").apply {
            isAccessible = true
        }.get(this) as Int

        val isSupportedFormatForSavingAttributes = ExifInterface::class.java.getDeclaredMethod(
            "isSupportedFormatForSavingAttributes", Int::class.java
        ).apply {
            isAccessible = true
        }

        return isSupportedFormatForSavingAttributes.invoke(null, mimeType) as Boolean
    }