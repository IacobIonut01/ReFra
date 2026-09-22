/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.netfs

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.netfs.bridge.NetFsByteRange
import com.dot.gallery.cloud.netfs.bridge.NetFsLoopbackRoute
import com.dot.gallery.cloud.netfs.bridge.buildNetFsLoopbackPath
import com.dot.gallery.cloud.netfs.bridge.parseNetFsByteRange
import com.dot.gallery.cloud.netfs.bridge.parseNetFsLoopbackRoute
import com.dot.gallery.core.usesLiveCloudAlbumMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkFileSystemSupportTest {

    private val entries = listOf(
        entry("Trips/one.jpg", 100L),
        entry("Trips/2025/two.jpg", 300L),
        entry("Trips-old/other.jpg", 400L),
        entry("Family/photo.jpg", 200L)
    )

    @Test
    fun mediaIndexReusesStablePagesAndScopesAlbumPaths() {
        val index = NetFsMediaIndex(entries)

        assertEquals(entries.take(2), index.page(0, 2))
        assertEquals(entries.drop(2).take(2), index.page(1, 2))
        assertEquals(listOf("Family", "Trips", "Trips-old"), index.rootAlbumPaths())
        assertEquals(
            listOf("Trips/one.jpg", "Trips/2025/two.jpg"),
            index.inAlbum("Trips").map { it.relativePath }
        )
    }

    @Test
    fun mediaIndexBuildsAlbumCountAndNewestCover() {
        val stats = NetFsMediaIndex(entries).albumStats("Trips")

        assertEquals(2, stats.assetCount)
        assertEquals("Trips/2025/two.jpg", stats.thumbnailAssetId)
    }

    @Test
    fun nasAlbumMembershipDoesNotWaitForPartialRoomIndex() {
        assertTrue(usesLiveCloudAlbumMembership(ProviderType.SMB))
        assertTrue(usesLiveCloudAlbumMembership(ProviderType.NFS))
        assertFalse(usesLiveCloudAlbumMembership(ProviderType.IMMICH))
    }

    @Test
    fun byteRangesSupportOpenEndedSuffixAndClampedRequests() {
        assertEquals(NetFsByteRange(100L, 999L), parseNetFsByteRange("bytes=100-", 1_000L))
        assertEquals(NetFsByteRange(900L, 999L), parseNetFsByteRange("bytes=-100", 1_000L))
        assertEquals(NetFsByteRange(100L, 999L), parseNetFsByteRange("bytes=100-2000", 1_000L))
        assertNull(parseNetFsByteRange("bytes=1000-", 1_000L))
        assertNull(parseNetFsByteRange("bytes=200-100", 1_000L))
        assertNull(parseNetFsByteRange("bytes=0-1,4-5", 1_000L))
    }

    @Test
    fun contentHashStreamsNetworkFileBytes() {
        assertEquals(
            "ec734b651574683f36974c7f12847fbbe084dbe2",
            ByteArrayInputStream("verified".toByteArray()).use(::contentSha1)
        )
    }

    @Test
    fun loopbackRouteRetainsAccountAndFullPath() {
        val path = "Photos/2026/Summer & winter/ä.jpg"
        val routePath = buildNetFsLoopbackPath(
            token = "token",
            providerType = ProviderType.SMB,
            configId = 42L,
            kind = "original",
            sizeName = "orig",
            path = path
        )

        assertEquals(
            NetFsLoopbackRoute(ProviderType.SMB, 42L, "original", "orig", path),
            parseNetFsLoopbackRoute(routePath, "token")
        )
        assertNull(parseNetFsLoopbackRoute(routePath, "different-token"))
    }

    @Test
    fun thumbnailSingleFlightKeepsDifferentKeysIndependent() {
        val singleFlight = NetFsThumbnailSingleFlight()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<Int> {
                singleFlight.run("slow") {
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                    1
                }
            }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            val second = executor.submit<Int> { singleFlight.run("fast") { 2 } }

            assertEquals(2, second.get(5, TimeUnit.SECONDS))
            releaseFirst.countDown()
            assertEquals(1, first.get(5, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun thumbnailSingleFlightSerializesTheSameKey() {
        val singleFlight = NetFsThumbnailSingleFlight()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondSubmitted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<Int> {
                singleFlight.run("same") {
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                    1
                }
            }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            val second = executor.submit<Int> {
                secondSubmitted.countDown()
                singleFlight.run("same") {
                    secondStarted.countDown()
                    2
                }
            }

            assertTrue(secondSubmitted.await(5, TimeUnit.SECONDS))
            assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertEquals(1, first.get(5, TimeUnit.SECONDS))
            assertEquals(2, second.get(5, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun connectionFailureMatchesSocketAndTransportTypes() {
        assertTrue(isNetFsConnectionFailure(SocketException("Connection reset")))
        assertTrue(isNetFsConnectionFailure(SocketTimeoutException("Read timed out")))
        assertTrue(isNetFsConnectionFailure(EOFException()))
        assertTrue(
            isNetFsConnectionFailure(
                IOException("wrapper", SocketException("Broken pipe"))
            )
        )
    }

    @Test
    fun connectionFailureMatchesSmbAndTransportMessages() {
        assertTrue(
            isNetFsConnectionFailure(
                IllegalStateException("The connection has already been closed")
            )
        )
        assertTrue(isNetFsConnectionFailure(IOException("No route to host")))
        assertTrue(
            isNetFsConnectionFailure(
                IOException("outer", IOException("Network is unreachable"))
            )
        )
    }

    @Test
    fun connectionFailureMatchesTransportExceptionClassName() {
        class TransportException : Exception("wrapped transport")
        assertTrue(isNetFsConnectionFailure(TransportException()))
    }

    @Test
    fun connectionFailureRejectsNonTransportErrors() {
        assertFalse(isNetFsConnectionFailure(IOException("Permission denied")))
        assertFalse(isNetFsConnectionFailure(IllegalArgumentException("Not configured")))
        assertFalse(isNetFsConnectionFailure(IOException("No such file or directory")))
    }

    private fun entry(path: String, modified: Long) = NetFsEntry(
        name = path.substringAfterLast('/'),
        relativePath = path,
        isDirectory = false,
        size = 1L,
        lastModified = modified
    )
}
