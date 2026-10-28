/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


/**
 * Optional password-based encryption for the whole backup archive.
 *
 * Layout: MAGIC | SALT(16) | BASE_NONCE(12) | CHUNK_SIZE(4) | [LEN(4) + GCM_CHUNK]*
 *
 * The plaintext (a ZIP archive) is split into [CHUNK_SIZE] blocks, each independently
 * encrypted with AES-256-GCM using a per-chunk nonce derived from [BASE_NONCE] + index,
 * so memory stays bounded regardless of backup size. The key is derived from the user
 * password with PBKDF2-HMAC-SHA256.
 *
 * Two magic values exist:
 *  - `RFBKP1` for legacy (schema v1/v2) whole-archive encryption.
 *  - `RFBKP2E` for the payload of a schema-v3 archive (which is preceded by a plaintext
 *    preview header starting with `RFBKP2`).
 */
object BackupCrypto {

    private val MAGIC_V1 = "RFBKP1".toByteArray()
    private val MAGIC_V2 = "RFBKP2E".toByteArray()

    /** Public copy for archive probing. */
    val MAGIC_V2_BYTES: ByteArray get() = MAGIC_V2.copyOf()
    const val MAGIC_SIZE = 6
    const val MAGIC_V2_SIZE = 7
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MiB
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH = 256

    /** Returns true if [header] marks a legacy whole-archive encrypted backup. */
    fun isEncrypted(header: ByteArray): Boolean = matches(header, MAGIC_V1)

    /** Returns true if [header] marks a schema-v3 encrypted payload. */
    fun isEncryptedV2(header: ByteArray): Boolean = matches(header, MAGIC_V2)

    private fun matches(header: ByteArray, magic: ByteArray): Boolean =
        header.size >= magic.size && header.copyOf(magic.size).contentEquals(magic)

    /** Reads up to [count] header bytes from [input] without consuming more than needed. */
    fun readHeader(input: InputStream, count: Int = MAGIC_SIZE): ByteArray {
        val buf = ByteArray(count)
        val read = readFully(input, buf)
        return if (read == count) buf else buf.copyOf(read)
    }

    /** Legacy (schema v1/v2) encryption. Does not close [output]. */
    fun encrypt(input: InputStream, output: OutputStream, password: String) =
        encryptInternal(input, output, password, MAGIC_V1)

    /** Schema-v3 payload encryption. Does not close [output]. */
    fun encryptV2(input: InputStream, output: OutputStream, password: String) =
        encryptInternal(input, output, password, MAGIC_V2)

    /** Decrypts a legacy encrypted backup. Throws [BackupPasswordException] on a wrong password. */
    fun decrypt(input: InputStream, output: OutputStream, password: String) =
        decryptInternal(input, output, password, MAGIC_V1)

    /** Decrypts a schema-v3 payload. Throws [BackupPasswordException] on a wrong password. */
    fun decryptV2(input: InputStream, output: OutputStream, password: String) =
        decryptInternal(input, output, password, MAGIC_V2)

    /**
     * Verifies [password] by decrypting the first schema-v3 payload chunk. The stream must be
     * positioned at the payload magic. Returns false for a wrong password or a corrupt header.
     */
    fun verifyPassword(input: InputStream, password: String): Boolean = try {
        val dis = DataInputStream(input)
        val magic = ByteArray(MAGIC_V2.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_V2)) return false
        val salt = ByteArray(SALT_LEN)
        dis.readFully(salt)
        val baseNonce = ByteArray(NONCE_LEN)
        dis.readFully(baseNonce)
        dis.readInt() // chunk size
        val key = deriveKey(password, salt)
        val length = dis.readInt()
        if (length <= 0 || length > CHUNK_SIZE + 32) return false
        val ciphertext = ByteArray(length)
        dis.readFully(ciphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, deriveChunkNonce(baseNonce, 0))
        )
        cipher.doFinal(ciphertext)
        true
    } catch (_: Exception) {
        false
    }

    private fun encryptInternal(
        input: InputStream,
        output: OutputStream,
        password: String,
        magic: ByteArray
    ) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val baseNonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val dos = DataOutputStream(output)
        dos.write(magic)
        dos.write(salt)
        dos.write(baseNonce)
        dos.writeInt(CHUNK_SIZE)

        val buf = ByteArray(CHUNK_SIZE)
        var index = 0L
        while (true) {
            val n = readFully(input, buf)
            if (n == 0) break
            val nonce = deriveChunkNonce(baseNonce, index++)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            val ct = cipher.doFinal(buf, 0, n)
            dos.writeInt(ct.size)
            dos.write(ct)
            if (n < CHUNK_SIZE) break
        }
        dos.flush()
    }

    private fun decryptInternal(
        input: InputStream,
        output: OutputStream,
        password: String,
        magic: ByteArray
    ) {
        val dis = DataInputStream(input)
        val header = ByteArray(magic.size)
        dis.readFully(header)
        require(header.contentEquals(magic)) { "Not an encrypted backup" }
        val salt = ByteArray(SALT_LEN)
        dis.readFully(salt)
        val baseNonce = ByteArray(NONCE_LEN)
        dis.readFully(baseNonce)
        @Suppress("UNUSED_VARIABLE")
        val chunkSize = dis.readInt()
        val key = deriveKey(password, salt)

        var index = 0L
        while (true) {
            val len = try {
                dis.readInt()
            } catch (_: EOFException) {
                break
            }
            val ct = ByteArray(len)
            dis.readFully(ct)
            val nonce = deriveChunkNonce(baseNonce, index++)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            val plain = try {
                cipher.doFinal(ct)
            } catch (e: Exception) {
                // AEADBadTagException etc. → wrong password or corrupted data
                throw BackupPasswordException()
            }
            output.write(plain)
        }
        output.flush()
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun deriveChunkNonce(base: ByteArray, index: Long): ByteArray {
        val nonce = base.copyOf()
        var i = nonce.size - 1
        var idx = index
        var b = 0
        while (b < 8 && i >= 0) {
            nonce[i] = (nonce[i].toInt() xor (idx and 0xFF).toInt()).toByte()
            idx = idx ushr 8
            i--
            b++
        }
        return nonce
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }
}
