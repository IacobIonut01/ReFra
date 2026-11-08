/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.util

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.cloud.ui.CloudAddServerScreen
import com.dot.gallery.cloud.ui.people.PersonDetailScreen
import com.dot.gallery.app.presentation.classifier.CategoryViewScreen
import com.dot.gallery.app.presentation.help.TutorialCategoryScreen
import com.dot.gallery.app.presentation.help.TutorialDetailScreen
import com.dot.gallery.app.presentation.location.LocationTimelineScreen
import com.dot.gallery.core.presentation.util.Screen


class ScreenRouteTest {

    @Test
    fun stringRouteBuildersEncodeAndRoundTripEveryArgument() {
        val value = "München / ? & # = + %"
        val otherValue = "Țară / ? & # = + %"
        val cases = listOf(
            RouteCase(Screen.AlbumViewScreen.album(7L, value), mapOf("albumName" to value)),
            RouteCase(Screen.MediaViewScreen.idAndCategory(7L, value), mapOf("category" to value)),
            RouteCase(
                Screen.MediaViewScreen.idAndPerson(7L, 11L, value),
                mapOf("configId" to "11", "personId" to value),
            ),
            RouteCase(
                Screen.MediaViewScreen.idAndLocation(7L, value, otherValue, 0.0, 0.0),
                mapOf(
                    "gpsLocationNameCity" to value,
                    "gpsLocationNameCountry" to otherValue,
                    "latitude" to "0.0",
                    "longitude" to "0.0",
                ),
            ),
            RouteCase(
                Screen.LocationTimelineScreen.location(value, otherValue, 0.0, 0.0),
                mapOf(
                    "gpsLocationNameCity" to value,
                    "gpsLocationNameCountry" to otherValue,
                    "latitude" to "0.0",
                    "longitude" to "0.0",
                ),
            ),
            RouteCase(Screen.CategoryViewScreen.category(value), mapOf("category" to value)),
            RouteCase(
                Screen.CollectionAlbumSelectorScreen.collectionName(value),
                mapOf("collectionName" to value),
            ),
            RouteCase(
                Screen.MetadataViewScreen.uriAndType(value, true),
                mapOf("mediaUri" to value),
            ),
            RouteCase(Screen.TutorialCategoryScreen.category(value), mapOf("category" to value)),
            RouteCase(Screen.TutorialDetailScreen.tipId(value), mapOf("tipId" to value)),
            RouteCase(Screen.CloudAddServerScreen.providerType(value), mapOf("providerType" to value)),
            RouteCase(
                Screen.PlaceDetailScreen.city(value, otherValue),
                mapOf("city" to value, "country" to otherValue),
            ),
            RouteCase(
                Screen.PersonDetailScreen.personId(11L, value),
                mapOf("configId" to "11", "personId" to value),
            ),
        )

        cases.forEach { case ->
            val parsed = Uri.parse(case.route)
            case.arguments.forEach { (name, expected) ->
                assertEquals("$name did not round-trip in ${case.route}", expected, parsed.getQueryParameter(name))
            }
            assertReservedCharactersAreEncoded(case.route)
        }
    }

    @Test
    fun locationViewerRouteCarriesSlideshowFlag() {
        val plain = Uri.parse(Screen.MediaViewScreen.idAndLocation(7L, "Banff", "Canada"))
        val slideshow = Uri.parse(Screen.MediaViewScreen.idAndLocation(7L, "Banff", "Canada", slideshow = true))

        assertEquals("false", plain.getQueryParameter("slideshow"))
        assertEquals("true", slideshow.getQueryParameter("slideshow"))
    }

    @Test
    fun coordinateOnlyLocationRouteKeepsZeroCoordinates() {
        val route = Screen.LocationTimelineScreen.location("", "", 0.0, 0.0)
        val parsed = Uri.parse(route)

        assertEquals("", parsed.getQueryParameter("gpsLocationNameCity"))
        assertEquals("", parsed.getQueryParameter("gpsLocationNameCountry"))
        assertEquals("0.0", parsed.getQueryParameter("latitude"))
        assertEquals("0.0", parsed.getQueryParameter("longitude"))
    }

    private fun assertReservedCharactersAreEncoded(route: String) {
        listOf("%20", "%2F", "%3F", "%26", "%23", "%3D", "%2B", "%25").forEach { encoding ->
            assertTrue("Expected $encoding in $route", route.contains(encoding))
        }
    }

    private data class RouteCase(
        val route: String,
        val arguments: Map<String, String>,
    )
}
