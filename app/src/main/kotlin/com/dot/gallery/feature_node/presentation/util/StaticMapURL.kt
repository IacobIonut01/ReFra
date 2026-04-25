/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Generates a static map tile URL for a given lat/lng.
 * No API key required. Uses Carto CDN basemap tiles (light/dark).
 */
object StaticMapURL {

    private const val CARTO_LIGHT = "https://basemaps.cartocdn.com/rastertiles/voyager"
    private const val CARTO_DARK = "https://basemaps.cartocdn.com/rastertiles/dark_all"

    operator fun invoke(
        latitude: Double,
        longitude: Double,
        darkTheme: Boolean = false,
        zoom: Int = 12,
    ): String {
        val x = lonToTileX(longitude, zoom)
        val y = latToTileY(latitude, zoom)
        val base = if (darkTheme) CARTO_DARK else CARTO_LIGHT
        return "$base/$zoom/$x/$y@2x.png"
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return floor(
            (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)
        ).toInt()
    }
}