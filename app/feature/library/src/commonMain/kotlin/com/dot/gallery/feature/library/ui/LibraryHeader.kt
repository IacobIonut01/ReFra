/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid
import kotlin.random.Random

/** How much room the header has: phone column, full-width tablet column, or side pane. */
enum class LibraryHeaderLayout { COMPACT, WIDE, SIDE_PANE }

/** Which of Locations, People and Categories have something to show and are shown as tiles. */
@Immutable
data class LibrarySections(
    val locations: Boolean,
    val people: Boolean,
    val categories: Boolean,
) {
    val count: Int get() = listOf(locations, people, categories).count { it }
}

/**
 * The layout each shortcut starts with. The retired Library switches for Locations, People and
 * Categories are read here only to keep what they hid hidden, until the layout is first saved.
 */
@Composable
private fun rememberShortcutDefaults(): (LibraryShortcut) -> LibraryShortcutPref {
    val showLocations by rememberPreference(SettingsKeys.showLocationCategories)
    val hiddenSections by rememberPreference(SettingsKeys.hiddenSections)
    return remember(showLocations, hiddenSections) {
        { shortcut -> defaultShortcutPref(shortcut, showLocations, hiddenSections) }
    }
}

@Composable
fun rememberLibrarySections(state: LibraryState): LibrarySections {
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    val layout by rememberPreference(libraryShortcutsLayoutKey)
    val defaults = rememberShortcutDefaults()
    val explore = libraryExploreData(state, noClassification)
    fun shown(shortcut: LibraryShortcut) =
        (layout.firstOrNull { it.id == shortcut.id } ?: defaults(shortcut)).visible
    return LibrarySections(
        locations = explore.locations && shown(LibraryShortcut.LOCATIONS),
        people = explore.people && shown(LibraryShortcut.PEOPLE),
        categories = explore.categories && shown(LibraryShortcut.CATEGORIES),
    )
}

/**
 * The bounded top of the Library page: the shortcut tiles, Locations, People and Categories
 * among them. Stays short so the album grid below is reachable.
 */
@Composable
fun LibraryHeader(
    state: LibraryState,
    actions: LibraryActions,
    routes: LibraryRoutes,
    layout: LibraryHeaderLayout,
    modifier: Modifier = Modifier,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
) {
    LibraryShortcuts(
        modifier = modifier.fillMaxWidth(),
        state = state,
        routes = routes,
        editMode = false,
        layout = layout,
        mapContent = mapContent,
        onNavigate = actions.onNavigate,
    )
}

@Composable
internal fun LibraryShortcuts(
    state: LibraryState,
    routes: LibraryRoutes,
    editMode: Boolean,
    layout: LibraryHeaderLayout,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
) {
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    // A new pick each time the Library opens, kept while it stays open.
    val shuffleSeed = remember { Random.nextInt() }
    val runtime = rememberLibraryRuntimeShortcuts(
        indicatorState = state.snapshot.indicators,
        cloudState = state.snapshot.cloud,
        routes = routes,
        supportsTrash = state.supportsTrash,
        supportsFavorites = state.supportsFavorites,
        previews = state.snapshot.shortcutPreviews,
        vaultCount = state.vaultCount,
        explore = libraryExploreData(state, noClassification),
        shuffleSeed = shuffleSeed,
    )
    var shortcutsLayout by rememberPreference(libraryShortcutsLayoutKey)
    val showAllShortcuts by rememberPreference(SettingsKeys.showAllLibraryShortcuts)
    val defaults = rememberShortcutDefaults()
    val working = remember(runtime, shortcutsLayout, defaults) {
        mergeShortcutPrefs(shortcutsLayout, runtime, defaults).map { it.pref }
    }
    EditableLibraryShortcutsGrid(
        modifier = modifier,
        working = working,
        runtime = runtime,
        editMode = editMode,
        columns = if (layout == LibraryHeaderLayout.WIDE) {
            LibraryShortcutSize.COLUMNS * 2
        } else LibraryShortcutSize.COLUMNS,
        collapsible = layout == LibraryHeaderLayout.COMPACT && !showAllShortcuts,
        collapsedRows = collapsedShortcutRows(working),
        mapContent = if (state.mapsEnabled) mapContent else null,
        onClick = onNavigate,
        onChange = { newWorking ->
            val availableIds = runtime.keys.map { it.id }.toSet()
            val leftovers = shortcutsLayout.filter { it.id !in availableIds }
            shortcutsLayout = newWorking + leftovers
        }
    )
}
