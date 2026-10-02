/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.core.CloudAlbum
import com.dot.gallery.cloud.core.CloudAuthToken
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.core.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Contract tests for [RemoteMediaProvider.fetchRange] — the ranged-read used by
 * the capture-time index to pull embedded metadata prefixes from remote media
 * (issue #1277). Every provider inherits this implementation, so the behaviour
 * is pinned here once rather than per provider.
 */
class RemoteRangeFetchTest {

    private lateinit var server: MockWebServer
    private var previousClient: OkHttpClient? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        previousClient = CloudFetcherRegistryHolder.okHttpClient
        CloudFetcherRegistryHolder.okHttpClient = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        CloudFetcherRegistryHolder.okHttpClient = previousClient
        server.shutdown()
    }

    @Test
    fun partialContentReturnsRequestedRange() = runTest {
        val body = ByteArray(1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setResponseCode(206).setBody(okio.Buffer().write(body)))

        val provider = FakeRangeProvider(server.url("/file").toString())
        val result = provider.fetchRange("file", offset = 0L, length = 100L)

        assertArrayEquals(body.copyOf(100), result)
        val request = server.takeRequest()
        assertEquals("bytes=0-99", request.getHeader("Range"))
        assertEquals("Basic fake", request.getHeader("Authorization"))
    }

    @Test
    fun midFileRangeIsRequestedWithAbsoluteOffsets() = runTest {
        server.enqueue(MockResponse().setResponseCode(206).setBody("tail-bytes"))

        val provider = FakeRangeProvider(server.url("/file").toString())
        provider.fetchRange("file", offset = 4096L, length = 512L)

        assertEquals("bytes=4096-4607", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun fullResponseAtOffsetZeroReturnsCappedPrefix() = runTest {
        // A server that ignores Range answers 200 with the whole body; at offset 0
        // the capped prefix is still what the caller asked for.
        server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789abcdef"))

        val provider = FakeRangeProvider(server.url("/file").toString())
        val result = provider.fetchRange("file", offset = 0L, length = 10L)

        assertArrayEquals("0123456789".toByteArray(), result)
    }

    @Test
    fun fullResponseAtNonZeroOffsetReturnsNull() = runTest {
        // Serving the whole body for a mid-file range would download the entire
        // prefix — not acceptable for a metadata probe.
        server.enqueue(MockResponse().setResponseCode(200).setBody("full-body"))

        val provider = FakeRangeProvider(server.url("/file").toString())
        val result = provider.fetchRange("file", offset = 100L, length = 50L)

        assertNull(result)
    }

    @Test
    fun httpErrorReturnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val provider = FakeRangeProvider(server.url("/file").toString())
        assertNull(provider.fetchRange("file", 0L, 100L))
    }

    @Test
    fun outOfRangeReturnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(416))

        val provider = FakeRangeProvider(server.url("/file").toString())
        assertNull(provider.fetchRange("file", 10_000L, 100L))
    }

    @Test
    fun blankOrNonHttpUrlReturnsNullWithoutRequest() = runTest {
        val blankUrl = FakeRangeProvider("")
        assertNull(blankUrl.fetchRange("file", 0L, 100L))
        assertNull(FakeRangeProvider("cloud://x").fetchRange("file", 0L, 100L))
        assertTrue(server.requestCount == 0)
    }

    @Test
    fun invalidRangeArgumentsReturnNull() = runTest {
        val provider = FakeRangeProvider(server.url("/file").toString())
        assertNull(provider.fetchRange("file", -1L, 100L))
        assertNull(provider.fetchRange("file", 0L, 0L))
        assertTrue(server.requestCount == 0)
    }

    private class FakeRangeProvider(
        private val url: String,
        private val headers: Map<String, String> = mapOf("Authorization" to "Basic fake")
    ) : RemoteMediaProvider {
        override val providerType = ProviderType.WEBDAV
        override val displayName = "fake"
        override val isAvailable = true
        override val capabilities = emptySet<ProviderCapability>()
        override val connectionState = MutableStateFlow(ConnectionState.CONNECTED)

        override suspend fun testConnection(config: CloudServerConfig) =
            Result.failure<CloudServerInfo>(UnsupportedOperationException())
        override suspend fun authenticate(config: CloudServerConfig) =
            Result.failure<CloudAuthToken>(UnsupportedOperationException())
        override fun getRemoteAssets(page: Int, pageSize: Int) = emptyMedia()
        override fun getRemoteAlbums(): Flow<Resource<List<CloudAlbum>>> =
            flowOf(Resource.Success(emptyList()))
        override fun getRemoteAlbumMedia(albumId: String) = emptyMedia()
        override fun getRemoteFavorites() = emptyMedia()
        override fun getRemoteTrashed() = emptyMedia()
        override suspend fun toggleFavorite(remoteId: String, favorite: Boolean) = Result.success(Unit)
        override suspend fun toggleArchive(remoteId: String, archived: Boolean) = Result.success(Unit)
        override suspend fun trashAsset(remoteId: String) = Result.success(Unit)
        override suspend fun restoreAsset(remoteId: String) = Result.success(Unit)
        override suspend fun deleteAsset(remoteId: String) = Result.success(Unit)
        override suspend fun emptyTrash() = Result.success(Unit)
        override suspend fun restoreAllTrash() = Result.success(Unit)
        override suspend fun createAlbum(name: String) =
            Result.failure<CloudAlbum>(UnsupportedOperationException())
        override suspend fun addToAlbum(albumId: String, assetIds: List<String>) = Result.success(Unit)
        override suspend fun search(query: String) = Result.success(emptyList<CloudMediaEntity>())
        override fun getRemoteArchived() = emptyMedia()
        override suspend fun getStorageInfo() =
            Result.failure<CloudStorageInfo>(UnsupportedOperationException())
        override fun getThumbnailUrl(remoteId: String, size: ThumbnailSize) = ""
        override fun getOriginalUrl(remoteId: String) = url
        override fun getAuthHeaders() = headers
        override fun configure(config: CloudServerConfig) = Unit

        private fun emptyMedia(): Flow<Resource<List<CloudMediaEntity>>> =
            flowOf(Resource.Success(emptyList()))
    }
}
