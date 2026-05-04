/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.previews

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import com.dot.gallery.core.util.SetupMediaProviders
import com.dot.gallery.feature_node.presentation.help.data.HelpMockData
import com.dot.gallery.feature_node.presentation.util.MockedEventHandler
import com.dot.gallery.feature_node.presentation.util.MockedMediaHandler
import com.dot.gallery.feature_node.presentation.util.MockedMediaImageRenderer
import com.dot.gallery.feature_node.presentation.util.MockedMediaSelector
import com.dot.gallery.feature_node.presentation.util.SetupMockedMediaProviders

/**
 * Wraps content with [SetupMockedMediaProviders], providing mocked
 * [MediaImageRenderer], [EventHandler], etc. so real composables
 * (MediaImage, AlbumImage) render colored placeholder boxes instead
 * of attempting real image loads.
 */
@Composable
fun PreviewImageProvider(content: @Composable () -> Unit) {
    SetupMockedMediaProviders {
        content()
    }
}

/**
 * Sets up all mocked providers with a [HelpMockData]-populated distributor
 * AND provides [SharedTransitionScope] + [AnimatedContentScope] so real
 * screen composables (TimelineScreen, AlbumsScreen, etc.) can render.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PreviewScreenProvider(
    content: @Composable (SharedTransitionScope, AnimatedContentScope) -> Unit
) {
    SetupMediaProviders(
        eventHandler = MockedEventHandler(),
        mediaDistributor = HelpMockData.createPreviewDistributor(),
        mediaHandler = MockedMediaHandler(),
        mediaSelector = MockedMediaSelector(),
        mediaImageRenderer = MockedMediaImageRenderer,
    ) {
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedContent(targetState = Unit, label = "preview") { _ ->
                content(sharedScope, this)
            }
        }
    }
}
