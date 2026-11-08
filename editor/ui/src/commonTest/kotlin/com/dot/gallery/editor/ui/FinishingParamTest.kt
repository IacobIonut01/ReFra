package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_temperature
import kotlin.test.Test
import kotlin.test.assertEquals

class FinishingParamTest {
    @Test
    fun unsignedReadoutsTrimOnlyFractionalZeroesAndKeepTheirUnits() {
        var value = 6500f
        val param = FinishingParam(
            key = "temperature",
            labelRes = Res.string.editor_temperature,
            displayRange = 2000f..12000f,
            step = 50f,
            neutral = 6500f,
            signed = false,
            unitSuffix = " K",
            read = { value },
            write = { edits, _ -> edits }
        )
        assertEquals("6500 K", param.displayOf(PhotoEdits()))
        value = 6500.25f
        assertEquals("6500.25 K", param.displayOf(PhotoEdits()))
        value = 0f
        assertEquals("0 K", param.displayOf(PhotoEdits()))
        value = 100f
        assertEquals("100 K", param.displayOf(PhotoEdits()))
    }
}
