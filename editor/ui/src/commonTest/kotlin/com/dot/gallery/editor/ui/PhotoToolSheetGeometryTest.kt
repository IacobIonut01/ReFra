package com.dot.gallery.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoToolSheetGeometryTest {
    @Test
    fun everyToolHasRoomToExpandBeyondItsUsablePeek() {
        photoToolEntries().forEach {
            assertTrue(photoToolSheetHeight(it.descriptor.id) > photoToolSheetPeek(it.descriptor.id))
        }
    }

    @Test
    fun materialTracksTravelBetweenTheActualPeekAndExpandedAnchors() {
        assertEquals(0f, photoToolSheetExpansion(548f, 768f, 600f, 220f))
        assertEquals(0.5f, photoToolSheetExpansion(358f, 768f, 600f, 220f))
        assertEquals(1f, photoToolSheetExpansion(168f, 768f, 600f, 220f))
        assertEquals(0f, photoToolSheetExpansion(548.2f, 768f, 600f, 220f))
        assertEquals(1f, photoToolSheetExpansion(-20f, 768f, 600f, 220f))
    }

    @Test
    fun graphPeeksAndUnmeasuredLayoutsDoNotInheritTheLightAnchors() {
        assertEquals(0f, photoToolSheetExpansion(464f, 768f, 600f, 304f))
        assertEquals(1f, photoToolSheetExpansion(168f, 768f, 600f, 304f))
        assertEquals(0f, photoToolSheetExpansion(Float.NaN, 768f, 600f, 220f))
        assertEquals(0f, photoToolSheetExpansion(0f, 0f, 0f, 220f))
        assertEquals(1f, photoToolSheetExpansion(0f, 200f, 200f, 220f))
    }
}
