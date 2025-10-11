package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.awxkee.aire.Aire
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter

data class Brightness(
    @param:FloatRange(from = -1.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = -1f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        return Aire.brightness(bitmap, value)
    }

    override fun revert(bitmap: Bitmap): Bitmap {
        return Aire.brightness(bitmap, -value)
    }

    override fun colorMatrix(): ColorMatrix =
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, value * 255,
                0f, 1f, 0f, 0f, value * 255,
                0f, 0f, 1f, 0f, value * 255,
                0f, 0f, 0f, 1f, 0f
            )
        )
}





