/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid
import kotlin.random.Random

/** How much room the header has: phone column, full-width tablet column, or side pane. */
enum class LibraryHeaderLayout { COMPACT, WIDE, SIDE_PANE }

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
fun rememberLibrarySections(
    state: LibraryState,
    reserved: Set<LibraryShortcut> = emptySet(),
): LibrarySections {
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    val layout by rememberPreference(libraryShortcutsLayoutKey)
    val defaults = rememberShortcutDefaults()
    val explore = libraryExploreData(state, noClassification)
    fun shown(shortcut: LibraryShortcut) =
        (layout.firstOrNull { it.id == shortcut.id } ?: defaults(shortcut)).visible
    fun present(shortcut: LibraryShortcut, hasData: Boolean) =
        (hasData || shortcut in reserved) && shown(shortcut)
    return LibrarySections(
        locations = present(LibraryShortcut.LOCATIONS, explore.locations),
        people = present(LibraryShortcut.PEOPLE, explore.people),
        categories = present(LibraryShortcut.CATEGORIES, explore.categories),
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
    rememberedShortcuts: Set<String>? = null,
    onRememberedShortcutsChange: (Set<String>) -> Unit = {},
) {
    LibraryShortcuts(
        modifier = modifier.fillMaxWidth(),
        state = state,
        routes = routes,
        editMode = false,
        layout = layout,
        mapContent = mapContent,
        onNavigate = actions.onNavigate,
        rememberedShortcuts = rememberedShortcuts,
        onRememberedShortcutsChange = onRememberedShortcutsChange,
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
    rememberedShortcuts: Set<String>? = null,
    onRememberedShortcutsChange: (Set<String>) -> Unit = {},
) {
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    // A new pick each time the Library opens, kept while it stays open.
    val shuffleSeed = remember { Random.nextInt() }
    val pending = pendingShortcuts(state, noClassification)
    val reserved = rememberedShortcuts?.let { reservedShortcuts(it, pending) }.orEmpty()
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
        reserved = reserved,
    )
    var shortcutsLayout by rememberPreference(libraryShortcutsLayoutKey)
    val showAllShortcuts by rememberPreference(SettingsKeys.showAllLibraryShortcuts)
    val defaults = rememberShortcutDefaults()
    val working = remember(runtime, shortcutsLayout, defaults) {
        mergeShortcutPrefs(shortcutsLayout, runtime, defaults).map { it.pref }
    }
    if (rememberedShortcuts != null) {
        val next = nextRememberedShortcuts(
            rememberedShortcuts,
            runtime.filterValues { !it.loading }.keys,
            pending,
        )
        val onRememberedChange by rememberUpdatedState(onRememberedShortcutsChange)
        LaunchedEffect(next) {
            if (next != rememberedShortcuts) onRememberedChange(next)
        }
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
