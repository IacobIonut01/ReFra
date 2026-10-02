package com.dot.gallery.feature_node.presentation.library

import androidx.compose.ui.graphics.Color
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.core.expandLocationMediaIds
import com.dot.gallery.core.matchingLocationMediaIds
import com.dot.gallery.feature_node.presentation.library.components.LibraryShortcut
import com.dot.gallery.feature_node.presentation.library.components.LibraryShortcutPref
import com.dot.gallery.feature_node.presentation.library.components.LibraryShortcutSpan
import com.dot.gallery.feature_node.presentation.library.components.RuntimeShortcut
import com.dot.gallery.feature_node.presentation.library.components.mergeShortcutPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        // #1262: every person hidden → PeopleHeader stays (entry to PeopleListScreen
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
