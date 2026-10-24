/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.components.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.idLessKey
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isEncrypted
import com.github.panpf.sketch.asBitmapOrNull
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Width of the pre-blurred backdrop bitmap, in pixels.
 *
 * This stands in for the `100.dp` GPU blur the viewer used to draw, which on a phone is a smear
 * wider than a tenth of the screen: at this width the photo is already reduced to a soft colour
 * wash, so stretching the bitmap over the screen is indistinguishable from that blur — while
 * costing one textured quad per frame instead of a fullscreen `RenderEffect` pass. The
 * difference is what lets the backdrop stay on screen during the shared-element open transition,
 * where a per-frame blur of a box being re-measured every frame is what made it stutter.
 */
const val BACKDROP_WIDTH = 48

/** Box-blur radius, in [BACKDROP_WIDTH] pixels. Two passes ≈ a Gaussian of σ ≈ radius. */
const val BACKDROP_BLUR_RADIUS = 4

/** Box-blur passes: one pass is visibly boxy, two smooth it out. */
const val BACKDROP_BLUR_PASSES = 2

/** Alpha the blurred backdrop settles at, over the viewer's black background. */
const val BACKDROP_ALPHA = 0.7f

/**
 * Fade used only when the backdrop was not ready on the first composition. A backdrop that is
 * already decoded (the tap-time prefetch) is drawn at [BACKDROP_ALPHA] immediately, so it
 * arrives together with the media instead of fading in behind it.
 */
const val BACKDROP_FADE_MILLIS = 150

/** Backdrops kept around; each is well under 32 KB, so this is a few hundred KB at most. */
private const val BACKDROP_CACHE_ENTRIES = 8

private val backdropCache = object : LruCache<String, Bitmap>(BACKDROP_CACHE_ENTRIES) {}

/**
 * Cache key for a media's backdrop.
 *
 * Keyed on the resolved URI rather than [Media.id]: ids are only unique inside a source, so a
 * cloud asset and a local file can carry the same id. The file version is part of the key, so an
 * in-place overwrite or a rotation (which re-encodes the bytes) recomputes the backdrop.
 */
fun backdropCacheKey(media: Media): String {
    val version = mediaPreviewVersion(media)
    val source = media.getUri().toString()
    // Encrypted previews decode with a different key, and the same file can exist in the vault
    // and outside it, so the preview key is part of the identity (#1004).
    return if (media.isEncrypted) "$source:$version:${media.idLessKey}" else "$source:$version"
}

/**
 * Size the backdrop bitmap is decoded down to: [targetWidth] wide, the source aspect preserved
 * and never upscaled.
 */
fun backdropTargetSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int): IntSize {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0) return IntSize.Zero
    val width = min(sourceWidth, targetWidth)
    val height = max(1, (sourceHeight.toFloat() * width / sourceWidth).roundToInt())
    return IntSize(width, height)
}

/**
 * Separable box blur over packed ARGB pixels, in place, leaving alpha untouched.
 *
 * Exposed for tests: the backdrop's whole quality story is this filter, and it is the only part
 * of the pipeline that can be checked without a screen.
 */
fun boxBlurArgb(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0 || width <= 0 || height <= 0 || pixels.size < width * height) return
    val count = width * height
    val red = IntArray(count)
    val green = IntArray(count)
    val blue = IntArray(count)
    for (i in 0 until count) {
        val pixel = pixels[i]
        red[i] = (pixel shr 16) and 0xFF
        green[i] = (pixel shr 8) and 0xFF
        blue[i] = pixel and 0xFF
    }
    val line = IntArray(count)
    boxBlurChannel(red, line, width, height, radius)
    boxBlurChannel(green, line, width, height, radius)
    boxBlurChannel(blue, line, width, height, radius)
    for (i in 0 until count) {
        pixels[i] = (pixels[i] and ALPHA_MASK) or (red[i] shl 16) or (green[i] shl 8) or blue[i]
    }
}

/** Blurs one channel horizontally then vertically, reusing [scratch] for the pass output. */
private fun boxBlurChannel(
    channel: IntArray,
    scratch: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    for (y in 0 until height) {
        blurLine(channel, scratch, start = y * width, step = 1, length = width, radius = radius)
        System.arraycopy(scratch, y * width, channel, y * width, width)
    }
    for (x in 0 until width) {
        blurLine(channel, scratch, start = x, step = width, length = height, radius = radius)
        var index = x
        for (i in 0 until height) {
            channel[index] = scratch[index]
            index += width
        }
    }
}

