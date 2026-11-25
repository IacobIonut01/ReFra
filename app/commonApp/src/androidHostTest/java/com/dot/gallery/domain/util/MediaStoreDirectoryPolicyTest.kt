/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreDirectoryPolicyTest {

    private fun collection(mime: String, path: String, access: Boolean = false) =
        mediaStoreWriteCollection(mime, path, access)

    @Test
    fun imagesAreTypedOnlyInDcimAndPictures() {
        assertEquals(MediaStoreWriteCollection.TYPED, collection("image/jpeg", "DCIM/Camera/"))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("image/png", "Pictures/Album/"))
        assertEquals(MediaStoreWriteCollection.UNSUPPORTED, collection("image/jpeg", "Movies/X/"))
        assertEquals(MediaStoreWriteCollection.FILES, collection("image/jpeg", "Download/"))
        assertEquals(MediaStoreWriteCollection.FILES, collection("image/jpeg", "Documents/"))
    }

    @Test
    fun videosAreTypedInDcimMoviesAndPictures() {
        assertEquals(MediaStoreWriteCollection.TYPED, collection("video/mp4", "DCIM/"))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("video/mp4", "Movies/Trips/"))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("video/mp4", "Pictures/"))
        assertEquals(MediaStoreWriteCollection.FILES, collection("video/mp4", "Download/"))
    }

    @Test
    fun downloadAndDocumentsUseFilesForAnyMime() {
        assertEquals(MediaStoreWriteCollection.FILES, collection("image/jpeg", "Download/"))
        assertEquals(MediaStoreWriteCollection.FILES, collection("video/mp4", "Documents/TriageDocs/"))
        assertEquals(MediaStoreWriteCollection.FILES, collection("application/pdf", "Download/"))
    }

    @Test
    fun directoryNameIsCaseInsensitive() {
        assertEquals(MediaStoreWriteCollection.FILES, collection("image/jpeg", "download/"))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("image/jpeg", "dcim/camera/"))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("video/mp4", "MOVIES/"))
    }

    @Test
    fun rootOrBlankPathFallsThroughToAccessRule() {
        assertEquals(MediaStoreWriteCollection.UNSUPPORTED, collection("image/jpeg", "/"))
        assertEquals(MediaStoreWriteCollection.UNSUPPORTED, collection("image/jpeg", ""))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("image/jpeg", "/", access = true))
        assertEquals(MediaStoreWriteCollection.FILES, collection("application/pdf", "", access = true))
    }

    @Test
    fun allFilesAccessOpensAnyDirectory() {
        assertEquals(MediaStoreWriteCollection.TYPED, collection("image/jpeg", "Movies/X/", true))
        assertEquals(MediaStoreWriteCollection.TYPED, collection("video/mp4", "Music/", true))
        assertEquals(MediaStoreWriteCollection.FILES, collection("application/pdf", "Music/", true))
    }

    @Test
    fun otherMimeOutsideFilesDirsIsUnsupportedWithoutAccess() {
        assertEquals(MediaStoreWriteCollection.UNSUPPORTED, collection("application/pdf", "Music/"))
    }
}
