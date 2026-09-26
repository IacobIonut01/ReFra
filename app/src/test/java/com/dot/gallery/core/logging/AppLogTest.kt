/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppLogTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("applog-test").toFile()
        AppLog.resetForTests()
        AppLog.initDir(dir)
    }

    @After
    fun tearDown() {
        AppLog.resetForTests()
        dir.deleteRecursively()
    }

    private fun awaitFileWritten(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        assertTrue("log file never received the entry", predicate())
    }

    @Test
    fun `entries are dropped while developer mode is off`() {
        AppLog.setEnabled(false)
        AppLog.log(LogLevel.ERROR, "test", "should not persist")
        AppLog.log(LogLevel.INFO, "test", "also not")
        val file = AppLog.sessionFiles().firstOrNull()
        // Either no file was created or it stayed empty
        assertTrue(file == null || file.length() == 0L)
    }

    @Test
    fun `enabled logging persists sanitized entries to disk`() {
        AppLog.setEnabled(true)
        AppLog.log(
            LogLevel.ERROR, "worker.upload", "upload of /storage/emulated/0/x.jpg failed",
            ctx = mapOf("files" to "3"), scope = "worker.upload"
        )
        awaitFileWritten { AppLog.readEntries().any { it.tag == "worker.upload" } }
        val entry = AppLog.readEntries().first { it.tag == "worker.upload" }
        assertEquals("ERROR", entry.level)
        assertTrue(entry.message.contains("<path>/*.jpg"))
        assertEquals(mapOf("files" to "3"), entry.ctx)
        assertEquals("worker.upload", entry.scope)
    }

    @Test
    fun `min level filters lower severities`() {
        AppLog.minLevel = LogLevel.WARN
        AppLog.setEnabled(true)
        AppLog.log(LogLevel.DEBUG, "t", "debug msg")
        AppLog.log(LogLevel.WARN, "t", "warn msg")
        awaitFileWritten { AppLog.readEntries().any { it.message == "warn msg" } }
        assertFalse(AppLog.readEntries().any { it.message == "debug msg" })
    }

    @Test
    fun `screen attribution is stamped from current screen`() {
        AppLog.setEnabled(true)
        AppLog.setScreen("LibraryScreen")
        AppLog.log(LogLevel.INFO, "t", "on library")
        awaitFileWritten { AppLog.readEntries().any { it.screen == "LibraryScreen" } }
        AppLog.setScreen(null)
    }

    @Test
    fun `breadcrumbs attach to errors`() {
        AppLog.setEnabled(true)
        AppLog.breadcrumb("navigate", mapOf("to" to "LibraryScreen"))
        AppLog.breadcrumb("tap", mapOf("action" to "delete"))
        AppLog.log(LogLevel.ERROR, "t", "boom")
        awaitFileWritten { AppLog.readEntries().any { it.message == "boom" && it.crumbs != null } }
        val entry = AppLog.readEntries().first { it.message == "boom" }
        assertTrue(entry.crumbs!!.any { it.label == "navigate" })
        assertTrue(entry.crumbs!!.any { it.label == "tap" })
    }

    @Test
    fun `grouped by area separates screens scopes and ungrouped`() {
        val entries = listOf(
            LogEntry(1, "ERROR", "t", "a", screen = "LibraryScreen"),
            LogEntry(2, "ERROR", "t", "b", screen = "LibraryScreen"),
            LogEntry(3, "WARN", "t", "c", scope = "worker.upload"),
            LogEntry(4, "INFO", "t", "d"),
        )
        val groups = AppLog.groupedByArea(entries)
        val byId = groups.associateBy { it.id }
        assertEquals(2, byId["LibraryScreen"]!!.entries.size)
        assertEquals(AreaGroup.Kind.SCREEN, byId["LibraryScreen"]!!.kind)
        assertEquals(AreaGroup.Kind.BACKGROUND, byId["worker.upload"]!!.kind)
        assertEquals(AreaGroup.Kind.UNGROUPED, byId["ungrouped"]!!.kind)
        // Group with errors sorts first
        assertEquals("LibraryScreen", groups.first().id)
    }

    @Test
    fun `crash file lifecycle tracks seen and reported sidecars`() {
        AppLog.setEnabled(true)
        AppLog.writeCrashSync(Thread.currentThread(), RuntimeException("kaboom"))
        val crash = AppLog.unreportedCrash()
        assertNotNull(crash)
        assertEquals(crash, AppLog.unseenCrash())

        AppLog.markCrashSeen(crash!!)
        assertNull(AppLog.unseenCrash())
        assertNotNull(AppLog.unreportedCrash())

        AppLog.markCrashReported(crash)
        assertNull(AppLog.unreportedCrash())
    }

    @Test
    fun `crash entries appear in readEntries`() {
        AppLog.setEnabled(true)
        AppLog.writeCrashSync(Thread.currentThread(), IllegalStateException("bad"))
        val entries = AppLog.readEntries()
        assertTrue(entries.any { it.logLevel == LogLevel.CRASH })
    }

    @Test
    fun `clear logs deletes files and keeps logging`() {
        AppLog.setEnabled(true)
        AppLog.log(LogLevel.ERROR, "t", "before clear")
        awaitFileWritten { AppLog.readEntries().isNotEmpty() }
        AppLog.clearLogs()
        // A fresh session-start event is emitted on clear, so only the old
        // entry is guaranteed gone.
        assertTrue(AppLog.readEntries().none { it.message == "before clear" })
        // Store still works after clearing
        AppLog.log(LogLevel.ERROR, "t", "after clear")
        awaitFileWritten { AppLog.readEntries().any { it.message == "after clear" } }
    }

    @Test
    fun `stats reflect the store`() {
        AppLog.setEnabled(true)
        AppLog.log(LogLevel.ERROR, "t", "something")
        awaitFileWritten { AppLog.stats().totalBytes > 0 }
        val stats = AppLog.stats()
        assertTrue(stats.sessionCount >= 1)
        assertTrue(stats.totalBytes > 0)
        assertFalse(stats.unreportedCrash)
    }
}
