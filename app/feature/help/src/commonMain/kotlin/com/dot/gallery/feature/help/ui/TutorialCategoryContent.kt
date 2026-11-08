/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.feature.help.domain.HelpCategory
import com.dot.gallery.feature.help.domain.HelpRepository
import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.displayTitle
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.help_tips_fallback
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

/**
 * Tips list for a single [HelpCategory]. [categoryName] is the enum name from
 * the navigation argument; unknown names render an empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialCategoryContent(
    categoryName: String,
    onNavigate: (HelpRoute) -> Unit,
) {
    val listState = rememberLazyListState()

    val category = remember(categoryName) {
        runCatching { HelpCategory.valueOf(categoryName) }.getOrNull()
    }
    val tips = remember(category) {
        category?.let { HelpRepository.getTipsByCategory(it) } ?: emptyList()
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = category?.displayTitle() ?: stringResource(Res.string.help_tips_fallback),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        val resolvedTips = tips.map { tip ->
            Triple(stringResource(tip.title), stringResource(tip.subtitle), tip)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize().hazeSource(LocalHazeState.current)
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
                        onClick = { onNavigate(HelpRoute.Tip(tip.id)) }
                    )
                }
            }
        }
    }
}
