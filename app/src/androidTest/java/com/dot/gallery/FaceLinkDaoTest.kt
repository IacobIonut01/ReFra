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
import com.dot.gallery.cloud.data.entity.FaceLinkEntity
import com.dot.gallery.cloud.data.entity.FaceLinkKind
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceLinkDaoTest {
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

    private suspend fun insertPerson(id: String) =
        personDao.insert(PersonEntity(id, "", ProviderType.LOCAL_PEOPLE))

    private fun link(
        mediaId: Long,
        personId: String,
        kind: FaceLinkKind,
        left: Float = 10f, top: Float = 10f, right: Float = 20f, bottom: Float = 20f
    ) = FaceLinkEntity(
        mediaId = mediaId, left = left, top = top, right = right, bottom = bottom,
        personId = personId, kind = kind, createdAt = 1L
    )

    // The same (mediaId, box) face excluded under both merge sides — e.g. the user
    // marked A-vs-C and B-vs-C "different people", then merged A into B. A plain
    // UPDATE violated the unique index and crashed the merge (issue #1228).
    @Test
    fun reassignCollapsesDuplicateExcludeAssertions() = runBlocking {
        insertPerson("personA")
        insertPerson("personB")
        insertPerson("personC")
        faceDao.upsertLinks(listOf(link(7L, "personA", FaceLinkKind.EXCLUDE)))
        faceDao.upsertLinks(listOf(link(7L, "personB", FaceLinkKind.EXCLUDE)))

        faceDao.reassignLinks("personA", "personB", FaceLinkKind.EXCLUDE)

        val links = faceDao.getLinks()
        assertEquals(1, links.size)
        assertEquals("personB", links.single().personId)
        assertEquals(FaceLinkKind.EXCLUDE, links.single().kind)
    }

    @Test
    fun reassignCollapsesDuplicateIncludeAssertions() = runBlocking {
        insertPerson("personA")
        insertPerson("personB")
        faceDao.upsertLinks(listOf(link(7L, "personA", FaceLinkKind.INCLUDE)))
        faceDao.upsertLinks(listOf(link(7L, "personB", FaceLinkKind.INCLUDE)))

        faceDao.reassignLinks("personA", "personB", FaceLinkKind.INCLUDE)

        val links = faceDao.getLinks()
        assertEquals(1, links.size)
        assertEquals("personB", links.single().personId)
        assertEquals(FaceLinkKind.INCLUDE, links.single().kind)
    }

    @Test
    fun reassignKeepsNonDuplicateAssertions() = runBlocking {
        insertPerson("personA")
        insertPerson("personB")
        faceDao.upsertLinks(
            listOf(
                link(7L, "personA", FaceLinkKind.EXCLUDE),
                link(8L, "personA", FaceLinkKind.INCLUDE),
                link(9L, "personB", FaceLinkKind.EXCLUDE)
            )
        )

        faceDao.reassignLinks("personA", "personB", FaceLinkKind.EXCLUDE)
        faceDao.reassignLinks("personA", "personB", FaceLinkKind.INCLUDE)

        val links = faceDao.getLinks()
        assertEquals(3, links.size)
        assertEquals(setOf("personB"), links.mapTo(hashSetOf()) { it.personId })
    }

    // The mergePeople statement order on the reported collision state: C's face is
    // excluded under both A and B, A owns a face, then A merges into B.
    @Test
    fun mergeSequenceOnSharedRejectionLeavesConsistentState() = runBlocking {
        insertPerson("personA")
        insertPerson("personB")
        insertPerson("personC")
        faceDao.insert(
            DetectedFaceEntity(
                mediaId = 7L, personId = "personA",
                left = 1f, top = 1f, right = 5f, bottom = 5f
            )
        )
        faceDao.insert(
            DetectedFaceEntity(
                mediaId = 9L, personId = "personC",
                left = 10f, top = 10f, right = 20f, bottom = 20f
            )
        )
        // rejectPersonPair(A, C) and rejectPersonPair(B, C) both asserted C's face.
        faceDao.upsertLinks(
            listOf(
                link(9L, "personA", FaceLinkKind.EXCLUDE),
                link(9L, "personB", FaceLinkKind.EXCLUDE)
            )
        )

        // mergePeople(A -> B): the pairwise retraction finds nothing (the excluded
        // face belongs to C), then both kinds are carried onto the target.
        faceDao.reassignLinks("personA", "personB", FaceLinkKind.INCLUDE)
        faceDao.reassignLinks("personA", "personB", FaceLinkKind.EXCLUDE)
        faceDao.upsertLinks(
            listOf(
                link(
                    7L, "personB", FaceLinkKind.INCLUDE,
                    left = 1f, top = 1f, right = 5f, bottom = 5f
                )
            )
        )
        faceDao.reassignPerson("personA", "personB")
        personDao.deleteById("personA")

        val links = faceDao.getLinks()
        assertEquals(2, links.size)
        assertEquals(setOf("personB"), links.mapTo(hashSetOf()) { it.personId })
        // Exactly one EXCLUDE row for C's face survived the collapse.
        assertEquals(1, links.count { it.kind == FaceLinkKind.EXCLUDE && it.mediaId == 9L })
        assertEquals("personB", faceDao.getByMedia(7L).single().personId)
        assertNull(personDao.getById("personA"))
    }
}
