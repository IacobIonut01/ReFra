/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.library.ui.LibraryShortcutPreviews
import com.dot.gallery.feature.library.ui.LibrarySnapshot
import com.dot.gallery.feature.library.ui.shortcutPreviews
import com.dot.gallery.feature.library.validateLibrarySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith


@SmallTest
@RunWith(AndroidJUnit4::class)
class LibraryShortcutPreviewsTest {

    private fun media(id: Long, trashed: Int = 0, expiry: Long? = null) = UriMedia(
        id = id,
        label = "photo-$id.jpg",
        uri = Uri.parse("content://media/external/images/media/$id"),
        path = "/storage/emulated/0/DCIM/photo-$id.jpg",
        relativePath = "DCIM/",
        albumID = 10L,
        albumLabel = "Camera",
        timestamp = 1_700_000_000L + id,
        expiryTimestamp = expiry,
        fullDate = "2023-11-14",
        mimeType = "image/jpeg",
        favorite = if (trashed == 0) 1 else 0,
        trashed = trashed,
        size = 1_024L
    )

    @Test
    fun favouritesCoverTakesTheNewestOrOldestFourByDate() {
        val favorites = (1L..10L).shuffled().map { media(it) }

        val previews = shortcutPreviews(trash = emptyList(), favorites = favorites)

        assertEquals(listOf(10L, 9L, 8L, 7L), previews.latestFavorites.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), previews.oldestFavorites.map { it.id })
    }

    @Test
    fun trashShowsTheMostRecentlyDeletedAndTheNextExpiry() {
        val trash = listOf(
            media(1, trashed = 1, expiry = 5_000),
            media(2, trashed = 1, expiry = 9_000),
            media(3, trashed = 1, expiry = 7_000),
        )

        val previews = shortcutPreviews(trash = trash, favorites = emptyList())

        assertEquals(listOf(2L, 3L, 1L), previews.recentTrash.map { it.id })
        assertEquals(5_000L, previews.nextTrashExpiry)
    }

    @Test
    fun cachedPreviewsAreValidatedLikeOtherCachedMedia() {
        val valid = LibrarySnapshot(
            shortcutPreviews = LibraryShortcutPreviews(
                latestFavorites = listOf(media(1)),
                recentTrash = listOf(media(2, trashed = 1, expiry = 5_000)),
                nextTrashExpiry = 5_000,
            )
        )
        assertTrue(validateLibrarySnapshot(valid))

        val trashedFavourite = valid.copy(
            shortcutPreviews = valid.shortcutPreviews.copy(latestFavorites = listOf(media(3, trashed = 1)))
        )
        assertFalse(validateLibrarySnapshot(trashedFavourite))

        val tooMany = valid.copy(
            shortcutPreviews = valid.shortcutPreviews.copy(latestFavorites = (1L..5L).map { media(it) })
        )
        assertFalse(validateLibrarySnapshot(tooMany))
    }
}
