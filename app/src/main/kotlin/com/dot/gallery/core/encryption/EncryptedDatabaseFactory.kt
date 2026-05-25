/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.core.content.edit
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.migration.MIGRATION_12_13
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Creates a Room [InternalDatabase] backed by SQLCipher encryption.
 *
 * On first use, if an existing unencrypted database exists, it is encrypted
 * in-place using `sqlcipher_export` so that all indexed media data is preserved.
 *
 * The encryption passphrase is a random 32-byte value wrapped with an
 * Android Keystore AES-GCM key and stored in SharedPreferences.
 */
object EncryptedDatabaseFactory {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "gallery_db_encryption_key"
    private const val PREFS_NAME = "encrypted_db_prefs"
    private const val PREF_WRAPPED_PASSPHRASE = "wrapped_passphrase"
    private const val FLAG_DB_ENCRYPTED = "db_encrypted"
    private const val GCM_TAG_LENGTH = 128

    fun create(context: Context): InternalDatabase {
        val createSpan = StartupTracer.begin("EncryptedDB.create")

        StartupTracer.trace("EncryptedDB.loadLibrary") {
            System.loadLibrary("sqlcipher")
        }

        // If the DB hasn't been encrypted yet, clear any stale passphrase from
        // previous failed attempts (which used raw bytes instead of hex).
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(FLAG_DB_ENCRYPTED, false)) {
            prefs.edit {remove(PREF_WRAPPED_PASSPHRASE)}
        }

        val passphrase = StartupTracer.trace("EncryptedDB.getOrCreatePassphrase") {
            getOrCreatePassphrase(context)
        }

        // Migrate existing unencrypted DB to encrypted if needed
        StartupTracer.trace("EncryptedDB.migrateIfNeeded") {
            migrateUnencryptedDbIfNeeded(context, passphrase)
        }

        val factory = SupportOpenHelperFactory(passphrase)
        val db = StartupTracer.trace("EncryptedDB.roomBuilder") {
            Room.databaseBuilder(
                context,
                InternalDatabase::class.java,
                InternalDatabase.NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_12_13)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .fallbackToDestructiveMigration(false)
                .build()
        }

