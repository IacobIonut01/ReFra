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
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumCopyState
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
    fun getRemoteAlbumsMergesSharedListingAndMapsRole() = runBlocking {
        val ownedJson = """
            [ { "id": "album-1", "albumName": "Trip", "assetCount": 5, "shared": false,
                "ownerId": "u1" } ]
        """.trimIndent()
        // The shared listing repeats an owned album (v2 includes both) — dedupe by id —
        // and carries the album shared WITH this account, owned by "u2".
        val sharedJson = """
            [ { "id": "album-1", "albumName": "Trip", "assetCount": 5, "shared": false,
                "ownerId": "u1" },
              { "id": "album-2", "albumName": "Family", "assetCount": 2, "shared": true,
                "ownerId": "u2", "owner": { "id": "u2", "name": "Partner", "email": "p@x" },
                "albumUsers": [ { "user": { "id": "u1" }, "role": "editor" } ] } ]
        """.trimIndent()
        server.dispatcher = dispatcher { req ->
            when {
                req.path?.endsWith("/api/auth/validateToken") == true ->
                    json("""{ "authStatus": true }""")
                req.path?.endsWith("/api/users/me") == true ->
                    json("""{ "id": "u1", "email": "g@x", "isAdmin": false }""")
                req.path == "/api/albums" -> json(ownedJson)
                req.path?.startsWith("/api/albums?shared=") == true -> json(sharedJson)
                else -> null
            }
        }
        val config = CloudServerConfig(id = 6, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        provider.configure(config)
        provider.authenticate(config)

        val resource = provider.getRemoteAlbums().first()

        val albums = (resource as Resource.Success).data!!
        assertEquals(listOf("album-1", "album-2"), albums.map { it.remoteId })
        val shared = albums.single { it.remoteId == "album-2" }
        assertTrue(shared.isShared)
        assertTrue("album owned by u2 must not be treated as owned", !shared.isOwned)
        assertEquals("editor", shared.shareRole)
        assertEquals("Partner", shared.ownerName)
    }

    @Test
    fun getRemoteAlbumMediaMapsTrashedAndArchivedMembers() = runBlocking {
        val albumJson = """
            { "id": "album-1", "albumName": "Trip", "assetCount": 3,
              "assets": [
                { "id": "a1", "type": "IMAGE", "originalFileName": "a.jpg" },
                { "id": "a2", "type": "IMAGE", "originalFileName": "b.jpg", "isTrashed": true },
                { "id": "a3", "type": "IMAGE", "originalFileName": "c.jpg", "isArchived": true }
              ] }
        """.trimIndent()
        server.dispatcher = dispatcher { req ->
            if (req.path == "/api/albums/album-1") json(albumJson) else null
        }
        provider.configure(
            CloudServerConfig(id = 7, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val resource = provider.getRemoteAlbumMedia("album-1").first()

        val members = (resource as Resource.Success).data!!
        assertEquals(3, members.size)
        assertTrue("trashed members must arrive flagged for the album-view filter",
            members.single { it.remoteId == "a2" }.trashed)
        assertTrue(members.single { it.remoteId == "a3" }.archived)
        assertTrue(!members.single { it.remoteId == "a1" }.trashed)
    }

    @Test
    fun removeFromAlbumFailsOnPerItemDiscard() = runBlocking {
        // Immich answers 200 even when individual ids are discarded — the only
        // trace is the per-item {id, success, error} verdict.
        val body = """
            [ { "id": "a1", "success": true },
              { "id": "a2", "success": false, "error": "no_permission" } ]
        """.trimIndent()
        server.dispatcher = dispatcher { req ->
            if (req.path == "/api/albums/album-1/assets") json(body) else null
        }
        provider.configure(
            CloudServerConfig(id = 8, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.removeFromAlbum("album-1", listOf("a1", "a2"))

        assertTrue("a discarded item must surface as failure, not transport success",
            result.isFailure)
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
    fun copyToAlbumAttachesExistingDuplicateWithoutUploadingAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.cacheDir.resolve("immich-copy-source.jpg").apply { writeText("duplicate") }
        val uploadCalled = AtomicBoolean(false)
        server.dispatcher = dispatcher { req ->
            when {
                req.path?.endsWith("/api/assets/bulk-upload-check") == true -> json(
                    """{ "results": [ { "id": "0", "action": "reject", "assetId": "asset-1", "reason": "duplicate", "isTrashed": false } ] }"""
                )
                req.path?.endsWith("/api/albums/album-1/assets") == true ->
                    json("""[ { "id": "asset-1", "success": true } ]""")
                req.path?.endsWith("/api/assets") == true -> {
                    uploadCalled.set(true)
                    json("""{ "id": "uploaded-1", "status": "created" }""")
                }
                else -> null
            }
        }
        provider.configure(
            CloudServerConfig(id = 7, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
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
            fullDate = "",
            mimeType = "image/jpeg",
            favorite = 0,
            trashed = 0,
            size = source.length()
        )

        val result = provider.copyToAlbum(
            media = media,
            remoteAlbumId = "album-1",
            checksum = "000102030405060708090a0b0c0d0e0f10111213"
        )

        assertEquals(RemoteAlbumCopyState.ALREADY_PRESENT, result.state)
        assertEquals("asset-1", result.remoteId)
        assertTrue(!uploadCalled.get())
        assertTrue(source.delete())
    }

    @Test
    fun smartSearchPostsQueryAndParsesHits() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/search/smart") == true) {
                val requestJson = JsonParser.parseString(req.body.readUtf8()).asJsonObject
                assertEquals("mountain", requestJson["query"].asString)
                json(assetsJson)
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 8, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.smartSearch("mountain")

        assertTrue("smart search should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        // The hit is cached so the timeline/viewer can render it before the next prefetch.
        assertEquals(1, dao.getByServerConfig(8L).first().size)
    }

    @Test
    fun smartSearchByAssetSendsQueryAssetIdAndDropsAnchor() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/search/smart") == true) {
                val requestJson = JsonParser.parseString(req.body.readUtf8()).asJsonObject
                assertEquals("asset-1", requestJson["queryAssetId"].asString)
                assertTrue("anchor search must not send a text query", !requestJson.has("query"))
                json(
                    """{ "assets": { "total": 2, "count": 2, "items": [
                        { "id": "asset-1", "type": "IMAGE", "originalFileName": "a.jpg",
                          "originalMimeType": "image/jpeg", "fileCreatedAt": "2024-01-15T10:30:00.000Z" },
                        { "id": "asset-2", "type": "IMAGE", "originalFileName": "b.jpg",
                          "originalMimeType": "image/jpeg", "fileCreatedAt": "2024-01-15T10:30:00.000Z" }
                    ] } }"""
                )
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 9, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.smartSearchByAsset("asset-1")

        assertTrue("anchor search should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val hits = result.getOrThrow()
        assertEquals(1, hits.size)
        assertTrue("the anchor asset must be excluded from its own results", hits.none { it.label == "a.jpg" })
    }

    @Test
    fun getTagsParsesTagList() = runBlocking {
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/tags") == true) {
                json(
                    """[ { "id": "tag-1", "name": "Nature", "value": "Nature", "color": "#12ab34" },
                         { "id": "tag-2", "name": "Family", "value": "Family" } ]"""
                )
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 10, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.getTags()

        assertTrue("tag fetch should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val tags = result.getOrThrow()
        assertEquals(2, tags.size)
        assertEquals("tag-1", tags[0].tagId)
        assertEquals("Nature", tags[0].name)
        assertEquals("#12ab34", tags[0].color)
        assertEquals(null, tags[1].color)
    }

    @Test
    fun getTagAssetIdsPaginatesMetadataSearch() = runBlocking {
        val seenPages = mutableListOf<Int>()
        server.dispatcher = dispatcher { req ->
            if (req.path?.endsWith("/api/search/metadata") == true) {
                val requestJson = JsonParser.parseString(req.body.readUtf8()).asJsonObject
                assertEquals("tag-1", requestJson["tagIds"].asJsonArray[0].asString)
                val page = requestJson["page"].asInt
                seenPages += page
                if (page == 1) {
                    json(
                        """{ "assets": { "total": 2, "count": 1, "nextPage": "2", "items": [
                            { "id": "asset-1", "type": "IMAGE", "originalFileName": "a.jpg",
                              "originalMimeType": "image/jpeg", "fileCreatedAt": "2024-01-15T10:30:00.000Z" }
                        ] } }"""
                    )
                } else {
                    json(
                        """{ "assets": { "total": 2, "count": 1, "items": [
                            { "id": "asset-2", "type": "IMAGE", "originalFileName": "b.jpg",
                              "originalMimeType": "image/jpeg", "fileCreatedAt": "2024-01-15T10:30:00.000Z" }
                        ] } }"""
                    )
                }
            } else {
                null
            }
        }
        provider.configure(
            CloudServerConfig(id = 11, providerType = ProviderType.IMMICH, serverUrl = baseUrl(), apiKey = "KEY")
        )

        val result = provider.getTagAssetIds("tag-1")

        assertTrue("tag asset listing should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(listOf("asset-1", "asset-2"), result.getOrThrow())
        assertEquals(listOf(1, 2), seenPages)
    }

    @Test
    fun capabilitiesIncludeAllImmichFeatures() {
        val caps = provider.capabilities.map { it.name }.toSet()
        assertNotNull(caps)
        listOf(
            "REMOTE_ASSETS", "REMOTE_ALBUMS", "SYNC", "ALBUM_WRITE", "PEOPLE", "MAP",
            "SMART_SEARCH", "SHARE_CREATE", "SHARE_MANAGE", "ARCHIVE", "MEMORIES", "TAGS"
        ).forEach { assertTrue("Immich must declare $it", it in caps) }
    }
}
