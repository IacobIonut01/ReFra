/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.PreferenceType
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState


/**
 * Shared settings-screen scaffold: [GalleryPageTopBar] header (the transparent blur bar once the
 * list scrolls under it), grouped preference list, initial focus for the first actionable row.
 *
 * [searchSink], when provided, receives `(route, titles)` for every non-header
 * toggle row so a platform search registry (Android: Help & Tips settings
 * search) can index them without this module knowing the registry exists.
 */
@Composable
fun BaseSettingsScreen(
    title: String,
    settingsList: SnapshotStateList<SettingsEntity>,
    settingsBuilder: @Composable (SettingsEntity, Int, Modifier) -> Unit = { item, _, modifier ->
        SettingsItem(item = item, modifier = modifier)
    },
    topContent: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    searchRoute: String? = null,
    searchSink: ((String, List<String>) -> Unit)? = null,
    /** Overrides the app-navigation back action — for hosts outside the nav graph (the editors). */
    onBack: (() -> Unit)? = null,
    /** Top-bar actions, built from `GalleryPageTopBarAction`. */
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    if (searchRoute != null && searchSink != null) {
        val toggleTitles = settingsList
            .filter { !it.isHeader && it.title.isNotBlank() }
            .map { it.title }
        LaunchedEffect(searchRoute, toggleTitles) {
            searchSink(searchRoute, toggleTitles)
        }
    }
    val initialFocusRequester = remember { FocusRequester() }
    val firstActionableIndex = settingsList.indexOfFirst { item ->
        item.enabled && !item.isHeader && when (item.type) {
            PreferenceType.Seek -> item.onSeek != null
            PreferenceType.Switch -> item.onCheck != null || item.onClick != null
            else -> item.onClick != null || item.onLongClick != null
        }
    }
    RequestInitialSettingsFocus(
        focusRequester = initialFocusRequester,
        enabled = firstActionableIndex >= 0,
        prepareFocus = {
            if (firstActionableIndex >= 0) {
                listState.scrollToItem(firstActionableIndex + if (topContent != null) 1 else 0)
            }
        },
    )
    // The page's only haze source: the top bar blurs it once rows scroll underneath.
    val hazeState = rememberHazeState()
    val scrolled by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            GalleryPageTopBar(title = title, onBack = onBack, hazeState = hazeState, scrolled = scrolled, actions = actions)
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .settingsFocusGroup(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 8.dp + padding.calculateTopPadding(),
                bottom = 24.dp + padding.calculateBottomPadding()
            ),
        ) {
            if (topContent != null) {
                item {
                    topContent()
                }
            }
            itemsIndexed(
                items = settingsList,
                key = { index, it -> "$index-${it.title}-${it.type}" }
            ) { index, item ->
                val itemModifier = if (index == firstActionableIndex) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
                settingsBuilder(item, index, itemModifier)
            }
            if (bottomContent != null) {
                item {
                    bottomContent()
                }
            }
        }
    }
}
