package com.dot.gallery.app.presentation.library

import com.dot.gallery.feature.library.ui.mergeShortcutPrefs
import com.dot.gallery.feature.library.ui.libraryGridSectionKeys
import com.dot.gallery.feature.library.ui.resolveCloudLibraryAvailability
import androidx.compose.ui.graphics.Color
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.core.expandLocationMediaIds
import com.dot.gallery.core.matchingLocationMediaIds
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPref
import com.dot.gallery.feature.library.ui.LibraryShortcutSize
import com.dot.gallery.feature.library.ui.LibraryShortcutSpan
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.ShortcutCell
import com.dot.gallery.feature.library.ui.ShortcutFold
import com.dot.gallery.feature.library.ui.foldShortcutGrid
import com.dot.gallery.feature.library.ui.nextShortcutHeight
import com.dot.gallery.feature.library.ui.nextShortcutWidth
import com.dot.gallery.feature.library.ui.packShortcutGrid
import com.dot.gallery.feature.library.ui.resizeShortcutHeight
import com.dot.gallery.feature.library.ui.resizeShortcutWidth
import com.dot.gallery.feature.library.ui.trashDaysLeft
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.cloud.ui.people.PeopleListScreen



class LibraryShortcutTest {

    private val runtime = linkedMapOf(
        LibraryShortcut.TRASH to runtimeShortcut(LibraryShortcut.TRASH),
        LibraryShortcut.FAVORITES to runtimeShortcut(LibraryShortcut.FAVORITES),
        LibraryShortcut.VAULT to runtimeShortcut(LibraryShortcut.VAULT)
    )

    @Test
    fun emptyLayoutRestoresDefaultVisibilityAndOrder() {
        val merged = mergeShortcutPrefs(emptyList(), runtime)

        assertEquals(
            listOf(LibraryShortcut.TRASH, LibraryShortcut.FAVORITES, LibraryShortcut.VAULT),
            merged.map { it.shortcut }
        )
        assertTrue(merged.all { it.visible })
        assertTrue(merged.all { it.span == LibraryShortcutSpan.HALF })
        assertTrue(merged.all { it.size == LibraryShortcutSize.HALF })
    }

    @Test
    fun layoutsSavedBeforeTileHeightsKeepTheirWidths() {
        val stored = """[{"id":"trash","span":2},{"id":"favorites"},{"id":"vault","visible":false}]"""
        val prefs = Json.decodeFromString(ListSerializer(LibraryShortcutPref.serializer()), stored)

        assertEquals(
            listOf(LibraryShortcutSize.FULL, LibraryShortcutSize.HALF, LibraryShortcutSize.HALF),
            prefs.map { it.size }
        )
        assertFalse(prefs.last().visible)
    }

    @Test
    fun newSizesRoundTripAndStillWriteALegacySpan() {
        val serializer = ListSerializer(LibraryShortcutPref.serializer())
        val prefs = LibraryShortcutSize.entries.map {
            LibraryShortcutPref(id = it.name).withSize(it)
        }
        val decoded = Json.decodeFromString(serializer, Json.encodeToString(serializer, prefs))

        assertEquals(LibraryShortcutSize.entries, decoded.map { it.size })
        assertEquals(LibraryShortcutSpan.FULL, decoded.first { it.size == LibraryShortcutSize.WIDE_CARD }.span)
        assertEquals(LibraryShortcutSpan.HALF, decoded.first { it.size == LibraryShortcutSize.TALL_CARD }.span)
    }

    @Test
    fun unknownStoredSizesSnapToAnAllowedOne() {
        assertEquals(LibraryShortcutSize.HALF, LibraryShortcutPref(id = "a", width = 3, height = 1).size)
        assertEquals(LibraryShortcutSize.ICON, LibraryShortcutPref(id = "a", width = 1, height = 3).size)
        assertEquals(LibraryShortcutSize.WIDE_CARD, LibraryShortcutPref(id = "a", width = 9, height = 3).size)
    }

    @Test
    fun tilesBesideATallCardFillTheSpaceNextToIt() {
        val cells = packShortcutGrid(
            listOf(
                LibraryShortcutSize.TALL_CARD,
                LibraryShortcutSize.HALF,
                LibraryShortcutSize.ICON,
                LibraryShortcutSize.ICON,
                LibraryShortcutSize.HALF,
                LibraryShortcutSize.FULL,
            ),
            columns = 4
        )

        assertEquals(
            listOf(
                ShortcutCell(0, 0, 2, 3),
                ShortcutCell(2, 0, 2, 1),
                ShortcutCell(2, 1, 1, 1),
                ShortcutCell(3, 1, 1, 1),
                ShortcutCell(2, 2, 2, 1),
                ShortcutCell(0, 3, 4, 1),
            ),
            cells
        )
    }

