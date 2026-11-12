/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.library

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.compose.ui.graphics.Color
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.feature.library.ui.LibraryShortcut
import com.dot.gallery.feature.library.ui.LibraryShortcutPreviews
import com.dot.gallery.feature.library.ui.MAX_SHORTCUT_FAVORITE_POOL
import com.dot.gallery.feature.library.ui.RuntimeShortcut
import com.dot.gallery.feature.library.ui.ShortcutPreviewStyle
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
    fun shufflePoolSpreadsAcrossEveryFavouriteAndStaysTheSame() {
        val favorites = (1L..100L).map { media(it) }

        val pool = shortcutPreviews(trash = emptyList(), favorites = favorites.shuffled()).favoritePool
        val again = shortcutPreviews(trash = emptyList(), favorites = favorites.shuffled()).favoritePool

        assertEquals(MAX_SHORTCUT_FAVORITE_POOL, pool.size)
        assertEquals(pool, again)
        assertTrue(pool.first().id > 90L)
        assertTrue(pool.last().id < 10L)
        assertEquals(
            (1L..3L).toSet(),
            shortcutPreviews(emptyList(), (1L..3L).map { media(it) }).favoritePool.map { it.id }.toSet()
        )
    }

    @Test
    fun aPlaceholderTileNeverDrawsMediaItWasGiven() {
        val media = listOf(media(1))
        val vault = runtimeShortcut(LibraryShortcut.VAULT).copy(
            previewMedia = media,
            oldestPreviewMedia = media,
            shufflePreviewMedia = media,
            previewStyle = ShortcutPreviewStyle.PLACEHOLDER,
        )

        listOf(false, true).forEach { oldest ->
            listOf(false, true).forEach { shuffle ->
                assertTrue(vault.preview(oldestFirst = oldest, shuffle = shuffle).isEmpty())
            }
        }
    }

    @Test
    fun shuffleWinsOverOldestOnlyWhenThereIsAShuffledCover() {
        val latest = listOf(media(3))
        val oldest = listOf(media(1))
        val shuffled = listOf(media(2))
        val favorites = runtimeShortcut(LibraryShortcut.FAVORITES).copy(
            previewMedia = latest,
            oldestPreviewMedia = oldest,
            shufflePreviewMedia = shuffled,
        )

        assertEquals(shuffled, favorites.preview(oldestFirst = true, shuffle = true))
        assertEquals(oldest, favorites.preview(oldestFirst = true, shuffle = false))
        assertEquals(
            oldest,
            favorites.copy(shufflePreviewMedia = emptyList()).preview(oldestFirst = true, shuffle = true)
        )
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

        val trashedInPool = valid.copy(
            shortcutPreviews = valid.shortcutPreviews.copy(favoritePool = listOf(media(4, trashed = 1)))
        )
        assertFalse(validateLibrarySnapshot(trashedInPool))

        val poolTooBig = valid.copy(
            shortcutPreviews = valid.shortcutPreviews.copy(
                favoritePool = (1L..MAX_SHORTCUT_FAVORITE_POOL + 1L).map { media(it) }
            )
        )
        assertFalse(validateLibrarySnapshot(poolTooBig))
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
