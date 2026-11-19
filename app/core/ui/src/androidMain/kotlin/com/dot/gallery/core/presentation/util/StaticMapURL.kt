/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import com.dot.gallery.core.location.MapAppearance
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sinh
import kotlin.math.tan


fun effectiveCartoBasemapKey(embeddedKey: String, userKey: String): String =
    embeddedKey.takeIf(String::isNotBlank) ?: userKey.trim()

fun shouldShowManualCartoKeySetting(mapsEnabled: Boolean, embeddedKey: String): Boolean =
    mapsEnabled && embeddedKey.isBlank()

object StaticMapURL {

    private const val CARTO_LIGHT = "https://basemaps.cartocdn.com/rastertiles/voyager"
    private const val CARTO_DARK = "https://basemaps.cartocdn.com/rastertiles/dark_all"

    operator fun invoke(
        latitude: Double,
        longitude: Double,
        appearance: MapAppearance = MapAppearance.SYSTEM,
        effectiveAppIsDark: Boolean = false,
        zoom: Int = 12,
        apiKey: String = StaticMapDefaults.apiKey,
    ): String {
        val safeZoom = zoom.coerceIn(0, 20)
        return tileUrl(
            tileX = lonToTileX(longitude, safeZoom),
            tileY = latToTileY(latitude, safeZoom),
            appearance = appearance,
            effectiveAppIsDark = effectiveAppIsDark,
            zoom = safeZoom,
            apiKey = apiKey,
        )
    }

    fun tileUrl(
        tileX: Int,
        tileY: Int,
        appearance: MapAppearance,
        effectiveAppIsDark: Boolean,
        zoom: Int,
        apiKey: String,
    ): String {
        val safeZoom = zoom.coerceIn(0, 20)
        val count = 1 shl safeZoom
        val wrappedX = ((tileX % count) + count) % count
        val safeY = tileY.coerceIn(0, count - 1)
        val base = if (appearance.resolvesDark(effectiveAppIsDark)) CARTO_DARK else CARTO_LIGHT
        val keyQuery = if (apiKey.isBlank()) {
            ""
        } else {
            val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name()).replace("+", "%20")
            "?key=$encodedKey"
        }
        return "$base/$safeZoom/$wrappedX/$safeY@2x.png$keyQuery"
    }

    fun centeredTiles(
        latitude: Double,
        longitude: Double,
        zoom: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): List<StaticMapTilePlacement> {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0 ||
            !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return emptyList()
        val safeZoom = zoom.coerceIn(0, 20)
        val count = 1 shl safeZoom
        val worldX = lonToWorldTileX(longitude, safeZoom)
        val worldY = latToWorldTileY(latitude, safeZoom)
        val tileSizePx = max(viewportWidthPx, viewportHeightPx).toFloat()
        val halfWidthInTiles = viewportWidthPx / (2.0 * tileSizePx)
        val halfHeightInTiles = viewportHeightPx / (2.0 * tileSizePx)
        val startX = floor(worldX - halfWidthInTiles).toInt()
        val endX = ceil(worldX + halfWidthInTiles).toInt() - 1
        val startY = floor(worldY - halfHeightInTiles).toInt().coerceAtLeast(0)
        val endY = (ceil(worldY + halfHeightInTiles).toInt() - 1).coerceAtMost(count - 1)
        return buildList {
            for (tileY in startY..endY) {
                for (tileX in startX..endX) {
                    add(
                        StaticMapTilePlacement(
                            tileX = tileX,
                            tileY = tileY,
                            leftPx = (viewportWidthPx / 2.0 + (tileX - worldX) * tileSizePx).toFloat(),
                            topPx = (viewportHeightPx / 2.0 + (tileY - worldY) * tileSizePx).toFloat(),
                            sizePx = tileSizePx,
                        )
                    )
                }
            }
        }
    }

    fun lonToWorldTileX(longitude: Double, zoom: Int): Double {
        val count = 1 shl zoom.coerceIn(0, 20)
        val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0
        return normalized / 360.0 * count
    }

    fun latToWorldTileY(latitude: Double, zoom: Int): Double {
        val count = 1 shl zoom.coerceIn(0, 20)
        val lat = latitude.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(lat)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * count)
            .coerceIn(0.0, count.toDouble() - 1e-9)
    }

    fun lonToTileX(longitude: Double, zoom: Int): Int =
        floor(lonToWorldTileX(longitude, zoom)).toInt()

    fun latToTileY(latitude: Double, zoom: Int): Int =
        floor(latToWorldTileY(latitude, zoom)).toInt()

    fun tileYToLatitude(y: Int, zoom: Int): Double {
        val count = 1 shl zoom.coerceIn(0, 20)
        return Math.toDegrees(kotlin.math.atan(sinh(PI * (1.0 - 2.0 * y / count))))
    }
}