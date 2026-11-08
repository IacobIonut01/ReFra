package com.dot.gallery.editor.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The session-scoped lease cache in [AndroidVideoSourceResolver]: one
 * backing per reference, shared by every lease, retired on close or
 * replacement but only released when the last lease lets go — FFmpeg must
 * never lose a file it is still reading, and a staged copy must never be
 * redone for a second probe/frame/export of the same source.
 *
 * All sources come through [PipeContentProvider] — non-seekable, so every
 * resolve that misses the cache would restage (two provider opens).
 */
@RunWith(AndroidJUnit4::class)
class AndroidVideoSourceResolverTest {

    private lateinit var context: Context
    private val errors = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PipeContentProvider.payloads.clear()
        PipeContentProvider.openCounts.clear()
        PipeContentProvider.bytesWritten.clear()
        File(context.cacheDir, "editor/imports").deleteRecursively()
    }

    private fun resolver() =
        AndroidVideoSourceResolver(context, onError = { errors += it })

    @Test
    fun repeatedResolvesOfOneReferenceShareOneCopy() = runBlocking {
        val name = "shared.bin"
        PipeContentProvider.payloads[name] = ByteArray(100_000) { 1 }
        val uri = PipeContentProvider.uri(name).toString()
        val r = resolver()
        try {
            val first = r.resolve(uri)
            val second = r.resolve(uri)
            val third = r.resolve(uri)
            try {
                assertEquals(first.path, second.path)
                assertEquals(second.path, third.path)
                // Descriptor probe + stream copy exactly once — not per call.
                assertEquals(2, PipeContentProvider.openCount(name))
            } finally {
                first.close()
                second.close()
                third.close()
            }
            // Backings are retained at zero refs while the resolver lives —
            // a fourth resolve reuses the same copy.
            val fourth = r.resolve(uri)
            assertEquals(first.path, fourth.path)
            fourth.close()
            assertEquals(2, PipeContentProvider.openCount(name))
        } finally {
            r.close()
        }
    }

    @Test
    fun closeWithAnActiveLeaseKeepsTheFileUntilTheLeaseEnds() = runBlocking {
        val name = "held.bin"
        PipeContentProvider.payloads[name] = ByteArray(50_000) { 2 }
        val uri = PipeContentProvider.uri(name).toString()
        val r = resolver()
        val lease = r.resolve(uri)
        val staged = File(lease.path)
        assertTrue(staged.isFile)
        r.close()
        // Retired but referenced: the file must survive until the last
        // lease closes.
        assertTrue(staged.isFile)
        lease.close()
        assertFalse(staged.exists())
        // Idempotent close.
        lease.close()
        r.close()
    }

    @Test
    fun aDifferentReferenceRetiresTheOldBacking() = runBlocking {
        PipeContentProvider.payloads["first.bin"] = ByteArray(10_000) { 3 }
        PipeContentProvider.payloads["second.bin"] = ByteArray(10_000) { 4 }
        val r = resolver()
        try {
            val a = r.resolve(PipeContentProvider.uri("first.bin").toString())
            val stagedA = File(a.path)
            val b = r.resolve(PipeContentProvider.uri("second.bin").toString())
            // A is retired now, but a live lease keeps its file.
            assertTrue(stagedA.isFile)
            a.close()
            assertFalse(stagedA.exists())
            b.close()
        } finally {
            r.close()
        }
    }

    @Test
    fun cancellingACopyLeavesNoPartialStage() = runBlocking {
        val name = "big.bin"
        PipeContentProvider.payloads[name] = ByteArray(16 * 1024 * 1024) { 5 }
        val uri = PipeContentProvider.uri(name).toString()
        val r = resolver()
        try {
            val job = async(Dispatchers.IO) { r.resolve(uri) }
            var guard = 0
            // Wait until the copy is actually streaming.
            while (PipeContentProvider.writtenCount(name) == 0 && guard++ < 500) {
                delay(10)
            }
            job.cancel()
            try {
                job.await()
            } catch (expected: CancellationException) {
                // Expected: resolve was cancelled mid-copy.
            }
            val imports = File(context.cacheDir, "editor/imports")
            // No partial staged file may survive a cancelled copy.
            val leftovers = imports.listFiles().orEmpty().toList()
            assertTrue("staged leftovers: $leftovers", leftovers.isEmpty())
        } finally {
            r.close()
        }
    }
}
