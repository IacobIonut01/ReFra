/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.util.getString
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_adv_clear_cache
import com.dot.gallery.generated.resources.cloud_adv_clearing_cache
import com.dot.gallery.generated.resources.cloud_adv_sync_deletions_desc
import com.dot.gallery.generated.resources.cloud_net_auto_url
import com.dot.gallery.generated.resources.cloud_notif_detail_progress
import com.dot.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The redesigned account sub-pages (Networking, Notifications, Advanced) keep every
 * action wired to the config, title the page with the account, and no longer show the
 * experimental marker inside the sync-deletions summary.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class CloudAccountSubPagesTest {

    @get:Rule
    val rule = createComposeRule()

    private val handler = object : EventHandler {
        override val updaterFlow: Flow<UIEvent> = emptyFlow()
        override var navigateAction: (String) -> Unit = {}
        override var toggleNavigationBarAction: (Boolean) -> Unit = {}
        override var navigateUpAction: () -> Unit = {}
        override var setFollowThemeAction: (Boolean) -> Unit = {}
        override fun pushEvent(event: UIEvent) {}
    }

    private fun string(res: StringResource): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    private fun config() = CloudServerConfigEntity(
        id = 7L,
        providerType = ProviderType.IMMICH,
        serverUrl = "https://photos.example.com",
        displayName = "Home photos",
        autoUrlSwitch = true,
        localWifiSsid = "Home Wi-Fi",
        localServerUrl = "https://photos.home.example.com",
        // The same address twice must not crash the list on duplicate keys.
        externalUrls = """["https://photos.example.com","https://photos.example.com"]""",
    )

    private fun host(content: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(
                LocalSettingsStore provides MapSettingsStore(),
                LocalEventHandler provides handler,
            ) {
                GalleryTheme(ignoreUserPreference = true) { content() }
            }
        }
    }

    @Test
    fun advancedMovesExperimentalOutOfSummaryAndKeepsCacheAction() {
        var cleared = 0
        var clearing by mutableStateOf(false)
        host {
            CloudAdvancedSettingsContent(
                config = config(),
                cacheClearing = clearing,
                onUpdateConfig = {},
                onClearImageCache = { cleared++ },
            )
        }

        rule.onNodeWithText("Home photos").assertExists()
        rule.onAllNodesWithText("[EXPERIMENTAL]", substring = true).assertCountEquals(0)
        rule.onNodeWithText(string(Res.string.cloud_adv_sync_deletions_desc)).assertExists()
        rule.onNodeWithTag(CloudAdvancedExperimentalNoteTag).assertExists()

        rule.onNodeWithText(string(Res.string.cloud_adv_clear_cache)).performClick()
        assertEquals(1, cleared)

        clearing = true
        rule.onNodeWithText(string(Res.string.cloud_adv_clearing_cache)).performClick()
        assertEquals("A disabled row must not clear again while clearing", 1, cleared)
    }

    @Test
    fun advancedSwitchesUpdateTheConfig() {
        var current by mutableStateOf(config())
        host {
            CloudAdvancedSettingsContent(
                config = current,
                cacheClearing = false,
                onUpdateConfig = { current = current.it() },
                onClearImageCache = {},
            )
        }
        rule.onNodeWithText(string(Res.string.cloud_adv_sync_deletions_desc)).performClick()
        assertTrue(current.syncRemoteDeletions)
    }

    @Test
    fun networkingShowsActiveTileAndTogglesSwitching() {
        var current by mutableStateOf(config())
        host {
            CloudNetworkingContent(
                config = current,
                effectiveUrl = "https://photos.home.example.com",
                localActive = true,
                onUpdateConfig = { current = current.it() },
            )
        }
        rule.onNodeWithTag(CloudNetLocalTileTag).assertIsSelected()
        rule.onNodeWithTag(CloudNetExternalTileTag).assertIsNotSelected()
        rule.onAllNodesWithText("https://photos.example.com").assertCountEquals(2)

        rule.onNodeWithText(string(Res.string.cloud_net_auto_url)).performClick()
        assertFalse(current.autoUrlSwitch)
        // With switching off the per-network groups go away.
        rule.onAllNodesWithText("https://photos.example.com").assertCountEquals(0)
    }

    @Test
    fun notificationSwitchUpdatesTheConfig() {
        var current by mutableStateOf(config().copy(showBackupDetailProgress = false))
        host {
            CloudNotificationSettingsContent(config = current, onUpdateConfig = { current = current.it() })
        }
        rule.onNodeWithText("Home photos").assertExists()
        rule.onNodeWithText(string(Res.string.cloud_notif_detail_progress)).performClick()
        assertTrue(current.showBackupDetailProgress)
    }
}
