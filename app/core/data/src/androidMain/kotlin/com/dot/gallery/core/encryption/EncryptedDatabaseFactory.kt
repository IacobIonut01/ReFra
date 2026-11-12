/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.core.content.edit
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printWarning
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.execSQL
import com.dot.gallery.data.DatabaseFileState
import com.dot.gallery.data.SqlCipherDriver
import com.dot.gallery.data.getAndroidDatabaseBuilder
import com.dot.gallery.data.getInternalDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.runBlocking
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.uri.query
import com.dot.gallery.core.startup.StartupWorkGate


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
    private const val PREF_RAW_KEY_MIGRATED = "raw_key_migrated"
    private const val VERIFIED_MARKER = "internal_db_verified"

    fun create(context: Context, startupGate: StartupWorkGate? = null): InternalDatabase {
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

        StartupTracer.trace("EncryptedDB.migrateIfNeeded") {
            migrateUnencryptedDbIfNeeded(context, passphrase)
        }

        // Convert the hex passphrase to raw-key format: x'<hex>'
        // This tells SQLCipher to use the 256-bit key directly, skipping
        // the expensive PBKDF2-HMAC-SHA512 (256k iterations, ~630ms).
        val hexString = String(passphrase, Charsets.UTF_8)
        val rawKeyPassphrase = "x'$hexString'".toByteArray(Charsets.UTF_8)

        // Must happen before Room opens the database.
        StartupTracer.trace("EncryptedDB.rekeyIfNeeded") {
            migrateToRawKeyIfNeeded(context, passphrase, hexString)
        }

        StartupTracer.trace("EncryptedDB.verifyOncePerInstall") {
            verifyDatabaseOncePerInstall(context, passphrase, "x'$hexString'")
        }

        val db = StartupTracer.trace("EncryptedDB.roomBuilder") {
            getInternalDatabase(
                getAndroidDatabaseBuilder(context)
                    .setDriver(SqlCipherDriver(rawKeyPassphrase))
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .fallbackToDestructiveMigration(false)
            )
        }

        // Eagerly open the database connection in the background to start
        // SQLCipher key derivation early, rather than lazily on first query.
        // This overlaps the expensive PBKDF2 work with other startup tasks.
        Thread({
            // Disable secure memory wiping — reduces per-page alloc/free overhead.
            // Safe for a gallery app where the DB key is already in process memory.
            StartupTracer.trace("EncryptedDB.warmup") {
                runBlocking {
                    db.useWriterConnection { conn ->
                        try {
                            conn.usePrepared("PRAGMA cipher_memory_security = OFF") { it.step() }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            if (startupGate != null) {
                runBlocking { startupGate.awaitFirstContent() }
            }
            // Warm SQLCipher's page cache by reading catalog + data tables.
            // Without this, the first Room query pays ~1.3s of cold-cache
            // overhead decrypting system pages from disk.
            StartupTracer.trace("EncryptedDB.cacheWarmup") {
                runBlocking {
                    db.useReaderConnection { conn ->
                        conn.usePrepared("SELECT * FROM room_master_table") { it.step() }
                        conn.usePrepared("SELECT * FROM blacklist") { it.step() }
                        try {
                            conn.usePrepared("SELECT * FROM cloud_media WHERE trashed = 0 AND archived = 0 ORDER BY timestamp DESC") {
                                while (it.step()) { /* iterate to decrypt SQLCipher pages into cache */ }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }, "db-warmup").start()

        StartupTracer.end(createSpan)
        return db
    }

    /**
     * One-time migration: rekey an existing database from passphrase-based
     * KDF (PBKDF2, ~630ms per open) to raw-key format (~0ms per open).
     *
     * Opens the DB with the old passphrase, then issues PRAGMA rekey with
     * the raw key prefix so subsequent opens skip KDF entirely.
     */
    private fun migrateToRawKeyIfNeeded(context: Context, passphrase: ByteArray, hexString: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_RAW_KEY_MIGRATED, false)) return

        val dbFile = context.getDatabasePath(InternalDatabase.NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            prefs.edit { putBoolean(PREF_RAW_KEY_MIGRATED, true) }
            return
        }

        try {
            // Open with the old passphrase (triggers PBKDF2 one last time)
            val oldDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                String(passphrase, Charsets.UTF_8),
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            try {
                oldDb.rawQuery("SELECT COUNT(*) FROM sqlite_schema", null)
                    .use { it.moveToFirst() }
                // Rekey to raw-key format — rewrites the file header so future
                // opens with x'<hex>' skip KDF entirely.
                oldDb.rawExecSQL("PRAGMA rekey = \"x'$hexString'\"")
                printDebug("EncryptedDatabaseFactory: rekeyed DB from passphrase → raw key")
            } finally {
                oldDb.close()
            }
            prefs.edit { putBoolean(PREF_RAW_KEY_MIGRATED, true) }
        } catch (e: Exception) {
            // If rekey fails, we'll fall back to raw-key open which will fail,
            // and Room will recreate the DB. Log but don't crash.
            printWarning("EncryptedDatabaseFactory: raw-key migration failed: ${e.message}")
        }
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
        when (DatabaseFileState.of(dbFile)) {
            DatabaseFileState.ABSENT -> {
                flags.edit {putBoolean(FLAG_DB_ENCRYPTED, true)}
                return
            }
            // Already encrypted, but with a key this install never had (for example a
            // database restored without the preferences holding its passphrase).
            DatabaseFileState.NOT_PLAINTEXT -> {
                discardUndecryptableDatabase(context, reason = "no passphrase for encrypted file", throwable = null)
                return
            }
            DatabaseFileState.PLAINTEXT -> Unit
        }

        val backupFile = File(dbFile.parentFile, "${InternalDatabase.NAME}_plaintext_backup")
        try {
            dbFile.renameTo(backupFile)
            File(dbFile.absolutePath + "-wal").let {
                if (it.exists()) it.renameTo(File(backupFile.absolutePath + "-wal"))
            }
            File(dbFile.absolutePath + "-shm").let {
                if (it.exists()) it.renameTo(File(backupFile.absolutePath + "-shm"))
            }

            val hexString = String(passphrase, Charsets.UTF_8)
            val rawKeyStr = "x'$hexString'"

            // Pre-create an empty encrypted database at the original path.
            // SQLCipher's ATTACH cannot create a new file when the main
            // connection was opened in plaintext mode (null key) — the
            // internal open() call omits O_CREAT, causing SQLITE_CANTOPEN.
            // By pre-creating the target, ATTACH just opens an existing file.
            // Use raw-key format directly so no PBKDF2 rekey is needed later.
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                rawKeyStr,
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
                    "ATTACH DATABASE '${dbFile.absolutePath}' AS encrypted KEY \"$rawKeyStr\""
                )

                // Export all tables, indexes, triggers from plaintext → encrypted
                plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")

                // Preserve Room's schema version
                plainDb.rawExecSQL("PRAGMA encrypted.user_version = $version")

                plainDb.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plainDb.close()
            }

            File(backupFile.absolutePath + "-wal").delete()
            File(backupFile.absolutePath + "-shm").delete()
            backupFile.delete()

            flags.edit {
                putBoolean(FLAG_DB_ENCRYPTED, true)
                putBoolean(PREF_RAW_KEY_MIGRATED, true)
            }
            printDebug("EncryptedDatabaseFactory: migrated plaintext DB → encrypted (raw key) via sqlcipher_export")
        } catch (e: Exception) {
            printWarning("EncryptedDatabaseFactory: migration failed: ${e.message}")
            // Delete any partially-created encrypted DB at the original path
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
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
     * back to the plaintext Room builder in [com.dot.gallery.injection.AppComponent].
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
        val (key, keyCreated) = loadOrCreateKey()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED_PASSPHRASE, null)

        if (stored != null) {
            val failure: Exception? = if (keyCreated) null else try {
                return unwrapPassphrase(key, stored)
            } catch (e: AEADBadTagException) {
                e
            } catch (e: KeyPermanentlyInvalidatedException) {
                e
            } catch (e: IllegalArgumentException) {
                e
            } catch (e: IndexOutOfBoundsException) {
                e
            }
            // The Keystore key that wrapped this passphrase is gone: Keystore keys are
            // deleted on uninstall and never leave the device, so a passphrase restored
            // from a backup can never be unwrapped, and neither can the database it keys.
            discardUndecryptableDatabase(
                context,
                reason = if (keyCreated) "keystore key missing" else "passphrase unwrap failed",
                throwable = failure,
            )
        }

        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = random.joinToString("") { "%02x".format(it) }
            .toByteArray(Charsets.UTF_8)
        val wrapped = wrapPassphrase(key, passphrase)
        prefs.edit {putString(PREF_WRAPPED_PASSPHRASE, wrapped)}
        printDebug("EncryptedDatabaseFactory: generated and wrapped new passphrase")
        return passphrase
    }

    /**
     * Once per install, checks that the database file opens with the stored passphrase
     * before Room gets it. A restore can bring back the database and [PREFS_NAME] from
     * different snapshots, or a plaintext file under a flag that says encrypted; Room
     * would then fail on the first query. The marker lives in `noBackupFilesDir`, so a
     * restored install always runs the check again.
     */
    private fun verifyDatabaseOncePerInstall(context: Context, passphrase: ByteArray, rawKey: String) {
        val marker = File(context.noBackupFilesDir, VERIFIED_MARKER)
        if (marker.exists()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(InternalDatabase.NAME)
        when (DatabaseFileState.of(dbFile)) {
            DatabaseFileState.ABSENT -> Unit
            // Flagged as encrypted but still plaintext: encrypt it now, keeping the data.
            DatabaseFileState.PLAINTEXT -> {
                prefs.edit { putBoolean(FLAG_DB_ENCRYPTED, false) }
                migrateUnencryptedDbIfNeeded(context, passphrase)
            }
            DatabaseFileState.NOT_PLAINTEXT -> try {
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, rawKey, null, SQLiteDatabase.OPEN_READWRITE, null, null
                )
                try {
                    db.rawQuery("SELECT COUNT(*) FROM sqlite_schema", null).use { it.moveToFirst() }
                } finally {
                    db.close()
                }
            } catch (e: SQLiteNotADatabaseException) {
                // Still keyed with the pre-raw-key passphrase after a failed rekey: not a
                // mismatch, so keep the file and check again on the next launch.
                if (!prefs.getBoolean(PREF_RAW_KEY_MIGRATED, false)) return
                discardUndecryptableDatabase(context, reason = "passphrase does not match the database", throwable = e)
            } catch (e: Exception) {
                printWarning("EncryptedDatabaseFactory: database check failed, retrying next launch: ${e.message}")
                return
            }
        }
        marker.createNewFile()
    }

    /**
     * Moves the database that nothing can decrypt aside (see [DatabaseFileState.moveAside]),
     * so [create] builds a fresh encrypted database. Indexes are rebuilt by the next scans;
     * anything that lived only in the old file was already lost with its key.
     */
    private fun discardUndecryptableDatabase(context: Context, reason: String, throwable: Throwable?) {
        val dbFile = context.getDatabasePath(InternalDatabase.NAME)
        val bytes = dbFile.length()
        val moved = if (DatabaseFileState.of(dbFile) == DatabaseFileState.ABSENT) {
            DatabaseFileState.companions(dbFile).forEach { it.delete() }
            null
        } else {
            DatabaseFileState.moveAside(dbFile)
        }
        // The stored passphrase is left alone: the caller replaces it, or it was just
        // generated for the fresh database.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(FLAG_DB_ENCRYPTED, true)
            putBoolean(PREF_RAW_KEY_MIGRATED, true)
        }
        printError(
            tag = "db.encryption",
            message = "Encrypted database cannot be decrypted ($reason); rebuilding it",
            throwable = throwable,
            ctx = mapOf(
                "reason" to reason,
                "bytes" to bytes.toString(),
                "movedTo" to (moved?.name ?: "none"),
            ),
        )
    }

    /**
     * Returns the database Keystore key, and whether it was generated by this call. A key
     * generated now cannot have wrapped any stored passphrase. Called once per [create].
     */
    private fun loadOrCreateKey(): Pair<SecretKey, Boolean> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getKey(KEY_ALIAS, null) as SecretKey) to false
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
            keyGen.generateKey() to true
        }
    }

    private fun wrapPassphrase(key: SecretKey, passphrase: ByteArray): String {
        val blob = KeystoreOperations.run {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.iv + cipher.doFinal(passphrase)
        }
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun unwrapPassphrase(key: SecretKey, wrapped: String): ByteArray {
        val blob = Base64.decode(wrapped, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        return KeystoreOperations.run {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        }
    }
}