    @Test
    fun packingKeepsReadingOrderAndNarrowsTilesToTheGrid() {
        val cells = packShortcutGrid(
            listOf(LibraryShortcutSize.ICON, LibraryShortcutSize.FULL, LibraryShortcutSize.ICON, LibraryShortcutSize.WIDE_CARD),
            columns = 2
        )

        assertEquals(
            listOf(
                ShortcutCell(0, 0, 1, 1),
                ShortcutCell(0, 1, 2, 1),
                ShortcutCell(0, 2, 1, 1),
                ShortcutCell(0, 3, 2, 2),
            ),
            cells
        )
    }

    @Test
    fun foldedGridShowsTwoRowsOfHalfTilesLikeBefore() {
        val half = List(5) { LibraryShortcutSize.HALF }

        assertEquals(ShortcutFold(3, LibraryShortcutSize.HALF), foldShortcutGrid(half, columns = 4))
        assertEquals(null, foldShortcutGrid(half.take(4), columns = 4))
    }

    @Test
    fun foldedGridKeepsALargeCardAfterAnIconVisible() {
        val sizes = listOf(LibraryShortcutSize.ICON, LibraryShortcutSize.TALL_CARD, LibraryShortcutSize.HALF)

        assertEquals(ShortcutFold(2, LibraryShortcutSize.ICON), foldShortcutGrid(sizes, columns = 4))
    }

    @Test
    fun foldedGridNeverCutsATallFirstCard() {
        val sizes = listOf(
            LibraryShortcutSize.TALL_CARD,
            LibraryShortcutSize.HALF,
            LibraryShortcutSize.HALF,
            LibraryShortcutSize.HALF,
            LibraryShortcutSize.HALF,
        )

        assertEquals(ShortcutFold(3, LibraryShortcutSize.HALF), foldShortcutGrid(sizes, columns = 4))
    }

    @Test
    fun dragSnapsPastTheHalfwayPointBetweenWidths() {
        val half = LibraryShortcutSize.HALF

        assertEquals(LibraryShortcutSize.HALF, resizeShortcutWidth(half, 1.6f, columns = 4))
        assertEquals(LibraryShortcutSize.ICON, resizeShortcutWidth(half, 1.4f, columns = 4))
        assertEquals(LibraryShortcutSize.FULL, resizeShortcutWidth(half, 3.1f, columns = 4))
        assertEquals(LibraryShortcutSize.HALF, resizeShortcutWidth(half, 3.1f, columns = 2))
    }

    @Test
    fun changingWidthKeepsTheTallestHeightTheNewWidthAllows() {
        assertEquals(LibraryShortcutSize.WIDE_CARD, resizeShortcutWidth(LibraryShortcutSize.TALL_CARD, 4f, 4))
        assertEquals(LibraryShortcutSize.CARD, resizeShortcutWidth(LibraryShortcutSize.WIDE_CARD, 2f, 4))
        assertEquals(LibraryShortcutSize.ICON, resizeShortcutWidth(LibraryShortcutSize.TALL_CARD, 1f, 4))
        assertEquals(LibraryShortcutSize.TALL_CARD, resizeShortcutHeight(LibraryShortcutSize.HALF, 2.6f))
        assertEquals(LibraryShortcutSize.WIDE_CARD, resizeShortcutHeight(LibraryShortcutSize.FULL, 3f))
        assertEquals(LibraryShortcutSize.ICON, resizeShortcutHeight(LibraryShortcutSize.ICON, 3f))
    }

    @Test
    fun tappingAHandleStepsThroughTheSizes() {
        assertEquals(LibraryShortcutSize.FULL, nextShortcutWidth(LibraryShortcutSize.HALF, 4))
        assertEquals(LibraryShortcutSize.ICON, nextShortcutWidth(LibraryShortcutSize.FULL, 4))
        assertEquals(LibraryShortcutSize.CARD, nextShortcutHeight(LibraryShortcutSize.HALF))
        assertEquals(LibraryShortcutSize.HALF, nextShortcutHeight(LibraryShortcutSize.TALL_CARD))
    }

    @Test
    fun trashExpiryCountsWholeDaysAndNeverGoesNegative() {
        assertEquals(3, trashDaysLeft(expirySeconds = 4 * 86_400L - 1, nowSeconds = 0))
        assertEquals(0, trashDaysLeft(expirySeconds = 10, nowSeconds = 100))
    }

    @Test
    fun hiddenTrashRemainsInEditableLayout() {
        val merged = mergeShortcutPrefs(
            prefs = listOf(LibraryShortcutPref(id = LibraryShortcut.TRASH.id, visible = false)),
            runtime = runtime
        )

        val trash = merged.first { it.shortcut == LibraryShortcut.TRASH }
        assertFalse(trash.visible)
        assertEquals(3, merged.size)
    }

