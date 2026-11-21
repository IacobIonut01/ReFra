/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.AppSearchField
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.feature.help.domain.BasicHelpSearchEngine
import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.feature.help.domain.HelpSearchEngine
import com.dot.gallery.feature.help.domain.HelpSearchIndex
import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.feature.help.domain.HelpSearchKind
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.settings_search_hint
import com.dot.gallery.feature.help.generated.resources.settings_search_no_results
import com.dot.gallery.feature.help.generated.resources.settings_search_title
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/** Settings search lists settings before tips: the user came from Settings. */
private val SettingsResultOrder = listOf(HelpSearchKind.SETTING, HelpSearchKind.TIP)

/**
 * Search across every settings page and help tip, opened from the Settings top bar. It reuses
 * the Help index ([HelpSearchIndex.forSettingsSearch] keeps settings and tips) and the Help
 * result rows; the field takes focus when the screen opens.
 */
@Composable
fun SettingsSearchContent(
    searchIndex: List<HelpSearchItem>,
    onNavigate: (HelpRoute) -> Unit,
    searchEngine: HelpSearchEngine = BasicHelpSearchEngine,
) {
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    val items = remember(searchIndex) { HelpSearchIndex.forSettingsSearch(searchIndex) }
    val results = remember(query, items) { searchEngine.search(items, query, limit = 40) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.settings_search_title),
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
            item(key = "settings_search_field") {
                AppSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(Res.string.settings_search_hint),
                    onClear = { query = "" },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .focusRequester(focusRequester)
                )
                Spacer(Modifier.height(8.dp))
            }
            if (query.isNotBlank()) {
                helpSearchResults(
                    results = results,
                    query = query,
                    order = SettingsResultOrder,
                    noResults = Res.string.settings_search_no_results,
                    onClick = { item -> item.route?.let(onNavigate) },
                )
            }
        }
    }
}
