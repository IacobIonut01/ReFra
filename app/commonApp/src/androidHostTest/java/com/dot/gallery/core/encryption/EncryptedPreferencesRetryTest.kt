/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.settings.writePreference
import com.dot.gallery.domain.util.OrderType
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.IllegalBlockSizeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder


class EncryptedPreferencesRetryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val noPause: suspend (Long) -> Unit = {}

    @Test
    fun aPrunedKeystoreOperationIsRetriedWithAFreshCipher() = runBlocking {
        var calls = 0
        val result = EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt", noPause) {
            calls++
            if (calls == 1) throw IllegalBlockSizeException()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun aKeystoreFailureThatKeepsHappeningStillThrows() {
        var calls = 0
        assertThrows(IllegalBlockSizeException::class.java) {
            runBlocking {
                EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt", noPause) {
                    calls++
                    throw IllegalBlockSizeException()
                }
            }
        }
        assertEquals(EncryptedPreferencesSerializer.KEYSTORE_ATTEMPTS, calls)
    }

    @Test
    fun retriesBackOffSoPrunedSlotsCanDrain() = runBlocking {
        val pauses = mutableListOf<Long>()
        runCatching {
            EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt", { pauses += it }) {
                throw IllegalBlockSizeException()
            }
        }

        val base = EncryptedPreferencesSerializer.KEYSTORE_RETRY_BASE_DELAY_MS
        assertEquals(
            List(EncryptedPreferencesSerializer.KEYSTORE_ATTEMPTS - 1) { base shl it },
            pauses,
        )
    }

    @Test
    fun keystoreOperationsFromDifferentThreadsNeverOverlap() {
        val open = AtomicInteger()
        val maxOpen = AtomicInteger()
        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                start.await()
                repeat(50) {
                    KeystoreOperations.run {
                        maxOpen.accumulateAndGet(open.incrementAndGet(), ::maxOf)
                        Thread.yield()
                        open.decrementAndGet()
                    }
                }
                done.countDown()
            }.start()
        }
        start.countDown()

        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, maxOpen.get())
    }

    /** Issue 1414: picking a timeline sort crashed when the encrypted write kept failing. */
    @Test
    fun aSettingsWriteThatKeepsFailingDoesNotEscapeToTheCaller() = runBlocking {
        val attempts = AtomicInteger()
        val serializer = object : Serializer<Preferences> {
            override val defaultValue: Preferences = emptyPreferences()
            override suspend fun readFrom(input: InputStream): Preferences = defaultValue
            override suspend fun writeTo(t: Preferences, output: OutputStream) {
                EncryptedPreferencesSerializer.retryKeystoreOperation("encrypt", noPause) {
                    attempts.incrementAndGet()
                    throw IllegalBlockSizeException()
                }
            }
        }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val dataStore = DataStoreFactory.create(
                serializer = serializer,
                scope = scope,
                produceFile = { tmp.root.resolve("encrypted_settings.pb") },
            )
            val store = DataStoreSettingsStore(dataStore)
            val key = SettingsKeys.timelineSort

            val saved = writePreference(key.name) {
                store.put(key, LastSort(OrderType.Ascending, FilterKind.DATE))
            }

            assertFalse(saved)
            assertEquals(EncryptedPreferencesSerializer.KEYSTORE_ATTEMPTS, attempts.get())
            assertEquals(key.default, store.observe(key).first())
        } finally {
            scope.cancel()
        }
    }
}