    @Test
    fun unknownAndDuplicateEntriesDoNotCorruptLayout() {
        val merged = mergeShortcutPrefs(
            prefs = listOf(
                LibraryShortcutPref(id = "unknown"),
                LibraryShortcutPref(id = LibraryShortcut.TRASH.id, span = LibraryShortcutSpan.FULL),
                LibraryShortcutPref(id = LibraryShortcut.TRASH.id)
            ),
            runtime = runtime
        )

        assertEquals(3, merged.size)
        assertEquals(LibraryShortcutSpan.FULL, merged.first().span)
        assertEquals(1, merged.count { it.shortcut == LibraryShortcut.TRASH })
    }

    @Test
    fun cachedRowsDoNotFabricateProviderCapabilities() {
        val availability = resolveCloudLibraryAvailability(
            hasConfiguredAccounts = true,
            configuredCapabilities = emptySet(),
            isConnected = false,
        )

        assertTrue(availability.hasCloud)
        assertFalse(availability.hasArchive)
        assertFalse(availability.hasMemories)
        assertFalse(availability.hasPeople)
        assertFalse(availability.hasMap)
    }

    @Test
    fun configuredCapabilitiesRemainVisibleWhileDisconnected() {
        val availability = resolveCloudLibraryAvailability(
            hasConfiguredAccounts = true,
            configuredCapabilities = setOf(
                ProviderCapability.ARCHIVE,
                ProviderCapability.MAP,
                ProviderCapability.SHARE_MANAGE,
            ),
            isConnected = false,
        )

        assertFalse(availability.isConnected)
        assertTrue(availability.hasArchive)
        assertTrue(availability.hasMap)
        assertTrue(availability.hasShareLink)
    }

    @Test
    fun cloudBackupLocationSelectsItsLocalTimelineOwner() {
        assertEquals(
            setOf(-7L, 42L),
            expandLocationMediaIds(
                matchingMediaIds = setOf(-7L),
                cloudBackupIdsByLocalId = mapOf(42L to listOf(-7L, -8L)),
            ),
        )
    }

    @Test
    fun locationTimelineIncludesCloudMediaFromTheSelectedPlace() {
        val matchingCloudMedia = CloudMediaEntity(
            remoteId = "matching",
            providerType = ProviderType.IMMICH,
            serverConfigId = 7L,
            city = "bristol",
            country = "UNITED KINGDOM",
        )
        val malformedMatchingCloudMedia = CloudMediaEntity(
            remoteId = "malformed-matching",
            providerType = ProviderType.IMMICH,
            serverConfigId = 7L,
            city = "\u00A0",
            country = "Bristol, United Kingdom",
        )
        val otherCloudMedia = CloudMediaEntity(
            remoteId = "other",
            providerType = ProviderType.IMMICH,
            serverConfigId = 7L,
            city = "Cardiff",
            country = "United Kingdom",
        )

        assertEquals(
            setOf(42L, 99L, matchingCloudMedia.globalMediaId, malformedMatchingCloudMedia.globalMediaId),
            matchingLocationMediaIds(
                localMediaIds = setOf(42L),
                cloudMedia = listOf(matchingCloudMedia, malformedMatchingCloudMedia, otherCloudMedia),
                city = "Bristol",
                country = "United Kingdom",
                additionalMediaIds = setOf(99L),
            ),
        )
    }

    @Test
    fun hiddenOnlyPeopleKeepTheHeaderKeyWithoutAListKey() {
        // every person hidden → PeopleHeader stays (entry to PeopleListScreen
        // where the hidden section lives) but no PeopleList row is emitted.
        val keys = libraryGridSectionKeys(
            hasLocations = false,
            hasLocationsList = false,
            hasPeople = true,
            hasPeopleList = false,
            hasCategories = false,
            hasNoCategories = false
        )

        assertEquals(listOf("libraryShortcuts", "PeopleHeader"), keys)
    }

    @Test
    fun sectionKeysTrackEveryVisibleBlock() {
        assertEquals(
            listOf(
                "libraryShortcuts",
                "LocationsHeader", "LocationsList",
                "PeopleHeader", "PeopleList",
                "CategoriesHeader", "CategoriesList",
                "NoCategories"
            ),
            libraryGridSectionKeys(
                hasLocations = true,
                hasLocationsList = true,
                hasPeople = true,
                hasPeopleList = true,
                hasCategories = true,
                hasNoCategories = true
            )
        )
        assertEquals(
            listOf("libraryShortcuts"),
            libraryGridSectionKeys(
                hasLocations = false,
                hasLocationsList = false,
                hasPeople = false,
                hasPeopleList = false,
                hasCategories = false,
                hasNoCategories = false
            )
        )
    }

    private fun runtimeShortcut(shortcut: LibraryShortcut) = RuntimeShortcut(
        shortcut = shortcut,
        title = shortcut.id,
        icon = null,
        contentColor = Color.Unspecified,
        useIndicator = false,
        indicatorCounter = 0,
        route = shortcut.id,
        available = true
    )
}
