/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.duplicates

import com.dot.gallery.data.entity.MediaContentHashEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSetsTest {

    private fun copy(
        id: Long,
        folder: String,
        favorite: Boolean = false,
        size: Long = 100,
        trashed: Boolean = false,
    ) = CopyInfo(
        id = id,
        relativePath = folder,
        favorite = favorite,
        size = size,
        modifiedSeconds = 10,
        path = "/storage/emulated/0/$folder$id.jpg",
        trashed = trashed,
    )

    private fun row(copy: CopyInfo, sha1: String?) = MediaContentHashEntity(
        mediaId = copy.id,
        sourceSize = copy.size,
        sourceModifiedSeconds = copy.modifiedSeconds,
        sourcePath = copy.path,
        partialHash = "p",
        sha1 = sha1,
        updatedAtMillis = 0,
    )

    private val rules = KeeperRules()

    @Test
    fun folderMatchingFindsTheFolderAnywhereInThePath() {
        assertTrue(folderMatches("DCIM/Camera/", "DCIM/Camera"))
        assertTrue(folderMatches("Pictures/Screenshots/", "Screenshots"))
        assertTrue(folderMatches("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "WhatsApp Images"))
        assertTrue(folderMatches("download/", "Download"))
        assertFalse(folderMatches("DCIM/CameraRoll/", "DCIM/Camera"))
        assertFalse(folderMatches("Pictures/", "DCIM/Camera"))
        assertFalse(folderMatches("DCIM/", ""))
    }

    @Test
    fun keeperPrefersFavouriteOverEveryFolderRule() {
        val camera = copy(1, "DCIM/Camera/")
        val download = copy(2, "Download/", favorite = true)
        assertEquals(download, chooseKeeper(listOf(camera, download), rules) { it })
    }

    @Test
    fun keeperPrefersKeepFolderThenAvoidsRemoveFolder() {
        val download = copy(1, "Download/")
        val camera = copy(5, "DCIM/Camera/")
        val other = copy(3, "Documents/")
        assertEquals(camera, chooseKeeper(listOf(download, camera, other), rules) { it })
        // Without a keep-folder copy, a copy outside the remove folders wins over an older one inside.
        assertEquals(other, chooseKeeper(listOf(download, other), rules) { it })
    }

    @Test
    fun keepFolderThatIsAlsoARemoveFolderIsNotPreferred() {
        // Pictures/Screenshots is under a keep folder (Pictures) and a remove folder (Screenshots).
        val screenshot = copy(1, "Pictures/Screenshots/")
        val edits = copy(4, "Pictures/Edits/")
        assertEquals(edits, chooseKeeper(listOf(screenshot, edits), rules) { it })
    }

    @Test
    fun keeperFallsBackToTheEarliestAdded() {
        val a = copy(9, "Documents/")
        val b = copy(4, "Movies/")
        assertEquals(b, chooseKeeper(listOf(a, b), rules) { it })
    }

    @Test
    fun customRulesChangeTheKeeper() {
        val download = copy(1, "Download/")
        val camera = copy(2, "DCIM/Camera/")
        val custom = KeeperRules(keepFolders = listOf("Download"), removeFolders = listOf("DCIM"))
        assertEquals(download, chooseKeeper(listOf(camera, download), custom) { it })
    }

    @Test
    fun setsSkipMissingTrashedAndChangedMedia() {
        val a = copy(1, "DCIM/Camera/")
        val b = copy(2, "Download/")
        val gone = copy(3, "Download/")
        val trashed = copy(4, "Download/", trashed = true)
        val changed = copy(5, "Download/")
        val rows = listOf(a, b, gone, trashed, changed).map { row(it, "same") } +
            row(copy(6, "Download/"), null)
        val live = mapOf(
            a.id to a,
            b.id to b,
            trashed.id to trashed,
            changed.id to changed.copy(size = 101),
            6L to copy(6, "Download/"),
        )
        val sets = buildDuplicateSets(rows, live, rules) { it }
        assertEquals(1, sets.size)
        assertEquals(listOf(a, b), sets.single().items)
        assertEquals(a, sets.single().keeper)
    }

    @Test
    fun setsNeedTwoLiveCopiesAndAreOrderedBySpaceToFree() {
        val small1 = copy(1, "DCIM/Camera/", size = 10)
        val small2 = copy(2, "Download/", size = 10)
        val small3 = copy(3, "Download/", size = 10)
        val big1 = copy(4, "DCIM/Camera/", size = 1_000)
        val big2 = copy(5, "Download/", size = 1_000)
        val lonely = copy(6, "Download/", size = 50)
        val rows = listOf(row(small1, "s"), row(small2, "s"), row(small3, "s"), row(big1, "b"), row(big2, "b"), row(lonely, "l"))
        val live = listOf(small1, small2, small3, big1, big2, lonely).associateBy { it.id }
        val sets = buildDuplicateSets(rows, live, rules) { it }
        assertEquals(listOf("b", "s"), sets.map { it.sha1 })
        assertEquals(1_000L, sets[0].extraBytes)
        assertEquals(20L, sets[1].extraBytes)
    }
}
