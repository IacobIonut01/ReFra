/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import androidx.compose.runtime.mutableStateOf
import com.dot.gallery.core.presentation.components.GalleryRailBackHost
import com.dot.gallery.core.presentation.components.GalleryRailSelectionHost
import com.dot.gallery.core.presentation.components.isImmersiveRoute
import com.dot.gallery.core.presentation.components.isRailHiddenRoute
import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRailPolicyTest {
    @Test
    fun secondaryScreensKeepTheRailAndMayOpenTheViewerBesideThem() {
        listOf(
            Screen.TrashedScreen.route,
            Screen.FavoriteScreen.route,
            Screen.SettingsScreen.route,
            Screen.VaultScreen.route,
            "${Screen.LocationTimelineScreen.route}?city=Bucharest",
        ).forEach { route ->
            assertFalse(route, isImmersiveRoute(route))
            assertFalse(route, isRailHiddenRoute(route))
        }
    }

    @Test
    fun viewersAndSetupAreImmersive() {
        assertTrue(isImmersiveRoute("${Screen.MediaViewScreen.route}?mediaId=1&albumId=-1"))
        assertTrue(isImmersiveRoute("${Screen.MediaViewScreen.route}_search?mediaId=1"))
        assertTrue(isImmersiveRoute(Screen.StoryViewerScreen.route))
        assertTrue(isImmersiveRoute(Screen.SetupScreen.route))
    }

    @Test
    fun searchKeepsTheRailAndSplitsWithTheViewer() {
        assertFalse(isRailHiddenRoute(Screen.SearchScreen.route))
        assertFalse(isImmersiveRoute(Screen.SearchScreen.route))
    }

    @Test
    fun railBackRunsTheMostRecentlyComposedScreen() {
        val host = GalleryRailBackHost()
        val calls = mutableListOf<String>()
        val first = mutableStateOf<() -> Unit>({ calls += "first" })
        val second = mutableStateOf<() -> Unit>({ calls += "second" })
        assertNull(host.action)
        host.register(first)
        host.register(second)
        host.action!!.invoke()
        host.unregister(second)
        host.action!!.invoke()
        assertEquals(listOf("second", "first"), calls)
    }

    @Test
    fun hubHostHidesBackButtonsWithoutOfferingTheirActions() {
        val host = GalleryRailBackHost(acceptsRegistrations = false)
        host.register(mutableStateOf<() -> Unit>({}))
        assertNull(host.action)
    }

    @Test
    fun onlyTheOwnerCanWithdrawPublishedSelection() {
        val host = GalleryRailSelectionHost()
        val owner = Any()
        host.publish(owner) {}
        host.withdraw(Any())
        assertTrue(host.content != null)
        host.withdraw(owner)
        assertNull(host.content)
    }
}
