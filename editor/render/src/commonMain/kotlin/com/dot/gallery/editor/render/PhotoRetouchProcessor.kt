package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoColourTone
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoEffects
import com.dot.gallery.editor.core.model.PhotoMarkupMetrics
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupText
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PhotoRetouch
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

/** A text label's coverage: one alpha byte per pixel, row-major. */
internal class PhotoTextMask(val width: Int, val height: Int, val alpha: ByteArray)

/**
 * Rasterizes left-aligned lines of text at [pixelHeight], or returns null when the
 * platform cannot — the label is then skipped rather than failing the render.
 */
internal expect fun rasterizeMarkupText(text: String, pixelHeight: Int): PhotoTextMask?

/**
 * A label's box in font sizes, measured with the font that renders it, so the
 * canvas handles and bounds checks agree with the pixels. Null when the platform
 * cannot measure.
 */
expect fun measureMarkupTextEm(text: String): Pair<Float, Float>?

/**
 * The shared retouch processor — the one implementation of `photo.retouch`
 * used by the render-graph preview and, band by band, by the strip exporter.
 * The stage works on display-encoded 8-bit pixels, matching the tools it comes
 * from and the export formats it feeds. One instance serves one recipe at one
 * output size and caches rasterized text between bands.
 */
internal class PhotoRetouchRenderer(
    private val retouch: PhotoRetouch,
    private val fullSize: PixelSize
) {
    private val textMasks = HashMap<Pair<String, Int>, PhotoTextMask?>()

    /** The vertical/horizontal read margin for output rows `top until top + rows`. */
    fun halo(top: Int, rows: Int): Int {
        if (retouch.isNeutral) return 0
        var reach = if (retouch.effects.edges > 0f) 1 else 0
        for (stroke in retouch.markup.strokes) {
            val read = PhotoMarkupMetrics.readReach(stroke, fullSize)
            if (read > reach && strokeTouchesRows(stroke, top, rows)) reach = read
        }
        return reach
    }

    private fun strokeTouchesRows(stroke: PhotoMarkupStroke, top: Int, rows: Int): Boolean {
        val radius = PhotoMarkupMetrics.strokeWidth(stroke.size, fullSize) / 2f + 1f
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (point in stroke.points) {
            val y = point.y * fullSize.height
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return maxY + radius >= top && minY - radius < top + rows
    }

    /**
     * Applies the stage to [frame], placed at ([originX], [originY]) in
     * absolute output coordinates. A neutral recipe returns the frame itself.
     */
    suspend fun process(
        frame: LinearRgbaFrame,
        originY: Int = 0,
        originX: Int = 0
    ): LinearRgbaFrame {
        if (retouch.isNeutral) return frame
        val width = frame.size.width
        val height = frame.size.height
        require(originY >= 0 && originY + height <= fullSize.height) {
            "retouch originY $originY + height $height exceeds ${fullSize.height}"
        }
        require(originX >= 0 && originX + width <= fullSize.width) {
            "retouch region exceeds the full output width"
        }
        val pixels = encode(frame)
        if (!retouch.tone.isNeutral) applyTone(pixels, retouch.tone)
        if (retouch.effects.posterize > 0f) applyPosterize(pixels, retouch.effects.posterize)
        if (retouch.effects.edges > 0f) {
            applyEdges(pixels, width, height, retouch.effects.edges)
        }
        if (!retouch.markup.isEmpty) applyMarkup(pixels, width, height, originX, originY)
        retouch.matte?.let { applyMatte(pixels, width, height, it, fullSize, originX, originY) }
        if (retouch.effects.borderWidth > 0f) {
            applyBorder(pixels, width, height, retouch.effects, fullSize, originX, originY)
        }
        return decode(frame.size, pixels)
    }

    private suspend fun encode(frame: LinearRgbaFrame): IntArray {
        val width = frame.size.width
        val out = IntArray(frame.componentCount / LinearRgbaFrame.COMPONENTS_PER_PIXEL)
        for (y in 0 until frame.size.height) {
            coroutineContext.ensureActive()
            val row = y * width
            for (x in 0 until width) {
                val i = (row + x) * LinearRgbaFrame.COMPONENTS_PER_PIXEL
                out[row + x] = (encodeAlpha(frame.component(i + 3)) shl 24) or
                    (encodeChannel(frame.component(i)) shl 16) or
                    (encodeChannel(frame.component(i + 1)) shl 8) or
                    encodeChannel(frame.component(i + 2))
            }
        }
        return out
    }

    private suspend fun decode(size: PixelSize, pixels: IntArray): LinearRgbaFrame {
        coroutineContext.ensureActive()
        val out = FloatArray(pixels.size * LinearRgbaFrame.COMPONENTS_PER_PIXEL)
        for (index in pixels.indices) {
            decodeSrgbPixel(pixels[index], out, index * LinearRgbaFrame.COMPONENTS_PER_PIXEL)
        }
        return LinearRgbaFrame.fromOwned(size, out)
    }

    private suspend fun applyMarkup(
        pixels: IntArray,
        width: Int,
        height: Int,
        originX: Int,
        originY: Int
    ) {
        val strokes = retouch.markup.strokes
        // Blur and Mosaic read the frame as it stood before markup.
        val base = if (strokes.any { it.readsBase }) pixels.copyOf() else null
        val coverage = FloatArray(pixels.size)
        for (stroke in strokes) {
            coroutineContext.ensureActive()
            val bounds = rasterizeStroke(
                stroke, coverage, width, height, originX, originY
            ) ?: continue
            when (stroke.type) {
                PhotoMarkupType.Pen -> paintPen(pixels, coverage, width, bounds, stroke.color)
                PhotoMarkupType.Highlighter ->
                    paintHighlighter(pixels, coverage, width, bounds, stroke.color)
                PhotoMarkupType.Blur -> paintBlur(
                    pixels, base!!, coverage, width, height, bounds,
                    PhotoMarkupMetrics.blurRadius(stroke.strength, fullSize)
                )
                PhotoMarkupType.Mosaic -> paintMosaic(
                    pixels, base!!, coverage, width, height, bounds,
                    PhotoMarkupMetrics.mosaicBlock(stroke.strength, fullSize),
                    originX, originY
                )
            }
            clearCoverage(coverage, width, bounds)
        }
        for (text in retouch.markup.texts) {
            coroutineContext.ensureActive()
            paintText(pixels, width, height, text, originX, originY)
        }
    }

    /**
     * Writes the stroke's anti-aliased capsule coverage into [coverage] and
     * returns the rectangle it touched, or null when it misses this frame.
     */
    private fun rasterizeStroke(
        stroke: PhotoMarkupStroke,
        coverage: FloatArray,
        width: Int,
        height: Int,
        originX: Int,
        originY: Int
    ): Bounds? {
        val radius = PhotoMarkupMetrics.strokeWidth(stroke.size, fullSize) / 2.0
        val points = stroke.points
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var previous: NormalizedPoint = points[0]
        for (index in points.indices) {
            val point = points[index]
            val ax = previous.x.toDouble() * fullSize.width - originX
            val ay = previous.y.toDouble() * fullSize.height - originY
            val bx = point.x.toDouble() * fullSize.width - originX
            val by = point.y.toDouble() * fullSize.height - originY
            previous = point
            val reach = radius + 1.0
            val left = floor(min(ax, bx) - reach).toInt().coerceAtLeast(0)
            val right = ceil(max(ax, bx) + reach).toInt().coerceAtMost(width - 1)
            val top = floor(min(ay, by) - reach).toInt().coerceAtLeast(0)
            val bottom = ceil(max(ay, by) + reach).toInt().coerceAtMost(height - 1)
            if (left > right || top > bottom) continue
            for (py in top..bottom) {
                val row = py * width
                val cy = py + .5
                for (px in left..right) {
                    val distance = segmentDistance(px + .5, cy, ax, ay, bx, by)
                    val value = (radius - distance + .5).coerceIn(0.0, 1.0).toFloat()
                    if (value > coverage[row + px]) coverage[row + px] = value
                }
            }
            if (left < minX) minX = left
            if (right > maxX) maxX = right
            if (top < minY) minY = top
            if (bottom > maxY) maxY = bottom
        }
        return if (minX == Int.MAX_VALUE) null else Bounds(minX, minY, maxX, maxY)
    }

    private fun clearCoverage(coverage: FloatArray, width: Int, bounds: Bounds) {
        for (y in bounds.top..bounds.bottom) {
            coverage.fill(0f, y * width + bounds.left, y * width + bounds.right + 1)
        }
    }

    private fun paintPen(
        pixels: IntArray,
        coverage: FloatArray,
        width: Int,
        bounds: Bounds,
        color: Int
    ) {
        forEachCovered(coverage, width, bounds) { index, amount ->
            pixels[index] = mixOpaque(pixels[index], color, amount)
        }
    }

    private fun paintHighlighter(
        pixels: IntArray,
        coverage: FloatArray,
        width: Int,
        bounds: Bounds,
        color: Int
    ) {
        val inkR = (color shr 16) and 0xFF
        val inkG = (color shr 8) and 0xFF
        val inkB = color and 0xFF
        forEachCovered(coverage, width, bounds) { index, amount ->
            val dst = pixels[index]
            val r = (dst shr 16) and 0xFF
            val g = (dst shr 8) and 0xFF
            val b = dst and 0xFF
            val multiplied = (0xFF shl 24) or
                ((r * inkR / 255) shl 16) or ((g * inkG / 255) shl 8) or (b * inkB / 255)
            pixels[index] = mixOpaque(dst, multiplied, amount * HIGHLIGHTER_ALPHA)
        }
    }

    private suspend fun paintBlur(
        pixels: IntArray,
        base: IntArray,
        coverage: FloatArray,
        width: Int,
        height: Int,
        bounds: Bounds,
        radius: Int
    ) {
        val reach = radius * 2
        val left = (bounds.left - reach).coerceAtLeast(0)
        val top = (bounds.top - reach).coerceAtLeast(0)
        val right = (bounds.right + reach).coerceAtMost(width - 1)
        val bottom = (bounds.bottom + reach).coerceAtMost(height - 1)
        val regionWidth = right - left + 1
        val regionHeight = bottom - top + 1
        val work = IntArray(regionWidth * regionHeight)
        for (y in 0 until regionHeight) {
            base.copyInto(
                work, y * regionWidth,
                (top + y) * width + left, (top + y) * width + left + regionWidth
            )
        }
        val scratch = IntArray(work.size)
        repeat(2) {
            coroutineContext.ensureActive()
            boxPass(work, scratch, regionWidth, regionHeight, radius, horizontal = true)
            boxPass(scratch, work, regionWidth, regionHeight, radius, horizontal = false)
        }
        forEachCovered(coverage, width, bounds) { index, amount ->
            val x = index % width - left
            val y = index / width - top
            pixels[index] = mixOpaque(pixels[index], work[y * regionWidth + x], amount)
        }
    }

    private fun boxPass(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val step = if (horizontal) 1 else width
        val lineStep = if (horizontal) width else 1
        val span = radius * 2 + 1
        for (line in 0 until lines) {
            val start = line * lineStep
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (offset in -radius..radius) {
                val c = source[start + offset.coerceIn(0, length - 1) * step]
                a += c ushr 24
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (index in 0 until length) {
                target[start + index * step] = ((a / span) shl 24) or
                    ((r / span) shl 16) or ((g / span) shl 8) or (b / span)
                val out = source[start + (index - radius).coerceIn(0, length - 1) * step]
                val enter = source[start + (index + radius + 1).coerceIn(0, length - 1) * step]
                a += (enter ushr 24) - (out ushr 24)
                r += ((enter shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((enter shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (enter and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun paintMosaic(
        pixels: IntArray,
        base: IntArray,
        coverage: FloatArray,
        width: Int,
        height: Int,
        bounds: Bounds,
        block: Int,
        originX: Int,
        originY: Int
    ) {
        // Cells sit on the absolute output grid, so bands agree on every block.
        val cells = HashMap<Long, Int>()
        forEachCovered(coverage, width, bounds) { index, amount ->
            val x = index % width
            val y = index / width
            val cellX = (x + originX) / block
            val cellY = (y + originY) / block
            val key = cellY.toLong() * fullSize.width + cellX
            val average = cells.getOrPut(key) {
                averageCell(base, width, height, cellX * block - originX, cellY * block - originY, block)
            }
            pixels[index] = mixOpaque(pixels[index], average, amount)
        }
    }

    private fun averageCell(
        base: IntArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        block: Int
    ): Int {
        var a = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (y in top.coerceAtLeast(0) until (top + block).coerceAtMost(height)) {
            for (x in left.coerceAtLeast(0) until (left + block).coerceAtMost(width)) {
                val c = base[y * width + x]
                a += c ushr 24
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
            }
        }
        if (count == 0L) return 0
        return ((a / count).toInt() shl 24) or ((r / count).toInt() shl 16) or
            ((g / count).toInt() shl 8) or (b / count).toInt()
    }

    private fun paintText(
        pixels: IntArray,
        width: Int,
        height: Int,
        text: PhotoMarkupText,
        originX: Int,
        originY: Int
    ) {
        val pixelHeight = max(1, (text.size * fullSize.height).roundToInt())
        val mask = textMasks.getOrPut(text.text to pixelHeight) {
            rasterizeMarkupText(text.text, pixelHeight)
        } ?: return
        val centreX = text.x * fullSize.width.toDouble() - originX
        val centreY = text.y * fullSize.height.toDouble() - originY
        if (text.rotation == 0f) {
            val left = (centreX - mask.width / 2.0).roundToInt()
            val top = (centreY - mask.height / 2.0).roundToInt()
            for (my in max(0, -top) until min(mask.height, height - top)) {
                val row = (top + my) * width + left
                for (mx in max(0, -left) until min(mask.width, width - left)) {
                    val alpha = mask.alpha[my * mask.width + mx].toInt() and 0xFF
                    if (alpha != 0) {
                        pixels[row + mx] = mixOpaque(pixels[row + mx], text.color, alpha / 255f)
                    }
                }
            }
            return
        }
        // Each covered frame pixel looks up its place in the upright mask.
        val radians = text.rotation * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)
        val halfW = mask.width / 2.0
        val halfH = mask.height / 2.0
        val reachX = abs(halfW * c) + abs(halfH * s) + 1.0
        val reachY = abs(halfW * s) + abs(halfH * c) + 1.0
        val left = floor(centreX - reachX).toInt().coerceAtLeast(0)
        val right = ceil(centreX + reachX).toInt().coerceAtMost(width - 1)
        val top = floor(centreY - reachY).toInt().coerceAtLeast(0)
        val bottom = ceil(centreY + reachY).toInt().coerceAtMost(height - 1)
        for (y in top..bottom) {
            val row = y * width
            val dy = y + .5 - centreY
            for (x in left..right) {
                val dx = x + .5 - centreX
                val coverage = sampleMask(
                    mask,
                    dx * c + dy * s + halfW - .5,
                    -dx * s + dy * c + halfH - .5
                )
                if (coverage > 0f) {
                    pixels[row + x] = mixOpaque(pixels[row + x], text.color, coverage)
                }
            }
        }
    }

    /** Bilinear coverage at a mask position; outside the mask reads as none. */
    private fun sampleMask(mask: PhotoTextMask, x: Double, y: Double): Float {
        if (x <= -1.0 || y <= -1.0 || x >= mask.width || y >= mask.height) return 0f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = (x - x0).toFloat()
        val fy = (y - y0).toFloat()
        fun at(ax: Int, ay: Int): Float =
            if (ax !in 0 until mask.width || ay !in 0 until mask.height) {
                0f
            } else {
                (mask.alpha[ay * mask.width + ax].toInt() and 0xFF) / 255f
            }
        val upper = at(x0, y0) * (1f - fx) + at(x0 + 1, y0) * fx
        val lower = at(x0, y0 + 1) * (1f - fx) + at(x0 + 1, y0 + 1) * fx
        return upper * (1f - fy) + lower * fy
    }

    private companion object {
        const val HIGHLIGHTER_ALPHA = 0.42f
    }
}

private val PhotoMarkupStroke.readsBase: Boolean
    get() = type == PhotoMarkupType.Blur || type == PhotoMarkupType.Mosaic

/** The pixel rectangle a stroke covered, in frame coordinates. */
private class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

private inline fun forEachCovered(
    coverage: FloatArray,
    width: Int,
    bounds: Bounds,
    action: (Int, Float) -> Unit
) {
    for (y in bounds.top..bounds.bottom) {
        val row = y * width
        for (x in bounds.left..bounds.right) {
            val amount = coverage[row + x]
            if (amount > 0f) action(row + x, amount)
        }
    }
}

/** Mixes [ink] over [dst]; the result's alpha rises with the covered amount. */
private fun mixOpaque(dst: Int, ink: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    val inverse = 1f - t
    val a = ((dst ushr 24) * inverse + (ink ushr 24) * t + .5f).toInt().coerceIn(0, 255)
    val r = (((dst shr 16) and 0xFF) * inverse + ((ink shr 16) and 0xFF) * t + .5f).toInt()
    val g = (((dst shr 8) and 0xFF) * inverse + ((ink shr 8) and 0xFF) * t + .5f).toInt()
    val b = ((dst and 0xFF) * inverse + (ink and 0xFF) * t + .5f).toInt()
    return (a shl 24) or (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}

private fun segmentDistance(
    px: Double, py: Double,
    ax: Double, ay: Double,
    bx: Double, by: Double
): Double {
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    val t = if (lengthSquared == 0.0) {
        0.0
    } else {
        (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0.0, 1.0)
    }
    return hypot(px - (ax + t * abx), py - (ay + t * aby))
}

/**
 * The old editor's colour matrices chained in its order: skin tone, blue tone,
 * hue rotation, then black and white, all on display-encoded channels.
 */
internal fun applyTone(pixels: IntArray, tone: PhotoColourTone) {
    val skin = tone.skin
    val blue = tone.blue
    val angle = (tone.hue * PI).toFloat()
    val c = cos(angle)
    val s = sin(angle)
    val m = floatArrayOf(
        LUM_R + c * (1 - LUM_R) + s * -LUM_R,
        LUM_G + c * -LUM_G + s * -LUM_G,
        LUM_B + c * -LUM_B + s * (1 - LUM_B),
        LUM_R + c * -LUM_R + s * 0.143f,
        LUM_G + c * (1 - LUM_G) + s * 0.140f,
        LUM_B + c * -LUM_B + s * -0.283f,
        LUM_R + c * -LUM_R + s * -(1 - LUM_R),
        LUM_G + c * -LUM_G + s * LUM_G,
        LUM_B + c * (1 - LUM_B) + s * LUM_B
    )
    val keep = 1f - abs(tone.blackWhite)
    for (index in pixels.indices) {
        val p = pixels[index]
        var r = ((p shr 16) and 0xFF) * (1f + skin * 0.15f) + skin * 8f
        var g = ((p shr 8) and 0xFF) * (1f + skin * 0.05f)
        var b = (p and 0xFF) * (1f - skin * 0.05f)
        b = b * (1f + blue * 0.4f) + blue * 20f
        val hr = r * m[0] + g * m[1] + b * m[2]
        val hg = r * m[3] + g * m[4] + b * m[5]
        val hb = r * m[6] + g * m[7] + b * m[8]
        val luma = LUM_R * hr + LUM_G * hg + LUM_B * hb
        r = luma + (hr - luma) * keep
        g = luma + (hg - luma) * keep
        b = luma + (hb - luma) * keep
        pixels[index] = (p and 0xFF000000.toInt()) or
            (r.roundToInt().coerceIn(0, 255) shl 16) or
            (g.roundToInt().coerceIn(0, 255) shl 8) or
            b.roundToInt().coerceIn(0, 255)
    }
}

/** 24 tonal levels at the lowest setting down to 2 at the highest. */
internal fun posterizeLevels(amount: Float): Int =
    (2f + (1f - amount) * 22f).roundToInt().coerceIn(2, 24)

private fun applyPosterize(pixels: IntArray, amount: Float) {
    val step = 255f / (posterizeLevels(amount) - 1)
    val lut = IntArray(256) { ((it / step).roundToInt() * step).toInt().coerceIn(0, 255) }
    for (index in pixels.indices) {
        val p = pixels[index]
        pixels[index] = (p and 0xFF000000.toInt()) or
            (lut[(p shr 16) and 0xFF] shl 16) or
            (lut[(p shr 8) and 0xFF] shl 8) or
            lut[p and 0xFF]
    }
}

/**
 * A Sobel map of display luma blended over the picture. True image borders
 * read as no edge, so a halo-expanded band matches a full-frame render.
 */
private fun applyEdges(
    pixels: IntArray,
    width: Int,
    height: Int,
    amount: Float
) {
    val luma = FloatArray(pixels.size) {
        val p = pixels[it]
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val index = row + x
            val onFrameEdge = x == 0 || y == 0 || x == width - 1 || y == height - 1
            val edge = if (onFrameEdge) {
                0f
            } else {
                val gx = (luma[index - width + 1] + 2 * luma[index + 1] + luma[index + width + 1]) -
                    (luma[index - width - 1] + 2 * luma[index - 1] + luma[index + width - 1])
                val gy = (luma[index + width - 1] + 2 * luma[index + width] + luma[index + width + 1]) -
                    (luma[index - width - 1] + 2 * luma[index - width] + luma[index - width + 1])
                sqrt(gx * gx + gy * gy).coerceIn(0f, 255f)
            }
            val v = edge.toInt()
            val map = (pixels[index] and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
            pixels[index] = mixOpaque(pixels[index], map, amount)
        }
    }
}

private fun applyMatte(
    pixels: IntArray,
    width: Int,
    height: Int,
    matte: PhotoCutoutMatte,
    size: PixelSize,
    originX: Int,
    originY: Int
) {
    val scaleX = matte.width.toDouble() / size.width
    val scaleY = matte.height.toDouble() / size.height
    for (y in 0 until height) {
        val my = ((y + originY + .5) * scaleY - .5).coerceIn(0.0, matte.height - 1.0)
        val y0 = my.toInt()
        val y1 = min(y0 + 1, matte.height - 1)
        val fy = my - y0
        val row = y * width
        for (x in 0 until width) {
            val mx = ((x + originX + .5) * scaleX - .5).coerceIn(0.0, matte.width - 1.0)
            val x0 = mx.toInt()
            val x1 = min(x0 + 1, matte.width - 1)
            val fx = mx - x0
            val top = matte.alphaAt(x0, y0) * (1 - fx) + matte.alphaAt(x1, y0) * fx
            val bottom = matte.alphaAt(x0, y1) * (1 - fx) + matte.alphaAt(x1, y1) * fx
            val keep = (top * (1 - fy) + bottom * fy) / 255.0
            val p = pixels[row + x]
            val alpha = ((p ushr 24) * keep + .5).toInt().coerceIn(0, 255)
            pixels[row + x] = (alpha shl 24) or (p and 0x00FFFFFF)
        }
    }
}

/** An inset ring in absolute output coordinates; it never grows the canvas. */
private fun applyBorder(
    pixels: IntArray,
    width: Int,
    height: Int,
    effects: PhotoEffects,
    size: PixelSize,
    originX: Int,
    originY: Int
) {
    val thickness = (min(size.width, size.height) * effects.borderWidth *
        PhotoEffects.MAX_BORDER_FRACTION).roundToInt()
    if (thickness <= 0) return
    val color = effects.borderColor or 0xFF000000.toInt()
    for (y in 0 until height) {
        val absY = y + originY
        val row = y * width
        val wholeRow = absY < thickness || absY >= size.height - thickness
        for (x in 0 until width) {
            val absX = x + originX
            if (wholeRow || absX < thickness || absX >= size.width - thickness) {
                pixels[row + x] = color
            }
        }
    }
}

private const val LUM_R = 0.213f
private const val LUM_G = 0.715f
private const val LUM_B = 0.072f
