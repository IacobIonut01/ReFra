/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.immich.ImmichProvider
import com.dot.gallery.cloud.immich.data.api.ImmichAuthInterceptor
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.Media
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exercises [ImmichProvider]'s HTTP wiring against a [MockWebServer]: the Immich v2.x `/api`
 * path prefix, API-key vs access-token auth, auth failure, and the assets/albums fetch->parse
 * pipeline. Runs without a live server.
 *
 * NOTE: talks cleartext HTTP to 127.0.0.1. If a run fails with a cleartext-blocked error, ensure
 * the debug/androidTest manifest permits cleartext to localhost (`usesCleartextTraffic`).
 */
@RunWith(AndroidJUnit4::class)
class ImmichProviderMockServerTest {

    private lateinit var server: MockWebServer
    private lateinit var db: InternalDatabase
    private lateinit var dao: CloudMediaDao
    private lateinit var provider: ImmichProvider

    private val assetsJson = """
        { "assets": { "total": 1, "count": 1, "items": [
            { "id": "asset-1", "type": "IMAGE", "originalFileName": "a.jpg",
              "originalMimeType": "image/jpeg", "fileCreatedAt": "2024-01-15T10:30:00.000Z",
              "isFavorite": true, "visibility": "ARCHIVE" }
        ] } }
    """.trimIndent()

