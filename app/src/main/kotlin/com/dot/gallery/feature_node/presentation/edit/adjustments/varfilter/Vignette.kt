package com.dot.gallery.feature_node.presentation.edit.adjustments.varfilter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.VariableFilter
import kotlin.math.max

data class Vignette(
    @param:FloatRange(from = 0.0, to = 1.0)
    override val value: Float = 0f
) : VariableFilter {
    override val maxValue = 1f
    override val minValue = 0f
    override val defaultValue = 0f

    override fun apply(bitmap: Bitmap): Bitmap {
        if (value <= 0f) return bitmap
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = max(cx, cy) * 1.2f
        val alpha = (value * 200f).toInt().coerceIn(0, 255)
        val gradient = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, 0x00000000, android.graphics.Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        return result
    }

    override fun revert(bitmap: Bitmap): Bitmap = bitmap // Vignette is not easily reversible

    override fun colorMatrix(): ColorMatrix? = null // Cannot be represented as ColorMatrix
}