/**
 * Averages one line of [source] into [dest] with a sliding window, clamping the window at both
 * ends so borders keep the image's edge colour instead of fading towards transparent.
 */
private fun blurLine(
    source: IntArray,
    dest: IntArray,
    start: Int,
    step: Int,
    length: Int,
    radius: Int,
) {
    if (length <= 1) {
        dest[start] = source[start]
        return
    }
    val window = 2 * radius + 1
    var sum = 0
    for (offset in -radius..radius) {
        sum += source[start + offset.coerceIn(0, length - 1) * step]
    }
    for (i in 0 until length) {
        dest[start + i * step] = sum / window
        val entering = (i + radius + 1).coerceAtMost(length - 1)
        val leaving = (i - radius).coerceAtLeast(0)
        sum += source[start + entering * step] - source[start + leaving * step]
    }
}

/**
 * Downscales [source] to [targetWidth] and blurs it into the bitmap the backdrop draws.
 *
 * Both scale steps render into bitmaps this function owns: the source comes from Sketch's memory
 * cache and must not be written to (its pixels are shared, and a hardware bitmap is immutable).
 * Two bilinear steps, because one step over a 10×+ ratio is effectively point sampling and the
 * aliasing it leaves behind is what the blur would smear into visible clumps.
 */
fun blurBackdrop(source: Bitmap, targetWidth: Int = BACKDROP_WIDTH): Bitmap? {
    if (targetWidth <= 0 || source.width <= 0 || source.height <= 0) return null
    // A hardware bitmap cannot be drawn into a software canvas, and Sketch hands out hardware
    // bitmaps when its bitmap config says so.
    val readable = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        source
    }
    val size = backdropTargetSize(readable.width, readable.height, targetWidth)
    if (size.width <= 0 || size.height <= 0) return null
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val intermediateWidth = min(readable.width, size.width * 4)
    val intermediate = Bitmap.createBitmap(
        intermediateWidth,
        max(1, (readable.height.toFloat() * intermediateWidth / readable.width).roundToInt()),
        Bitmap.Config.ARGB_8888,
    )
    Canvas(intermediate).drawBitmap(
        readable,
        null,
        Rect(0, 0, intermediate.width, intermediate.height),
        paint,
    )
    val scaled = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    Canvas(scaled).drawBitmap(
        intermediate,
        null,
        Rect(0, 0, size.width, size.height),
        paint,
    )
    val pixels = IntArray(size.width * size.height)
    scaled.getPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
    repeat(BACKDROP_BLUR_PASSES) {
        boxBlurArgb(pixels, size.width, size.height, BACKDROP_BLUR_RADIUS)
    }
    scaled.setPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
    return scaled
}

/**
 * The blurred backdrop for [media], or null until it is ready.
 *
 * The bitmap is derived from the same low-res preview the grid prefetched on tap, so the common
 * case resolves from Sketch's memory cache and is available on the viewer's first frame — that
 * is what makes the backdrop appear *with* the photo rather than fading in after it.
 */
@Composable
fun rememberMediaBackdrop(media: Media?): ImageBitmap? {
    if (media == null) return null
    val context = LocalContext.current
    val key = backdropCacheKey(media)
    // Keyed on the cache key: a page change resets the state instead of showing the previous
    // page's backdrop while the new one decodes.
    var bitmap by remember(key) { mutableStateOf(backdropCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap != null) return@LaunchedEffect
        val source = withContext(Dispatchers.IO) { decodePreview(context, media) }
            ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) { blurBackdrop(source) }
            ?: return@LaunchedEffect
        backdropCache.put(key, blurred)
        bitmap = blurred
    }
    return remember(bitmap) { bitmap?.asImageBitmap() }
}

/** Decodes the shared low-res preview; a memory-cache hit returns without touching the disk. */
private suspend fun decodePreview(context: Context, media: Media): Bitmap? = runCatching {
    val request = ImageRequest(context, media.getUri().toString()) {
        mediaPreviewOptions(media)
    }
    context.sketch.execute(request).image?.asBitmapOrNull()
}.getOrNull()

private const val ALPHA_MASK = -0x1000000
