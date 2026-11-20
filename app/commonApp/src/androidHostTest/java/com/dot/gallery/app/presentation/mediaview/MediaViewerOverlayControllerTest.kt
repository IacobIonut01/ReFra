package com.dot.gallery.app.presentation.mediaview

import com.dot.gallery.core.mediaViewerExitSteps
import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.feature.viewer.shouldCommitViewerDismiss
import com.dot.gallery.feature.viewer.viewerDismissProgress
import com.dot.gallery.app.presentation.favorites.FavoriteScreen
import com.dot.gallery.app.presentation.timeline.TimelineScreen
import com.dot.gallery.core.Constants.Target.TARGET_FAVORITES
import com.dot.gallery.core.ViewerExitStep



class MediaViewerOverlayControllerTest {

    @Test
    fun controllerRetainsRouteForExitAfterVisibilityIsCleared() {
        val controller = MediaViewerOverlayController()
        val route = Screen.MediaViewScreen.idAndAlbum(id = 42L, albumId = -1L)

        assertTrue(controller.open(route))
        assertEquals(route, controller.route)
        assertEquals(route, controller.retainedRoute)
        assertEquals(42L, controller.currentMediaId)

        controller.updateCurrentMedia(84L)
        controller.dismiss()

        assertNull(controller.route)
        assertEquals(route, controller.retainedRoute)
        assertEquals(84L, controller.currentMediaId)

        controller.clearRetained()
        assertNull(controller.retainedRoute)
    }

    @Test
    fun successfulOpensGenerateDistinctEntryKeys() {
        val controller = MediaViewerOverlayController()

        assertTrue(controller.open(Screen.MediaViewScreen.idAndAlbum(id = 1L, albumId = -1L)))
        val firstKey = controller.entryKey
        assertTrue(firstKey.isNotEmpty())

        controller.dismiss()
        assertTrue(controller.open(Screen.MediaViewScreen.idAndAlbum(id = 2L, albumId = -1L)))

        assertTrue(controller.entryKey.isNotEmpty())
        assertNotEquals(firstKey, controller.entryKey)
    }

    @Test
    fun rejectedOpenLeavesEntryKeyUnchanged() {
        val controller = MediaViewerOverlayController()

        assertTrue(controller.open(Screen.MediaViewScreen.idAndAlbum(id = 1L, albumId = -1L)))
        val entryKey = controller.entryKey

        assertFalse(controller.open(Screen.TimelineScreen.route))
        assertEquals(entryKey, controller.entryKey)
    }

    @Test
    fun parserPreservesTypedSourceArguments() {
        val parsed = parseMediaViewerRoute(
            "${Screen.MediaViewScreen.route}?mediaId=7&gpsLocationNameCity=New%20York" +
                "&gpsLocationNameCountry=United%20States&latitude=40.7&longitude=-74.0"
        )

        assertEquals(7L, parsed.mediaId)
        assertEquals("New York", parsed.city)
        assertEquals("United States", parsed.country)
        assertEquals(40.7, parsed.latitude!!, 0.0)
        assertEquals(-74.0, parsed.longitude!!, 0.0)
    }

    @Test
    fun overlayEligibilityRequiresMatchingLiveOrigin() {
        val timelineRoute = Screen.MediaViewScreen.idAndAlbum(id = 1L, albumId = -1L)
        val albumRoute = Screen.MediaViewScreen.idAndAlbum(id = 1L, albumId = 9L)
        val favoriteRoute = "${Screen.MediaViewScreen.route}?mediaId=1&target=$TARGET_FAVORITES"

        assertTrue(canPresentMediaViewerOverlay(timelineRoute, Screen.TimelineScreen.route))
        assertTrue(canPresentMediaViewerOverlay(albumRoute, Screen.AlbumViewScreen.albumAndName()))
        assertTrue(canPresentMediaViewerOverlay(favoriteRoute, Screen.FavoriteScreen.route))
        assertFalse(canPresentMediaViewerOverlay(albumRoute, Screen.TimelineScreen.route))
        assertFalse(canPresentMediaViewerOverlay(favoriteRoute, Screen.TimelineScreen.route))
        assertFalse(canPresentMediaViewerOverlay(timelineRoute, Screen.MediaViewScreen.idAndAlbum()))
    }

    @Test
    fun dismissProgressClampsAndCommitsAtTwelvePercent() {
        assertEquals(0f, viewerDismissProgress(-1f, height = 1_000), 0f)
        assertEquals(0.5f, viewerDismissProgress(175f, height = 1_000), 0.001f)
        assertEquals(1f, viewerDismissProgress(500f, height = 1_000), 0f)
        assertFalse(shouldCommitViewerDismiss(119f, height = 1_000))
        assertTrue(shouldCommitViewerDismiss(120f, height = 1_000))
    }

    @Test
    fun overlayExitNavigatePushesUnderneathThenDismisses() {
        assertEquals(
            listOf(ViewerExitStep.Navigate, ViewerExitStep.DismissOverlay),
            mediaViewerExitSteps(isOverlay = true)
        )
    }

    @Test
    fun standaloneExitNavigatePopsViewerBeforePushing() {
        assertEquals(
            listOf(ViewerExitStep.PopViewer, ViewerExitStep.Navigate),
            mediaViewerExitSteps(isOverlay = false)
        )
    }

    @Test
    fun locationsScreenDeepLinkCarriesMediaId() {
        assertEquals(
            "locations_screen?mediaId=42",
            Screen.LocationsScreen.withMediaId(42L)
        )
    }
}
