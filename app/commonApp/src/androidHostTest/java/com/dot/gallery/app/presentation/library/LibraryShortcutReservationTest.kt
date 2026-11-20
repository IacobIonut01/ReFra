/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature.library.ui.DataBackedShortcuts
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.LibraryState
import com.dot.gallery.feature.library.ui.nextRememberedShortcuts
import com.dot.gallery.feature.library.ui.pendingShortcuts
import com.dot.gallery.feature.library.ui.reservedShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test


class LibraryShortcutReservationTest {

    private fun state(
        snapshot: LibrarySnapshot = LibrarySnapshot(),
        aiAvailable: Boolean = true,
    ) = LibraryState(
        snapshot = snapshot,
        modelStatus = ModelStatus.READY,
        aiAvailable = aiAvailable,
        mapsEnabled = true,
    )

    @Test
    fun pendingCoversEveryUnsettledDataBackedShortcut() {
        val pending = pendingShortcuts(state(), noClassification = false)
        assertEquals(
            setOf(
                LibraryShortcut.LOCATIONS,
                LibraryShortcut.PEOPLE,
                LibraryShortcut.CATEGORIES,
                LibraryShortcut.CLOUD_ARCHIVE,
                LibraryShortcut.CLOUD_SHARED_LINKS,
                LibraryShortcut.CLOUD_BACKUP,
                LibraryShortcut.CLOUD_ACCOUNTS,
            ),
            pending
        )
    }

    @Test
    fun pendingDropsSettledSections() {
        val settled = LibrarySnapshot(
            locations = emptyList(),
            peopleLoaded = true,
            categories = emptyList(),
            cloudLoaded = true,
        )
        assertEquals(emptySet<LibraryShortcut>(), pendingShortcuts(state(settled), false))
    }

    @Test
    fun pendingSkipsCategoriesWithoutAiOrClassification() {
        val snapshot = LibrarySnapshot(
            locations = emptyList(),
            peopleLoaded = true,
            categories = emptyList(),
            cloudLoaded = true,
        )
        assertEquals(
            emptySet<LibraryShortcut>(),
            pendingShortcuts(state(snapshot, aiAvailable = false), false)
        )
        assertEquals(
            emptySet<LibraryShortcut>(),
            pendingShortcuts(state(snapshot), noClassification = true)
        )
        // Categories stay pending while their first load is outstanding.
        val loading = LibrarySnapshot(
            locations = emptyList(),
            peopleLoaded = true,
            cloudLoaded = true,
        )
        assertEquals(
            setOf(LibraryShortcut.CATEGORIES),
            pendingShortcuts(state(loading), false)
        )
    }

    @Test
    fun reservedIsTheRememberedIntersectionWithPending() {
        val pending = setOf(LibraryShortcut.PEOPLE, LibraryShortcut.LOCATIONS)
        assertEquals(
            setOf(LibraryShortcut.PEOPLE, LibraryShortcut.LOCATIONS),
            reservedShortcuts(setOf("people", "locations"), pending)
        )
        assertEquals(
            setOf(LibraryShortcut.PEOPLE),
            reservedShortcuts(setOf("people", "categories"), pending)
        )
        assertEquals(
            emptySet<LibraryShortcut>(),
            reservedShortcuts(setOf("people"), emptySet())
        )
    }

    @Test
    fun nextKeepsPendingAnswersAndSettledShownTiles() {
        val pending = setOf(LibraryShortcut.PEOPLE)
        val shown = setOf(LibraryShortcut.LOCATIONS)
        // "people" is still pending and was remembered; "locations" settled and
        // is shown; "categories" settled and is gone; "favorites" is not
        // data-backed and must never be stored.
        assertEquals(
            setOf("people", "locations"),
            nextRememberedShortcuts(
                remembered = setOf("people", "categories", "favorites"),
                shown = shown,
                pending = pending,
            )
        )
    }

    @Test
    fun nextDropsPendingTilesThatWereNeverShown() {
        assertEquals(
            emptySet<String>(),
            nextRememberedShortcuts(
                remembered = emptySet(),
                shown = emptySet(),
                pending = setOf(LibraryShortcut.PEOPLE),
            )
        )
    }

    @Test
    fun nextNeverStoresNonDataBackedIds() {
        val shown = LibraryShortcut.entries.toSet() - DataBackedShortcuts
        val result = nextRememberedShortcuts(
            remembered = shown.map { it.id }.toSet(),
            shown = shown,
            pending = emptySet(),
        )
        assertEquals(emptySet<String>(), result)
    }
}
