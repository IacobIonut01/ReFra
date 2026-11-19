/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.data

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.core.presentation.components.moveToSectionOptions
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.AlbumSectionDao
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.AlbumSectionType
import com.dot.gallery.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.domain.uri.parseUri
import com.dot.gallery.domain.util.AlbumClassifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumSectionMoveTest {

    private lateinit var db: InternalDatabase
    private lateinit var sectionDao: AlbumSectionDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sectionDao = db.getAlbumSectionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun emptySectionIsADestinationAndShowsOnAlbumsPageAfterMove() = runBlocking {
        val commonId = sectionDao.insertSection(
            AlbumSection(label = "Common", type = AlbumSectionType.COMMON.value, displayOrder = 0)
        )
        val appsId = sectionDao.insertSection(
            AlbumSection(label = "Apps", type = AlbumSectionType.APPS.value, displayOrder = 1)
        )
        val otherId = sectionDao.insertSection(
            AlbumSection(label = "Other", type = AlbumSectionType.UNCATEGORIZED.value, displayOrder = 2)
        )
        val maxOrder = sectionDao.getAllSectionsAsync().maxOf { it.displayOrder }
        val testId = sectionDao.insertSection(
            AlbumSection(label = "Test", type = AlbumSectionType.CUSTOM.value, displayOrder = maxOrder + 1)
        )

        val camera = album(1, "Camera", "DCIM/Camera/")
        val whatsapp = album(2, "WhatsApp", "Pictures/WhatsApp/")
        val notes = album(3, "Notes", "Documents/Notes/")
        val albums = listOf(camera, whatsapp, notes)

        suspend fun sectionsWithAlbums(): List<AlbumSectionWithAlbums> = AlbumClassifier.sectionsWithAlbums(
            albums,
            sectionDao.getAllSectionsAsync(),
            sectionDao.getAllSectionMembersAsync(),
        )

        suspend fun optionsFor(page: List<AlbumSectionWithAlbums>) = moveToSectionOptions(
            sectionDao.getAllSectionsAsync(),
            page.associate { it.section.id to it.albums.size },
        )

        val before = sectionsWithAlbums()
        val beforeBySection = before.associate { it.section.id to it.albums.map { a -> a.id } }
        assertEquals(listOf(camera.id), beforeBySection[commonId])
        assertEquals(listOf(whatsapp.id), beforeBySection[appsId])
        assertEquals(listOf(notes.id), beforeBySection[otherId])
        assertTrue(before.none { it.section.id == testId })

        val optionsBefore = optionsFor(before)
        assertEquals(listOf(commonId, appsId, otherId, testId), optionsBefore.map { it.id })
        assertEquals(0, optionsBefore.last().albumCount)
        assertEquals("Test", optionsBefore.last().label)

        sectionDao.removeAlbumFromAllSections(notes.id)
        sectionDao.addAlbumToSection(AlbumSectionMember(sectionId = testId, albumId = notes.id))

        val after = sectionsWithAlbums()
        val testSection = after.first { it.section.id == testId }
        assertEquals(listOf(notes.id), testSection.albums.map { it.id })
        after.filter { it.section.id != testId }.forEach { section ->
            assertTrue(section.albums.none { it.id == notes.id })
        }

        val optionsAfter = optionsFor(after)
        val testOption = optionsAfter.first { it.id == testId }
        assertEquals(1, testOption.albumCount)
    }

    private fun album(id: Long, label: String, relativePath: String) = Album(
        id = id,
        label = label,
        uri = parseUri("content://media/external/images/media/$id"),
        pathToThumbnail = "/storage/emulated/0/$relativePath$id.jpg",
        relativePath = relativePath,
        timestamp = id,
    )
}
