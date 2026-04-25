package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.awxkee.aire.Aire
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter

data class Denoise(
    @param:FloatRange(from = 0.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = 0f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        if (value <= 0f) return bitmap
        val radius = (value * 10f).toInt().coerceIn(1, 10)
        return Aire.stackBlur(bitmap, radius, radius)
    }

    override fun revert(bitmap: Bitmap): Bitmap = bitmap

    override fun colorMatrix(): ColorMatrix? = null
}
