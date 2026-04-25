package com.dot.gallery.feature_node.presentation.edit.adjustments.filters

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix

data class Warm(override val name: String = "Warm") : ImageFilter {

    override fun colorMatrix(): ColorMatrix = ColorMatrix(
        floatArrayOf(
            1.2f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    override fun apply(bitmap: Bitmap): Bitmap =
        applyColorMatrix(bitmap, colorMatrix().values)
}