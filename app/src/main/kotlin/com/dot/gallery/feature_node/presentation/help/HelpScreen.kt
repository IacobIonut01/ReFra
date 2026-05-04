/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.help.components.WhatsNewHeroCard
import com.dot.gallery.feature_node.presentation.help.data.HelpCategory
import com.dot.gallery.feature_node.presentation.help.data.HelpRepository
import com.dot.gallery.feature_node.presentation.help.data.displayTitle
import com.dot.gallery.feature_node.presentation.help.data.icon
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val eventHandler = LocalEventHandler.current

    val currentRelease = remember { HelpRepository.getCurrentRelease() }
    val getStartedCategories = remember { HelpRepository.getGetStartedCategories() }
    val makeMostCategories = remember { HelpRepository.getMakeMostCategories() }
    val exploreMoreCategories = remember { HelpRepository.getExploreMoreCategories() }

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
            // What's New Hero Card
            item {
                WhatsNewHeroCard(
                    versionName = currentRelease.versionName,
                    onClick = { eventHandler.navigate(Screen.WhatsNewScreen()) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
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
