/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.cloud.immich.ImmichProvider
import com.dot.gallery.cloud.immich.data.api.ImmichAuthInterceptor
import com.dot.gallery.cloud.sync.CloudDownloadWorker
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import okio.Buffer
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64

/**
 * End-to-end download regression for #1270, run against real on-device pieces:
 * a real [ImmichProvider] (MockWebServer or the seeded test lab), a real
 * in-memory Room database, the real [CloudDownloadWorker] via
 * [TestListenableWorkerBuilder], and real MediaStore writes. Asserts that an
 * asset whose server `originalPath` is an internal shard tree still lands in a
 * user folder, that identical local content is linked instead of re-downloaded,
 * and that `appLocalCopy` tells the two apart.
 *
 * The live-lab case is skipped unless the ReFra test lab's Immich answers —
 * run `testing/test-lab.sh up`, `testing/test-lab.sh seed-cloud`, and
 * `adb reverse tcp:2283 tcp:2283` first (some emulator images don't route
 * `10.0.2.2` for app sockets, so the adb tunnel is the reliable path).
 */
@RunWith(AndroidJUnit4::class)
class CloudDownloadWorkerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var db: InternalDatabase
    private lateinit var mediaDao: CloudMediaDao
    private lateinit var configDao: CloudServerConfigDao
    private lateinit var registry: ProviderRegistry
    private lateinit var provider: ImmichProvider
    private lateinit var providerConfig: CloudServerConfig

    private var configId = -1L
    private var previousClient: OkHttpClient? = null
    private val configIds = mutableListOf<Long>()
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mediaDao = db.getCloudMediaDao()
        configDao = db.getCloudServerConfigDao()
        configId = insertConfig(displayName = "Lab Immich", serverUrl = baseUrl())
        registry = ProviderRegistry()
        provider = ImmichProvider(context, ImmichAuthInterceptor(), mediaDao)
        providerConfig = providerDomainConfig(configId, baseUrl(), apiKey = "KEY")
        provider.configure(providerConfig)
        registry.register(configId, provider)
        previousClient = CloudFetcherRegistryHolder.okHttpClient
        CloudFetcherRegistryHolder.okHttpClient = OkHttpClient()
    }

    @After
    fun tearDown() {
        // Delete every copy the worker produced plus locally created fixtures —
        // same cleanup "Remove downloaded copies" performs.
        runCatching {
            runBlocking {
                configIds.forEach { id ->
                    mediaDao.getLocalCopyStatesForConfig(id).forEach {
                        runCatching {
                            context.contentResolver.delete(it.localCopyPath.toUri(), null, null)
                        }
                    }
                }
            }
        }
        createdUris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        CloudFetcherRegistryHolder.okHttpClient = previousClient
        runCatching { db.close() }
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    private fun providerDomainConfig(
        id: Long,
        serverUrl: String,
        apiKey: String? = null,
        username: String? = null,
        password: String? = null
    ) = CloudServerConfig(
        id = id,
        providerType = ProviderType.IMMICH,
        serverUrl = serverUrl,
        apiKey = apiKey,
        username = username,
        password = password,
        displayName = "Lab Immich"
    )

    private suspend fun insertConfig(
        displayName: String,
        serverUrl: String,
        username: String? = null,
        password: String? = null,
        apiKey: String? = "KEY"
    ): Long = configDao.insert(
        CloudServerConfigEntity(
            providerType = ProviderType.IMMICH,
            serverUrl = serverUrl,
            apiKey = apiKey,
            username = username,
            encryptedPassword = password,
            displayName = displayName,
            isActive = true,
            syncEnabled = true,
            downloadRemoteEnabled = true,
            downloadVideos = true
        )
    ).also { configIds += it }

    private fun dispatcher(block: (RecordedRequest) -> MockResponse?) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            block(request) ?: MockResponse().setResponseCode(404)
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(body)

    private fun authResponses(req: RecordedRequest): MockResponse? = when {
        req.path?.endsWith("/api/auth/validateToken") == true ->
            json("""{ "authStatus": true }""")
        req.path?.endsWith("/api/users/me") == true ->
            json("""{ "id": "u1", "email": "g@x", "isAdmin": false }""")
        else -> null
    }

    private fun sha1Base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(bytes))

    private fun remoteEntity(
        remoteId: String,
        label: String,
        size: Long,
        contentHash: String?,
        ownerConfigId: Long = configId
    ) = CloudMediaEntity(
        remoteId = remoteId,
        providerType = ProviderType.IMMICH,
        serverConfigId = ownerConfigId,
        label = label,
        // The exact kind of path Immich hands out — the server's internal
        // asset-store layout, never a user folder.
        path = "/photos/upload/e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01/ed/4f/$label",
        relativePath = "",
        mimeType = "image/jpeg",
        timestamp = 1_726_000_000_000,
        size = size,
        contentHash = contentHash,
        syncState = SyncState.REMOTE_ONLY
    )

    private fun newWorker(id: Long): CloudDownloadWorker =
        TestListenableWorkerBuilder<CloudDownloadWorker>(
            context,
            workDataOf(
                CloudDownloadWorker.KEY_MANUAL to true,
                CloudDownloadWorker.KEY_CONFIG_ID to id
            )
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = CloudDownloadWorker(
                appContext, workerParameters, registry, configDao, mediaDao
            )
        }).build()

    private fun relativePathOf(uri: Uri): String =
        context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null
        )!!.use { c ->
            assertTrue(c.moveToFirst())
            c.getString(0)
        }

    private suspend fun createLocalImage(fileName: String, bytes: ByteArray): Uri {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ReFraTestLab-Worker/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        context.contentResolver.update(
            uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null, null
        )
        createdUris += uri
        return uri
    }

    @Test
    fun workerDownloadsIntoAccountAlbumNotShardTree() = runBlocking {
        val payload = ByteArray(512) { (it % 251).toByte() }
        var originalHits = 0
        server.dispatcher = dispatcher { req ->
            authResponses(req) ?: when {
                req.path?.startsWith("/api/albums") == true &&
                        req.requestUrl?.queryParameter("assetId") == "asset-1" ->
                    json("""[ { "id": "al-1", "albumName": "Lab Trip", "assetCount": 1, "shared": false } ]""")
                req.path == "/api/assets/asset-1/original" -> {
                    originalHits++
                    MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "image/jpeg")
                        .setBody(Buffer().write(payload))
                }
                else -> null
            }
        }
        val auth = provider.authenticate(providerConfig)
        assertTrue("auth: ${auth.exceptionOrNull()}", auth.isSuccess)
        mediaDao.insertAll(
            listOf(
                remoteEntity(
                    "asset-1",
                    "lab-only-${System.nanoTime()}.jpg",
                    payload.size.toLong(),
                    sha1Base64(payload)
                )
            )
        )

        val result = newWorker(configId).doWork()

        assertTrue("worker should succeed, got $result", result is ListenableWorker.Result.Success)
        assertEquals(1, originalHits)
        val entity = mediaDao.getByRemoteId("asset-1", ProviderType.IMMICH, configId)!!
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertTrue("downloaded copy must be app-owned", entity.appLocalCopy)
        val relPath = relativePathOf(entity.localCopyPath.toUri())
        assertTrue(
            "expected Pictures/Lab Immich/Lab Trip/, got $relPath",
            relPath.endsWith("Lab Immich/Lab Trip/")
        )
        assertFalse("shard leaf must never appear", relPath.contains("/ed/"))
        assertFalse("uuid segment must never appear", relPath.contains("e2f0c95f"))
        val downloaded = context.contentResolver.openInputStream(entity.localCopyPath.toUri())!!
            .use { it.readBytes() }
        assertTrue("payload must round-trip", downloaded.contentEquals(payload))
        assertFalse(
            "download cache file must be removed",
            File(context.cacheDir, "download_asset-1.jpg").exists()
        )
    }

    @Test
    fun workerLinksIdenticalLocalFileWithoutDownloading() = runBlocking {
        val payload = ByteArray(640) { (it % 199).toByte() }
        val name = "lab-link-${System.nanoTime()}.jpg"
        val localUri = createLocalImage(name, payload)
        var originalHits = 0
        server.dispatcher = dispatcher { req ->
            authResponses(req) ?: when {
                req.path?.endsWith("/original") == true -> {
                    originalHits++
                    MockResponse().setResponseCode(500)
                }
                else -> null
            }
        }
        val auth = provider.authenticate(providerConfig)
        assertTrue("auth: ${auth.exceptionOrNull()}", auth.isSuccess)
        mediaDao.insertAll(
            listOf(remoteEntity("asset-2", name, payload.size.toLong(), sha1Base64(payload)))
        )

        val result = newWorker(configId).doWork()

        assertTrue("worker should succeed, got $result", result is ListenableWorker.Result.Success)
        assertEquals("identical local file must be linked, not re-downloaded", 0, originalHits)
        val entity = mediaDao.getByRemoteId("asset-2", ProviderType.IMMICH, configId)!!
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertFalse("a user's pre-existing file must never become app-owned", entity.appLocalCopy)
        // The reconcile path stores the Files-collection URI — same row id, canonical form.
        assertEquals(
            ContentUris.parseId(localUri),
            ContentUris.parseId(entity.localCopyPath.toUri())
        )
    }

    @Test
    fun liveLabDownloadNeverProducesShardFolders() = runBlocking {
        val labUrl = labImmichBaseUrl()
        println("LIVE-LAB-PROBE: baseUrl=$labUrl")
        assumeTrue("test lab Immich not reachable — run testing/test-lab.sh up + adb reverse tcp:2283 tcp:2283", labUrl != null)
        val liveId = insertConfig(
            displayName = "Local Immich",
            serverUrl = labUrl!!,
            username = "gallery@refra.local",
            password = "refra-local-only",
            apiKey = null
        )
        providerConfig = providerDomainConfig(
            liveId,
            serverUrl = labUrl,
            username = "gallery@refra.local",
            password = "refra-local-only"
        )
        provider.configure(providerConfig)
        registry.register(liveId, provider)
        val auth = provider.authenticate(providerConfig)
        assumeTrue("lab Immich auth failed: ${auth.exceptionOrNull()}", auth.isSuccess)

        val resource = provider.getRemoteAssets(page = 0, pageSize = 50).first()
        assumeTrue("lab Immich asset fetch failed", resource is Resource.Success)
        val assets = (resource as Resource.Success).data.orEmpty()
        assumeTrue("lab Immich has no assets — run seed-cloud first", assets.isNotEmpty())
        mediaDao.insertAll(
            assets.map {
                it.copy(syncState = SyncState.REMOTE_ONLY, localCopyPath = "", appLocalCopy = false)
            }
        )

        val result = newWorker(liveId).doWork()

        assertTrue("worker should succeed, got $result", result is ListenableWorker.Result.Success)
        val states = mediaDao.getLocalCopyStatesForConfig(liveId)
        assertTrue("every seeded row must end up satisfied", states.size == assets.size)
        var linked = 0
        states.forEach { state ->
            val relPath = relativePathOf(state.localCopyPath.toUri())
            assertFalse("shard tree must never appear, got $relPath", relPath.contains("/upload/"))
            assertFalse(
                "shard leaf must never appear, got $relPath",
                Regex("[0-9a-f]{2}/[0-9a-f]{2}/$").containsMatchIn(relPath)
            )
            if (state.appLocalCopy) {
                // Videos route under Movies/, images under Pictures/ — the
                // account + album segments are the invariant under test.
                assertTrue(
                    "downloaded copy must land under {Pictures|Movies}/Local Immich*, got $relPath",
                    relPath.startsWith("Pictures/Local Immich") ||
                        relPath.startsWith("Movies/Local Immich")
                )
            } else {
                linked++
            }
        }
        println("liveLab: ${states.size} rows satisfied, $linked linked to existing local files")
    }

    /**
     * Emulator networking differs between the slirp "radio" network and the
     * emulated WiFi stack — on some images `10.0.2.2` only works for shell
     * processes, not app sockets. `adb reverse tcp:2283 tcp:2283` tunnels the
     * lab over the adb transport instead, which always works. Probe both.
     */
    private fun labImmichBaseUrl(): String? = LAB_HOSTS.firstOrNull { host ->
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, LAB_PORT), 3000) }
        }.isSuccess
    }?.let { "http://$it:$LAB_PORT" }

    companion object {
        private const val LAB_PORT = 2283
        private val LAB_HOSTS = listOf("127.0.0.1", "10.0.2.2")
    }
}
