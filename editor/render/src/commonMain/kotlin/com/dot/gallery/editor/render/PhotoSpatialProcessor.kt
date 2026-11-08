package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoDetails
import com.dot.gallery.editor.core.model.PhotoLensBlur
import com.dot.gallery.editor.core.model.PhotoSpatialEffects
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

private const val COMPONENTS = LinearRgbaFrame.COMPONENTS_PER_PIXEL

/**
 * The shared spatial processor — the one implementation of the
 * `photo.spatial` stage used by the render-graph preview
 * ([PhotoSpatialEffect]) and by
 * [com.dot.gallery.editor.render.photo.PhotoStripExporter] per expanded
 * output band. Order inside a pixel is fixed: the Details 3x3
 * local-contrast pass, then the masked lens blur over the Details
 * result — all on straight linear RGB, source alpha preserved.
 * A neutral [spatial] returns the input frame bit-identical, same
 * object.
 *
 * [originX]/[originY]/[fullSize] place the frame in absolute output coordinates —
 * a full render passes (0, frame.size); a banded export passes the
 * expanded band's top row and the full output size — so the focus mask
 * cannot change at band boundaries. Neighbourhood reads clamp to the
 * frame edge; the exporter's halo expansion guarantees the clamp only
 * bites at true image borders.
 */
internal suspend fun processPhotoSpatial(
    frame: LinearRgbaFrame,
    spatial: PhotoSpatialEffects,
    originY: Int,
    fullSize: PixelSize,
    originX: Int = 0
): LinearRgbaFrame {
    if (spatial.isNeutral) return frame
    val width = frame.size.width
    val height = frame.size.height
    require(originY >= 0 && originY + height <= fullSize.height) {
        "spatial originY $originY + height $height exceeds " +
            "full output height ${fullSize.height}"
    }
    require(originX >= 0 && originX + width <= fullSize.width) {
        "spatial region exceeds the full output width"
    }
    // Details feeds the blur, so an active blur needs the Details
    // result even when its own recipe is neutral.
    val detailed =
        if (spatial.details.isNeutral) frame
        else applyDetails(frame, spatial.details)
    if (spatial.lensBlur.isNeutral) return detailed
    return applyLensBlur(detailed, spatial.lensBlur, originY, fullSize, originX)
}

/**
 * The Details pass: one edge-clamped 3x3 box blur in linear RGB. The
 * high band (`rgb - blur`) is lifted by texture and sharpening; the
 * luma-local band (`luma - blurLuma`) is lifted by clarity; noise
 * reduction leans the pixel toward its neighbourhood mean. No clamp —
 * the recipe may overshoot; alpha passes through.
 */
private suspend fun applyDetails(
    frame: LinearRgbaFrame,
    details: PhotoDetails
): LinearRgbaFrame {
    val width = frame.size.width
    val height = frame.size.height
    val out = FloatArray(frame.componentCount)
    val nrMix = details.noiseReduction * 0.72f
    val bandGain = details.texture * 0.38f + details.sharpening * 0.9f
    val clarityGain = details.clarity * 0.65f
    for (y in 0 until height) {
        coroutineContext.ensureActive()
        for (x in 0 until width) {
            val i = (y * width + x) * COMPONENTS
            var br = 0f
            var bg = 0f
            var bb = 0f
            // Nine taps with edge-clamped coordinates — border pixels
            // repeat the edge sample rather than shrinking the box.
            for (dy in -1..1) {
                val ny = (y + dy).coerceIn(0, height - 1)
                for (dx in -1..1) {
                    val j = (ny * width +
                        (x + dx).coerceIn(0, width - 1)) * COMPONENTS
                    br += frame.component(j)
                    bg += frame.component(j + 1)
                    bb += frame.component(j + 2)
                }
            }
            br *= 1f / 9f
            bg *= 1f / 9f
            bb *= 1f / 9f
            val r = frame.component(i)
            val g = frame.component(i + 1)
            val b = frame.component(i + 2)
            val a = frame.component(i + 3)
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val blurLuma = 0.2126f * br + 0.7152f * bg + 0.0722f * bb
            val local = luma - blurLuma
            val hr = r - br
            val hg = g - bg
            val hb = b - bb
            val nr = r + (br - r) * nrMix
            val ng = g + (bg - g) * nrMix
            val nb = b + (bb - b) * nrMix
            val outR = nr + hr * bandGain + local * clarityGain
            val outG = ng + hg * bandGain + local * clarityGain
            val outB = nb + hb * bandGain + local * clarityGain
            require(
                outR.isFinite() && outG.isFinite() && outB.isFinite() &&
                    a.isFinite()
            ) {
                "photo.spatial details produced a non-finite pixel " +
                    "at ($x, $y)"
            }
            out[i] = outR
            out[i + 1] = outG
            out[i + 2] = outB
            out[i + 3] = a
        }
    }
    return LinearRgbaFrame.fromOwned(frame.size, out)
}

