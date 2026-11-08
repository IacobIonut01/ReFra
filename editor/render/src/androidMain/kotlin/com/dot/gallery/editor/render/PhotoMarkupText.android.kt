package com.dot.gallery.editor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.dot.gallery.editor.core.model.PhotoMarkupTextLayout
import java.nio.ByteBuffer
import kotlin.math.ceil

private const val MEASURE_PIXELS = 200

private fun markupPaint(pixelHeight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    textSize = pixelHeight.toFloat()
    color = android.graphics.Color.WHITE
}

private fun Paint.widestLine(lines: List<String>): Float =
    lines.maxOfOrNull { measureText(it) } ?: 0f

actual fun measureMarkupTextEm(text: String): Pair<Float, Float>? {
    val lines = text.split('\n')
    return markupPaint(MEASURE_PIXELS).widestLine(lines) / MEASURE_PIXELS to
        lines.size * PhotoMarkupTextLayout.LINE_HEIGHT
}

internal actual fun rasterizeMarkupText(text: String, pixelHeight: Int): PhotoTextMask? =
    try {
        val paint = markupPaint(pixelHeight)
        val lines = text.split('\n')
        val lineHeight = pixelHeight * PhotoMarkupTextLayout.LINE_HEIGHT
        val width = ceil(paint.widestLine(lines)).toInt().coerceAtLeast(1)
        val height = ceil(lines.size * lineHeight).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        try {
            val canvas = Canvas(bitmap)
            val metrics = paint.fontMetrics
            // Each line's glyphs sit centred in their line box.
            val inset = (lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, 0f, index * lineHeight + inset, paint)
            }
            val alpha = ByteArray(width * height)
            val buffer = ByteBuffer.allocate(bitmap.rowBytes * height)
            bitmap.copyPixelsToBuffer(buffer)
            for (row in 0 until height) {
                buffer.position(row * bitmap.rowBytes)
                buffer.get(alpha, row * width, width)
            }
            PhotoTextMask(width, height, alpha)
        } finally {
            bitmap.recycle()
        }
    } catch (_: IllegalArgumentException) {
        // An absurd label size: the caller skips the label.
        null
    } catch (_: OutOfMemoryError) {
        null
    }
