package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix

data class BlueTone(
    @param:FloatRange(from = -1.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = -1f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        return applyColorMatrix(bitmap, colorMatrix().values)
    }

    override fun revert(bitmap: Bitmap): Bitmap = BlueTone(-value).apply(bitmap)

    override fun colorMatrix(): ColorMatrix {
        val v = value
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f + v * 0.4f, 0f, v * 20f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}
