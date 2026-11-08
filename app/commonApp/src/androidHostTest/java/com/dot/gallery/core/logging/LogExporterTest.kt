/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.dot.gallery.core.logging.LogEntry


class LogExporterTest {

    @Test
    fun `issue url contains template labels and encoded title`() {
        val url = LogExporter.issueUrl("[BUG] A crash in Library", "body text")
        assertTrue(url.startsWith(LogExporter.ISSUES_NEW_URL))
        assertTrue(url.contains("template=bug_report.md"))
        assertTrue(url.contains("labels=bug"))
        assertTrue(url.contains("title=%5BBUG%5D%20A%20crash%20in%20Library"))
        assertTrue(url.contains("body=body%20text"))
    }

    @Test
    fun `issue url stays under the cap for huge bodies`() {
        val body = "x".repeat(50_000)
        val url = LogExporter.issueUrl("t", body)
        assertTrue(url.length <= 7_500)
        assertTrue(url.endsWith("%0A%5Btruncated%5D"))
    }

    @Test
    fun `entry text wraps the entry as fenced json`() {
        val entry = LogEntry(1, "ERROR", "tag", "message")
        val text = LogExporter.entryText(entry)
        assertTrue(text.startsWith("```json"))
        assertTrue(text.contains("\"tag\":\"tag\""))
        assertTrue(text.trim().endsWith("```"))
    }

    @Test
    fun `log block keeps the newest entries within the clipboard budget`() {
        val entries = (0 until 5_000).map { LogEntry(5_000L - it, "DEBUG", "tag", "message $it ${"x".repeat(900)}") }
        val block = LogExporter.logBlock(entries, forGithub = true, maxChars = LogExporter.MAX_GITHUB_LOG_CHARS)
        assertTrue(block.length < 65_536)
        assertTrue(block.contains("message 0 "))
        assertFalse(block.contains("message 4999 "))
        assertTrue(block.contains("older entries omitted"))
        assertTrue(block.contains("<summary>Logs ("))
    }

    @Test
    fun `log block keeps every entry when under the budget`() {
        val entries = listOf(LogEntry(2, "ERROR", "tag", "b"), LogEntry(1, "INFO", "tag", "a"))
        val block = LogExporter.logBlock(entries, forGithub = false, maxChars = LogExporter.MAX_CLIPBOARD_LOG_CHARS)
        assertEquals(2, block.lines().count { it.startsWith("{") })
        assertFalse(block.contains("omitted"))
    }
}
