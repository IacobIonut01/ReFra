package com.dot.gallery.feature_node.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.dot.gallery.core.metadata.AndroidMetadataSanitizer
import com.dot.gallery.core.presentation.components.util.hasMediaAccess
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.core.smart.SmartScanProcessorRegistry
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.core.startup.StartupMediaCache
import com.dot.gallery.core.startup.StartupWorkGate
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature_node.data.data_source.AlbumThumbnailDao
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.data.repository.MediaRepositoryImpl
import com.dot.gallery.feature_node.domain.model.AlbumThumbnail
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #1247: a pinned album cover whose media is deleted must not keep rendering a
 * dangling URI. [MediaRepositoryImpl.getAlbumThumbnails] re-validates stored covers on
 * every MediaStore change, skips dead ones and prunes their rows.
 */
@RunWith(AndroidJUnit4::class)
class AlbumThumbnailPruningTest {

    private val albumId = 424242L

    private lateinit var context: Context
    private lateinit var db: InternalDatabase
    private lateinit var dao: AlbumThumbnailDao
    private lateinit var repository: MediaRepositoryImpl
    private val resolver get() = context.contentResolver
    private val insertedUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        // connectedAndroidTest reinstalls both APKs, so runtime grants must be
        // re-applied per run.
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.READ_MEDIA_IMAGES
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.READ_MEDIA_VIDEO
        )
        assumeTrue("media permission required", context.hasMediaAccess())
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.getAlbumThumbnailDao()
        val workManager = WorkManager.getInstance(context)
        repository = MediaRepositoryImpl(
            context = context,
            workManager = workManager,
            database = db,
            keychainHolder = KeychainHolder(context),
            geocoder = null,
            isolatedParser = IsolatedMetadataParser(context),
            metadataSanitizer = AndroidMetadataSanitizer(context),
            smartScanScheduler = SmartScanScheduler(
                workManager,
                db.getSmartScanDao(),
                db,
                Provider { SmartScanProcessorRegistry(emptySet()) },
                context
            ),
            startupCache = StartupMediaCache(context),
            startupGate = StartupWorkGate()
        )
    }

    @After
    fun tearDown() {
        insertedUris.forEach { uri ->
            runCatching {
                resolver.delete(uri, null, null)
            }
        }
        if (::db.isInitialized) db.close()
    }

    @Test
    fun liveCoverIsReturned() = runBlocking<Unit> {
        val uri = insertTestImage("cover_alive.jpg")
        dao.updateAlbumThumbnail(AlbumThumbnail(albumId, uri))

        assertEquals(
            listOf(AlbumThumbnail(albumId, uri)),
            repository.getAlbumThumbnails().first()
        )
    }

    @Test
    fun deletedCoverIsSkippedAndRowPruned() = runBlocking<Unit> {
        val uri = insertTestImage("cover_gone.jpg")
        dao.updateAlbumThumbnail(AlbumThumbnail(albumId, uri))
        assertEquals(1, repository.getAlbumThumbnails().first().size)

        resolver.delete(uri, null, null)
        insertedUris.remove(uri)

        val thumbnails = withTimeout(10_000) {
            repository.getAlbumThumbnails().first { it.isEmpty() }
        }
        assertTrue(thumbnails.isEmpty())
        assertFalse(dao.hasAlbumThumbnail(albumId).first())
    }

    @Test
    fun deletionDuringCollectionEmitsPrunedList() = runBlocking<Unit> {
        val uri = insertTestImage("cover_pulse.jpg")
        dao.updateAlbumThumbnail(AlbumThumbnail(albumId, uri))

        // One shared collection: the delete below must reach this active collector
        // through the MediaStore observer pulse, not a fresh subscription. The scope
        // is detached from runBlocking so the infinite flow doesn't hang the test.
        val scope = CoroutineScope(coroutineContext + Job())
        val shared = repository.getAlbumThumbnails()
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)

        try {
            val alive = withTimeout(10_000) { shared.first { it.isNotEmpty() } }
            assertEquals(listOf(AlbumThumbnail(albumId, uri)), alive)

            resolver.delete(uri, null, null)
            insertedUris.remove(uri)

            val pruned = withTimeout(10_000) { shared.first { it.isEmpty() } }
            assertTrue(pruned.isEmpty())
            assertFalse(dao.hasAlbumThumbnail(albumId).first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun trashedCoverIsSkippedButRowKept() = runBlocking<Unit> {
        assumeTrue("trash unsupported on this SDK", SdkCompat.supportsTrash)
        val uri = insertTestImage("cover_trash.jpg")
        dao.updateAlbumThumbnail(AlbumThumbnail(albumId, uri))

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 1)
        }
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        resolver.update(uri, values, args)

        assertTrue(repository.getAlbumThumbnails().first().isEmpty())
        assertTrue(dao.hasAlbumThumbnail(albumId).first())

        // Restore so tearDown can hard-delete the file.
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) },
            args
        )
    }

    private fun insertTestImage(displayName: String): Uri {
        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ReFraCoverTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { out ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
                compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } ?: error("openOutputStream failed")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        insertedUris += uri
        return uri
    }
}