    private val albumsJson = """
        [ { "id": "album-1", "albumName": "Trip", "assetCount": 5, "shared": false,
            "createdAt": "2024-01-01T00:00:00.000Z" } ]
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.getCloudMediaDao()
        provider = ImmichProvider(context, ImmichAuthInterceptor(), dao)
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    private fun dispatcher(block: (RecordedRequest) -> MockResponse?) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            block(request) ?: MockResponse().setResponseCode(404)
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun authenticateWithApiKeySucceedsAndSetsConnected() = runBlocking {
        server.dispatcher = dispatcher { req ->
            when {
                req.path?.endsWith("/api/auth/validateToken") == true ->
                    json("""{ "authStatus": true }""")
                req.path?.endsWith("/api/users/me") == true ->
                    json("""{ "id": "u1", "email": "g@x", "isAdmin": true }""")
                else -> null
            }
        }
        val config = CloudServerConfig(id = 1, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        provider.configure(config)

        val result = provider.authenticate(config)

        assertTrue("auth should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(ConnectionState.CONNECTED, provider.connectionState.value)
        assertTrue(provider.isAvailable)
        // Every request must carry the API key as the x-api-key header (not Authorization).
        val requests = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }.toList()
        assertTrue("expected at least one request", requests.isNotEmpty())
        assertTrue(
            "x-api-key header must carry the API key",
            requests.all { it.getHeader("x-api-key") == "KEY" && it.getHeader("Authorization") == null }
        )
    }

    @Test
    fun authenticateWithUsernamePasswordUsesLoginToken() = runBlocking {
        server.dispatcher = dispatcher { req ->
            when {
                req.path?.endsWith("/api/auth/login") == true ->
                    json("""{ "accessToken": "TOKEN123", "userId": "u1", "userEmail": "g@x", "isAdmin": false }""")
                else -> null
            }
        }
        val config = CloudServerConfig(
            id = 1, providerType = ProviderType.IMMICH, serverUrl = baseUrl(),
            username = "g@x", password = "pw"
        )
        provider.configure(config)

        val result = provider.authenticate(config)

        assertTrue("login should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("TOKEN123", result.getOrNull()?.accessToken)
        assertEquals(ConnectionState.CONNECTED, provider.connectionState.value)
        val loginReq = server.takeRequest()
        assertTrue("must hit the v2 /api/auth/login path", loginReq.path!!.endsWith("/api/auth/login"))
    }

    @Test
    fun authenticateFailsOnInvalidApiKey() = runBlocking {
        server.dispatcher = dispatcher { req ->
            when {
                req.path?.endsWith("/api/auth/validateToken") == true ->
                    json("""{ "authStatus": false }""")
                else -> null
            }
        }
        val config = CloudServerConfig(id = 1, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "BAD")
        provider.configure(config)

        val result = provider.authenticate(config)

        assertTrue(result.isFailure)
        assertEquals(ConnectionState.ERROR, provider.connectionState.value)
    }

    @Test
    fun getRemoteAssetsParsesSearchResponse() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/search/metadata") == true) json(assetsJson) else null
        }
        val config = CloudServerConfig(id = 3, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        provider.configure(config)

        val resource = provider.getRemoteAssets(page = 0, pageSize = 100).first()

        assertTrue(resource is Resource.Success)
        val items = (resource as Resource.Success).data!!
        assertEquals(1, items.size)
        val entity = items.single()
        assertEquals("asset-1", entity.remoteId)
        assertEquals(3L, entity.serverConfigId)
        assertTrue(entity.favorite)
        assertTrue("visibility ARCHIVE should map to archived", entity.archived)
    }

    @Test
    fun getRemoteAlbumsParsesAlbumList() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/albums") == true) json(albumsJson) else null
        }
        val config = CloudServerConfig(id = 4, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        provider.configure(config)

        val resource = provider.getRemoteAlbums().first()

        assertTrue(resource is Resource.Success)
        val albums = (resource as Resource.Success).data!!
        assertEquals(1, albums.size)
        assertEquals("album-1", albums.single().remoteId)
        assertEquals("Trip", albums.single().name)
        assertEquals(5, albums.single().assetCount)
    }

    @Test
    fun uploadStreamsSourceWithoutCreatingTemporaryCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.cacheDir.resolve("immich-upload-source.jpg").apply {
            writeText("streamed-upload-payload")
        }
        val sawTemporaryUploadCopy = AtomicBoolean(false)
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/assets") == true) {
                sawTemporaryUploadCopy.set(
                    context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("upload_") }
                )
                assertEquals("AAECAwQFBgcICQoLDA0ODxAREhM=", req.getHeader("x-immich-checksum"))
                assertTrue(req.body.readUtf8().contains("streamed-upload-payload"))
                json("""{ "id": "uploaded-1", "status": "created" }""")
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 5, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )
        val media = Media.UriMedia(
            id = 42,
            label = "source.jpg",
            uri = source.toUri(),
            path = source.path,
            relativePath = "",
            albumID = 1,
            albumLabel = "Camera",
            timestamp = 1_705_315_800,
            takenTimestamp = null,
            fullDate = "",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 0,
            size = source.length()
        )

        val checksum = "000102030405060708090a0b0c0d0e0f10111213"
        val result = provider.uploadAsset(media, checksum = checksum)

        assertTrue("upload should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("source.jpg", result.getOrThrow().label)
        assertEquals("image/jpeg", result.getOrThrow().mimeType)
        assertEquals(checksum, result.getOrThrow().contentHash)
        assertTrue(!sawTemporaryUploadCopy.get())
        assertTrue(source.delete())
    }

    @Test
    fun bulkUploadCheckSendsBase64Checksum() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/assets/bulk-upload-check") == true) {
                val requestJson = JsonParser.parseString(req.body.readUtf8()).asJsonObject
                val checksum = requestJson["assets"].asJsonArray[0].asJsonObject["checksum"].asString
                assertEquals("AAECAwQFBgcICQoLDA0ODxAREhM=", checksum)
                json(
                    """{ "results": [ { "id": "0", "action": "reject", "assetId": "asset-1", "reason": "duplicate", "isTrashed": false } ] }"""
                )
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 6, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.bulkUploadCheck(
            listOf("000102030405060708090a0b0c0d0e0f10111213")
        )

        assertTrue("bulk check should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(true, result.getOrThrow()["0"])
    }

    @Test
    fun capabilitiesIncludeAllImmichFeatures() {
        val caps = provider.capabilities.map { it.name }.toSet()
        assertNotNull(caps)
        listOf(
            "REMOTE_ASSETS", "REMOTE_ALBUMS", "SYNC", "PEOPLE", "MAP",
            "SMART_SEARCH", "SHARE_CREATE", "SHARE_MANAGE", "ARCHIVE", "MEMORIES"
        ).forEach { assertTrue("Immich must declare $it", it in caps) }
    }
}
