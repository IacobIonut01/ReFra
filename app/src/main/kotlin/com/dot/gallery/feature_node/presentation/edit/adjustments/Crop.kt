package com.dot.gallery.feature_node.presentation.edit.adjustments

import android.graphics.Bitmap
import android.graphics.RectF
import com.dot.gallery.feature_node.domain.model.editor.Adjustment

/**
 * Crop adjustment that stores a normalized crop rect (0-1 range).
 * [apply] crops [bitmap] using the normalized rect, producing a full-resolution result
 * regardless of the preview bitmap size used during editing.
 */
data class Crop(val normalizedRect: RectF): Adjustment {

    override fun apply(bitmap: Bitmap): Bitmap {
        val x = (normalizedRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val y = (normalizedRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val w = ((normalizedRect.right - normalizedRect.left) * bitmap.width).toInt()
            .coerceIn(1, bitmap.width - x)
        val h = ((normalizedRect.bottom - normalizedRect.top) * bitmap.height).toInt()
            .coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

}