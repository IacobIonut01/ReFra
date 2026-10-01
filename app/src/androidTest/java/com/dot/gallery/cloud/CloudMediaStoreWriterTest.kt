/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud

import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.sync.CloudMediaStoreWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device regression for #1270: writes go through real MediaStore, so the
 * resulting RELATIVE_PATH is the truth — a remote asset-store shard path must
 * never become a user-visible folder, whatever the provider handed us.
 */
@RunWith(AndroidJUnit4::class)
class CloudMediaStoreWriterTest {

    private fun writeAndReadRelativePath(subPath: String, fallback: String): Pair<String, String> =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "issue1270-${System.nanoTime()}.jpg"
            val src = File(context.cacheDir, name).apply { writeText("payload") }
            val uri = CloudMediaStoreWriter.write(
                context,
                src.toUri(),
                CloudMediaStoreWriter.Request(
                    displayName = name,
                    mimeType = "image/jpeg",
                    relativeSubPath = subPath,
                    fallbackSubPath = fallback
                )
            )
            try {
                assertNotNull("MediaStore insert should succeed", uri)
                val relPath = context.contentResolver.query(
                    uri!!, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                    null, null, null
                )?.use { c ->
                    assertTrue(c.moveToFirst())
                    c.getString(0)
                }
                val body = context.contentResolver.openInputStream(uri)?.use {
                    it.reader().readText()
                }
                assertEquals("payload", body)
                (relPath ?: "") to name
            } finally {
                runCatching { context.contentResolver.delete(uri!!, null, null) }
                src.delete()
            }
        }

    @Test
    fun shardTreeSubPathLandsInFallbackFolder() {
        val (relPath) = writeAndReadRelativePath(
            subPath = "upload/e2f0c95f-f60b-41bd-af75-1c3d0f6b5a01/ed/4f",
            fallback = "Immich Fallback"
        )
        assertTrue("expected Pictures/<fallback>/, got $relPath", relPath.endsWith("Immich Fallback/"))
        assertFalse("no shard leaf may survive", relPath.contains("/ed/"))
        assertFalse("no uuid segment may survive", relPath.contains("e2f0c95f"))
    }

    @Test
    fun userFolderSubPathMirrorsUnderPictures() {
        val (relPath) = writeAndReadRelativePath(
            subPath = "Camera/Trip",
            fallback = "Fallback"
        )
        assertTrue("expected Pictures/Camera/Trip/, got $relPath", relPath.endsWith("Camera/Trip/"))
    }
}
