package com.dot.gallery.location

import com.dot.gallery.core.presentation.util.StaticMapDefaults
import com.dot.gallery.core.presentation.util.StaticMapURL
import com.dot.gallery.core.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.core.presentation.util.shouldShowManualCartoKeySetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.core.location.MapAppearance
import com.dot.gallery.BuildConfig



class StaticMapURLTest {
    private val apiKey = "test key"

    @Test
    fun appearanceSelectsMatchingTileProvider() {
        val light = StaticMapURL(
            46.77,
            23.59,
            MapAppearance.LIGHT,
            effectiveAppIsDark = true,
            apiKey = apiKey,
        )
        val dark = StaticMapURL(
            46.77,
            23.59,
            MapAppearance.DARK,
            effectiveAppIsDark = false,
            apiKey = apiKey,
        )
        assertTrue(light.contains("rastertiles/voyager"))
        assertTrue(dark.contains("rastertiles/dark_all"))
    }

    @Test
    fun systemUsesEffectiveAppTheme() {
        val light = StaticMapURL(
            0.0,
            0.0,
            MapAppearance.SYSTEM,
            effectiveAppIsDark = false,
            apiKey = apiKey,
        )
        val dark = StaticMapURL(
            0.0,
            0.0,
            MapAppearance.SYSTEM,
            effectiveAppIsDark = true,
            apiKey = apiKey,
        )
        assertTrue(light.contains("voyager"))
        assertTrue(dark.contains("dark_all"))
    }

    @Test
    fun coordinatesAreClampedAndWrapped() {
        val url = StaticMapURL(
            1000.0,
            540.0,
            MapAppearance.LIGHT,
            zoom = 8,
            apiKey = apiKey,
        )
        val parts = url.substringAfter("voyager/").substringBefore("@2x.png").split('/')
        assertEquals(listOf("8", "0", "0"), parts)
    }

    @Test
    fun previewTilesPlaceTheRequestedCoordinateAtTheViewportCenter() {
        val latitude = 36.4241
        val longitude = 32.1581
        val zoom = 14
        val width = 919
        val height = 400
        val worldX = StaticMapURL.lonToWorldTileX(longitude, zoom)
        val worldY = StaticMapURL.latToWorldTileY(latitude, zoom)
        val containingTile = StaticMapURL.centeredTiles(latitude, longitude, zoom, width, height)
            .single { it.tileX == worldX.toInt() && it.tileY == worldY.toInt() }

        assertEquals(
            width / 2.0,
            containingTile.leftPx + (worldX - containingTile.tileX) * containingTile.sizePx,
            0.01,
        )
        assertEquals(
            height / 2.0,
            containingTile.topPx + (worldY - containingTile.tileY) * containingTile.sizePx,
            0.01,
        )
    }

    @Test
    fun apiKeyIsOptionalAndEncodedWhenPresent() {
        assertFalse(StaticMapURL(46.77, 23.59, apiKey = "").contains("?key="))
        assertFalse(StaticMapURL(46.77, 23.59, apiKey = "   ").contains("?key="))
        val url = StaticMapURL(46.77, 23.59, apiKey = apiKey)
        assertTrue(url.endsWith("?key=test%20key"))
    }

    @Test
    fun apiKeyWithReservedAndNonAsciiCharactersIsPercentEncoded() {
        val url = StaticMapURL.tileUrl(
            tileX = 1,
            tileY = 2,
            appearance = MapAppearance.LIGHT,
            effectiveAppIsDark = false,
            zoom = 3,
            apiKey = "k+y/a=b&c d?\u00e9",
        )
        assertEquals(
            "https://basemaps.cartocdn.com/rastertiles/voyager/3/1/2@2x.png" +
                "?key=k%2By%2Fa%3Db%26c%20d%3F%C3%A9",
            url,
        )
    }

    @Test
    fun zoomIsClamped() {
        val url = StaticMapURL(0.0, 0.0, zoom = 100, apiKey = apiKey)
        assertEquals("20", url.substringAfter("voyager/").substringBefore('/'))
    }

    @Test
    fun embeddedKeyTakesPrecedenceWithoutExposingManualSetting() {
        assertEquals("embedded", effectiveCartoBasemapKey("embedded", "manual"))
        assertFalse(shouldShowManualCartoKeySetting(mapsEnabled = true, embeddedKey = "embedded"))
    }

    @Test
    fun manualKeyIsUsedOnlyWhenEmbeddedKeyIsMissing() {
        assertEquals("manual", effectiveCartoBasemapKey("", " manual "))
        assertTrue(shouldShowManualCartoKeySetting(mapsEnabled = true, embeddedKey = ""))
        assertFalse(shouldShowManualCartoKeySetting(mapsEnabled = false, embeddedKey = ""))
    }

    @Test
    fun buildConfigControlsDefaultUrlAuthentication() {
        StaticMapDefaults.apiKey = BuildConfig.CARTO_BASEMAP_KEY
        try {
            val url = StaticMapURL(0.0, 0.0)
            assertEquals(BuildConfig.CARTO_BASEMAP_KEY.isNotBlank(), url.contains("?key="))
        } finally {
            StaticMapDefaults.apiKey = ""
        }
    }
}
