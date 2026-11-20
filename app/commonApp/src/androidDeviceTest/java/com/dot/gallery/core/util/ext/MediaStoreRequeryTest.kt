/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util.ext

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.util.ext.MediaStoreRequery
import com.dot.gallery.core.util.ext.queryFlow
import com.dot.gallery.core.util.ext.querySteppedFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class MediaStoreRequeryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri: Uri = MediaStore.Files.getContentUri("external")
    private val projection = arrayOf(MediaStore.Files.FileColumns._ID)
    private val args = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_LIMIT, "1")
    }

    @Test
    fun queryFlowReemitsOnRequeryRequest() = runBlocking {
        val received = Channel<Cursor?>(Channel.UNLIMITED)
        val job = launch {
            context.contentResolver.queryFlow(uri, projection, args).collect { cursor ->
                received.trySend(cursor)
            }
        }
        try {
            val first = withTimeout(5_000) { received.receive() }
            first?.close()
            MediaStoreRequery.request()
            val second = withTimeout(5_000) { received.receive() }
            second?.close()
            assertNotNull(first)
        } finally {
            job.cancel()
            var drained = received.tryReceive()
            while (drained.isSuccess) {
                drained.getOrNull()?.close()
                drained = received.tryReceive()
            }
        }
    }

    @Test
    fun querySteppedFlowReemitsOnRequeryRequest() = runBlocking {
        val received = Channel<Cursor?>(Channel.UNLIMITED)
        val job = launch {
            context.contentResolver.querySteppedFlow(uri, projection, args).collect { cursor ->
                received.trySend(cursor)
            }
        }
        try {
            val first = withTimeout(5_000) { received.receive() }
            first?.close()
            MediaStoreRequery.request()
            val second = withTimeout(5_000) { received.receive() }
            second?.close()
            assertNotNull(first)
        } finally {
            job.cancel()
            var drained = received.tryReceive()
            while (drained.isSuccess) {
                drained.getOrNull()?.close()
                drained = received.tryReceive()
            }
        }
    }
}
