package com.dot.gallery.feature_node.presentation.search

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.R
import com.dot.gallery.core.SettingsEntity

/**
 * Represents a search suggestion that can be displayed in the SearchScreen.
 *
 * This system is designed to be easily extensible: to add a new suggestion type,
 * simply create a new [SearchSuggestionProvider] implementation and register it
 * in [SearchSuggestionProviders.all].
 */
data class SearchSuggestion(
    val title: String,
    val icon: ImageVector,
    val action: (SearchViewModel) -> Unit
)

/**
 * Provides a group of search suggestions with a header title.
 * Implement this interface to add new suggestion categories
 * (e.g. locations, mime types, media tags, albums, etc.)
 */
interface SearchSuggestionProvider {
    /** Header title shown above this group of suggestions */
    val headerTitle: String

    /** The list of suggestions in this group. Empty list means this group is hidden. */
    val suggestions: List<SearchSuggestion>
}

/**
 * Converts a list of [SearchSuggestionProvider]s into a flat list of [SettingsEntity]
 * items suitable for rendering via [SettingsOptionLayout],
 * wiring each suggestion's action to the given [viewModel].
 */
fun List<SearchSuggestionProvider>.toSettingsEntities(
    viewModel: SearchViewModel
): List<SettingsEntity> {
    return flatMap { provider ->
        if (provider.suggestions.isEmpty()) return@flatMap emptyList()
        listOf(SettingsEntity.Header(provider.headerTitle)) +
            provider.suggestions.map { suggestion ->
                SettingsEntity.Preference(
                    title = suggestion.title,
                    icon = suggestion.icon,
                    onClick = { suggestion.action(viewModel) }
                )
            }
    }
}

// ── Built-in providers ─────────────────────────────────────────────

/**
 * Quick-access suggestions like Screenshots, Videos, and Selfies.
 */
class QuickSuggestionProvider(resources: Resources) : SearchSuggestionProvider {
    override val headerTitle: String = resources.getString(R.string.suggestions)
    override val suggestions: List<SearchSuggestion> = listOf(
        SearchSuggestion(
            title = resources.getString(R.string.screenshots),
            icon = Icons.Outlined.Screenshot,
            action = { vm -> vm.setQuery(resources.getString(R.string.screenshots), apply = true) }
        ),
        SearchSuggestion(
            title = resources.getString(R.string.videos),
            icon = Icons.Outlined.PlayCircle,
            action = { vm -> vm.setMimeTypeQuery("video/", hideExplicitQuery = true) }
        ),
        SearchSuggestion(
            title = resources.getString(R.string.selfies),
            icon = Icons.Outlined.Portrait,
            action = { vm -> vm.setQuery(resources.getString(R.string.selfies), apply = true) }
        ),
    )
}

// ── Future providers (examples) ────────────────────────────────────
// To add more suggestion types, create a new SearchSuggestionProvider:
//
// class MimeTypeSuggestionProvider(...) : SearchSuggestionProvider { ... }
// class MediaTagSuggestionProvider(...) : SearchSuggestionProvider { ... }
// class AlbumSuggestionProvider(...) : SearchSuggestionProvider { ... }
//
// Then add them to the providers list in SearchScreen.
