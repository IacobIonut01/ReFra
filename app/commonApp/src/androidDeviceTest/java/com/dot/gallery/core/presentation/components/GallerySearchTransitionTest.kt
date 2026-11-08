/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.presentation.util.NavigationItem
import com.dot.gallery.core.presentation.util.sharedSearchBounds
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SearchDestinationTag = "Gallery.TestSearchDestination"

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalSharedTransitionApi::class)
class GallerySearchTransitionTest {
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    @Test
    fun floatingSearchButtonSharesBoundsWithTheDestinationInBothDirections() {
        var searchOpen by mutableStateOf(false)
        lateinit var sharedScope: SharedTransitionScope
        val store = MapSettingsStore().apply { seed(SettingsKeys.allowBlur to false) }
        val items = listOf(NavigationItem("Photos", "photos", Icons.Outlined.Photo))
        rule.mainClock.autoAdvance = false
        try {
            rule.setContent {
                CompositionLocalProvider(LocalSettingsStore provides store) {
                    PreviewHost {
                        SharedTransitionLayout {
                            sharedScope = this
                            Box(Modifier.fillMaxSize()) {
                                AnimatedContent(targetState = searchOpen, label = "search-route") { open ->
                                    if (open) {
                                        Box(
                                            Modifier.fillMaxSize()
                                                .sharedSearchBounds(this@SharedTransitionLayout, this@AnimatedContent)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .testTag(SearchDestinationTag),
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize())
                                    }
                                }
                                AnimatedVisibility(
                                    visible = !searchOpen,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    GalleryNavigationBarContent(
                                        selectedRoute = "photos",
                                        navigationItems = items,
                                        onClick = {},
                                        onSearchClick = { searchOpen = true },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag(GalleryNavigationSearchTag).assertIsDisplayed().performClick()
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeUntil(1_000) { sharedScope.isTransitionActive }
            rule.mainClock.advanceTimeUntil(5_000) { !sharedScope.isTransitionActive }
            rule.onNodeWithTag(SearchDestinationTag).assertIsDisplayed()

            rule.runOnUiThread { searchOpen = false }
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeUntil(1_000) { sharedScope.isTransitionActive }
            rule.mainClock.advanceTimeUntil(5_000) { !sharedScope.isTransitionActive }
            rule.onNodeWithTag(GalleryNavigationSearchTag).assertIsDisplayed()
            rule.runOnUiThread { assertFalse(sharedScope.isTransitionActive) }
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }
}
