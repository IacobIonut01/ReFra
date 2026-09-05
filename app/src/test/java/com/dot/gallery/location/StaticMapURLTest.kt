package com.dot.gallery.location

import com.dot.gallery.BuildConfig
import com.dot.gallery.feature_node.presentation.location.MapAppearance
import com.dot.gallery.feature_node.presentation.util.StaticMapURL
import com.dot.gallery.feature_node.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.feature_node.presentation.util.shouldShowManualCartoKeySetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun apiKeyIsOptionalAndEncodedWhenPresent() {
        assertFalse(StaticMapURL(46.77, 23.59, apiKey = "").contains("?key="))
        assertFalse(StaticMapURL(46.77, 23.59, apiKey = "   ").contains("?key="))
        val url = StaticMapURL(46.77, 23.59, apiKey = apiKey)
        assertTrue(url.endsWith("?key=test%20key"))
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
        val url = StaticMapURL(0.0, 0.0)
        assertEquals(BuildConfig.CARTO_BASEMAP_KEY.isNotBlank(), url.contains("?key="))
    }
}
