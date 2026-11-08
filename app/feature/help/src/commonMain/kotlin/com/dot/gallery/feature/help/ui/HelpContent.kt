/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.AppSearchField
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.feature.help.domain.BasicHelpSearchEngine
import com.dot.gallery.feature.help.domain.HelpCategory
import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.feature.help.domain.HelpSearchKind
import com.dot.gallery.feature.help.domain.displayTitle
import com.dot.gallery.feature.help.domain.icon
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.help_explore_tips
import com.dot.gallery.feature.help.generated.resources.help_get_started
import com.dot.gallery.feature.help.generated.resources.help_make_most
import com.dot.gallery.feature.help.generated.resources.help_search_hint
import com.dot.gallery.feature.help.generated.resources.help_search_no_results
import com.dot.gallery.feature.help.generated.resources.help_search_section_actions
import com.dot.gallery.feature.help.generated.resources.help_search_section_changelog
import com.dot.gallery.feature.help.generated.resources.help_search_section_settings
import com.dot.gallery.feature.help.generated.resources.help_search_section_tips
import com.dot.gallery.feature.help.generated.resources.help_title
import com.dot.gallery.feature.help.domain.HelpSearchEngine
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

/**
 * Help & tips hub: unified fuzzy search over tips/settings/quick actions/
 * changelog plus grouped category entry points.
 *
 * The app wrapper supplies [state] (releases come from the asset-bound
 * changelog loader) and resolves [HelpActions.onNavigate] to `Screen.*`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpContent(
    state: HelpState,
    actions: HelpActions = HelpActions(),
    searchEngine: HelpSearchEngine = BasicHelpSearchEngine,
) {
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    val results = remember(query, state.searchIndex) {
        searchEngine.search(state.searchIndex, query, limit = 25)
    }
    val isSearching = query.isNotBlank()

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.help_title),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
        ) {
            // Unified fuzzy search bar
            item {
                AppSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(Res.string.help_search_hint),
                    onClear = { query = "" },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (isSearching) {
                helpSearchResults(
                    results = results,
                    query = query,
                    onClick = { item -> item.route?.let(actions.onNavigate) }
                )
            } else {
                // What's New Hero Card
                state.currentRelease?.let { release ->
                    item {
                        WhatsNewHeroCard(
                            versionName = release.versionName,
                            onClick = { actions.onNavigate(HelpRoute.Target.WHATS_NEW) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Get Started Section
                item {
                    SettingsCategoryGroup(
                        headerTitle = stringResource(Res.string.help_get_started),
                        categories = state.getStartedCategories.map {
                            Triple(it, it.displayTitle(), it.icon())
                        },
                        onCategoryClick = { actions.onNavigate(HelpRoute.Category(it)) }
                    )
                }

                // Make the Most Section
                item {
                    SettingsCategoryGroup(
                        headerTitle = stringResource(Res.string.help_make_most),
                        categories = state.makeMostCategories.map {
                            Triple(it, it.displayTitle(), it.icon())
                        },
                        onCategoryClick = { actions.onNavigate(HelpRoute.Category(it)) }
                    )
                }

                // Explore More Section
                item {
                    SettingsCategoryGroup(
                        headerTitle = stringResource(Res.string.help_explore_tips),
                        categories = state.exploreMoreCategories.map {
                            Triple(it, it.displayTitle(), it.icon())
                        },
                        onCategoryClick = { actions.onNavigate(HelpRoute.Category(it)) }
                    )
                }
            }
        }
    }
}

/** Grouped, ranked search results rendered as LazyColumn items. */
private fun LazyListScope.helpSearchResults(
    results: List<HelpSearchItem>,
    query: String,
    onClick: (HelpSearchItem) -> Unit
) {
    if (results.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.help_search_no_results, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val order = listOf(
        HelpSearchKind.TIP to Res.string.help_search_section_tips,
        HelpSearchKind.SETTING to Res.string.help_search_section_settings,
        HelpSearchKind.QUICK_ACTION to Res.string.help_search_section_actions,
        HelpSearchKind.CHANGELOG to Res.string.help_search_section_changelog,
    )
    order.forEach { (kind, headerRes) ->
        val group = results.filter { it.kind == kind }
        if (group.isNotEmpty()) {
            item(key = "header_${kind.name}") {
                SettingsItem(item = SettingsEntity.Header(title = stringResource(headerRes)))
            }
            items(group, key = { "${it.kind}_${it.id}" }) { item ->
                SettingsItem(
                    item = SettingsEntity.Preference(
                        title = item.title,
                        summary = item.subtitle.ifBlank { null },
                        icon = item.icon,
                        onClick = { onClick(item) },
                        screenPosition = Position.Alone
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    headerTitle: String,
    categories: List<Triple<HelpCategory, String, ImageVector>>,
    onCategoryClick: (HelpCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsItem(
            item = SettingsEntity.Header(title = headerTitle)
        )
        categories.forEachIndexed { index, (category, title, icon) ->
            val position = when {
                categories.size == 1 -> Position.Alone
                index == 0 -> Position.Top
                index == categories.lastIndex -> Position.Bottom
                else -> Position.Middle
            }
            SettingsItem(
                item = SettingsEntity.Preference(
                    title = title,
                    icon = icon,
                    onClick = { onCategoryClick(category) },
                    screenPosition = position
                )
            )
        }
    }
}
