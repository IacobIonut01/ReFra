/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.MediaSelectorImpl
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MockedMediaImageRenderer
import com.dot.gallery.core.startup.LocalStartupWorkGate
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.model.LibraryIndicatorState
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.UIEvent
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.EventHandler
import com.dot.gallery.feature.library.ui.CloudLibraryState
import com.dot.gallery.feature.library.ui.LibraryActions
import com.dot.gallery.feature.library.ui.LibraryCategoryPreview
import com.dot.gallery.feature.library.ui.LibraryGeoPreview
import com.dot.gallery.feature.library.ui.LibraryHeader
import com.dot.gallery.feature.library.ui.LibraryHeaderLayout
import com.dot.gallery.feature.library.ui.LibraryRoutes
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class LibrarySnapshotNavigationTest {

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

    private fun person(id: String) = PersonInfo(
        id = id,
        name = "person-$id",
        providerType = ProviderType.LOCAL_PEOPLE,
        serverConfigId = LOCAL_PEOPLE_CONFIG_ID,
        assetCount = 2
    )

    private fun media(id: Long) = UriMedia(
        id = id,
        label = "photo-$id.jpg",
        uri = Uri.parse("content://media/external/images/media/$id"),
        path = "/storage/emulated/0/DCIM/photo-$id.jpg",
        relativePath = "DCIM/",
        albumID = 10L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L + id,
        fullDate = "2023-11-14",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_024L
    )

    private fun locationRow(id: Long) = LocationMedia(
        media = media(id),
        location = "loc-$id",
        city = "loc-$id",
        country = "DE",
        latitude = 52.5,
        longitude = 13.4
    )

    private fun category(id: Long) = LibraryCategoryPreview(
        id = id,
        name = "category-$id",
        mediaCount = 3,
        thumbnailMedia = null
    )

    private fun populatedSnapshot() = LibrarySnapshot(
        categories = (1L..8L).map(::category),
        categoryCount = 12,
        locations = (0 until 20).map { locationRow(1_000L + it) },
        locationCount = 34,
        latestGeo = LibraryGeoPreview(media(3L), 52.5, 13.4),
        peopleCount = 20,
        peopleCountsByAccount = mapOf(LOCAL_PEOPLE_CONFIG_ID to 20),
        cloud = CloudLibraryState(
            hasPeople = true,
            people = (0 until 12).map { person("p$it") }
        ),
        indicators = LibraryIndicatorState(trashCount = 1, favoriteCount = 2),
    )

    private val routes = LibraryRoutes(
        library = "library",
        settings = "settings",
        locations = "locations",
        people = "people",
        categories = "categories",
        trash = "trash",
        favorites = "favorites",
        vault = "vault",
        ignored = "ignored",
        privateFolder = "privateFolder",
        cloudArchive = "cloudArchive",
        memories = "memories",
        cloudSharedLinks = "cloudSharedLinks",
        cloudBackup = "cloudBackup",
        cloudAccounts = "cloudAccounts"
    )

    private class Rig(
        val snapshot: MutableStateFlow<LibrarySnapshot>,
        val navigated: MutableList<String> = mutableListOf(),
    )

    private fun setHeader(initial: LibrarySnapshot): Rig {
        val rig = Rig(MutableStateFlow(initial))
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                CompositionLocalProvider(
                    LocalEventHandler provides handler,
                    LocalMediaSelector provides MediaSelectorImpl(),
                    LocalStartupWorkGate provides StartupWorkGate(),
                    // In-memory store so section toggles stay hermetic per test run.
                    LocalSettingsStore provides MapSettingsStore(),
                    LocalMediaImageRenderer provides MockedMediaImageRenderer
                ) {
                    val snapshot by rig.snapshot.collectAsState()
                    LibraryHeader(
                        state = LibraryState(
                            snapshot = snapshot,
                            modelStatus = ModelStatus.READY,
                            aiAvailable = true,
                            mapsEnabled = false
                        ),
                        actions = LibraryActions(onNavigate = { rig.navigated += it }),
                        routes = routes,
                        layout = LibraryHeaderLayout.COMPACT,
                        onEnterEditMode = {},
                    )
                }
            }
        }
        return rig
    }

    private fun assertSections(locations: Boolean, people: Boolean, categories: Boolean) {
        listOf(
            "library-locations" to locations,
            "library-people" to people,
            "library-categories" to categories,
        ).forEach { (tag, expected) ->
            val node = rule.onNodeWithTag(tag)
            if (expected) node.assertIsDisplayed() else node.assertDoesNotExist()
        }
    }

    @Test
    fun firstDrawDeliversCachedContentBeforeAnyLiveUpdate() {
        setHeader(populatedSnapshot())
        rule.onNodeWithText("34").assertIsDisplayed()
        rule.onNodeWithText("20").assertIsDisplayed()
        rule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun restoredContentRendersAllSectionsImmediately() {
        setHeader(populatedSnapshot())
        assertSections(locations = true, people = true, categories = true)
    }

    @Test
    fun sectionCardsOpenTheirFullScreens() {
        val rig = setHeader(populatedSnapshot())
        rule.onNodeWithTag("library-locations").performClick()
        rule.onNodeWithTag("library-people").performClick()
        rule.onNodeWithTag("library-categories").performClick()
        rule.waitForIdle()
        assertEquals(listOf("locations", "people", "categories"), rig.navigated)
    }

    @Test
    fun missingSectionsLeaveNoPlaceholder() {
        val rig = setHeader(
            populatedSnapshot().copy(locations = emptyList(), locationCount = 0)
        )
        assertSections(locations = false, people = true, categories = true)

        rig.snapshot.value = populatedSnapshot().copy(
            cloud = CloudLibraryState(hasPeople = false, people = emptyList()),
            peopleCount = 0,
        )
        rule.waitForIdle()
        assertSections(locations = true, people = false, categories = true)

        rig.snapshot.value = populatedSnapshot().copy(
            locations = emptyList(),
            locationCount = 0,
            cloud = CloudLibraryState(hasPeople = false, people = emptyList()),
            peopleCount = 0,
            categories = null,
            categoryCount = 0,
        )
        rule.waitForIdle()
        assertSections(locations = false, people = false, categories = false)
    }

    @Test
    fun hiddenPeopleKeepTheCardReachable() {
        setHeader(
            populatedSnapshot().copy(
                cloud = CloudLibraryState(hasPeople = true, people = emptyList(), hiddenPeopleCount = 2)
            )
        )
        rule.onNodeWithTag("library-people").assertIsDisplayed()
    }

    @Test
    fun unknownCategoriesRenderNoCardWhileKnownEmptyStaysReachable() {
        val rig = setHeader(populatedSnapshot().copy(categories = null, categoryCount = 0))
        rule.onNodeWithTag("library-categories").assertDoesNotExist()

        rig.snapshot.value = populatedSnapshot().copy(categories = emptyList(), categoryCount = 0)
        rule.waitForIdle()
        rule.onNodeWithTag("library-categories").assertIsDisplayed()
    }
}
