/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.albums

import com.dot.gallery.feature.albums.ui.util.AlbumsFilter
import com.dot.gallery.feature.albums.ui.util.AlbumsFilterKind
import com.dot.gallery.feature.albums.ui.util.albumsFilterBarPinned
import com.dot.gallery.feature.albums.ui.util.availableAlbumFilters
import com.dot.gallery.feature.albums.ui.util.resolveAlbumFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumsFilterTest {

    @Test
    fun `one kind of album offers no chips`() {
        assertEquals(
            emptyList<AlbumsFilter>(),
            availableAlbumFilters(hasDevice = true, hasCollections = false, hasCloud = false, hasMediaTypes = false),
        )
    }

    @Test
    fun `chips follow what the user has`() {
        assertEquals(
            listOf(
                AlbumsFilter.All,
                AlbumsFilter(AlbumsFilterKind.DEVICE),
                AlbumsFilter(AlbumsFilterKind.MEDIA_TYPES),
            ),
            availableAlbumFilters(hasDevice = true, hasCollections = false, hasCloud = false, hasMediaTypes = true),
        )
    }

    @Test
    fun `hiding media types drops an active media types filter back to all`() {
        val withoutMediaTypes = availableAlbumFilters(
            hasDevice = true, hasCollections = true, hasCloud = false, hasMediaTypes = false,
        )
        assertEquals(
            AlbumsFilter.All,
            resolveAlbumFilter(AlbumsFilter(AlbumsFilterKind.MEDIA_TYPES), withoutMediaTypes),
        )
        val collections = AlbumsFilter(AlbumsFilterKind.COLLECTIONS)
        assertEquals(collections, resolveAlbumFilter(collections, withoutMediaTypes))
    }

    @Test
    fun `album sections replace the on device chip with one chip each`() {
        val filters = availableAlbumFilters(
            hasDevice = true, sectionIds = listOf(1L, 2L), hasCollections = false, hasCloud = false, hasMediaTypes = false,
        )
        assertEquals(
            listOf(
                AlbumsFilter.All,
                AlbumsFilter(AlbumsFilterKind.SECTION, 1L),
                AlbumsFilter(AlbumsFilterKind.SECTION, 2L),
            ),
            filters,
        )
    }

    @Test
    fun `a removed section falls back to all`() {
        val filters = availableAlbumFilters(
            hasDevice = true, sectionIds = listOf(1L), hasCollections = true, hasCloud = false, hasMediaTypes = false,
        )
        assertEquals(AlbumsFilter.All, resolveAlbumFilter(AlbumsFilter(AlbumsFilterKind.SECTION, 9L), filters))
    }

    @Test
    fun `visible bar pins once it reaches the top chrome`() {
        assertFalse(albumsFilterBarPinned(barOffset = 0, firstVisibleKey = "libraryHeader"))
        assertTrue(albumsFilterBarPinned(barOffset = -1, firstVisibleKey = "libraryHeader"))
    }

    @Test
    fun `bar below the viewport on short windows is not pinned`() {
        assertFalse(albumsFilterBarPinned(barOffset = null, firstVisibleKey = "libraryHeader"))
        assertFalse(albumsFilterBarPinned(barOffset = null, firstVisibleKey = "pinned_12"))
    }

    @Test
    fun `bar scrolled past is pinned`() {
        assertTrue(albumsFilterBarPinned(barOffset = null, firstVisibleKey = "album_3"))
    }

    @Test
    fun `empty list is not pinned`() {
        assertFalse(albumsFilterBarPinned(barOffset = null, firstVisibleKey = null))
    }
}
