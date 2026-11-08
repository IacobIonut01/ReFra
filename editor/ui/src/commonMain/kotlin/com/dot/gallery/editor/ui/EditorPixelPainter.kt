package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.dot.gallery.editor.render.SrgbPixels
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.LocalPlatformContext

@Composable
internal fun rememberEditorPixelPainter(
    pixels: SrgbPixels?,
    fallback: ImageBitmap?
): Painter? {
    if (pixels == null) return fallback?.let { remember(it) { BitmapPainter(it) } }
    val source = remember(pixels) { EditorPixelDataSource(pixels) }
    val context = LocalPlatformContext.current
    val request = remember(context, source) {
        ImageRequest.Builder(context, source.key)
            .components {
                add(EditorPixelFetcher.Factory(source))
                add(EditorPixelDecoder.Factory())
            }
            .size(pixels.size.width, pixels.size.height)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .resultCachePolicy(CachePolicy.DISABLED)
            .downloadCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    // New pixels own a new state, so Sketch cannot retain another source's image.
    val painter = key(source) { rememberAsyncImagePainter(request) }
    return if (painter.intrinsicSize.width > 0f || fallback == null) painter
    else remember(fallback) { BitmapPainter(fallback) }
}
