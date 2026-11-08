/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataCoverTest {

    @Test
    fun metadataRouteStaysOnTopOfTheOpenPhoto() {
        val route = "${Screen.MetadataViewScreen.route}?mediaUri=" +
            "content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F7&isVideo=false"

        assertEquals(
            MetadataCoverRequest(
                mediaUri = "content://media/external/images/media/7",
                isVideo = false,
            ),
            metadataCoverOverMedia(route, isStandalone = false),
        )
    }

    @Test
    fun aVideoFlagAndEncodedUriSurvive() {
        val route = "${Screen.MetadataViewScreen.route}?mediaUri=" +
            "content%3A%2F%2Fmedia%2Fexternal%2Fvideo%2Fmedia%2F9&isVideo=true"

        assertEquals(
            MetadataCoverRequest(
                mediaUri = "content://media/external/video/media/9",
                isVideo = true,
            ),
            metadataCoverRequest(route),
        )
    }

    @Test
    fun standaloneReviewKeepsItsOwnMetadataScreen() {
        val route = "${Screen.MetadataViewScreen.route}?mediaUri=" +
            "content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F7&isVideo=false"

        assertNull(metadataCoverOverMedia(route, isStandalone = true))
    }

    @Test
    fun leavingForAnotherScreenStillClosesTheViewer() {
        assertNull(
            metadataCoverOverMedia(
                Screen.LocationsScreen.withMediaId(42L),
                isStandalone = false,
            ),
        )
        assertNull(metadataCoverRequest("metadata_view_screen"))
        assertNull(metadataCoverRequest("metadata_view_screen?mediaUri="))
    }
}
