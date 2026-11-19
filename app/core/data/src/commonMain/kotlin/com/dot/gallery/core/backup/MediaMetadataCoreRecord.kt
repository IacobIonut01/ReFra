/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class MediaMetadataCoreRecord(
    val imageDescription: String? = null,
    val dateTimeOriginal: String? = null,
    val manufacturerName: String? = null,
    val modelName: String? = null,
    val lensModel: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val focalLength: Double? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsLocationName: String? = null,
    val gpsLocationNameCountry: String? = null,
    val gpsLocationNameCity: String? = null,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageResolutionX: Double? = null,
    val imageResolutionY: Double? = null,
    val resolutionUnit: Int? = null
)
