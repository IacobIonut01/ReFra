/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "media_metadata")
@Serializable
data class MediaMetadata(
    @PrimaryKey val mediaId: Long,

    val imageDescription: String?,
    val dateTimeOriginal: String?,
    val manufacturerName: String?,
    val modelName: String?,
    val lensModel: String? = null,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    val focalLength: Double? = null,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsLocationName: String?,
    val gpsLocationNameCountry: String?,
    val gpsLocationNameCity: String?,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolutionX: Double?,    // e.g. 72.0 PPI
    val imageResolutionY: Double?,    // e.g. 72.0 PPI
    val resolutionUnit: Int?,         // 2=inches, 3=cm

    val durationMs: Long?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val frameRate: Float?,
    val bitRate: Int?,

    val isNightMode: Boolean,
    val isPanorama: Boolean,
    val isPhotosphere: Boolean,
    val isLongExposure: Boolean,
    val isMotionPhoto: Boolean
) {

    companion object {
        val PANORMA_KEYS = arrayOf(
            "ProjectionType",
            "FullPanoHeightPixels",
            "FullPanoWidthPixels"
        )
        val PHOTOSPERE_KEYS = arrayOf(
            "IsPhotosphere",
            "UsePanoramaViewer"
        )
        val PHOTOSPHERE_VALUES = arrayOf(
            "True",
            "com.google.android.apps.camera.gallery.specialtype.SpecialType-PHOTOSPHERE"
        )
        val LONG_EXPOSURE_KEYS = arrayOf(
            "BurstID",
            "CameraBurstID"
        )
    }
}

val MediaMetadata.isRelevant: Boolean
    get() = isNightMode || isPanorama || isPhotosphere || isLongExposure || isMotionPhoto

val MediaMetadata.searchableText: String
    get() = buildString {
        imageDescription?.let { append(it).append(' ') }
        gpsLocationName?.let { append(it).append(' ') }
        gpsLocationNameCity?.let { append(it).append(' ') }
        gpsLocationNameCountry?.let { append(it).append(' ') }
        manufacturerName?.let { append(it).append(' ') }
        modelName?.let { append(it) }
    }

val MediaMetadata.formattedCords: String?
    get() = if (gpsLatitude != null && gpsLongitude != null &&
        !isRedactedCoordinate(gpsLatitude, gpsLongitude)
    ) "${gpsLatitude.fmt3()}, ${gpsLongitude.fmt3()}" else null

val MediaMetadata.imageMp: String
    get() {
        val tenths = kotlin.math.floor(imageWidth * imageHeight / 102400.0).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }

val MediaMetadata.lensDescription: String?
    get() {
        return if (!manufacturerName.isNullOrEmpty() && !modelName.isNullOrEmpty() && aperture != null) {
            "$manufacturerName $modelName - $aperture - $imageMp MP"
        } else null
    }


/** Fixed 3-decimal formatting — String.format is JVM-only. */
private fun Double.fmt3(): String {
    val neg = this < 0
    val scaled = kotlin.math.round(kotlin.math.abs(this) * 1000).toLong()
    return "${if (neg) "-" else ""}${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
}
