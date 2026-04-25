package com.dot.gallery.feature_node.presentation.edit.adjustments.filters

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorMatrix
import com.dot.gallery.feature_node.domain.model.editor.ImageFilter
import com.dot.gallery.feature_node.presentation.util.applyColorMatrix

data class Sepia(override val name: String = "Sepia") : ImageFilter {

    override fun colorMatrix(): ColorMatrix = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    override fun apply(bitmap: Bitmap): Bitmap =
        applyColorMatrix(bitmap, colorMatrix().values)

}