/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.picker.ui

import com.dot.gallery.core.util.MediaStoreBuckets

/** Layout rules for the Albums tab's source tiles. */
object PickerSources {
    /** Virtual album id the favorites media flow is queried with. */
    val FAVORITES_ALBUM_ID: Long = MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id

    /** Sources to show, in display order. */
    fun visible(
        favorites: Boolean,
        privateFolder: Boolean,
        vault: Boolean,
        cloud: Boolean
    ): List<PickerSource> = buildList {
        if (favorites) add(PickerSource.FAVORITES)
        if (privateFolder) add(PickerSource.PRIVATE_FOLDER)
        if (vault) add(PickerSource.VAULT)
        if (cloud) add(PickerSource.CLOUD)
    }

    /** Tiles per row: two columns when all four sources fit, three otherwise. */
    fun columns(count: Int): Int = if (count == 4) 2 else 3

    /**
     * Whether [albumId] is an aggregate view (timeline, favorites) whose items must be
     * filtered against locked albums, as opposed to a single album that is itself lockable.
     */
    fun hidesLockedAlbums(albumId: Long): Boolean =
        albumId == -1L || albumId == FAVORITES_ALBUM_ID
}
