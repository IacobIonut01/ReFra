/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class DatabaseFileStateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun db(bytes: ByteArray?): File = File(folder.root, "internal_db").apply {
        if (bytes != null) writeBytes(bytes)
    }

    @Test
    fun missingOrEmptyFileIsAbsent() {
        assertEquals(DatabaseFileState.ABSENT, DatabaseFileState.of(db(null)))
        assertEquals(DatabaseFileState.ABSENT, DatabaseFileState.of(db(ByteArray(0))))
    }

    @Test
    fun sqliteHeaderIsPlaintext() {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertEquals(DatabaseFileState.PLAINTEXT, DatabaseFileState.of(db(header + ByteArray(4080))))
    }

    @Test
    fun sqlcipherFileIsNotPlaintext() {
        // SQLCipher starts the file with a random salt instead of the SQLite header,
        // which is why the plaintext driver reports "file is not a database" (code 26).
        assertEquals(DatabaseFileState.NOT_PLAINTEXT, DatabaseFileState.of(db(Random(7).nextBytes(4096))))
    }

    @Test
    fun truncatedHeaderIsNotPlaintext() {
        assertEquals(DatabaseFileState.NOT_PLAINTEXT, DatabaseFileState.of(db("SQLite".toByteArray())))
    }

    @Test
    fun moveAsideMovesDatabaseAndSidecarsAndReplacesEarlierCopy() {
        val content = Random(1).nextBytes(4096)
        val file = db(content)
        File(file.path + "-wal").writeText("wal")
        File(file.path + "-shm").writeText("shm")
        File(folder.root, "internal_db.undecryptable").writeText("older copy")
        File(folder.root, "internal_db.undecryptable-journal").writeText("older journal")

        val moved = DatabaseFileState.moveAside(file)

        assertEquals("internal_db.undecryptable", moved.name)
        assertArrayEquals(content, moved.readBytes())
        assertEquals("wal", File(moved.path + "-wal").readText())
        assertEquals("shm", File(moved.path + "-shm").readText())
        assertFalse(File(moved.path + "-journal").exists())
        assertFalse(file.exists())
        DatabaseFileState.companions(file).forEach { assertFalse(it.exists()) }
        assertEquals(DatabaseFileState.ABSENT, DatabaseFileState.of(file))
        assertTrue(moved.exists())
    }
}
