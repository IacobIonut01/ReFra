package com.dot.gallery.editor.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The workbench's multi-window contract at the model level: opening a
 * second document must not close or dispose the first's session, and a
 * guarded close request targets exactly one window. Each document gets
 * its own id — the composition keys on it, so distinct documents are
 * distinct sessions.
 */
class EditorWorkbenchWindowTest {

    @Test
    fun openingSecondDocumentKeepsFirstSession() {
        val documents = WorkbenchDocuments()
        val first = documents.open(Path.of("/tmp/a.png"))
        val second = documents.open(Path.of("/tmp/b.mp4"))

        assertEquals(2, documents.windows.size)
        assertNotEquals(first.id, second.id)
        assertSame(first, documents.windows[0])
        assertEquals(EditorFileKind.Photo, first.kind)
        assertEquals(EditorFileKind.Video, second.kind)

        // A guarded close on the second window leaves the first's
        // close-request count — and the first window — untouched.
        second.closeRequest++
        documents.close(second)
        assertEquals(listOf(first), documents.windows)
        assertEquals(0, first.closeRequest)
    }

    @Test
    fun closingFirstDocumentKeepsSecondSession() {
        val documents = WorkbenchDocuments()
        val first = documents.open(Path.of("/tmp/a.png"))
        val second = documents.open(Path.of("/tmp/b.png"))

        first.closeRequest++
        documents.close(first)
        assertEquals(listOf(second), documents.windows)
        assertEquals(0, second.closeRequest)
        assertTrue(documents.windows.isNotEmpty())
    }
}
