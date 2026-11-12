/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.core.presentation.util.Screen
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.cloud.ui.backup.BackupOptionsScreen
import com.dot.gallery.cloud.ui.settings.CloudAdvancedSettingsScreen
import com.dot.gallery.cloud.ui.settings.CloudNetworkingScreen
import com.dot.gallery.cloud.ui.settings.CloudNotificationSettingsScreen


class CloudAccountSettingsRouteTest {

    @Test
    fun accountSettingsRoutesRetainSameProviderAccountIdentity() {
        val firstConfigId = 41L
        val secondConfigId = 42L
        val routeBuilders = listOf<(Long) -> String>(
            Screen.CloudNetworkingScreen::configId,
            Screen.CloudNotificationSettingsScreen::configId,
            Screen.CloudAdvancedSettingsScreen::configId,
            Screen.BackupOptionsScreen::configId
        )

        routeBuilders.forEach { route ->
            val first = route(firstConfigId)
            val second = route(secondConfigId)
            assertTrue(first.endsWith("configId=$firstConfigId"))
            assertTrue(second.endsWith("configId=$secondConfigId"))
            assertNotEquals(first, second)
        }
    }
}
