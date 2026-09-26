/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `file path is redacted keeping the extension`() {
        val out = LogSanitizer.sanitize(
            "failed to copy /storage/emulated/0/DCIM/Camera/IMG_20240101.jpg"
        )
        assertTrue(out.contains("<path>/*.jpg"))
        assertFalse(out.contains("IMG_20240101"))
        assertFalse(out.contains("DCIM"))
    }

    @Test
    fun `file path without extension becomes generic placeholder`() {
        val out = LogSanitizer.sanitize("cannot read /data/user/0/com.dot.gallery/cache/blob")
        assertTrue(out.contains("<path>"))
        assertFalse(out.contains("blob"))
    }

    @Test
    fun `url keeps scheme and hashed host`() {
        val out = LogSanitizer.sanitize("GET https://photos.example.com/api/albums failed")
        assertTrue(out.startsWith("GET https://h_"))
        assertFalse(out.contains("photos.example.com"))
    }

    @Test
    fun `same host produces same hash for correlation`() {
        val a = LogSanitizer.sanitize("https://server.lan:2283/api/ping")
        val b = LogSanitizer.sanitize("https://server.lan:2283/api/other")
        val hostA = a.substringAfter("https://").substringBefore("/")
        val hostB = b.substringAfter("https://").substringBefore("/")
        assertEquals(hostA, hostB)
        assertTrue(hostA.startsWith("h_"))
    }

    @Test
    fun `different hosts produce different hashes`() {
        val a = LogSanitizer.sanitize("https://a.example.com/x")
        val b = LogSanitizer.sanitize("https://b.example.com/x")
        assertTrue(a != b)
    }

    @Test
    fun `email is redacted`() {
        val out = LogSanitizer.sanitize("login failed for john.doe@example.com")
        assertEquals("login failed for <email>", out)
    }

    @Test
    fun `ipv4 with port is redacted`() {
        val out = LogSanitizer.sanitize("connection to 192.168.1.104:8080 refused")
        assertEquals("connection to <ip> refused", out)
    }

    @Test
    fun `gps coordinates are redacted`() {
        val out = LogSanitizer.sanitize("location 44.4268, 26.1025 indexed")
        assertTrue(out.contains("<coords>"))
        assertFalse(out.contains("44.4268"))
    }

    @Test
    fun `bare media filename keeps extension only`() {
        val out = LogSanitizer.sanitize("skipped VID_holiday_2024.MP4 (duplicate)")
        assertTrue(out.contains("*.MP4"))
        assertFalse(out.contains("holiday"))
    }

    @Test
    fun `content uri keeps trailing media id`() {
        val out = LogSanitizer.sanitize(
            "query failed for content://media/external/images/media/1042"
        )
        assertEquals("query failed for content://media/<1042>", out)
    }

    @Test
    fun `plain message is unchanged`() {
        val msg = "album refresh finished in 240 ms with 12 items"
        assertEquals(msg, LogSanitizer.sanitize(msg))
    }

    @Test
    fun `sensitive context keys are fully redacted`() {
        val ctx = mapOf(
            "files" to "12",
            "auth_token" to "abc123secret",
            "server_password" to "hunter2",
            "endpoint" to "https://immich.home/api",
        )
        val out = LogSanitizer.sanitizeContext(ctx)
        assertEquals("12", out["files"])
        assertEquals("<redacted>", out["auth_token"])
        assertEquals("<redacted>", out["server_password"])
        assertTrue(out["endpoint"]!!.startsWith("https://h_"))
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", LogSanitizer.sanitize(""))
    }
}
