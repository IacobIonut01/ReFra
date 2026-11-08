package com.dot.gallery.editor.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.dot.gallery.editor.render.SrgbPixels
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Skia raster conversion: straight ARGB ints become UNPREMUL RGBA_8888 bytes
 * so translucent source pixels keep their colour. [Bitmap.installPixels]
 * transfers the buffer — the returned bitmap is safe to keep drawing while
 * the next conversion runs.
 */
internal actual fun SrgbPixels.toEditorImageBitmap(): ImageBitmap {
    val width = size.width
    val height = size.height
    val argb = copyPixels()
    val rgba = ByteArray(argb.size * 4)
    for (i in argb.indices) {
        val p = argb[i]
        val o = i * 4
        rgba[o] = (p shr 16 and 0xFF).toByte()
        rgba[o + 1] = (p shr 8 and 0xFF).toByte()
        rgba[o + 2] = (p and 0xFF).toByte()
        rgba[o + 3] = (p ushr 24).toByte()
    }
    val bitmap = Bitmap()
    check(
        bitmap.installPixels(
            ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
            rgba,
            width * 4
        )
    ) { "cannot install ${width}x$height pixel buffer" }
    return bitmap.asComposeImageBitmap()
}
