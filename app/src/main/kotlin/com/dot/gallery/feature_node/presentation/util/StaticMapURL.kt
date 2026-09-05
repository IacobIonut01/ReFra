/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.presentation.location.MapAppearance
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

internal fun effectiveCartoBasemapKey(embeddedKey: String, userKey: String): String =
    embeddedKey.takeIf(String::isNotBlank) ?: userKey.trim()

internal fun shouldShowManualCartoKeySetting(mapsEnabled: Boolean, embeddedKey: String): Boolean =
    mapsEnabled && embeddedKey.isBlank()

/**
 * Generates a static map tile URL for a given lat/lng.
 * Adds the CARTO basemap key when available.
 */
object StaticMapURL {

    private const val CARTO_LIGHT = "https://basemaps.cartocdn.com/rastertiles/voyager"
    private const val CARTO_DARK = "https://basemaps.cartocdn.com/rastertiles/dark_all"

    operator fun invoke(
        latitude: Double,
        longitude: Double,
        appearance: MapAppearance = MapAppearance.SYSTEM,
        effectiveAppIsDark: Boolean = false,
        zoom: Int = 12,
        apiKey: String = BuildConfig.CARTO_BASEMAP_KEY,
    ): String {
        val safeZoom = zoom.coerceIn(0, 20)
        val x = lonToTileX(longitude, safeZoom)
        val y = latToTileY(latitude, safeZoom)
        val base = if (appearance.resolvesDark(effectiveAppIsDark)) CARTO_DARK else CARTO_LIGHT
        val keyQuery = if (apiKey.isBlank()) {
            ""
        } else {
            val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8).replace("+", "%20")
            "?key=$encodedKey"
        }
        return "$base/$safeZoom/$x/$y@2x.png$keyQuery"
    }

    internal fun lonToTileX(longitude: Double, zoom: Int): Int {
        val count = 1 shl zoom.coerceIn(0, 20)
        val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0
        return floor(normalized / 360.0 * count).toInt().coerceIn(0, count - 1)
    }

    internal fun latToTileY(latitude: Double, zoom: Int): Int {
        val count = 1 shl zoom.coerceIn(0, 20)
        val lat = latitude.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(lat)
        return floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * count
        ).toInt().coerceIn(0, count - 1)
    }

    internal fun tileYToLatitude(y: Int, zoom: Int): Double {
        val count = 1 shl zoom.coerceIn(0, 20)
        return Math.toDegrees(kotlin.math.atan(sinh(PI * (1.0 - 2.0 * y / count))))
    }
}