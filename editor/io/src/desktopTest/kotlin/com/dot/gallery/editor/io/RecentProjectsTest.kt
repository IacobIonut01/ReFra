package com.dot.gallery.editor.io

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recents index: newest-first, deduplicated by reference, capped,
 * tolerant of a corrupt index file (it is a convenience, not an
 * authority), and riding the store's atomic write.
 */
class RecentProjectsTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "recent-projects-test-${System.nanoTime()}"
    )

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun harness(maxEntries: Int = 16): RecentProjects =
        RecentProjects(FileProjectStore(root), maxEntries = maxEntries)

    private fun entry(id: Int) = RecentProjects.Entry(
        name = "pic-$id.png",
        reference = "/media/pic-$id.png",
        projectFile = "photo-$id.json"
    )

    @Test
    fun `record lists newest first and round-trips`() {
        val recents = harness()
        recents.record(entry(1))
        recents.record(entry(2))
        assertEquals(listOf(entry(2), entry(1)), recents.list())
    }

    @Test
    fun `re-recording a reference moves it to the front`() {
        val recents = harness()
        recents.record(entry(1))
        recents.record(entry(2))
        recents.record(entry(1).copy(name = "renamed.png"))
        val list = recents.list()
        assertEquals(2, list.size)
        assertEquals("renamed.png", list[0].name)
        assertEquals(entry(2), list[1])
    }

    @Test
    fun `the index is capped`() {
        val recents = harness(maxEntries = 3)
        repeat(5) { recents.record(entry(it)) }
        assertEquals(
            listOf(entry(4), entry(3), entry(2)),
            recents.list()
        )
    }

    @Test
    fun `remove drops one reference and clear empties the file`() {
        val recents = harness()
        recents.record(entry(1))
        recents.record(entry(2))
        recents.remove("/media/pic-1.png")
        assertEquals(listOf(entry(2)), recents.list())
        recents.clear()
        assertTrue(recents.list().isEmpty())
        assertTrue(File(root, "recents.json").let { !it.exists() })
    }

    @Test
    fun `a missing or corrupt index reads as empty`() {
        val store = FileProjectStore(root)
        assertTrue(RecentProjects(store).list().isEmpty())
        store.write("recents.json", "{ not json")
        assertTrue(RecentProjects(store).list().isEmpty())
        // A shaped-but-wrong payload is empty too, never a crash.
        store.write("recents.json", "{\"version\":1,\"items\":\"x\"}")
        assertTrue(RecentProjects(store).list().isEmpty())
    }
}
