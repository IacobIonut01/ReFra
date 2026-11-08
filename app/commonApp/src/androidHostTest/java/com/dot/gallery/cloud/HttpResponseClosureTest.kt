/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import com.dot.gallery.cloud.image.CloudFetcherRegistryHolder
import com.dot.gallery.cloud.image.fetchBytes
import com.dot.gallery.cloud.image.isClearlyTruncatedJpeg
import com.dot.gallery.cloud.image.resolveImageMimeType
import com.github.panpf.sketch.fetch.FetchResult
import com.dot.gallery.cloud.webdav.shouldReconcileWebDavScan
import com.dot.gallery.cloud.webdav.data.api.OcsApiClient
import com.dot.gallery.cloud.webdav.data.api.WebDavClient
import com.dot.gallery.cloud.webdav.data.api.WebDavException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.dot.gallery.domain.uri.path


class HttpResponseClosureTest {
    private lateinit var server: MockWebServer
    private val closeCount = AtomicInteger()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun webDavWriteOperationsCloseSuccessfulResponses() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200).setBody("response")) }
        val file = Files.createTempFile("webdav-upload", ".jpg").toFile()
        val client = webDavClient()

        try {
            client.upload("image.jpg", file)
            client.mkdir("album")
            client.delete("image.jpg")
            client.setFavorite("image.jpg", true)

            assertEquals(4, closeCount.get())
        } finally {
            file.delete()
        }
    }

    @Test
    fun deletingAnAlreadyMissingWebDavFileIsSuccessful() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("already gone"))

        webDavClient().delete("gone.jpg")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/dav/gone.jpg", request.path)
        assertEquals(1, closeCount.get())
    }

    @Test
    fun webDavDeleteStillFailsForServerErrors() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        assertThrows(WebDavException::class.java) {
            webDavClient().delete("file.jpg")
        }

        assertEquals(1, closeCount.get())
    }

    @Test
    fun webDavCacheReconciliationRequiresACompleteFirstPageScan() {
        assertTrue(shouldReconcileWebDavScan(page = 0, configId = 4L, complete = true))
        assertFalse(shouldReconcileWebDavScan(page = 1, configId = 4L, complete = true))
        assertFalse(shouldReconcileWebDavScan(page = 0, configId = 0L, complete = true))
        assertFalse(shouldReconcileWebDavScan(page = 0, configId = 4L, complete = false))
    }

    @Test
    fun successfulBodyRequestClosesResponseOnce() {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
        )

        webDavClient().propFind("")

        assertEquals(1, closeCount.get())
    }

    @Test
    fun failedRequestsCloseResponses() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("webdav error"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("ocs error"))

        assertThrows(WebDavException::class.java) {
            webDavClient().propFind("")
        }
        assertThrows(Exception::class.java) {
            OcsApiClient(trackingClient(), server.url("/").toString(), "user", "password")
                .getCapabilities()
        }

        assertEquals(2, closeCount.get())
    }

    @Test
    fun jpegValidationRejectsTruncationButAllowsTrailingMotionPhotoData() {
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
        val motionPhoto = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte(), 0x02
        )

        assertTrue(isClearlyTruncatedJpeg("image/jpeg", truncated))
        assertFalse(isClearlyTruncatedJpeg("image/jpeg", motionPhoto))
        assertFalse(isClearlyTruncatedJpeg("image/png", byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun cloudFetchReturnsBeforeTheNetworkResponseCompletes() {
        val responseBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()
        )
        val requestStarted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val resultRef = AtomicReference<Result<FetchResult>?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestStarted.countDown()
                if (!releaseResponse.await(30, TimeUnit.SECONDS)) {
                    return MockResponse().setResponseCode(500)
                }
                return MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(Buffer().write(responseBytes))
            }
        }
        val previousClient = CloudFetcherRegistryHolder.okHttpClient
        CloudFetcherRegistryHolder.okHttpClient = OkHttpClient()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val load = executor.submit {
                resultRef.set(
                    fetchBytes(
                        url = server.url("/image.jpg").toString(),
                        authHeaders = emptyMap(),
                        offlineKey = "key"
                    )
                )
            }

            assertTrue(
                "request did not start; result=${resultRef.get()}",
                requestStarted.await(5, TimeUnit.SECONDS)
            )
            releaseResponse.countDown()
            load.get(10, TimeUnit.SECONDS)
            val result = resultRef.get()
            assertNotNull(result)
            val fetchResult = result!!.getOrNull()
            assertNotNull("fetch failed: ${result.exceptionOrNull()}", fetchResult)
            val bytes = fetchResult!!.dataSource.openSource().buffer().readByteArray()
            assertArrayEquals(responseBytes, bytes)
        } finally {
            releaseResponse.countDown()
            executor.shutdownNow()
            CloudFetcherRegistryHolder.okHttpClient = previousClient
        }
    }

    @Test
    fun cloudImageMimeIsSniffedWhenTheResponseOmitsOrGenericisesTheType() {
        assertEquals("image/webp", resolveImageMimeType(null, webpHeader))
        assertEquals("image/webp", resolveImageMimeType("application/octet-stream", webpHeader))
        assertEquals("image/jpeg", resolveImageMimeType("image/jpeg", byteArrayOf(0x01)))
        assertNull(resolveImageMimeType(null, "<html>login</html>".toByteArray()))
        assertNull(resolveImageMimeType("application/json", "{\"error\":1}".toByteArray()))
    }

    @Test
    fun cloudFetchAcceptsWebpPreviewServedWithoutContentType() {
        server.enqueue(MockResponse().setBody(Buffer().write(webpHeader)))
        val result = fetchWithClient("/api/assets/a/thumbnail")
        val fetchResult = result.getOrNull()
        assertNotNull("fetch failed: ${result.exceptionOrNull()}", fetchResult)
        assertEquals("image/webp", fetchResult!!.mimeType)
    }

    @Test
    fun cloudFetchStillRejectsNonImagePayloads() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>login</html>"))
        assertTrue(fetchWithClient("/api/assets/a/thumbnail").isFailure)
    }

    private val webpHeader = "RIFF\u0024\u0000\u0000\u0000WEBPVP8X".toByteArray(Charsets.ISO_8859_1)

    private fun fetchWithClient(path: String): Result<FetchResult> {
        val previousClient = CloudFetcherRegistryHolder.okHttpClient
        CloudFetcherRegistryHolder.okHttpClient = OkHttpClient()
        return try {
            fetchBytes(url = server.url(path).toString(), authHeaders = emptyMap(), offlineKey = "key")
        } finally {
            CloudFetcherRegistryHolder.okHttpClient = previousClient
        }
    }

    private fun webDavClient(): WebDavClient = WebDavClient(
        okHttpClient = trackingClient(),
        baseUrl = server.url("/dav").toString(),
        username = "user",
        password = "password",
        filesEndpoint = ""
    )

    private fun trackingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            val trackingSource = object : ForwardingSource(body.source()) {
                private var closed = false

                override fun close() {
                    if (!closed) {
                        closed = true
                        closeCount.incrementAndGet()
                    }
                    super.close()
                }
            }.buffer()
            response.newBuilder()
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = body.contentType()
                    override fun contentLength(): Long = body.contentLength()
                    override fun source(): BufferedSource = trackingSource
                })
                .build()
        }
        .build()
}
