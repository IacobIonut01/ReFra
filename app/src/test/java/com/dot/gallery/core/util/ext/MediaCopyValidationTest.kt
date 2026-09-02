package com.dot.gallery.core.util.ext

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaCopyValidationTest {

    @Test
    fun exactKnownSizesVerifyCopy() {
        assertTrue(isVerifiedMediaCopy(sourceSize = 4096L, copiedBytes = 4096L, destinationSize = 4096L))
    }

    @Test
    fun unknownProviderSizesAcceptNonEmptyCopy() {
        assertTrue(isVerifiedMediaCopy(sourceSize = -1L, copiedBytes = 4096L, destinationSize = -1L))
    }

    @Test
    fun emptyOrMismatchedCopiesFailVerification() {
        assertFalse(isVerifiedMediaCopy(sourceSize = 0L, copiedBytes = 0L, destinationSize = 0L))
        assertFalse(isVerifiedMediaCopy(sourceSize = 4096L, copiedBytes = 2048L, destinationSize = 2048L))
        assertFalse(isVerifiedMediaCopy(sourceSize = 4096L, copiedBytes = 4096L, destinationSize = 2048L))
    }

    @Test
    fun modifiedTimestampFollowsTheSelectedPolicy() {
        assertEquals(
            1_600_000_000L,
            selectedModifiedTimestamp(false, 1_600_000_000L, 2_000_000_000L)
        )
        assertEquals(
            2_000_000_000L,
            selectedModifiedTimestamp(true, 1_600_000_000L, 2_000_000_000L)
        )
        assertEquals(null, selectedModifiedTimestamp(false, 0L, 2_000_000_000L))
    }

    @Test
    fun streamCopyStopsAtTheNextChunkAfterCancellation() = runBlocking {
        val output = ByteArrayOutputStream()
        val job = launch {
            ByteArrayInputStream(ByteArray(DEFAULT_BUFFER_SIZE * 3)).use { input ->
                input.copyToCancellable(output) {
                    currentCoroutineContext().cancel()
                }
            }
        }

        job.join()

        assertTrue(job.isCancelled)
        assertEquals(DEFAULT_BUFFER_SIZE, output.size())
    }
}
