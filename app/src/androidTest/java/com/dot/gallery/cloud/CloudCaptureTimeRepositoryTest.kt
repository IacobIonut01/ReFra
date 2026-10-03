/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.repository.CloudRepositoryImpl
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CloudCaptureTimeRepositoryTest {
    private lateinit var database: InternalDatabase
    private lateinit var dao: CloudMediaDao
    private lateinit var parser: IsolatedMetadataParser
    private lateinit var repository: CloudRepositoryImpl
    private lateinit var directory: File
    private lateinit var image: File
    private val media = CloudMediaEntity(
        remoteId = "image.jpg", providerType = ProviderType.WEBDAV, serverConfigId = 1L,
        label = "image.jpg", mimeType = "image/jpeg", timestamp = 2000L,
        originalUrl = "https://example.test/image.jpg"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build()
        dao = database.getCloudMediaDao()
        parser = IsolatedMetadataParser(context)
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "capture-test-").toFile()
        val testContext = object : ContextWrapper(context) {
            override fun getCacheDir(): File = directory
        }
        repository = CloudRepositoryImpl(
            ProviderRegistry(), dao, database.getCloudTagDao(), ServerUrlResolver(testContext),
            database.getCloudServerConfigDao(), database.getSyncStateDao(), testContext, parser
        )
        image = File(directory, "image.jpg")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(image).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2013:08:21 16:17:28")
            setAttribute(ExifInterface.TAG_DATETIME, "2024:08:15 12:00:00")
            saveAttributes()
        }
    }

    @After
    fun tearDown() {
        parser.unbind()
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun loadedBytesStoreCaptureTimeAndCleanUpTemporaryFile() = runBlocking {
        dao.insert(media)
        repository.recordCaptureTime(media.providerType, 1L, media.remoteId, media.originalUrl, image.readBytes())

        val stored = dao.getByRemoteId(media.remoteId, media.providerType, 1L)!!
        val expected = LocalDateTime.of(2013, 8, 21, 16, 17, 28)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, stored.takenTimestamp)
        assertEquals(media.timestamp, stored.timestamp)
        assertEquals(listOf(image.name), directory.list()!!.toList())
    }

    @Test
    fun cachedOriginalCanBeReadWithoutDeletingIt() = runBlocking {
        dao.insert(media)
        repository.recordCaptureTime(media.providerType, 1L, media.remoteId, media.originalUrl, image)

        assertTrue(dao.getByRemoteId(media.remoteId, media.providerType, 1L)?.takenTimestamp != null)
        assertTrue(image.isFile)
    }

    @Test
    fun serverPreviewAndUnreadableOriginalDoNotInventDates() = runBlocking {
        dao.insert(media)
        repository.recordCaptureTime(media.providerType, 1L, media.remoteId, "https://example.test/preview", image.readBytes())
        assertNull(dao.getByRemoteId(media.remoteId, media.providerType, 1L)?.takenTimestamp)

        repository.recordCaptureTime(media.providerType, 1L, media.remoteId, media.originalUrl, "broken".toByteArray())
        assertNull(dao.getByRemoteId(media.remoteId, media.providerType, 1L)?.takenTimestamp)
        assertEquals(listOf(image.name), directory.list()!!.toList())
    }
}
