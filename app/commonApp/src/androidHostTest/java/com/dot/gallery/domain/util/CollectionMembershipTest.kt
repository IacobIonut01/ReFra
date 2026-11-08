/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionMembershipTest {

    @Test
    fun newAlbumPhotoIsAMemberBeforeItIsSnapshotted() {
        assertTrue(
            isCollectionMember(
                mediaId = 9L,
                albumId = -525230641L,
                explicitMediaIds = setOf(1L, 2L),
                memberAlbumIds = setOf(-525230641L),
                excludedMediaIds = emptySet(),
            )
        )
    }

    @Test
    fun removedPhotoStaysOut() {
        assertFalse(
            isCollectionMember(
                mediaId = 9L,
                albumId = -525230641L,
                explicitMediaIds = setOf(9L),
                memberAlbumIds = setOf(-525230641L),
                excludedMediaIds = setOf(9L),
            )
        )
    }

    @Test
    fun explicitPhotoStaysWhenItsAlbumIsNotAMember() {
        assertTrue(
            isCollectionMember(
                mediaId = 4L,
                albumId = 99L,
                explicitMediaIds = setOf(4L),
                memberAlbumIds = setOf(-525230641L),
                excludedMediaIds = emptySet(),
            )
        )
    }

    @Test
    fun unrelatedPhotoStaysOut() {
        assertFalse(
            isCollectionMember(
                mediaId = 8L,
                albumId = 99L,
                explicitMediaIds = setOf(1L),
                memberAlbumIds = setOf(-525230641L),
                excludedMediaIds = emptySet(),
            )
        )
    }
}
