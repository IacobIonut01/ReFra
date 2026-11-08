package com.dot.gallery.editor.ui.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.dot.gallery.editor.ui.EditorToolIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoAdaptiveLayoutTest {

    @Test
    fun audioAndDetailToolsAreToolFirst() {
        listOf(
            EditorToolIds.VideoMixer, EditorToolIds.VideoEq, EditorToolIds.VideoDucking,
            EditorToolIds.VideoRouting, EditorToolIds.VideoClipAudio, EditorToolIds.VideoVoice
        ).forEach { assertTrue(videoToolIsToolFirst(it), it.value) }
        listOf(
            EditorToolIds.VideoTrim, EditorToolIds.VideoArrange, EditorToolIds.VideoTransform,
            EditorToolIds.VideoColour, EditorToolIds.VideoLayers
        ).forEach { assertFalse(videoToolIsToolFirst(it), it.value) }
        assertFalse(videoToolIsToolFirst(null))
    }

    @Test
    fun upwardScrollCollapsesThePreviewBeforeTheLanes() {
        val collapse = VideoStageCollapse(rangePx = 100f, initial = 0f)
        // The preview takes the first 100 px of an upward scroll...
        assertEquals(Offset(0f, -60f), collapse.onPreScroll(Offset(0f, -60f), NestedScrollSource.UserInput))
        assertEquals(60f, collapse.offset)
        // ...and passes the remainder on once it is fully docked.
        assertEquals(Offset(0f, -40f), collapse.onPreScroll(Offset(0f, -70f), NestedScrollSource.UserInput))
        assertEquals(100f, collapse.offset)
        assertEquals(Offset.Zero, collapse.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput))
    }

    @Test
    fun downwardScrollRestoresThePreviewOnlyAfterTheLanes() {
        val collapse = VideoStageCollapse(rangePx = 100f, initial = 100f)
        // Downward deltas go to the lanes first: pre-scroll never grows it.
        assertEquals(Offset.Zero, collapse.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput))
        assertEquals(100f, collapse.offset)
        // What the lanes leave over (they are at their top) restores it.
        assertEquals(
            Offset(0f, 30f),
            collapse.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput)
        )
        assertEquals(70f, collapse.offset)
        assertEquals(
            Offset(0f, 70f),
            collapse.onPostScroll(Offset.Zero, Offset(0f, 200f), NestedScrollSource.UserInput)
        )
        assertEquals(0f, collapse.offset)
    }
}
