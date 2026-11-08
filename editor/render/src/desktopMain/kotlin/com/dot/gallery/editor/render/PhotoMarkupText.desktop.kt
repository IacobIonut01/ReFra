package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoMarkupTextLayout
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

private const val MEASURE_PIXELS = 200

private fun metricsFor(font: Font): FontMetrics {
    val probe = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).createGraphics()
    return try {
        probe.getFontMetrics(font)
    } finally {
        probe.dispose()
    }
}

actual fun measureMarkupTextEm(text: String): Pair<Float, Float>? {
    val lines = text.split('\n')
    val metrics = metricsFor(Font(Font.SANS_SERIF, Font.PLAIN, MEASURE_PIXELS))
    return (lines.maxOfOrNull { metrics.stringWidth(it) } ?: 0).toFloat() / MEASURE_PIXELS to
        lines.size * PhotoMarkupTextLayout.LINE_HEIGHT
}

internal actual fun rasterizeMarkupText(text: String, pixelHeight: Int): PhotoTextMask? =
    try {
        val font = Font(Font.SANS_SERIF, Font.PLAIN, pixelHeight)
        val metrics = metricsFor(font)
        val lines = text.split('\n')
        val lineHeight = pixelHeight * PhotoMarkupTextLayout.LINE_HEIGHT
        val width = (lines.maxOfOrNull { metrics.stringWidth(it) } ?: 0).coerceAtLeast(1)
        val height = ceil(lines.size * lineHeight).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )
            graphics.color = Color.WHITE
            graphics.font = font
            // Each line's glyphs sit centred in their line box.
            val inset = (lineHeight - metrics.ascent - metrics.descent) / 2f + metrics.ascent
            lines.forEachIndexed { index, line ->
                graphics.drawString(line, 0f, index * lineHeight + inset)
            }
        } finally {
            graphics.dispose()
        }
        val alpha = ByteArray(width * height)
        image.raster.getDataElements(0, 0, width, height, alpha)
        PhotoTextMask(width, height, alpha)
    } catch (_: IllegalArgumentException) {
        // An absurd label size: the caller skips the label.
        null
    } catch (_: OutOfMemoryError) {
        null
    }
