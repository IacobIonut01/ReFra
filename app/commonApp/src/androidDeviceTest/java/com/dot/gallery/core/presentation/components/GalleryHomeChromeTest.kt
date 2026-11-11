/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class GalleryHomeChromeTest {
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun compactDockKeepsDestinationsAndSearchIndependentlyReachable() {
        var selected by mutableStateOf("photos")
        var searchClicks = 0
        val store = MapSettingsStore().apply { seed(SettingsKeys.allowBlur to false) }
        val items = listOf(
            NavigationItem("Photos", "photos", Icons.Outlined.Photo),
            NavigationItem("Albums", "albums", Icons.Outlined.FolderOpen),
            NavigationItem("Library", "library", Icons.Outlined.PhotoLibrary),
        )
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                PreviewHost {
                    Box(Modifier.width(320.dp)) {
                        GalleryNavigationBarContent(
                            selectedRoute = selected,
                            navigationItems = items,
                            onClick = { selected = it },
                            onSearchClick = { searchClicks++ },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag(galleryNavigationItemTag("photos"))
            .assertIsDisplayed()
            .assertIsSelected()
            .assertContentDescriptionEquals("Photos")
        rule.onNodeWithTag(galleryNavigationItemTag("albums"))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsSelected()
        rule.onNodeWithTag(GalleryNavigationSearchTag)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(64.dp)
            .assertHasClickAction()
            .performClick()
        rule.runOnIdle {
            assertEquals("albums", selected)
            assertEquals(1, searchClicks)
        }
    }

    @Test
    fun toolbarRetainsMenuAndBrandWhenTheOptionalFilterIsHidden() {
        var showFilter by mutableStateOf(true)
        var brandClicks = 0
        var menuClicks = 0
        rule.setContent {
            PreviewHost {
                Box(Modifier.width(320.dp)) {
                    GalleryHomeTopBar(
                        onBrandClick = { brandClicks++ },
                        brandDescription = "Settings",
                        statusBarInsets = WindowInsets(0, 0, 0, 0),
                        menu = {
                            IconButton(onClick = { menuClicks++ }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                        },
                        filter = if (showFilter) {
                            {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Outlined.FilterList, contentDescription = "Filter")
                                }
                            }
                        } else null,
                    )
                }
            }
        }

        rule.onNodeWithTag(GalleryHomeBrandTag)
            .assertHeightIsEqualTo(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assertContentDescriptionEquals("Settings")
            .performClick()
        rule.onNodeWithContentDescription("Filter").assertIsDisplayed()
        rule.runOnIdle { showFilter = false }
        rule.onNodeWithContentDescription("Filter").assertDoesNotExist()
        rule.onNodeWithContentDescription("More options").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(1, brandClicks)
            assertEquals(1, menuClicks)
        }
    }

    @Test
    fun shortcutsSitNearestTheBrandAndToggleInPlace() {
        var favoritesVisible by mutableStateOf(true)
        var favoritesClicks = 0
        rule.setContent {
            PreviewHost {
                Box(Modifier.width(360.dp)) {
                    GalleryHomeTopBar(
                        onBrandClick = {},
                        brandDescription = "Settings",
                        statusBarInsets = WindowInsets(0, 0, 0, 0),
                        actions = listOf(
                            GalleryHomeAction(
                                key = "favorites",
                                icon = Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorites",
                                visible = favoritesVisible,
                                onClick = { favoritesClicks++ },
                            ),
                        ),
                        menu = {
                            IconButton(onClick = {}) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                        },
                        filter = {
                            IconButton(onClick = {}) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "Filter")
                            }
                        },
                    )
                }
            }
        }

        val favorites = rule.onNodeWithTag(galleryHomeActionTag("favorites"))
            .assertHeightIsEqualTo(48.dp)
            .assertContentDescriptionEquals("Favorites")
        val favoritesRight = favorites.fetchSemanticsNode().boundsInRoot.right
        val filterLeft = rule.onNodeWithContentDescription("Filter")
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue(favoritesRight <= filterLeft)
        favorites.performClick()
        rule.runOnIdle {
            assertEquals(1, favoritesClicks)
            favoritesVisible = false
        }
        rule.onNodeWithTag(galleryHomeActionTag("favorites")).assertDoesNotExist()
        rule.onNodeWithContentDescription("Filter").assertIsDisplayed()
        rule.runOnIdle { favoritesVisible = true }
        rule.onNodeWithTag(galleryHomeActionTag("favorites")).assertIsDisplayed()
    }
}
