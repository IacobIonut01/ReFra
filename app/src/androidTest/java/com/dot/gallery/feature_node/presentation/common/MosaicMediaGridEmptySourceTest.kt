/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.common.components.MosaicMediaGrid
import com.dot.gallery.ui.theme.GalleryTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: [MosaicMediaGrid] used to drop the passed [Modifier] in every non-CONTENT branch,
 * so a caller-supplied `hazeSource` never attached when the grid was empty/loading/error. With
 * zero captured areas on the shared [HazeState], frosted chrome above the grid (search bar, nav
 * bar) rendered fully transparent. The testTag rides the same modifier chain — if the modifier
 * is dropped, the node disappears.
 */
@MediumTest
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class MosaicMediaGridEmptySourceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun emptyStateKeepsCallerModifierAttached() {
        val hazeState = HazeState()
        rule.setContent {
            GalleryTheme(ignoreUserPreference = true) {
                SharedTransitionLayout {
                    AnimatedContent(targetState = Unit, label = "st") { _ ->
                        MosaicMediaGrid<Media.UriMedia>(
                            modifier = Modifier
                                .testTag("grid-source")
                                .hazeSource(hazeState),
                            gridState = rememberLazyGridState(),
                            mediaState = remember {
                                mutableStateOf(MediaState(isLoading = false))
                            },
                            metadataState = remember {
                                mutableStateOf(MediaMetadataState())
                            },
                            mappedData = emptyList(),
                            paddingValues = PaddingValues(),
                            allowSelection = false,
                            canScroll = true,
                            allowHeaders = false,
                            aboveGridContent = null,
                            isScrolling = remember { mutableStateOf(false) },
                            emptyContent = { Text("empty-grid") },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent,
                            onMediaClick = {},
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("empty-grid").assertIsDisplayed()
        rule.onNodeWithTag("grid-source").assertIsDisplayed()
    }
}