        StartupTracer.end(createSpan)
        return db
    }

    /**
     * If the plaintext database exists and hasn't been encrypted yet,
     * encrypt it using `sqlcipher_export`.
     *
     * Steps:
     * 1. Move the plaintext DB to a backup path
     * 2. Open the backup with SQLCipher in plaintext mode (`null` password)
     * 3. ATTACH a new encrypted DB at the original path with the passphrase
     * 4. `sqlcipher_export` all data from plaintext → encrypted
     * 5. Copy the schema version and clean up
     */
    private fun migrateUnencryptedDbIfNeeded(context: Context, passphrase: ByteArray) {
        val flags = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (flags.getBoolean(FLAG_DB_ENCRYPTED, false)) return

        val dbFile = context.getDatabasePath(InternalDatabase.NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            // No existing DB — mark as encrypted (will be created encrypted by Room)
            flags.edit {putBoolean(FLAG_DB_ENCRYPTED, true)}
            return
        }

        val backupFile = File(dbFile.parentFile, "${InternalDatabase.NAME}_plaintext_backup")
        try {
            // Move plaintext DB + journal files to backup
            dbFile.renameTo(backupFile)
            File(dbFile.absolutePath + "-wal").let {
                if (it.exists()) it.renameTo(File(backupFile.absolutePath + "-wal"))
            }
            File(dbFile.absolutePath + "-shm").let {
                if (it.exists()) it.renameTo(File(backupFile.absolutePath + "-shm"))
            }

            val passphraseStr = String(passphrase)

            // Pre-create an empty encrypted database at the original path.
            // SQLCipher's ATTACH cannot create a new file when the main
            // connection was opened in plaintext mode (null key) — the
            // internal open() call omits O_CREAT, causing SQLITE_CANTOPEN.
            // By pre-creating the target, ATTACH just opens an existing file.
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphraseStr,
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            ).close()

            // Open the plaintext backup without a key.
            // null password → sqlcipher skips sqlite3_key → plaintext mode.
            val plainDb = SQLiteDatabase.openDatabase(
                backupFile.absolutePath,
                null as String?,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )

            try {
                val version = plainDb.version

                // Attach the pre-created encrypted DB at the original path.
                // SupportOpenHelperFactory converts byte[] → String via new String(byte[]),
                // so we must use the same conversion for the KEY clause.
                plainDb.rawExecSQL(
                    "ATTACH DATABASE '${dbFile.absolutePath}' AS encrypted KEY '$passphraseStr'"
                )

                // Export all tables, indexes, triggers from plaintext → encrypted
                plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")

                // Preserve Room's schema version
                plainDb.rawExecSQL("PRAGMA encrypted.user_version = $version")

                plainDb.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plainDb.close()
            }

            // Clean up backup
            File(backupFile.absolutePath + "-wal").delete()
            File(backupFile.absolutePath + "-shm").delete()
            backupFile.delete()

            flags.edit {putBoolean(FLAG_DB_ENCRYPTED, true)}
            printDebug("EncryptedDatabaseFactory: migrated plaintext DB → encrypted via sqlcipher_export")
        } catch (e: Exception) {
            printWarning("EncryptedDatabaseFactory: migration failed: ${e.message}")
            // Delete any partially-created encrypted DB at the original path
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            // Restore plaintext backup
            if (backupFile.exists()) {
                backupFile.renameTo(dbFile)
                File(backupFile.absolutePath + "-wal").let {
                    if (it.exists()) it.renameTo(File(dbFile.absolutePath + "-wal"))
                }
                File(backupFile.absolutePath + "-shm").let {
                    if (it.exists()) it.renameTo(File(dbFile.absolutePath + "-shm"))
                }
            }
            // Clear stale passphrase so next attempt generates a fresh one.
            // Do NOT mark as encrypted — let the plaintext fallback handle it.
            flags.edit {remove(PREF_WRAPPED_PASSPHRASE)}
            throw e
        }
    }

    /**
     * Quick-check that the existing DB file can actually be opened with
     * [passphrase]. If it can't (e.g. a previous buggy migration set
     * [FLAG_DB_ENCRYPTED] but left the DB unencrypted), reset the flag
     * and passphrase so the next launch retries, then throw to fall
     * back to the plaintext Room builder in [AppModule].
     */
    private fun validatePassphrase(context: Context, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(InternalDatabase.NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) return

        try {
            val testDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                String(passphrase),
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                testDb.rawQuery("SELECT COUNT(*) FROM sqlite_schema", null)
                    .use { it.moveToFirst() }
            } finally {
                testDb.close()
            }
        } catch (e: Exception) {
            printWarning("EncryptedDatabaseFactory: passphrase validation failed, resetting: ${e.message}")
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean(FLAG_DB_ENCRYPTED, false)
                remove(PREF_WRAPPED_PASSPHRASE)
            }
            throw e
        }
    }

    /**
     * Returns the passphrase for SQLCipher as UTF-8 bytes of a hex string.
     *
     * On first call, 32 random bytes are generated and hex-encoded to produce
     * a 64-character ASCII passphrase. This is then encrypted with the Keystore
     * key and stored in SharedPreferences. Using hex ensures the passphrase
     * survives the `byte[]` → `String` conversion that [SupportOpenHelperFactory]
     * performs internally.
     *
     * Works with both software-backed and hardware-backed (TEE/StrongBox)
     * Keystore keys because we never call [SecretKey.getEncoded].
     */
    private fun getOrCreatePassphrase(context: Context): ByteArray {
        val key = getOrCreateKey()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED_PASSPHRASE, null)

        return if (stored != null) {
            unwrapPassphrase(key, stored)
        } else {
            // Generate 32 random bytes → 64 hex chars (ASCII-safe)
            val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val passphrase = random.joinToString("") { "%02x".format(it) }
                .toByteArray(Charsets.UTF_8)
            val wrapped = wrapPassphrase(key, passphrase)
            prefs.edit {putString(PREF_WRAPPED_PASSPHRASE, wrapped)}
            printDebug("EncryptedDatabaseFactory: generated and wrapped new passphrase")
            passphrase
        }
    }

    // Cache the Keystore key to avoid repeated expensive Keystore lookups.
    private val cachedKey: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }
    }

    private fun getOrCreateKey(): SecretKey = cachedKey

    private fun wrapPassphrase(key: SecretKey, passphrase: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(passphrase)
        val blob = iv + ciphertext
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun unwrapPassphrase(key: SecretKey, wrapped: String): ByteArray {
        val blob = Base64.decode(wrapped, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
