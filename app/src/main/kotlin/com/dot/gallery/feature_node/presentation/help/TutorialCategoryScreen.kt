/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.help.data.HelpCategory
import com.dot.gallery.feature_node.presentation.help.data.HelpRepository
import com.dot.gallery.feature_node.presentation.help.data.displayTitle
import com.dot.gallery.feature_node.presentation.settings.components.settings
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialCategoryScreen(categoryName: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val eventHandler = LocalEventHandler.current

    val category = remember(categoryName) {
        runCatching { HelpCategory.valueOf(categoryName) }.getOrNull()
    }
    val tips = remember(category) {
        category?.let { HelpRepository.getTipsByCategory(it) } ?: emptyList()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(category?.displayTitle() ?: "Tips") },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val resolvedTips = tips.map { tip ->
            Triple(stringResource(tip.title), stringResource(tip.subtitle), tip)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
        ) {
            settings {
                resolvedTips.forEach { (title, subtitle, tip) ->
                    Preference(
                        title = title,
                        icon = tip.icon.vector ?: Icons.AutoMirrored.Outlined.Article,
                        summary = subtitle,
                        onClick = {
                            eventHandler.navigate(Screen.TutorialDetailScreen.tipId(tip.id))
                        }
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun TutorialCategoryScreenPreview() {
    PreviewHost {
        TutorialCategoryScreen(categoryName = HelpCategory.GET_STARTED_BASICS.name)
    }
}
