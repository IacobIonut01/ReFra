/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.classifier

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.feature_node.data.data_source.CategoryDao
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.MediaDao
import com.dot.gallery.feature_node.domain.model.Category
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaCategory
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the #1076 category-integrity contract:
 *  - counts and covers ignore memberships whose media no longer exists in the internal mirror;
 *  - deleting the current cover promotes the next highest-scored *existing* member;
 *  - deleting every member drops the category from the populated list;
 *  - the orphan cleanup query removes stale automatic rows without losing manual assignments.
 */
@RunWith(AndroidJUnit4::class)
class CategoryIntegrityDaoTest {

    private lateinit var db: InternalDatabase
    private lateinit var categoryDao: CategoryDao
    private lateinit var mediaDao: MediaDao

    private val vaultUuid: UUID = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryDao = db.getCategoryDao()
        mediaDao = db.getMediaDao()
    }

    @After
    fun tearDown() = db.close()

    private fun media(id: Long) = UriMedia(
        id = id,
        label = "media_$id",
        uri = Uri.parse("content://media/external/file/$id"),
        path = "/storage/emulated/0/DCIM/media_$id.jpg",
        relativePath = "DCIM/",
        albumID = 1L,
        albumLabel = "DCIM",
        timestamp = id,
        fullDate = "2026-01-01",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_000L
    )

    private fun mirror(vararg ids: Long) = runBlocking {
        mediaDao.updateMedia(ids.map { media(it) })
    }

    private fun vault(vararg ids: Long) = runBlocking {
        ids.forEach { id ->
            db.getVaultDao().addMediaToVault(
                Media.EncryptedMedia2(
                    id = id,
                    label = "vault_$id",
                    uuid = vaultUuid,
                    path = "/vault/$id.enc",
                    relativePath = "vault/",
                    albumID = -1L,
                    albumLabel = "vault",
                    timestamp = id,
                    fullDate = "2026-01-01",
                    mimeType = "image/jpeg",
                    favorite = 0,
                    trashed = 0,
                    size = 1_000L
                )
            )
        }
    }

    private fun membership(mediaId: Long, categoryId: Long, score: Float, isManual: Boolean = false) =
        MediaCategory(
            mediaId = mediaId,
            categoryId = categoryId,
            similarityScore = score,
            isManuallyAdded = isManual
        )

    @Test
    fun updateCategoryEmbedding_persistsConvertedVector() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = "art"))
        val embedding = floatArrayOf(-1.25f, 0f, 0.5f, 42.75f)

        categoryDao.updateCategoryEmbedding(categoryId, embedding)

        assertArrayEquals(embedding, categoryDao.getCategoryById(categoryId)?.embedding, 0f)
    }

    @Test
    fun countAndCover_ignoreMediaMissingFromMirror() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        // Membership for 3 media, but only 10 and 30 still exist in the mirror.
        categoryDao.insertMediaCategories(
            listOf(
                membership(10, categoryId, 0.5f),
                membership(20, categoryId, 0.9f), // highest score but deleted
                membership(30, categoryId, 0.7f)
            )
        )
        mirror(10, 30)

        val result = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals(1, result.size)
        assertEquals("stale members must not be counted", 2, result[0].mediaCount)
        assertEquals("cover must be the top-scored existing media", 30L, result[0].thumbnailMediaId)
    }

    @Test
    fun deletingCurrentCover_promotesNextValidMember() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategories(
            listOf(
                membership(10, categoryId, 0.9f),
                membership(20, categoryId, 0.6f),
                membership(30, categoryId, 0.3f)
            )
        )
        mirror(10, 20, 30)
        assertEquals(10L, categoryDao.getCategoriesWithMediaCount().first()[0].thumbnailMediaId)

        // User deletes the current cover (id 10); mirror re-syncs without it.
        mirror(20, 30)
        val afterDelete = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals(2, afterDelete[0].mediaCount)
        assertEquals(20L, afterDelete[0].thumbnailMediaId)
    }

    @Test
    fun deletingAllMembers_dropsCategoryFromPopulatedList() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategories(
            listOf(membership(10, categoryId, 0.9f), membership(20, categoryId, 0.6f))
        )
        mirror(99) // none of the members exist anymore
        assertTrue(categoryDao.getCategoriesWithMediaCount().first().isEmpty())
    }

    @Test
    fun deletingLastMirroredMedia_dropsCategoryFromPopulatedList() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategory(membership(10, categoryId, 0.9f))
        mirror(10)

        mirror()
        assertTrue(categoryDao.getCategoriesWithMediaCount().first().isEmpty())

        mirror(10)
        assertEquals(10L, categoryDao.getCategoriesWithMediaCount().first()[0].thumbnailMediaId)
    }

    @Test
    fun cleanup_removesOnlyOrphanedMemberships() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategories(
            listOf(
                membership(10, categoryId, 0.9f),
                membership(20, categoryId, 0.6f),
                membership(30, categoryId, 0.3f)
            )
        )
        mirror(10, 30)

        val removed = categoryDao.cleanupCategoriesForDeletedMedia()
        assertEquals(1, removed)
        assertEquals(2, categoryDao.getMediaCountInCategoryAsync(categoryId))
        assertNull(categoryDao.getSimilarityScore(20, categoryId))
    }

    @Test
    fun cleanup_preservesManualMembershipForRestoredMedia() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategory(membership(10, categoryId, 1f, isManual = true))
        mirror(99)

        assertEquals(0, categoryDao.cleanupCategoriesForDeletedMedia())

        mirror(10)
        val restored = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals(1, restored.size)
        assertEquals(10L, restored[0].thumbnailMediaId)
    }

    @Test
    fun mirroredMediaCount_reflectsMirrorState() = runBlocking {
        assertEquals(0, categoryDao.getMirroredMediaCount())
        mirror(1, 2, 3)
        assertEquals(3, categoryDao.getMirroredMediaCount())
    }

    /**
     * #1106: memberships survive vaulting (so un-vaulting restores categorisation), but a
     * vaulted item must never count toward or surface in a category — even when its `media`
     * mirror row is still present (e.g. mid-sync) or a `cloud_media` row remains.
     */
    @Test
    fun vaultedMembers_areNotCountedOrListed() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategories(
            listOf(
                membership(10, categoryId, 0.9f),
                membership(20, categoryId, 0.8f), // vaulted, mirror row still present
                membership(30, categoryId, 0.7f)
            )
        )
        mirror(10, 20, 30)
        vault(20)

        val result = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals("vaulted member must not count", 2, result[0].mediaCount)
        assertEquals("vaulted member must not be the cover", 10L, result[0].thumbnailMediaId)

        val ids = categoryDao.getMediaIdsInCategoryAsync(categoryId)
        assertEquals("vaulted member must not be listed", listOf(10L, 30L), ids.sorted())
        assertEquals(2, categoryDao.getMediaCountInCategoryAsync(categoryId))
    }

    @Test
    fun unvaultedMember_countsAndListsAgain() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        categoryDao.insertMediaCategories(
            listOf(
                membership(10, categoryId, 0.9f),
                membership(20, categoryId, 0.8f)
            )
        )
        mirror(10, 20)
        vault(20)
        assertEquals(1, categoryDao.getCategoriesWithMediaCount().first()[0].mediaCount)

        db.getVaultDao().deleteMediaFromVault(vaultUuid, 20)
        val restored = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals("membership must restore on un-vault", 2, restored[0].mediaCount)
    }

    @Test
    fun mediaMirroredInBothTables_countsOnce() = runBlocking {
        val categoryId = categoryDao.insertCategory(Category(name = "Art", searchTerms = ""))
        // A synced item can live in `media` (local copy) and `cloud_media` under the same
        // globalMediaId — UNION must dedup it or the count doubles (#1106).
        val sharedId = com.dot.gallery.cloud.core.cloudMediaId(
            com.dot.gallery.cloud.core.ProviderType.IMMICH, 7L, "remote_shared"
        )
        categoryDao.insertMediaCategories(listOf(membership(sharedId, categoryId, 0.9f)))
        mirror(sharedId)
        db.getCloudMediaDao().insert(
            com.dot.gallery.cloud.data.entity.CloudMediaEntity(
                remoteId = "remote_shared",
                providerType = com.dot.gallery.cloud.core.ProviderType.IMMICH,
                serverConfigId = 7L,
                label = "cloud_shared.jpg",
                mimeType = "image/jpeg",
                timestamp = sharedId
            )
        )

        val result = categoryDao.getCategoriesWithMediaCount().first()
        assertEquals(1, result[0].mediaCount)
    }
}
