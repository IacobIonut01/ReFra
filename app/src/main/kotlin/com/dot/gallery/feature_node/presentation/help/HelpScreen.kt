/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.help.components.WhatsNewHeroCard
import com.dot.gallery.feature_node.presentation.help.data.HelpCategory
import com.dot.gallery.feature_node.presentation.help.data.HelpRepository
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchIndex
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchItem
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchKind
import com.dot.gallery.core.presentation.components.AppSearchField
import com.dot.gallery.feature_node.presentation.help.data.SettingsSearchRegistry
import com.dot.gallery.feature_node.presentation.help.data.displayTitle
import com.dot.gallery.feature_node.presentation.help.data.icon
import com.dot.gallery.feature_node.presentation.help.search.HelpFuzzyMatcher
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val eventHandler = LocalEventHandler.current

    val context = LocalContext.current
    val currentRelease = remember { HelpRepository.getCurrentRelease(context) }
    val getStartedCategories = remember { HelpRepository.getGetStartedCategories() }
    val makeMostCategories = remember { HelpRepository.getMakeMostCategories() }
    val exploreMoreCategories = remember { HelpRepository.getExploreMoreCategories() }

    val registeredSettings by SettingsSearchRegistry.entries.collectAsStateWithLifecycle()
    val searchIndex = remember(context, registeredSettings) { HelpSearchIndex.build(context) }
    var query by remember { mutableStateOf("") }
    val results = remember(query, searchIndex) { HelpFuzzyMatcher.search(searchIndex, query) }
    val isSearching = query.isNotBlank()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
        ) {
            // Unified fuzzy search bar
            item {
                HelpSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (isSearching) {
                helpSearchResults(
                    results = results,
                    query = query,
                    onClick = { item -> item.route?.let { eventHandler.navigate(it) } }
                )
            } else {
                // What's New Hero Card
                if (currentRelease != null) {
                    item {
                        WhatsNewHeroCard(
                            versionName = currentRelease.versionName,
                            onClick = { eventHandler.navigate(Screen.WhatsNewScreen()) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Get Started Section
                item {
                    val headerTitle = stringResource(R.string.help_get_started)
                    val resolved = getStartedCategories.map { Triple(it, it.displayTitle(), it.icon()) }
                    SettingsCategoryGroup(
                        headerTitle = headerTitle,
                        categories = resolved,
                        onCategoryClick = { category ->
                            eventHandler.navigate(Screen.TutorialCategoryScreen.category(category.name))
                        }
                    )
                }

                // Make the Most Section
                item {
                    val headerTitle = stringResource(R.string.help_make_most)
                    val resolved = makeMostCategories.map { Triple(it, it.displayTitle(), it.icon()) }
                    SettingsCategoryGroup(
                        headerTitle = headerTitle,
                        categories = resolved,
                        onCategoryClick = { category ->
                            eventHandler.navigate(Screen.TutorialCategoryScreen.category(category.name))
                        }
                    )
                }

                // Explore More Section
                item {
                    val headerTitle = stringResource(R.string.help_explore_tips)
                    val resolved = exploreMoreCategories.map { Triple(it, it.displayTitle(), it.icon()) }
                    SettingsCategoryGroup(
                        headerTitle = headerTitle,
                        categories = resolved,
                        onCategoryClick = { category ->
                            eventHandler.navigate(Screen.TutorialCategoryScreen.category(category.name))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppSearchField(
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.help_search_hint),
        onClear = onClear,
        modifier = modifier
    )
}

/** Grouped, ranked search results rendered as LazyColumn items. */
private fun androidx.compose.foundation.lazy.LazyListScope.helpSearchResults(
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
                    text = stringResource(R.string.help_search_no_results, query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val order = listOf(
        HelpSearchKind.TIP to R.string.help_search_section_tips,
        HelpSearchKind.SETTING to R.string.help_search_section_settings,
        HelpSearchKind.QUICK_ACTION to R.string.help_search_section_actions,
        HelpSearchKind.CHANGELOG to R.string.help_search_section_changelog,
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

@Preview(showBackground = true)
@Composable
private fun HelpScreenPreview() {
    PreviewHost {
        HelpScreen()
    }
}
