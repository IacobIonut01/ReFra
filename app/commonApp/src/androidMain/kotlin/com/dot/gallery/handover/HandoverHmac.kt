/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Request signing for `HANDOVER_AUTH_MODE=hmac` (docs/protocol.md):
 *
 *     X-Handover-Ts         unix seconds (±5 min skew)
 *     X-Handover-Nonce      random per request (replay cache)
 *     X-Handover-Signature  hex(HMAC_SHA256(secret,
 *         "$ts\n$nonce\n$METHOD\n$path[+?query]\n$sha256hex(body)"))
 *
 * The [target] is `path` plus `"?" + rawQuery` when present — the server binds
 * the query into the signed message so replayed signatures cannot be re-scoped.
 */
internal object HandoverHmac {

    const val HEADER_TS = "X-Handover-Ts"
    const val HEADER_NONCE = "X-Handover-Nonce"
    const val HEADER_SIGNATURE = "X-Handover-Signature"

    fun signature(
        secret: String,
        tsSeconds: String,
        nonce: String,
        method: String,
        target: String,
        body: ByteArray,
    ): String {
        val bodyHash = sha256Hex(body)
        val message = "$tsSeconds\n$nonce\n$method\n$target\n$bodyHash"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256"))
        return mac.doFinal(message.encodeToByteArray()).toHex()
    }

    /** Fresh ts/nonce + signature headers for one request. */
    fun headers(
        secret: String,
        method: String,
        target: String,
        body: ByteArray,
    ): Map<String, String> {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        return mapOf(
            HEADER_TS to ts,
            HEADER_NONCE to nonce,
            HEADER_SIGNATURE to signature(secret, ts, nonce, method, target, body),
        )
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        val digits = "0123456789abcdef"
        for (i in indices) {
            out[i * 2] = digits[(this[i].toInt() shr 4) and 0xF]
            out[i * 2 + 1] = digits[this[i].toInt() and 0xF]
        }
        return String(out)
    }
}
