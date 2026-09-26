/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import java.security.MessageDigest

/**
 * Single choke point for PII removal. [AppLog] sanitizes every message, context
 * value and stack trace before it reaches disk, so whatever the developer
 * screen shows is already safe to copy or share.
 *
 * What is kept on purpose: media file extensions (format bugs need them),
 * MediaStore ids (not identifying), numeric ids, and stable short host hashes
 * so repeated errors against the same server correlate without revealing it.
 */
object LogSanitizer {

    private val URL_REGEX = Regex("""\b(https?)://([^\s/"'<>]+)([^\s?"'<>]*)(\?[^\s"'<>]*)?""")
    private val CONTENT_URI_REGEX = Regex("""content://[^\s"'<>)\]]+""")
    private val FILE_PATH_REGEX =
        Regex("""/(?:storage|sdcard|mnt|data|vendor|system|product|apex|oem)[^\s"'<>):]*""")
    private val EMAIL_REGEX = Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+""")
    private val IPV4_REGEX = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
    private val IPV6_REGEX = Regex("""\b(?:[0-9a-fA-F]{1,4}:){3,}[0-9a-fA-F:%]{1,8}\b""")
    private val COORDS_REGEX = Regex("""\b-?\d{1,3}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,}\b""")
    private val FILENAME_REGEX = Regex(
        """\b[\w][\w .()\-]*\.(?:jpe?g|jpf|png|gif|heic|heifs?|avif|jxl|webp|mp4|m4v|mov|mkv|webm|3gp|mts|m2ts|avi|dng|cr2|cr3|nef|nrw|arw|orf|rw2|pef|srw|x3f|raf|raw|tiff?|psd|jp2|j2k|svg|bmp|mp3|aac|flac|ogg|wav|zip)\b""",
        RegexOption.IGNORE_CASE
    )
    private val SENSITIVE_KEY_REGEX = Regex(
        """token|pass|secret|auth|cookie|cred|key|pwd|signature|bearer|api_?key|session""",
        RegexOption.IGNORE_CASE
    )

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        out = URL_REGEX.replace(out) { m ->
            val scheme = m.groupValues[1]
            val host = m.groupValues[2]
            val path = sanitizeUrlPath(m.groupValues[3])
            "$scheme://${hostHash(host)}$path"
        }
        out = CONTENT_URI_REGEX.replace(out) { m ->
            val uri = m.value
            val id = uri.takeLastWhile { it.isDigit() }
            if (id.isNotEmpty()) "content://media/<$id>" else "content://<redacted>"
        }
        out = FILE_PATH_REGEX.replace(out) { m ->
            val base = m.value.substringAfterLast('/')
            val ext = base.substringAfterLast('.', "")
            if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) "<path>/*.$ext" else "<path>"
        }
        out = EMAIL_REGEX.replace(out, "<email>")
        out = IPV4_REGEX.replace(out, "<ip>")
        out = IPV6_REGEX.replace(out, "<ip6>")
        out = COORDS_REGEX.replace(out, "<coords>")
        out = FILENAME_REGEX.replace(out) { m ->
            "*." + m.value.substringAfterLast('.')
        }
        return out
    }

    /**
     * Redacts sensitive context keys outright (tokens, passwords, auth, cookies,
     * keys, signatures) and sanitizes the remaining values with [sanitize].
     */
    fun sanitizeContext(ctx: Map<String, String>): Map<String, String> =
        ctx.mapValues { (key, value) ->
            if (SENSITIVE_KEY_REGEX.containsMatchIn(key)) "<redacted>" else sanitize(value)
        }

    private fun sanitizeUrlPath(path: String): String =
        path.split('/').joinToString("/") { seg ->
            when {
                seg.isEmpty() -> seg
                seg.length > 20 || seg.contains('.') || seg.any { it.isDigit() } -> "*"
                else -> seg
            }
        }

    private fun hostHash(host: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(host.toByteArray())
        return "h_" + digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
