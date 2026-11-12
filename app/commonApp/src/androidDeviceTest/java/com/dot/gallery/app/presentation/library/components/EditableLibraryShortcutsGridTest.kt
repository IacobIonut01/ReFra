/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library.components

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MockedMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.LibraryShortcutSpan
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dot.gallery.feature.library.ui.ShortcutPreviewStyle
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
            CompositionLocalProvider(
                LocalSettingsStore provides MapSettingsStore(),
                LocalMediaImageRenderer provides MockedMediaImageRenderer,
            ) {
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

    @Test
    fun tappingATileInEditModeShowsItsResizeHandles() {
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id)),
            editMode = true,
        )
        val handle = "library-shortcut-resize-width-${LibraryShortcut.TRASH.id}"
        rule.onNodeWithTag(handle).assertDoesNotExist()

        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}").performClick()

        rule.onNodeWithTag(handle).assertIsDisplayed()
        rule.onNodeWithTag("library-shortcut-resize-height-${LibraryShortcut.TRASH.id}").assertIsDisplayed()
    }

    @Test
    fun handleTapsStepTheWidthAndHeight() {
        var emitted: List<LibraryShortcutPref>? = null
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id)),
            editMode = true,
            onChange = { emitted = it },
        )
        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}").performClick()

        rule.onNodeWithTag("library-shortcut-resize-width-${LibraryShortcut.TRASH.id}").performClick()
        rule.waitForIdle()
        assertEquals(LibraryShortcutSize.THREE_QUARTER, emitted!!.single().size)
        // 3×1 has one height, so the bottom handle goes away until the next width.
        rule.onNodeWithTag("library-shortcut-resize-height-${LibraryShortcut.TRASH.id}").assertDoesNotExist()

        rule.onNodeWithTag("library-shortcut-resize-width-${LibraryShortcut.TRASH.id}").performClick()
        rule.waitForIdle()
        assertEquals(LibraryShortcutSize.FULL, emitted!!.single().size)
        assertEquals(LibraryShortcutSpan.FULL, emitted!!.single().span)

        rule.onNodeWithTag("library-shortcut-resize-height-${LibraryShortcut.TRASH.id}").performClick()
        rule.waitForIdle()
        assertEquals(LibraryShortcutSize.WIDE_CARD, emitted!!.single().size)
    }

    @Test
    fun draggingTheSideHandleLeftSnapsToAnIcon() {
        var emitted: List<LibraryShortcutPref>? = null
        var writes = 0
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id)),
            editMode = true,
            onChange = { emitted = it; writes++ },
        )
        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}").performClick()

        rule.onNodeWithTag("library-shortcut-resize-width-${LibraryShortcut.TRASH.id}")
            .performTouchInput { swipeLeft(startX = centerX, endX = centerX - 300f) }
        rule.waitForIdle()

        assertEquals(LibraryShortcutSize.ICON, emitted!!.single().size)
        assertEquals(1, writes)
        rule.onNodeWithTag("library-shortcut-resize-height-${LibraryShortcut.TRASH.id}").assertDoesNotExist()
    }

    @Test
    fun dragReorderingWritesTheLayoutOnceWhenTheDragEnds() {
        val writes = mutableListOf<List<LibraryShortcutPref>>()
        setGrid(
            working = listOf(
                LibraryShortcutPref(id = LibraryShortcut.TRASH.id),
                LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id),
                LibraryShortcutPref(id = LibraryShortcut.VAULT.id),
            ),
            editMode = true,
            onChange = { writes += it },
        )
        val vault = rule.onNodeWithTag("library-shortcut-${LibraryShortcut.VAULT.id}").getUnclippedBoundsInRoot()
        val trash = rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}").getUnclippedBoundsInRoot()

        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.VAULT.id}").performTouchInput {
            val dx = (trash.left - vault.left).toPx()
            val dy = (trash.top - vault.top).toPx()
            down(center)
            repeat(20) { step -> moveTo(center + Offset(dx * (step + 1) / 20f, dy * (step + 1) / 20f)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(1, writes.size)
        assertEquals(LibraryShortcut.VAULT.id, writes.single().first().id)
    }

    @Test
    fun aTallCardTakesThreeRows() {
        setGrid(
            working = listOf(
                LibraryShortcutPref(id = LibraryShortcut.TRASH.id).withSize(LibraryShortcutSize.TALL_CARD),
                LibraryShortcutPref(id = LibraryShortcut.VAULT.id),
            ),
        )
        val card = rule.onNodeWithTag("library-shortcut-${LibraryShortcut.TRASH.id}").getUnclippedBoundsInRoot()
        val beside = rule.onNodeWithTag("library-shortcut-${LibraryShortcut.VAULT.id}").getUnclippedBoundsInRoot()

        assertEquals((56.dp * 3 + 8.dp * 2).value, (card.bottom - card.top).value, 1f)
        assertEquals(card.top, beside.top)
        assertTrue(beside.left > card.left)
    }

    @Test
    fun favouritesCardOffersLatestOrOldest() {
        var emitted: List<LibraryShortcutPref>? = null
        val favorites = runtimeShortcut(LibraryShortcut.FAVORITES, "Favorites").copy(
            previewMedia = listOf(media(2)),
            oldestPreviewMedia = listOf(media(1)),
        )
        setGrid(
            working = listOf(
                LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id).withSize(LibraryShortcutSize.CARD),
            ),
            runtime = linkedMapOf(LibraryShortcut.FAVORITES to favorites),
            editMode = true,
            onChange = { emitted = it },
        )
        rule.onNodeWithTag("library-shortcut-cover-order").assertDoesNotExist()

        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.FAVORITES.id}").performClick()
        rule.onNodeWithText("Oldest").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertTrue(emitted!!.single().oldestFirst)
    }

    @Test
    fun anEmptyCardDrawsItsIconOnce() {
        val trash = runtimeShortcut(LibraryShortcut.TRASH, "Trash").copy(icon = Icons.Outlined.Delete)
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id).withSize(LibraryShortcutSize.WIDE_CARD)),
            runtime = linkedMapOf(LibraryShortcut.TRASH to trash),
        )

        rule.onAllNodesWithTag("library-shortcut-icon", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theCountSitsRightAfterTheTitle() {
        val favorites = runtimeShortcut(LibraryShortcut.FAVORITES, "Favorites").copy(
            useIndicator = true,
            indicatorCounter = 12,
            previewMedia = listOf(media(1), media(2), media(3)),
        )
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id).withSize(LibraryShortcutSize.FULL)),
            runtime = linkedMapOf(LibraryShortcut.FAVORITES to favorites),
        )

        val title = rule.onNodeWithText("Favorites", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val count = rule.onNodeWithText("· 12", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val tile = rule.onNodeWithTag("library-shortcut-${LibraryShortcut.FAVORITES.id}").getUnclippedBoundsInRoot()
        assertTrue("count should follow the title", (count.left - title.right).value in 0f..12f)
        assertTrue("photos stay at the end", (tile.right - count.right).value > 100f)
    }

    @Test
    fun theVaultCardShowsPlaceholdersInsteadOfPhotos() {
        val vault = runtimeShortcut(LibraryShortcut.VAULT, "Vault").copy(
            previewMedia = listOf(media(1)),
            previewStyle = ShortcutPreviewStyle.PLACEHOLDER,
        )
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.VAULT.id).withSize(LibraryShortcutSize.TALL_CARD)),
            runtime = linkedMapOf(LibraryShortcut.VAULT to vault),
        )

        rule.onNodeWithTag("library-shortcut-placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun favouritesCanShuffleAndUseAScrapbookLayout() {
        var emitted: List<LibraryShortcutPref>? = null
        val favorites = runtimeShortcut(LibraryShortcut.FAVORITES, "Favorites").copy(
            previewMedia = listOf(media(3), media(4)),
            oldestPreviewMedia = listOf(media(1)),
            shufflePreviewMedia = listOf(media(2)),
        )
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.FAVORITES.id).withSize(LibraryShortcutSize.CARD)),
            runtime = linkedMapOf(LibraryShortcut.FAVORITES to favorites),
            editMode = true,
            onChange = { emitted = it },
        )
        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.FAVORITES.id}").performClick()

        rule.onNodeWithText("Shuffle").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertTrue(emitted!!.single().shuffle)

        rule.onNodeWithText("Scrapbook").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertTrue(emitted!!.single().scrapbook)
        rule.onNodeWithTag("library-shortcut-scrapbook", useUnmergedTree = true).assertExists()
    }

    @Test
    fun exploreTilesResizeLikeAnyOtherShortcut() {
        var emitted: List<LibraryShortcutPref>? = null
        val people = runtimeShortcut(LibraryShortcut.PEOPLE, "People").copy(
            previewStyle = ShortcutPreviewStyle.FACES,
            previewFaces = listOf(null, null),
        )
        setGrid(
            working = listOf(LibraryShortcutPref(id = LibraryShortcut.PEOPLE.id)),
            runtime = linkedMapOf(LibraryShortcut.PEOPLE to people),
            editMode = true,
            onChange = { emitted = it },
        )
        rule.onNodeWithTag("library-shortcut-${LibraryShortcut.PEOPLE.id}").performClick()

        rule.onNodeWithTag("library-shortcut-resize-height-${LibraryShortcut.PEOPLE.id}").performClick()
        rule.waitForIdle()

        assertEquals(LibraryShortcutSize.CARD, emitted!!.single().size)
        rule.onNodeWithText("People", useUnmergedTree = true).assertExists()
    }

    private fun media(id: Long) = UriMedia(
        id = id,
        label = "photo-$id.jpg",
        uri = Uri.parse("content://media/external/images/media/$id"),
        path = "/storage/emulated/0/DCIM/photo-$id.jpg",
        relativePath = "DCIM/",
        albumID = 10L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L + id,
        fullDate = "2023-11-14",
        mimeType = "image/jpeg",
        favorite = 1,
        trashed = 0,
        size = 1_024L
    )

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
