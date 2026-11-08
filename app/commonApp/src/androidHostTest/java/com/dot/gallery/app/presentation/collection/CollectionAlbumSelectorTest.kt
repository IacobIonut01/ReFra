/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.collection

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.core.presentation.components.MediaTypeAlbum
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.feature.albums.ui.collections.isSelectableCollectionAlbumId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class CollectionAlbumSelectorTest {

    @Test
    fun localBucketIdsStaySelectableAndCloudAlbumsStayOut() {
        val computedCloudAlbumId = cloudAlbumId(ProviderType.IMMICH, 42L, "remote-album")

        assertFalse(isSelectableCollectionAlbumId(computedCloudAlbumId))
        assertTrue(isSelectableCollectionAlbumId(0L))
        assertTrue(isSelectableCollectionAlbumId(42L))
        assertTrue(isSelectableCollectionAlbumId(-525230641L))
        assertTrue(isSelectableCollectionAlbumId(Int.MIN_VALUE.toLong()))
        assertFalse(isSelectableCollectionAlbumId(Int.MIN_VALUE.toLong() - 1))
        assertFalse(isSelectableCollectionAlbumId(MediaTypeAlbum.VIDEOS.albumId))
        assertFalse(isSelectableCollectionAlbumId(-200L))
        assertFalse(isSelectableCollectionAlbumId(-300L))
        assertFalse(isSelectableCollectionAlbumId(-500L))
        assertFalse(isSelectableCollectionAlbumId(MergedSubfolderAlbum.VIRTUAL_ALBUM_ID_BASE))
    }
}
