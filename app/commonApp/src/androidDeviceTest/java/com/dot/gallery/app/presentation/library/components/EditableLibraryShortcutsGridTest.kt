/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.library_customize
import com.dot.gallery.core.util.getString
import org.jetbrains.compose.resources.StringResource


@MediumTest
@RunWith(AndroidJUnit4::class)
class EditableLibraryShortcutsGridTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(res: StringResource): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(res)

    private fun runtime() = linkedMapOf(
        LibraryShortcut.TRASH to runtimeShortcut(LibraryShortcut.TRASH, "Trash"),
        LibraryShortcut.FAVORITES to runtimeShortcut(LibraryShortcut.FAVORITES, "Favorites"),
        LibraryShortcut.VAULT to runtimeShortcut(LibraryShortcut.VAULT, "Vault"),
    )

    private fun allHidden() = listOf(
        LibraryShortcutPref(id = LibraryShortcut.TRASH.id, visible = false),
        LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id, visible = false),
        LibraryShortcutPref(id = LibraryShortcut.VAULT.id, visible = false),
    )

    @Test
    fun allHiddenShowsCustomizeEntryPoint() {
        var enteredEditMode = false
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
            GalleryTheme(ignoreUserPreference = true) {
                EditableLibraryShortcutsGrid(
                    working = allHidden(),
                    runtime = runtime(),
                    editMode = false,
                    onClick = {},
                    onEnterEditMode = { enteredEditMode = true },
                    onChange = {}
                )
            }
            }
        }

        // Every tile hidden leaves nothing to long-press; the obvious
        // "Customize shortcuts" entry point must be shown instead.
        rule.onNodeWithTag("library-shortcuts-customize").assertIsDisplayed()
        rule.onNodeWithText(string(Res.string.library_customize))
            .assertIsDisplayed()
            .performClick()
        assertTrue(enteredEditMode)
    }

    @Test
    fun hiddenTrayRestoresATileInEditMode() {
        var emitted: List<LibraryShortcutPref>? = null
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
            GalleryTheme(ignoreUserPreference = true) {
                EditableLibraryShortcutsGrid(
                    working = allHidden(),
                    runtime = runtime(),
                    editMode = true,
                    onClick = {},
                    onEnterEditMode = {},
                    onChange = { emitted = it }
                )
            }
            }
        }

        // In edit mode the placeholder is replaced by the hidden tray, which
        // re-adds a tile on tap.
        rule.onNodeWithTag("library-shortcuts-customize").assertDoesNotExist()
        rule.onNodeWithText("Trash").assertIsDisplayed().performClick()
        assertNotNull(emitted)
        assertTrue(
            emitted!!.first { it.id == LibraryShortcut.TRASH.id }.visible
        )
    }

    @Test
    fun longListsCollapseBehindAnExpandTile() {
        val shortcuts = listOf(
            LibraryShortcut.TRASH to "Trash",
            LibraryShortcut.FAVORITES to "Favorites",
            LibraryShortcut.VAULT to "Vault",
            LibraryShortcut.IGNORED to "Ignored",
            LibraryShortcut.CLOUD_ARCHIVE to "Archive",
        )
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
            GalleryTheme(ignoreUserPreference = true) {
                EditableLibraryShortcutsGrid(
                    working = shortcuts.map { (shortcut, _) -> LibraryShortcutPref(id = shortcut.id, visible = true) },
                    runtime = shortcuts.associate { (shortcut, title) -> shortcut to runtimeShortcut(shortcut, title) },
                    editMode = false,
                    collapsible = true,
                    onClick = {},
                    onEnterEditMode = {},
                    onChange = {}
                )
            }
            }
        }

        rule.onNodeWithText("Vault").assertIsDisplayed()
        rule.onNodeWithText("Ignored").assertDoesNotExist()
        rule.onNodeWithText("Archive").assertDoesNotExist()

        rule.onNodeWithTag("library-shortcuts-more").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Ignored").assertIsDisplayed()
        rule.onNodeWithText("Archive").assertIsDisplayed()
    }

    @Test
    fun visibleTilesHideCustomizeEntryPoint() {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
            GalleryTheme(ignoreUserPreference = true) {
                EditableLibraryShortcutsGrid(
                    working = listOf(
                        LibraryShortcutPref(id = LibraryShortcut.TRASH.id, visible = true),
                        LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id, visible = false),
                    ),
                    runtime = runtime(),
                    editMode = false,
                    onClick = {},
                    onEnterEditMode = {},
                    onChange = {}
                )
            }
            }
        }

        rule.onNodeWithTag("library-shortcuts-customize").assertDoesNotExist()
    }

    private fun runtimeShortcut(shortcut: LibraryShortcut, title: String) = RuntimeShortcut(
        shortcut = shortcut,
        title = title,
        icon = null,
        contentColor = Color.Unspecified,
        useIndicator = false,
        indicatorCounter = 0,
        route = shortcut.id,
        available = true
    )
}
