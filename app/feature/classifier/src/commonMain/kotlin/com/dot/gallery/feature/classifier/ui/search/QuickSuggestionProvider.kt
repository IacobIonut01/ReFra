/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.favorites
import com.dot.gallery.feature.classifier.generated.resources.screenshots
import com.dot.gallery.feature.classifier.generated.resources.selfies
import com.dot.gallery.feature.classifier.generated.resources.suggestions
import com.dot.gallery.feature.classifier.generated.resources.videos


/**
 * Quick-access suggestions like Screenshots, Videos, Selfies and Favorites.
 */
class QuickSuggestionProvider(
    headerTitle: String,
    screenshotsLabel: String,
    videosLabel: String,
    selfiesLabel: String,
    favoritesLabel: String,
) : SearchSuggestionProvider {
    override val headerTitle: String = headerTitle
    override val suggestions: List<SearchSuggestion> = listOf(
        SearchSuggestion(
            title = screenshotsLabel,
            icon = Icons.Outlined.Screenshot,
            action = { actions -> actions.onQueryChange(screenshotsLabel, true) }
        ),
        SearchSuggestion(
            title = videosLabel,
            icon = Icons.Outlined.PlayCircle,
            action = { actions -> actions.onMimeTypeFilter("video/", true) }
        ),
        SearchSuggestion(
            title = selfiesLabel,
            icon = Icons.Outlined.Portrait,
            action = { actions -> actions.onQueryChange(selfiesLabel, true) }
        ),
        SearchSuggestion(
            title = favoritesLabel,
            icon = Icons.Outlined.FavoriteBorder,
            action = { actions -> actions.onFavoritesFilter(favoritesLabel) }
        ),
    )
}

/**
 * The default suggestion providers, with localized labels resolved from the
 * module resources. Extend the list to add new suggestion categories.
 */
@Composable
fun rememberSearchSuggestionProviders(): List<SearchSuggestionProvider> {
    val header = stringResource(Res.string.suggestions)
    val screenshots = stringResource(Res.string.screenshots)
    val videos = stringResource(Res.string.videos)
    val selfies = stringResource(Res.string.selfies)
    val favorites = stringResource(Res.string.favorites)
    return remember(header, screenshots, videos, selfies, favorites) {
        listOf(
            QuickSuggestionProvider(
                headerTitle = header,
                screenshotsLabel = screenshots,
                videosLabel = videos,
                selfiesLabel = selfies,
                favoritesLabel = favorites,
            ),
            // Add more providers here:
            // MimeTypeSuggestionProvider(mimeTypes),
            // MediaTagSuggestionProvider(tags),
        )
    }
}
