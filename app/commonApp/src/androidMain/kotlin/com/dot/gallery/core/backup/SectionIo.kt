/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.KSerializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/** Wraps an output stream while counting bytes and hashing them with SHA-256. */
internal class DigestingOutputStream(
    private val delegate: OutputStream? = null
) : OutputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    var byteCount: Long = 0L
        private set

    override fun write(b: Int) {
        delegate?.write(b)
        digest.update(b.toByte())
        byteCount++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate?.write(b, off, len)
        digest.update(b, off, len)
        byteCount += len
    }

    override fun flush() {
        delegate?.flush()
    }

    fun sha256(): String = digest.digest().toHex()
}

/**
 * Streams section records as JSON Lines. One record per line keeps memory bounded and lets
 * restore process a section without loading the whole archive into memory.
 */
internal class SectionWriter(output: OutputStream) {
    private val digesting = DigestingOutputStream(output)
    private val buffered = BufferedOutputStream(digesting, 64 * 1024)
    var count: Int = 0
        private set

    fun <T> write(serializer: KSerializer<T>, value: T) {
        buffered.write(backupJson.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
        buffered.write('\n'.code)
        count++
    }

    fun finish(): SectionDescriptor {
        buffered.flush()
        return SectionDescriptor(
            file = "",
            count = count,
            sha256 = digesting.sha256(),
            bytes = digesting.byteCount
        )
    }
}

/** Reads and verifies a JSON Lines section file. */
internal class SectionReader(
    private val file: File,
    private val descriptor: SectionDescriptor?
) {
    suspend fun <T> read(serializer: KSerializer<T>, block: suspend (T) -> Unit): Int {
        if (!file.isFile) return 0
        if (descriptor != null && descriptor.sha256.isNotBlank()) {
            val actual = file.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().toHex()
            }
            if (actual != descriptor.sha256) {
                throw java.io.IOException("Backup section checksum mismatch: ${file.name}")
            }
        }
        var count = 0
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) {
                    block(backupJson.decodeFromString(serializer, line))
                    count++
                }
            }
        }
        if (descriptor != null && descriptor.count != count) {
            throw java.io.IOException("Backup section record count mismatch: ${file.name}")
        }
        return count
    }
}
