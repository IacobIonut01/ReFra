/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.smart.carrySearchEmbeddingsToCloudCopies
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.entity.CloudBackupRevisionEntity
import com.dot.gallery.data.entity.CloudMediaEntity
import com.dot.gallery.domain.model.ImageEmbedding
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1468: when a backed-up local file is deleted, the cloud copy that survives
 * must keep its search vector. The carry runs right before `deleteOrphans`.
 */
@RunWith(AndroidJUnit4::class)
class SearchEmbeddingCarryDaoTest {

    private lateinit var db: InternalDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletedLocalCopyCarriesEmbeddingOntoCloudRow() = runBlocking {
        val entity = CloudMediaEntity(
            remoteId = "remote-42",
            providerType = ProviderType.WEBDAV,
            serverConfigId = 1L,
            label = "remote-42.jpg",
            timestamp = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            localCopyPath = "content://media/external/images/media/42"
        )
        db.getCloudMediaDao().insert(entity)
        val vector = floatArrayOf(0.1f, 0.2f, 0.3f)
        db.getImageEmbeddingDao().addImageEmbedding(
            ImageEmbedding(id = 42L, date = 1L, embedding = vector)
        )

        val carried = carrySearchEmbeddingsToCloudCopies(db)
        assertEquals(1, carried)

        db.getImageEmbeddingDao().deleteOrphans()

        val cloudRow = db.getImageEmbeddingDao().getRecord(entity.globalMediaId)
        assertNotNull(cloudRow)
        assertEquals(1_700_000_000L, cloudRow!!.date)
        assertTrue(cloudRow.embedding.contentEquals(vector))
        assertNull(db.getImageEmbeddingDao().getRecord(42L))
    }

    @Test
    fun deletedLocalCopyCarriesEmbeddingViaBackupRevision() = runBlocking {
        // Path-based providers store revision localUri as "$uri\u001f$remoteId";
        // the media id must be read from the uri part only.
        val entity = CloudMediaEntity(
            remoteId = "Screenshots/b.png",
            providerType = ProviderType.WEBDAV,
            serverConfigId = 1L,
            label = "b.png",
            timestamp = 1_700_000_000_000L,
            mimeType = "image/png"
        )
        db.getCloudMediaDao().insert(entity)
        db.getCloudMediaDao().upsertBackupRevision(
            CloudBackupRevisionEntity(
                serverConfigId = 1L,
                providerType = ProviderType.WEBDAV,
                localUri = "content://media/external/images/media/43\u001fScreenshots/b.png",
                localSize = 100L,
                localTimestamp = 1L,
                remoteId = "Screenshots/b.png",
                remoteFingerprint = "fp",
                verifiedAt = 1L
            )
        )
        val vector = floatArrayOf(0.4f, 0.5f, 0.6f)
        db.getImageEmbeddingDao().addImageEmbedding(
            ImageEmbedding(id = 43L, date = 1L, embedding = vector)
        )

        val carried = carrySearchEmbeddingsToCloudCopies(db)
        assertEquals(1, carried)

        db.getImageEmbeddingDao().deleteOrphans()

        val cloudRow = db.getImageEmbeddingDao().getRecord(entity.globalMediaId)
        assertNotNull(cloudRow)
        assertEquals(1_700_000_000L, cloudRow!!.date)
        assertTrue(cloudRow.embedding.contentEquals(vector))
        assertNull(db.getImageEmbeddingDao().getRecord(43L))
    }
}
