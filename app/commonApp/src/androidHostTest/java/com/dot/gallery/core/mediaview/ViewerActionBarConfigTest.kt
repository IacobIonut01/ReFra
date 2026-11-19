/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.mediaview

import com.dot.gallery.core.ui.mediaview.ViewerActionBarConfig
import com.dot.gallery.core.ui.mediaview.ViewerActionPlacement
import com.dot.gallery.core.ui.mediaview.ViewerQuickAction
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewerActionBarConfigTest {

    @Test
    fun defaultsPinVisualSearchAndCastToTheTopBar() {
        val config = ViewerActionBarConfig()

        assertEquals(ViewerQuickAction.VISUAL_SEARCH, config.topStart)
        assertEquals(ViewerQuickAction.CAST, config.topEnd)
        assertEquals(
            listOf(
                ViewerQuickAction.SHARE,
                ViewerQuickAction.COPY_TO_CLIPBOARD,
                ViewerQuickAction.FAVORITE,
                ViewerQuickAction.OPEN_AS,
                ViewerQuickAction.RESTORE,
                ViewerQuickAction.DOWNLOAD,
                ViewerQuickAction.RENAME,
                ViewerQuickAction.EDIT,
                ViewerQuickAction.TRASH,
            ),
            config.actions
        )
        assertFalse(config.actions.contains(ViewerQuickAction.VISUAL_SEARCH))
        assertFalse(config.actions.contains(ViewerQuickAction.CAST))
    }

    @Test
    fun sanitizedPreservesOrderAndDropsDuplicates() {
        val config = ViewerActionBarConfig(
            actions = listOf(
                ViewerQuickAction.EDIT,
                ViewerQuickAction.SHARE,
                ViewerQuickAction.EDIT,
                ViewerQuickAction.TRASH,
            )
        )

        assertEquals(
            listOf(ViewerQuickAction.EDIT, ViewerQuickAction.SHARE, ViewerQuickAction.TRASH),
            config.sanitized().actions
        )
    }

    @Test
    fun sanitizedDropsTopSlotActionsFromTheBottomList() {
        val config = ViewerActionBarConfig(
            actions = listOf(
                ViewerQuickAction.SHARE,
                ViewerQuickAction.CAST,
                ViewerQuickAction.EDIT,
            ),
            topStart = ViewerQuickAction.VISUAL_SEARCH,
            topEnd = ViewerQuickAction.CAST,
        )

        assertEquals(
            listOf(ViewerQuickAction.SHARE, ViewerQuickAction.EDIT),
            config.sanitized().actions
        )
    }

    @Test
    fun sanitizedClearsTopEndWhenItMatchesTopStart() {
        val config = ViewerActionBarConfig(
            actions = ViewerActionBarConfig.DEFAULT_ACTIONS,
            topStart = ViewerQuickAction.SHARE,
            topEnd = ViewerQuickAction.SHARE,
        )

        val sanitized = config.sanitized()
        assertEquals(ViewerQuickAction.SHARE, sanitized.topStart)
        assertEquals(null, sanitized.topEnd)
    }

    @Test
    fun emptyBottomFallsBackToDefaultsMinusTopActions() {
        val config = ViewerActionBarConfig(
            actions = listOf(ViewerQuickAction.SHARE),
            topStart = ViewerQuickAction.SHARE,
            topEnd = null,
        )

        assertEquals(
            ViewerActionBarConfig.DEFAULT_ACTIONS - ViewerQuickAction.SHARE,
            config.sanitized().actions
        )
    }

    @Test
    fun emptyConfigurationFallsBackToDefaults() {
        assertEquals(
            ViewerActionBarConfig.DEFAULT_ACTIONS,
            ViewerActionBarConfig(actions = emptyList()).sanitized().actions
        )
    }

    @Test
    fun legacyJsonWithoutSlotsDecodesWithDefaultTopSlots() {
        // A config stored before the top slots existed has no topStart/topEnd keys,
        // so the defaults apply — and the old "top" position becomes a no-op because
        // the top slot wins over the bottom list.
        val decoded = Json.decodeFromString(
            ViewerActionBarConfig.serializer(),
            """{"actions":["SHARE","VISUAL_SEARCH","EDIT"]}"""
        )

        assertEquals(ViewerQuickAction.VISUAL_SEARCH, decoded.topStart)
        assertEquals(ViewerQuickAction.CAST, decoded.topEnd)
        assertEquals(
            listOf(ViewerQuickAction.SHARE, ViewerQuickAction.EDIT),
            decoded.sanitized().actions
        )
    }

    @Test
    fun legacyBottomPositionMigrationKeepsVisualSearchInPlace() {
        val legacy = Json.decodeFromString(
            ViewerActionBarConfig.serializer(),
            """{"actions":["SHARE","VISUAL_SEARCH","EDIT"]}"""
        )

        val migrated = legacy.withVisualSearchInBottomBar()
        assertEquals(null, migrated.topStart)
        assertEquals(ViewerQuickAction.CAST, migrated.topEnd)
        assertEquals(
            listOf(ViewerQuickAction.SHARE, ViewerQuickAction.VISUAL_SEARCH, ViewerQuickAction.EDIT),
            migrated.actions
        )
    }

    @Test
    fun bottomPositionMigrationInsertsVisualSearchAfterShare() {
        val migrated = ViewerActionBarConfig().withVisualSearchInBottomBar()

        assertEquals(null, migrated.topStart)
        assertEquals(ViewerQuickAction.CAST, migrated.topEnd)
        val shareIndex = migrated.actions.indexOf(ViewerQuickAction.SHARE)
        assertEquals(ViewerQuickAction.VISUAL_SEARCH, migrated.actions[shareIndex + 1])
    }

    @Test
    fun bottomPositionMigrationInsertsAtStartWhenShareIsAbsent() {
        val config = ViewerActionBarConfig(
            actions = listOf(ViewerQuickAction.EDIT, ViewerQuickAction.TRASH),
            topStart = ViewerQuickAction.VISUAL_SEARCH,
            topEnd = ViewerQuickAction.CAST,
        )

        val migrated = config.withVisualSearchInBottomBar()
        assertEquals(ViewerQuickAction.VISUAL_SEARCH, migrated.actions.first())
        assertEquals(null, migrated.topStart)
    }

    @Test
    fun jsonRoundTripPreservesClearedAndReassignedSlots() {
        val config = ViewerActionBarConfig(
            actions = ViewerActionBarConfig.DEFAULT_ACTIONS - ViewerQuickAction.SHARE,
            topStart = null,
            topEnd = ViewerQuickAction.SHARE,
        )

        val encoded = Json.encodeToString(ViewerActionBarConfig.serializer(), config)
        val decoded = Json.decodeFromString(ViewerActionBarConfig.serializer(), encoded)

        // explicitNulls keeps the cleared slot distinct from "field absent" (defaults).
        assertEquals(null, decoded.topStart)
        assertEquals(ViewerQuickAction.SHARE, decoded.topEnd)
        assertEquals(config.actions, decoded.actions)
    }

    @Test
    fun favoriteMigrationDropsOnlyTheFavoriteAction() {
        val config = ViewerActionBarConfig(
            actions = listOf(ViewerQuickAction.SHARE, ViewerQuickAction.FAVORITE, ViewerQuickAction.EDIT)
        )

        assertEquals(
            listOf(ViewerQuickAction.SHARE, ViewerQuickAction.EDIT),
            config.withoutFavorite().actions
        )
    }

    @Test
    fun favoriteMigrationClearsFavoriteFromTopSlots() {
        val config = ViewerActionBarConfig(
            actions = ViewerActionBarConfig.DEFAULT_ACTIONS,
            topStart = ViewerQuickAction.FAVORITE,
            topEnd = ViewerQuickAction.CAST,
        )

        val migrated = config.withoutFavorite()
        assertEquals(null, migrated.topStart)
        assertEquals(ViewerQuickAction.CAST, migrated.topEnd)
        assertFalse(migrated.actions.contains(ViewerQuickAction.FAVORITE))
        assertFalse(migrated.sanitized().topActions.contains(ViewerQuickAction.FAVORITE))
    }

    @Test
    fun favoriteMigrationNeverLeavesAnEmptyBar() {
        val onlyFavorite = ViewerActionBarConfig(actions = listOf(ViewerQuickAction.FAVORITE))

        val migrated = onlyFavorite.withoutFavorite()
        assertFalse(migrated.actions.contains(ViewerQuickAction.FAVORITE))
        assertEquals(migrated.sanitized().actions, migrated.actions)
    }

    @Test
    fun withTopSlotMovesActionOutOfTheBottomList() {
        val config = ViewerActionBarConfig()

        val updated = config.withTopSlot(ViewerActionPlacement.TOP_START, ViewerQuickAction.SHARE)

        assertEquals(ViewerQuickAction.SHARE, updated.topStart)
        assertFalse(updated.actions.contains(ViewerQuickAction.SHARE))
        // The displaced slot action is dropped rather than duplicated into the bottom list.
        assertFalse(updated.topActions.contains(ViewerQuickAction.VISUAL_SEARCH))
    }

    @Test
    fun withTopSlotClearsTheOtherSlotWhenItHoldsTheSameAction() {
        val config = ViewerActionBarConfig(
            actions = ViewerActionBarConfig.DEFAULT_ACTIONS,
            topStart = ViewerQuickAction.VISUAL_SEARCH,
            topEnd = ViewerQuickAction.SHARE,
        )

        val updated = config.withTopSlot(ViewerActionPlacement.TOP_START, ViewerQuickAction.SHARE)

        assertEquals(ViewerQuickAction.SHARE, updated.topStart)
        assertEquals(null, updated.topEnd)
    }

    @Test
    fun withTopSlotRefusesToEmptyTheBottomList() {
        val config = ViewerActionBarConfig(
            actions = listOf(ViewerQuickAction.SHARE),
            topStart = null,
            topEnd = ViewerQuickAction.CAST,
        )

        val updated = config.withTopSlot(ViewerActionPlacement.TOP_START, ViewerQuickAction.SHARE)

        assertEquals(config, updated)
    }

    @Test
    fun withTopSlotNullClearsTheSlotWithoutReturningTheAction() {
        val config = ViewerActionBarConfig()

        val updated = config.withTopSlot(ViewerActionPlacement.TOP_END, null)

        assertEquals(ViewerQuickAction.VISUAL_SEARCH, updated.topStart)
        assertEquals(null, updated.topEnd)
        assertFalse(updated.actions.contains(ViewerQuickAction.CAST))
        assertEquals(ViewerActionBarConfig.DEFAULT_ACTIONS, updated.actions)
    }
}
