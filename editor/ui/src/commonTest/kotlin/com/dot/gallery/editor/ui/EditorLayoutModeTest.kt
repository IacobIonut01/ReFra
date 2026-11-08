package com.dot.gallery.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditorLayoutModeTest {

    @Test
    fun phonePortraitIsCompact() {
        assertEquals(EditorLayoutMode.Compact, EditorLayoutMode.resolve(390f, 844f))
    }

    @Test
    fun landscapePhoneGetsSidebar() {
        assertEquals(EditorLayoutMode.Sidebar, EditorLayoutMode.resolve(800f, 360f))
    }

    @Test
    fun largeNearSquareWindowGetsMasterDetail() {
        assertEquals(EditorLayoutMode.MasterDetail, EditorLayoutMode.resolve(1000f, 800f))
        // The tablet reference: 1280x1024 keeps the two-pane master-detail.
        assertEquals(EditorLayoutMode.MasterDetail, EditorLayoutMode.resolve(1280f, 1024f))
    }

    @Test
    fun wideDesktopKeepsTheFourPaneWorkbench() {
        // >=1200x600 at 1.4:1 or wider switches to the four-pane Desktop
        // composition; a 4:3 window of the same width does not.
        assertEquals(EditorLayoutMode.Desktop, EditorLayoutMode.resolve(1280f, 800f))
        assertEquals(EditorLayoutMode.Desktop, EditorLayoutMode.resolve(1440f, 960f))
        assertEquals(EditorLayoutMode.MasterDetail, EditorLayoutMode.resolve(1600f, 1200f))
    }

    @Test
    fun narrowDesktopWindowStaysCompact() {
        assertEquals(EditorLayoutMode.Compact, EditorLayoutMode.resolve(500f, 600f))
    }

    @Test
    fun squareWindowStaysCompact() {
        // 700dp wide is enough for a sidebar but not wider than tall.
        assertEquals(EditorLayoutMode.Compact, EditorLayoutMode.resolve(700f, 700f))
    }

    @Test
    fun sidebarNeedsCanvasRoom() {
        // 700x360 qualifies (412dp left for the canvas); 690x360 would not.
        assertEquals(EditorLayoutMode.Sidebar, EditorLayoutMode.resolve(700f, 360f))
        assertEquals(EditorLayoutMode.Compact, EditorLayoutMode.resolve(690f, 360f))
    }

    @Test
    fun masterDetailNeedsBothDimensions() {
        // Wide but short -> plain sidebar, not master-detail.
        assertEquals(EditorLayoutMode.Sidebar, EditorLayoutMode.resolve(1100f, 500f))
    }

    @Test
    fun invalidDimsRejected() {
        assertFailsWith<IllegalArgumentException> { EditorLayoutMode.resolve(0f, 800f) }
        assertFailsWith<IllegalArgumentException> { EditorLayoutMode.resolve(390f, 0f) }
        assertFailsWith<IllegalArgumentException> { EditorLayoutMode.resolve(-1f, 800f) }
        assertFailsWith<IllegalArgumentException> {
            EditorLayoutMode.resolve(Float.NaN, 800f)
        }
        assertFailsWith<IllegalArgumentException> {
            EditorLayoutMode.resolve(390f, Float.POSITIVE_INFINITY)
        }
    }
}
