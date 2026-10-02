/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.DetectedFaceDao
import com.dot.gallery.cloud.data.dao.PersonDao
import com.dot.gallery.cloud.data.entity.DetectedFaceEntity
import com.dot.gallery.cloud.data.entity.FaceSuppressionEntity
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import kotlinx.coroutines.flow.first
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
 * Covers the `face_suppressions` table written by `LocalPeopleProvider.deletePerson`
 * (#1262): rows are keyed by media + box with no person FK, so they must survive the
 * person delete they were created by, and media-scoped orphan cleanup mirrors
 * `deleteOrphanLinks`/`deleteOrphanExclusions`.
 */
@RunWith(AndroidJUnit4::class)
class FaceSuppressionDaoTest {
    private lateinit var db: InternalDatabase
    private lateinit var faceDao: DetectedFaceDao
    private lateinit var personDao: PersonDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        faceDao = db.getDetectedFaceDao()
        personDao = db.getPersonDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertPerson(id: String, hidden: Boolean = false) {
        personDao.insert(PersonEntity(id, "", ProviderType.LOCAL_PEOPLE))
        if (hidden) personDao.setHidden(id, true)
    }

    private fun insertMediaRow(id: Long) {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO media (
                id, label, uri, path, relativePath, albumID, albumLabel,
                timestamp, fullDate, mimeType, favorite, trashed, size
            ) VALUES (?, 'img.jpg', 'content://media/external/$id', '/x/img.jpg',
                      'x/', 1, 'Album', 100, 'date', 'image/jpeg', 0, 0, 1)
            """.trimIndent(),
            arrayOf(id)
        )
    }

    private fun suppression(
        mediaId: Long,
        left: Float = 10f, top: Float = 10f, right: Float = 20f, bottom: Float = 20f,
        createdAt: Long = 1L
    ) = FaceSuppressionEntity(
        mediaId = mediaId, left = left, top = top, right = right, bottom = bottom,
        createdAt = createdAt
    )

    // The delete sequence from LocalPeopleProvider.deletePerson: suppressions are
    // written first, then the person row goes. Suppressions have no person FK on
    // purpose — they must outlive the delete to block re-clustering.
    @Test
    fun deletingPersonLeavesSuppressionsBehind() = runBlocking {
        insertPerson("personA")
        faceDao.insert(
            DetectedFaceEntity(
                mediaId = 7L, personId = "personA",
                left = 10f, top = 10f, right = 20f, bottom = 20f
            )
        )
        faceDao.insertSuppressions(listOf(suppression(7L)))

        personDao.deleteById("personA")

        assertEquals(1, faceDao.getSuppressions().size)
        assertEquals(7L, faceDao.getSuppressions().single().mediaId)
        // The face row itself survives with a NULL person link (SET_NULL FK).
        assertNull(faceDao.getByMedia(7L).single().personId)
        assertNull(personDao.getById("personA"))
    }

    @Test
    fun reinsertReplacesExistingSuppression() = runBlocking {
        faceDao.insertSuppressions(listOf(suppression(7L, createdAt = 1L)))
        faceDao.insertSuppressions(listOf(suppression(7L, createdAt = 2L)))

        assertEquals(
            listOf(suppression(7L, createdAt = 2L)),
            faceDao.getSuppressions()
        )
    }

    @Test
    fun orphanSuppressionsAreCleanedUpWithMedia() = runBlocking {
        insertMediaRow(7L)
        faceDao.insertSuppressions(
            listOf(
                suppression(7L),
                suppression(9L) // media 9 does not exist
            )
        )

        assertEquals(1, faceDao.deleteOrphanSuppressions())
        assertEquals(listOf(7L), faceDao.getSuppressions().map { it.mediaId })
    }

    @Test
    fun hiddenPeopleKeepSectionCountAndResolveIndividually() = runBlocking {
        insertPerson("personA")
        insertPerson("personB", hidden = true)
        insertPerson("personC", hidden = true)

        // The Library People section reads this flow to stay reachable (#1262).
        assertEquals(2, personDao.observeHiddenCount().first())
        // Hidden rows are excluded from the visible list but resolve by id.
        assertEquals(
            listOf("personA"),
            personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE).first()
                .map { it.id }
        )
        assertNotNull(personDao.observeById("personB").first())
        assertTrue(personDao.observeById("personB").first()!!.hidden)
    }

    @Test
    fun unhideReturnsPersonToVisibleList() = runBlocking {
        insertPerson("personA", hidden = true)

        assertEquals(1, personDao.observeHiddenCount().first())
        assertTrue(
            personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE).first().isEmpty()
        )

        personDao.setHidden("personA", false)

        assertEquals(0, personDao.observeHiddenCount().first())
        assertEquals(
            listOf("personA"),
            personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE).first()
                .map { it.id }
        )
    }
}
