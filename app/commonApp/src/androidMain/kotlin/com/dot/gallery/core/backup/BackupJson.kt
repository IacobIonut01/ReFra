/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

/** Shared JSON configuration for manifests, preview headers, and section records. */
internal val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

internal fun FloatArray.toBase64(): String {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach(buffer::putFloat)
    return Base64.getEncoder().encodeToString(buffer.array())
}

internal fun floatArrayFromBase64(value: String): FloatArray? = runCatching {
    val bytes = Base64.getDecoder().decode(value)
    require(bytes.size % Float.SIZE_BYTES == 0)
    val floats = FloatArray(bytes.size / Float.SIZE_BYTES)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (index in floats.indices) floats[index] = buffer.float
    floats
}.getOrNull()

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
