package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.time.EditorTime
import com.dot.gallery.editor.core.time.FrameRate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MediaAssetTest {

    private val size = PixelSize(4, 2)

    @Test
    fun `image requires non-blank identity`() {
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Image("", "content://1", size)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Image("a", " ", size)
        }
        MediaAsset.Image("a", "content://1", size)
    }

    @Test
    fun `video requires identity and positive duration`() {
        val rate = FrameRate(30)
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video(" ", "ref", size, EditorTime(1), rate, false)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video("v", "", size, EditorTime(1), rate, false)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video("v", "ref", size, EditorTime.Zero, rate, false)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video("v", "ref", size, EditorTime(-1), rate, true)
        }
        MediaAsset.Video("v", "ref", size, EditorTime(1), rate, true)
    }

    @Test
    fun `video frame rate may be null and rotation is quarter-turn only`() {
        // Unknown / unrepresentable source cadence is representable.
        MediaAsset.Video("v", "ref", size, EditorTime(1), null, true)
        // Display rotation accepts multiples of 90 in either direction.
        MediaAsset.Video("v", "ref", size, EditorTime(1), null, true, -270)
        MediaAsset.Video("v", "ref", size, EditorTime(1), null, true, 90)
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video("v", "ref", size, EditorTime(1), null, true, 45)
        }
        assertFailsWith<IllegalArgumentException> {
            MediaAsset.Video("v", "ref", size, EditorTime(1), null, true, 89)
        }
    }
}
