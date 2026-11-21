/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.data.dao.CloudLocalCopyLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEmbeddingCarryTest {

    @Test
    fun mediaStoreIdOf_parsesMediaContentUris() {
        assertEquals(42L, mediaStoreIdOf("content://media/external/images/media/42"))
        assertEquals(7L, mediaStoreIdOf("content://media/external_primary/video/media/7?x=1"))
        // Backup revisions append "\u001f<remoteId>" to the local uri.
        assertEquals(
            42L,
            mediaStoreIdOf("content://media/external/images/media/42\u001fScreenshots/a.png")
        )
    }

    @Test
    fun mediaStoreIdOf_rejectsNonMediaUris() {
        assertNull(mediaStoreIdOf("cloud://WEBDAV/a.jpg"))
        assertNull(mediaStoreIdOf(""))
        assertNull(mediaStoreIdOf("/storage/emulated/0/a.jpg"))
    }

    @Test
    fun carryPlan_groupsMissingIdsAndDropsEmbeddedTargets() {
        // Local id 42 is orphaned; its bytes live on cloud -100 (twice: once via
        // localCopyPath, once via a backup revision) and on -200, which already
        // has its own embedding and must not be re-carried.
        val links = listOf(
            CloudLocalCopyLink("content://media/external/images/media/42", -100L, 1_700_000_000_000L),
            CloudLocalCopyLink("content://media/external/images/media/42", -100L, 1_700_000_000_000L),
            CloudLocalCopyLink("content://media/external/images/media/42", -200L, 1_700_000_000_000L)
        )
        val plan = searchEmbeddingCarryPlan(
            missingLocalIds = listOf(42L),
            links = links,
            embeddedIds = setOf(-200L)
        )
        assertEquals(setOf(42L), plan.keys)
        assertEquals(listOf(-100L), plan.getValue(42L).map { it.cloudMediaId })
    }

    @Test
    fun carryPlan_ignoresLocalIdsThatAreNotMissing() {
        val links = listOf(
            CloudLocalCopyLink("content://media/external/images/media/42", -100L, 1L)
        )
        val plan = searchEmbeddingCarryPlan(
            missingLocalIds = listOf(7L),
            links = links,
            embeddedIds = emptySet()
        )
        assertEquals(emptyMap<Long, List<CloudLocalCopyLink>>(), plan)
    }

    @Test
    fun carryPlan_ignoresUnparseableLocalUris() {
        val links = listOf(
            CloudLocalCopyLink("cloud://WEBDAV/a.jpg", -100L, 1L),
            CloudLocalCopyLink("content://media/external/images/media/notanumber", -200L, 1L)
        )
        val plan = searchEmbeddingCarryPlan(
            missingLocalIds = listOf(42L),
            links = links,
            embeddedIds = emptySet()
        )
        assertEquals(emptyMap<Long, List<CloudLocalCopyLink>>(), plan)
    }
}