/**
 * The lens-blur pass over the Details result: a per-pixel focus mask
 * sets the blur radius (0 in focus, `round(strength * 16)` fully
 * blurred), then a fixed 13-tap cross/disk kernel — centre, the four
 * axis taps at ±r and the eight diagonal taps at 45° and 30°/60° —
 * averages the Details output at those rounded, edge-clamped offsets.
 * Highlight bokeh re-adds `(luma(blur) - .7)+ * highlightBokeh * mask`
 * equally to every channel. Source alpha passes through unblurred.
 */
private suspend fun applyLensBlur(
    frame: LinearRgbaFrame,
    blur: PhotoLensBlur,
    originY: Int,
    fullSize: PixelSize,
    originX: Int
): LinearRgbaFrame {
    val width = frame.size.width
    val height = frame.size.height
    val out = FloatArray(frame.componentCount)
    val context = LensBlurContext(blur, fullSize)
    // The 13-tap kernel's fractional offsets, baked once — the radius
    // changes per pixel but the disk's shape does not.
    for (y in 0 until height) {
        coroutineContext.ensureActive()
        val absY = originY + y
        for (x in 0 until width) {
            val i = (y * width + x) * COMPONENTS
            val a = frame.component(i + 3)
            val radius = context.radius(originX + x, absY)
            var r: Float
            var g: Float
            var b: Float
            if (radius == 0) {
                r = frame.component(i)
                g = frame.component(i + 1)
                b = frame.component(i + 2)
            } else {
                var sr = 0f
                var sg = 0f
                var sb = 0f
                for (tap in 0 until TAP_COUNT) {
                    val sx = (x + TAP_DX[tap] * radius)
                        .roundToInt().coerceIn(0, width - 1)
                    val sy = (y + TAP_DY[tap] * radius)
                        .roundToInt().coerceIn(0, height - 1)
                    val j = (sy * width + sx) * COMPONENTS
                    sr += frame.component(j)
                    sg += frame.component(j + 1)
                    sb += frame.component(j + 2)
                }
                r = sr * (1f / TAP_COUNT)
                g = sg * (1f / TAP_COUNT)
                b = sb * (1f / TAP_COUNT)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val boost = max(0f, luma - 0.7f) *
                    blur.highlightBokeh * context.mask(originX + x, absY)
                r += boost
                g += boost
                b += boost
            }
            require(
                r.isFinite() && g.isFinite() && b.isFinite() && a.isFinite()
            ) {
                "photo.spatial lens blur produced a non-finite pixel " +
                    "at ($x, $absY)"
            }
            out[i] = r
            out[i + 1] = g
            out[i + 2] = b
            out[i + 3] = a
        }
    }
    return LinearRgbaFrame.fromOwned(frame.size, out)
}

/** The 13-tap disk: centre, ±r on both axes, the 45° diagonals and the
 * 30°/60° diagonals — unit fractions of the per-pixel radius. */
private const val TAP_COUNT = 13
private val TAP_DX = floatArrayOf(
    0f,
    1f, -1f, 0f, 0f,
    0.70710678f, -0.70710678f, 0.70710678f, -0.70710678f,
    0.5f, -0.5f, 0.5f, -0.5f
)
private val TAP_DY = floatArrayOf(
    0f,
    0f, 0f, 1f, -1f,
    0.70710678f, 0.70710678f, -0.70710678f, -0.70710678f,
    0.8660254f, 0.8660254f, -0.8660254f, -0.8660254f
)

/**
 * Precomputed focus-mask geometry over the full output frame: radial
 * measures aspect-corrected distance from the centre, linear measures
 * perpendicular distance from the rotated centre line. [mask] is the
 * smoothstepped blur factor; [radius] the integral tap radius it
 * selects.
 */
private class LensBlurContext(
    private val blur: PhotoLensBlur,
    fullSize: PixelSize
) {
    private val width = fullSize.width.toFloat()
    private val height = fullSize.height.toFloat()
    private val aspect = width / height
    private val cosA = cos(Math.toRadians(blur.angleDegrees.toDouble()))
        .toFloat()
    private val sinA = sin(Math.toRadians(blur.angleDegrees.toDouble()))
        .toFloat()
    private val boundary =
        if (blur.mode == LensBlurMode.Radial) 0.18f else 0.12f
    private val rampEnd = boundary + 0.04f + blur.transition * 0.45f
    private val maxRadius = blur.strength * PhotoSpatialEffects.MAX_LENS_RADIUS

    fun mask(x: Int, absY: Int): Float {
        val px = (x + 0.5f) / width
        val py = (absY + 0.5f) / height
        val d = when (blur.mode) {
            LensBlurMode.Radial -> {
                val dx = (px - blur.centerX) * aspect
                val dy = py - blur.centerY
                sqrt(dx * dx + dy * dy)
            }
            LensBlurMode.Linear -> abs(
                (px - blur.centerX) * cosA + (py - blur.centerY) * sinA
            )
        }
        val t = ((d - boundary) / (rampEnd - boundary)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun radius(x: Int, absY: Int): Int =
        (maxRadius * mask(x, absY)).roundToInt()
}
