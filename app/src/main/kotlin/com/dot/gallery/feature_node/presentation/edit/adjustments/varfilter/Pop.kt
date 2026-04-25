package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix

data class Pop(
    @param:FloatRange(from = 0.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = 0f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        if (value <= 0f) return bitmap
        return applyColorMatrix(bitmap, colorMatrix().values)
    }

    override fun revert(bitmap: Bitmap): Bitmap = bitmap // Not easily reversible

    override fun colorMatrix(): ColorMatrix {
        val contrast = 1f + value * 0.5f
        val saturation = 1f + value * 0.15f
        val cm1 = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 128f * (1 - contrast),
            0f, contrast, 0f, 0f, 128f * (1 - contrast),
            0f, 0f, contrast, 0f, 128f * (1 - contrast),
            0f, 0f, 0f, 1f, 0f
        ))
        val s = saturation
        val cm2 = ColorMatrix(floatArrayOf(
            0.213f * (1 - s) + s, 0.715f * (1 - s), 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s) + s, 0.072f * (1 - s), 0f, 0f,
            0.213f * (1 - s), 0.715f * (1 - s), 0.072f * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm1.timesAssign(cm2)
        return cm1
    }
}
