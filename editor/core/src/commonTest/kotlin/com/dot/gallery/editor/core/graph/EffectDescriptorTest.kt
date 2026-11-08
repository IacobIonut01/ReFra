package com.dot.gallery.editor.core.graph

import com.dot.gallery.editor.core.time.EditorTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EffectDescriptorTest {

    @Test
    fun `standard descriptors exist with pinned identity`() {
        assertEquals("source.image", EffectDescriptor.SourceImage.id)
        assertEquals(1, EffectDescriptor.SourceImage.version)
        assertEquals(0, EffectDescriptor.SourceImage.inputCount)
        assertEquals("color.basic", EffectDescriptor.ColorBasic.id)
        // v2 added the tonal operation (highlights/shadows/whites/blacks).
        assertEquals(2, EffectDescriptor.ColorBasic.version)
        assertEquals(1, EffectDescriptor.ColorBasic.inputCount)
    }

    @Test
    fun `invalid descriptors are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            EffectDescriptor(" ", 1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            EffectDescriptor("e", 0, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            EffectDescriptor("e", 1, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            EffectDescriptor("e", 1, 0, haloPixels = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            EffectDescriptor("e", 1, 0, temporalRadius = EditorTime(-1))
        }
        EffectDescriptor("e", 1, 2, haloPixels = 4, temporalRadius = EditorTime(100))
    }
}
