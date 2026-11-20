/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker

import com.dot.gallery.core.util.MediaStoreBuckets
import com.dot.gallery.feature.picker.ui.PickerSource
import com.dot.gallery.feature.picker.ui.PickerSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerSourcesTest {

    @Test
    fun favoritesOnlyWhenSupportedAndFirst() {
        assertEquals(
            listOf(PickerSource.FAVORITES),
            PickerSources.visible(
                favorites = true,
                privateFolder = false,
                vault = false,
                cloud = false
            )
        )
        assertEquals(
            emptyList<PickerSource>(),
            PickerSources.visible(
                favorites = false,
                privateFolder = false,
                vault = false,
                cloud = false
            )
        )
    }

    @Test
    fun fullOrderIsFavoritesPrivateFolderVaultCloud() {
        assertEquals(
            listOf(
                PickerSource.FAVORITES,
                PickerSource.PRIVATE_FOLDER,
                PickerSource.VAULT,
                PickerSource.CLOUD
            ),
            PickerSources.visible(
                favorites = true,
                privateFolder = true,
                vault = true,
                cloud = true
            )
        )
    }

    @Test
    fun columnsIsThreeUnlessAllFourSources() {
        assertEquals(3, PickerSources.columns(1))
        assertEquals(3, PickerSources.columns(2))
        assertEquals(3, PickerSources.columns(3))
        assertEquals(2, PickerSources.columns(4))
    }

    @Test
    fun hidesLockedAlbumsForAggregateBucketsOnly() {
        assertTrue(PickerSources.hidesLockedAlbums(-1L))
        assertTrue(
            PickerSources.hidesLockedAlbums(
                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id
            )
        )
        assertFalse(PickerSources.hidesLockedAlbums(12345L))
    }
}
