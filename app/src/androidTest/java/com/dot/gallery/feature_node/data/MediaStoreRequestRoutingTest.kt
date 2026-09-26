package com.dot.gallery.feature_node.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
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
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.data.repository.MediaRepositoryImpl
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #1263: on API 30+ supported media must always route through the
 * MediaStore request APIs (createTrashRequest / createDeleteRequest /
 * createFavoriteRequest), even when all-files access is granted — the grant
 * does not guarantee ContentResolver write access on every platform, and the
 * request performs the mutation itself on approval.
 *
 * Each test asserts the request was captured by the fake launcher AND that no
 * direct write happened (the row's state is unchanged), which is what
 * distinguishes the fixed routing from the old direct-write shortcut. Since the
 * direct path no longer depends on the all-files grant, the assertions hold
 * whether or not MANAGE_EXTERNAL_STORAGE is allowed for the test package.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreRequestRoutingTest {

    private lateinit var context: Context
    private lateinit var db: InternalDatabase
    private lateinit var repository: MediaRepositoryImpl
    private val resolver get() = context.contentResolver
    private val insertedUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
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
        val untrash = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        insertedUris.forEach { uri ->
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) },
                    untrash
                )
            }
            runCatching {
                resolver.delete(uri, null, null)
            }
        }
        if (::db.isInitialized) db.close()
    }

    @Test
    fun trashLaunchesRequestWithoutDirectWrite() = runBlocking<Unit> {
        assumeTrue("request APIs unsupported", SdkCompat.supportsMediaStoreRequests)
        val uri = insertTestImage("route_trash.jpg")
        val media = uriMedia(uri, "route_trash.jpg", "image/jpeg")
        val launcher = CapturingLauncher()

        val result = repository.trashMedia(launcher, listOf(media), true)

        assertEquals(MediaMutationResult.REQUEST_LAUNCHED, result)
        assertEquals(1, launcher.requests.size)
        assertNotNull(launcher.requests[0].intentSender)
        // A direct write would have set IS_TRASHED immediately.
        assertEquals(0, readIntColumn(uri, MediaStore.MediaColumns.IS_TRASHED))
    }

    @Test
    fun restoreLaunchesRequestWithoutDirectWrite() = runBlocking<Unit> {
        assumeTrue("request APIs unsupported", SdkCompat.supportsMediaStoreRequests)
        val uri = insertTestImage("route_restore.jpg")
        setTrashed(uri, 1)
        val media = uriMedia(uri, "route_restore.jpg", "image/jpeg", trashed = 1)
        val launcher = CapturingLauncher()

        val result = repository.trashMedia(launcher, listOf(media), false)

        assertEquals(MediaMutationResult.REQUEST_LAUNCHED, result)
        assertEquals(1, launcher.requests.size)
        // A direct write would have restored the row immediately.
        assertEquals(1, readIntColumn(uri, MediaStore.MediaColumns.IS_TRASHED))
    }

    @Test
    fun deleteLaunchesRequestWithoutDirectWrite() = runBlocking<Unit> {
        assumeTrue("request APIs unsupported", SdkCompat.supportsMediaStoreRequests)
        val uri = insertTestImage("route_delete.jpg")
        val media = uriMedia(uri, "route_delete.jpg", "image/jpeg")
        val launcher = CapturingLauncher()

        val result = repository.deleteMedia(launcher, listOf(media))

        assertEquals(MediaMutationResult.REQUEST_LAUNCHED, result)
        assertEquals(1, launcher.requests.size)
        // A direct write would have deleted the row immediately.
        assertTrue(rowExists(uri))
    }

    @Test
    fun favoriteLaunchesRequestWithoutDirectWrite() = runBlocking<Unit> {
        assumeTrue("request APIs unsupported", SdkCompat.supportsMediaStoreRequests)
        val uri = insertTestImage("route_favorite.jpg")
        val media = uriMedia(uri, "route_favorite.jpg", "image/jpeg")
        val launcher = CapturingLauncher()

        repository.toggleFavorite(launcher, listOf(media), true)

        assertEquals(1, launcher.requests.size)
        assertEquals(0, readIntColumn(uri, MediaStore.MediaColumns.IS_FAVORITE))
    }

    @Test
    fun filesCollectionTrashFailsWithoutAllFiles() = runBlocking<Unit> {
        assumeTrue("request APIs unsupported", SdkCompat.supportsMediaStoreRequests)
        assumeFalse(
            "direct path is exercised instead when all-files access is granted",
            SdkCompat.hasFullFileAccess
        )
        val uri = insertTestFile("route_jxl.jxl")
        val media = uriMedia(uri, "route_jxl.jxl", "image/jxl")
        val launcher = CapturingLauncher()

        val result = repository.trashMedia(launcher, listOf(media), true)

        // Files-collection URIs are rejected by the request APIs, so without
        // all-files access the operation must fail rather than launch a
        // request that MediaStore cannot serve.
        assertEquals(MediaMutationResult.FAILED, result)
        assertTrue(launcher.requests.isEmpty())
    }

    private class CapturingLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        val requests = mutableListOf<IntentSenderRequest>()

        override val contract: ActivityResultContract<IntentSenderRequest, *> =
            ActivityResultContracts.StartIntentSenderForResult()

        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) {
            requests += input
        }

        override fun unregister() = Unit
    }

    private fun uriMedia(
        uri: Uri,
        label: String,
        mimeType: String,
        trashed: Int = 0
    ) = Media.UriMedia(
        id = ContentUris.parseId(uri),
        label = label,
        uri = uri,
        path = uri.path.orEmpty(),
        relativePath = uri.path.orEmpty().substringBeforeLast("/"),
        albumID = -99L,
        albumLabel = "",
        timestamp = System.currentTimeMillis() / 1000,
        fullDate = "",
        mimeType = mimeType,
        favorite = 0,
        trashed = trashed,
        size = 0
    )

    private fun setTrashed(uri: Uri, trashed: Int) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, trashed) },
            args
        )
    }

    private fun readIntColumn(uri: Uri, column: String): Int {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        resolver.query(uri, arrayOf(column), args, null)?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return -1
    }

    private fun rowExists(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null)
            ?.use { it.moveToFirst() } == true

    private fun insertTestImage(displayName: String): Uri {
        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ReFraRequestTest")
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

    private fun insertTestFile(displayName: String): Uri {
        val collection =
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jxl")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ReFraRequestTest")
        }
        val inserted = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        resolver.openOutputStream(inserted)?.use { it.write(byteArrayOf(0, 1, 2, 3)) }
        // The canonical item URI lives in the Files collection regardless of
        // the collection URI used at insert time.
        val itemUri = ContentUris.withAppendedId(collection, ContentUris.parseId(inserted))
        insertedUris += itemUri
        return itemUri
    }
}
