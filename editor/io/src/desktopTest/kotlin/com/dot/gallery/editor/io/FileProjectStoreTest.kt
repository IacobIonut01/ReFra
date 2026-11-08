package com.dot.gallery.editor.io

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FileProjectStore]'s durability contract: every write stages to a
 * same-directory temp, fsyncs and renames — a failed write leaves the
 * previous file authoritative and no staging litter behind. Names are
 * single path segments, so a stored name can never escape the root.
 */
class FileProjectStoreTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "file-project-store-test-${System.nanoTime()}"
    )

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store() = FileProjectStore(root)

    @Test
    fun `write then read round-trips and leaves no staging`() {
        store().write("a.json", "{\"version\":1}")
        assertEquals("{\"version\":1}", store().read("a.json"))
        // The commit is the only artifact — the temp name is gone.
        assertEquals(listOf("a.json"), store().list())
        assertFalse(File(root, "a.json.tmp").exists())
    }

    @Test
    fun `a second write replaces atomically`() {
        store().write("a.json", "old")
        store().write("a.json", "new")
        assertEquals("new", store().read("a.json"))
    }

    @Test
    fun `read of a missing file is null, delete is quiet`() {
        assertNull(store().read("none.json"))
        store().delete("none.json")
        assertFalse(store().exists("none.json"))
    }

    @Test
    fun `list sorts stored names and skips staging`() {
        store().write("b.json", "2")
        store().write("a.json", "1")
        File(root, "stray.tmp").writeText("partial")
        assertEquals(listOf("a.json", "b.json"), store().list())
    }

    @Test
    fun `a failed write keeps the previous file and no staging`() {
        store().write("a.json", "good")
        // An unwritable target directory fails the rename — the old
        // bytes stay authoritative and the temp is cleaned up.
        val readOnly = File(
            System.getProperty("java.io.tmpdir"),
            "file-project-store-ro-${System.nanoTime()}"
        )
        try {
            readOnly.mkdirs()
            File(readOnly, "a.json").writeText("good")
            readOnly.setWritable(false)
            FileProjectStore(readOnly).apply {
                assertFailsWith<Exception> { write("a.json", "bad") }
                assertFalse(File(readOnly, "a.json.tmp").exists())
                // The old bytes are still authoritative.
                assertEquals("good", read("a.json"))
            }
        } finally {
            readOnly.setWritable(true)
            readOnly.deleteRecursively()
        }
    }

    @Test
    fun `names are single path segments only`() {
        for (bad in listOf("../x", "a/b", "a\\b", "", "  ")) {
            assertFailsWith<IllegalArgumentException> {
                store().write(bad, "x")
            }
        }
    }
}
