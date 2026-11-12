/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import java.io.ByteArrayOutputStream
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.logging.printWarning
import kotlinx.coroutines.delay
import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


/**
 * A [Serializer] for [Preferences] that encrypts/decrypts using AES-GCM
 * backed by Android Keystore via [MasterKey].
 *
 * File format: `[12-byte IV][ciphertext+tag]`
 *
 * Falls back to [emptyPreferences] if decryption fails (e.g. key rotation or corruption).
 */
class EncryptedPreferencesSerializer(context: Context) : Serializer<Preferences> {

    override val defaultValue: Preferences = emptyPreferences()

    // Cache the Keystore key — Keystore lookups are expensive (50-200ms each)
    // and this was being called on every single DataStore read/write.
    private val secretKey: SecretKey by lazy { StartupTracer.trace("EncryptedPrefsSerializer.keystoreInit") {
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
    } }

    override suspend fun readFrom(input: InputStream): Preferences {
        val span = StartupTracer.begin("EncryptedPrefsSerializer.readFrom")
        return try {
            val allBytes = input.readBytes()
            if (allBytes.isEmpty()) return defaultValue

            val iv = allBytes.copyOfRange(0, GCM_IV_SIZE)
            val ciphertext = allBytes.copyOfRange(GCM_IV_SIZE, allBytes.size)

            val plainBytes = retryKeystoreOperation("decrypt") {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext)
            }

            // Deserialize the preferences proto via Okio
            val protoSerializer = androidx.datastore.preferences.core.PreferencesSerializer
            plainBytes.inputStream().source().buffer().use { protoSerializer.readFrom(it) }
        } catch (e: GeneralSecurityException) {
            printWarning("EncryptedPreferencesSerializer: decryption failed, returning empty: ${e.message}")
            defaultValue
        } catch (e: Exception) {
            printWarning("EncryptedPreferencesSerializer: read failed: ${e.message}")
            defaultValue
        } finally {
            StartupTracer.end(span)
        }
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) {
        try {
            // Serialize preferences to proto bytes via Okio
            val protoSerializer = PreferencesSerializer
            val plainStream = ByteArrayOutputStream()
            plainStream.sink().buffer().use { sink -> protoSerializer.writeTo(t, sink) }
            val plainBytes = plainStream.toByteArray()

            val (iv, ciphertext) = retryKeystoreOperation("encrypt") {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.iv to cipher.doFinal(plainBytes)
            }

            output.write(iv)
            output.write(ciphertext)
            output.flush()
            printDebug("EncryptedPreferencesSerializer: wrote ${plainBytes.size}b -> ${iv.size + ciphertext.size}b encrypted")
        } catch (e: Exception) {
            printWarning("EncryptedPreferencesSerializer: write failed: ${e.message}")
            throw e
        }
    }

    companion object {
        /** Attempts per cipher operation; see [retryKeystoreOperation]. */
        const val KEYSTORE_ATTEMPTS = 5

        /** Pause before the second attempt; it doubles before each later one. */
        const val KEYSTORE_RETRY_BASE_DELAY_MS = 20L

        /**
         * Runs [block] again with a fresh cipher when Keystore fails it. Keystore prunes open
         * operations when KeyMint is out of slots (`INVALID_OPERATION_HANDLE`, "Pruned"), which
         * surfaces as an [GeneralSecurityException] from `doFinal`. Each attempt holds
         * [KeystoreOperations] so the app's other Keystore work cannot prune it, and the
         * attempts back off ([pause]) so slots held by other processes can drain. A read that
         * still fails falls back to empty preferences; a write rethrows to DataStore.
         */
        suspend fun <T> retryKeystoreOperation(
            name: String,
            pause: suspend (Long) -> Unit = { delay(it) },
            block: () -> T,
        ): T {
            var attempt = 1
            var backoff = KEYSTORE_RETRY_BASE_DELAY_MS
            while (true) {
                try {
                    return KeystoreOperations.run(block)
                } catch (e: GeneralSecurityException) {
                    if (attempt >= KEYSTORE_ATTEMPTS) throw e
                    printWarn(
                        tag = "app.prefs",
                        message = "Encrypted settings $name failed, retrying",
                        ctx = mapOf(
                            "attempt" to attempt.toString(),
                            "backoffMs" to backoff.toString(),
                            "exception" to "${e.javaClass.name}: ${e.message}",
                        ),
                    )
                    pause(backoff)
                    backoff *= 2
                    attempt++
                }
            }
        }

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gallery_encrypted_prefs_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
    }
}
