/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import android.content.Context
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.data.DatabaseFileState
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.getBundledDatabase
import com.dot.gallery.data.getFallbackDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/**
 * A reinstall that restores the encrypted database and its wrapped passphrase from a
 * backup has a new Keystore key, so neither can be decrypted. Startup must rebuild the
 * database instead of handing the encrypted file to the plaintext driver, which failed
 * every query with "file is not a database" (code 26).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseRestoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile: File get() = context.getDatabasePath(InternalDatabase.NAME)
    private val movedAside: File get() = File(dbFile.parentFile, "${InternalDatabase.NAME}.undecryptable")
    private val verifiedMarker: File get() = File(context.noBackupFilesDir, VERIFIED_MARKER)

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    @Test
    fun reopeningWithTheSameKeyKeepsTheDatabase() {
        createAndClose { db -> runBlocking { db.useWriterConnection { c -> c.usePrepared("CREATE TABLE marker(x INTEGER)") { it.step() } } } }

        createAndClose { db -> assertTrue(hasTable(db, "marker")) }

        assertFalse(movedAside.exists())
    }

    @Test
    fun restoredDatabaseWithoutItsKeystoreKeyIsRebuilt() {
        createAndClose { db -> runBlocking { db.useWriterConnection { c -> c.usePrepared("CREATE TABLE marker(x INTEGER)") { it.step() } } } }
        assertEquals(DatabaseFileState.NOT_PLAINTEXT, DatabaseFileState.of(dbFile))
        // Uninstall deletes the Keystore key; backup brings back the database and the prefs.
        deleteKeystoreKey()

        createAndClose { db -> assertFalse(hasTable(db, "marker")) }

        assertTrue(movedAside.exists())
        // The rebuilt database stays readable on the following launch.
        createAndClose { db -> assertTrue(hasTable(db, "room_master_table")) }
    }

    @Test
    fun encryptedDatabaseWithoutItsPreferencesIsRebuilt() {
        createAndClose { db -> runBlocking { db.useWriterConnection { c -> c.usePrepared("CREATE TABLE marker(x INTEGER)") { it.step() } } } }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        createAndClose { db -> assertFalse(hasTable(db, "marker")) }

        assertTrue(movedAside.exists())
    }

    @Test
    fun restoredDatabaseFromAnotherSnapshotIsRebuilt() {
        createAndClose { db -> runBlocking { db.useWriterConnection { c -> c.usePrepared("CREATE TABLE marker(x INTEGER)") { it.step() } } } }
        val foreign = dbFile.readBytes()
        // A later install with its own key and passphrase, then a restore that brings back
        // only the database from the earlier snapshot.
        reset()
        createAndClose { }
        DatabaseFileState.companions(dbFile).forEach { it.delete() }
        dbFile.writeBytes(foreign)
        verifiedMarker.delete()

        createAndClose { db -> assertFalse(hasTable(db, "marker")) }

        assertTrue(movedAside.exists())
        createAndClose { db -> assertTrue(hasTable(db, "room_master_table")) }
    }

    @Test
    fun plaintextDatabaseFlaggedAsEncryptedIsEncryptedWithItsData() {
        createAndClose { }
        context.deleteDatabase(InternalDatabase.NAME)
        val plain = getBundledDatabase(context)
        try {
            runBlocking { plain.useWriterConnection { c -> c.usePrepared("CREATE TABLE marker(x INTEGER)") { it.step() } } }
        } finally {
            plain.close()
        }
        assertEquals(DatabaseFileState.PLAINTEXT, DatabaseFileState.of(dbFile))
        verifiedMarker.delete()

        createAndClose { db -> assertTrue(hasTable(db, "marker")) }

        assertEquals(DatabaseFileState.NOT_PLAINTEXT, DatabaseFileState.of(dbFile))
        assertFalse(movedAside.exists())
    }

    @Test
    fun fallbackNeverOpensAnEncryptedFileWithThePlaintextDriver() {
        createAndClose { }
        val before = dbFile.readBytes()

        val db = getFallbackDatabase(context)
        try {
            assertTrue(hasTable(db, "room_master_table"))
        } finally {
            db.close()
        }

        assertTrue(before.contentEquals(dbFile.readBytes()))
    }

    private fun createAndClose(block: (InternalDatabase) -> Unit) {
        val db = EncryptedDatabaseFactory.create(context)
        try {
            block(db)
            awaitWarmup()
        } finally {
            db.close()
        }
    }

    private fun hasTable(db: InternalDatabase, name: String): Boolean = runBlocking {
        db.useReaderConnection { conn ->
            conn.usePrepared("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?") {
                it.bindText(1, name)
                it.step()
                it.getLong(0) > 0
            }
        }
    }

    /** The factory warms the connection on a background thread; let it finish before closing. */
    private fun awaitWarmup() {
        Thread.getAllStackTraces().keys.filter { it.name == "db-warmup" }.forEach { it.join(10_000) }
    }

    private fun deleteKeystoreKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun reset() {
        awaitWarmup()
        context.deleteDatabase(InternalDatabase.NAME)
        (listOf(movedAside) + DatabaseFileState.companions(movedAside)).forEach { it.delete() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        deleteKeystoreKey()
        verifiedMarker.delete()
    }

    private companion object {
        const val PREFS_NAME = "encrypted_db_prefs"
        const val VERIFIED_MARKER = "internal_db_verified"
        const val KEY_ALIAS = "gallery_db_encryption_key"
    }
}
