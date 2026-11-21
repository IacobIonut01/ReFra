/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.feature.help.domain.HelpSearchIndex
import com.dot.gallery.feature.help.domain.HelpSearchItem
import com.dot.gallery.feature.help.domain.SettingsSearchRegistry
import com.dot.gallery.feature.help.search.HelpFuzzyMatcher
import com.dot.gallery.feature.help.toScreenRoute
import com.dot.gallery.feature.help.ui.SettingsSearchContent

/**
 * Android shell for [SettingsSearchContent]: builds the help index (curated settings, the titles
 * settings pages registered at runtime, and tips; no changelog), ranks with the fuzzy matcher
 * and resolves results to real `Screen` routes.
 */
@Composable
fun SettingsSearchScreen() {
    val eventHandler = LocalEventHandler.current
    val registeredSettings by SettingsSearchRegistry.entries.collectAsStateWithLifecycle()
    val searchIndex by produceState<List<HelpSearchItem>>(emptyList(), registeredSettings) {
        value = HelpSearchIndex.build(
            releases = emptyList(),
            settingsEntries = registeredSettings,
        )
    }

    SettingsSearchContent(
        searchIndex = searchIndex,
        onNavigate = { route -> eventHandler.navigate(route.toScreenRoute()) },
        searchEngine = HelpFuzzyMatcher,
    )
}
