/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.ui.theme.GalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid


@MediumTest
@RunWith(AndroidJUnit4::class)
class EditableLibraryShortcutsGridTest {

    @get:Rule
    val rule = createComposeRule()

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

    private val longList = listOf(
        LibraryShortcut.TRASH to "Trash",
        LibraryShortcut.FAVORITES to "Favorites",
        LibraryShortcut.VAULT to "Vault",
        LibraryShortcut.IGNORED to "Ignored",
        LibraryShortcut.CLOUD_ARCHIVE to "Archive",
    )

    private fun setGrid(
        working: List<LibraryShortcutPref>,
        runtime: Map<LibraryShortcut, RuntimeShortcut> = runtime(),
        editMode: Boolean = false,
        collapsible: Boolean = false,
        onClick: (String) -> Unit = {},
        onChange: (List<LibraryShortcutPref>) -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides MapSettingsStore()) {
                GalleryTheme(ignoreUserPreference = true) {
                    EditableLibraryShortcutsGrid(
                        working = working,
                        runtime = runtime,
                        editMode = editMode,
                        collapsible = collapsible,
                        onClick = onClick,
                        onChange = onChange
                    )
                }
            }
        }
    }

    private fun setLongList() = setGrid(
        working = longList.map { (shortcut, _) -> LibraryShortcutPref(id = shortcut.id, visible = true) },
        runtime = longList.associate { (shortcut, title) -> shortcut to runtimeShortcut(shortcut, title) },
        collapsible = true,
    )

    @Test
    fun allHiddenShowsNoCustomizePlaceholder() {
        setGrid(working = allHidden())

        // Editing is reached from the Library overflow menu, so an empty grid
        // renders nothing instead of a "Customize shortcuts" placeholder.
        rule.onNodeWithTag("library-shortcuts-customize").assertDoesNotExist()
        rule.onNodeWithText("Trash").assertDoesNotExist()
    }

    @Test
    fun hiddenTrayRestoresATileInEditMode() {
        var emitted: List<LibraryShortcutPref>? = null
        setGrid(working = allHidden(), editMode = true, onChange = { emitted = it })

        rule.onNodeWithText("Trash").assertIsDisplayed().performClick()
        assertNotNull(emitted)
        assertTrue(
            emitted!!.first { it.id == LibraryShortcut.TRASH.id }.visible
        )
    }

    @Test
    fun longListsCollapseBehindAnExpandTile() {
        setLongList()

        rule.onNodeWithText("Vault").assertIsDisplayed()
        rule.onNodeWithText("Ignored").assertDoesNotExist()
        rule.onNodeWithText("Archive").assertDoesNotExist()
        rule.onNodeWithText("Show more").assertIsDisplayed()

        rule.onNodeWithTag("library-shortcuts-more").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Ignored").assertIsDisplayed()
        rule.onNodeWithText("Archive").assertIsDisplayed()
        rule.onNodeWithText("Show less").assertIsDisplayed()
    }

    @Test
    fun expandToggleKeepsItsSlotAndExtraTilesFollowIt() {
        setLongList()

        val toggle = rule.onNodeWithTag("library-shortcuts-more")
        val collapsedBounds = toggle.getUnclippedBoundsInRoot()

        toggle.performClick()
        rule.waitForIdle()

        // "Show less" sits where "Show more" was, and the revealed tiles are
        // laid out after it rather than pushing it to the end.
        val expandedBounds = toggle.getUnclippedBoundsInRoot()
        assertEquals(collapsedBounds, expandedBounds)
        listOf("Ignored", "Archive").forEach { title ->
            val tile = rule.onNodeWithText(title).getUnclippedBoundsInRoot()
            assertTrue("$title should follow the toggle", tile.top > expandedBounds.top)
        }
    }

    @Test
    fun tilesOfferNoLongPressAction() {
        var emitted: List<LibraryShortcutPref>? = null
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id, visible = true)),
            onChange = { emitted = it },
        )

        // Editing opens from the Library overflow menu only; a tile exposes a
        // tap and nothing on long press.
        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .performTouchInput { longClick() }
        rule.waitForIdle()

        assertNull(emitted)
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
