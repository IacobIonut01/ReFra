package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.PhotoColourGrade
import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoCurves
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoGrain
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoVignette
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.WhiteBalanceModel
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive

/**
 * The shared finishing processor — the one implementation of the
 * `photo.finishing` stage used by the render-graph preview
 * ([PhotoFinishingEffect]) and by [com.dot.gallery.editor.render.photo.PhotoStripExporter]
 * per output band. Order inside a pixel is fixed: white balance, colour
 * grade, tone curves, look, vignette, grain — all on straight linear RGB, alpha
 * preserved, except that the curves read and write display-encoded values.
 * Every neutral stage performs no arithmetic, and a neutral
 * [finishing] returns the input frame bit-identical.
 *
 * [originX]/[originY]/[fullSize] place the frame in absolute output coordinates —
 * a full render passes (0, frame.size); a banded export passes the
 * band's top row and the full output size — so vignette and grain cannot
 * change at band boundaries.
 */
internal suspend fun processPhotoFinishing(
    frame: LinearRgbaFrame,
    finishing: PhotoFinishing,
    originY: Int,
    fullSize: PixelSize,
    originX: Int = 0
): LinearRgbaFrame {
    if (finishing.isNeutral) return frame
    val width = frame.size.width
    val height = frame.size.height
    require(originY >= 0 && originY + height <= fullSize.height) {
        "finishing originY $originY + height $height exceeds " +
            "full output height ${fullSize.height}"
    }
    require(originX >= 0 && originX + width <= fullSize.width) {
        "finishing region exceeds the full output width"
    }
    // Stage gates and per-frame constants — a neutral stage keeps its
    // pixels untouched, never multiplied by an identity factor.
    val wbGains =
        if (finishing.whiteBalance.isNeutral) null
        else whiteBalanceGains(finishing.whiteBalance)
    val grade =
        if (finishing.grade.isNeutral) null
        else ColourGradeContext(finishing.grade)
    val gradeOut = grade?.let { FloatArray(3) }
    val curves =
        if (finishing.curves.isNeutral) null else finishing.curves
    val look = if (finishing.look.isNeutral) null else finishing.look
    val lookTarget = look?.let { FloatArray(3) }
    val vignette =
        if (finishing.vignette.amount == 0f) null else finishing.vignette
    val grain =
        if (finishing.grain.amount == 0f) null else finishing.grain
    val vignetteContext = vignette?.let { VignetteContext(it, fullSize) }
    val grainCell = grain?.let { 1 + (it.size * 7f).roundToInt() }

    val out = FloatArray(width * height * LinearRgbaFrame.COMPONENTS_PER_PIXEL)
    for (y in 0 until height) {
        coroutineContext.ensureActive()
        val absY = originY + y
        val rowBase = y * width * LinearRgbaFrame.COMPONENTS_PER_PIXEL
        for (x in 0 until width) {
            val i = rowBase + x * LinearRgbaFrame.COMPONENTS_PER_PIXEL
            var r = frame.component(i)
            var g = frame.component(i + 1)
            var b = frame.component(i + 2)
            val a = frame.component(i + 3)

            if (wbGains != null) {
                r *= wbGains[0]
                g *= wbGains[1]
                b *= wbGains[2]
            }
            if (grade != null && gradeOut != null) {
                grade.apply(r, g, b, gradeOut)
                r = gradeOut[0]
                g = gradeOut[1]
                b = gradeOut[2]
            }
            if (curves != null) {
                r = curveAt(curves.master, curves.red, r)
                g = curveAt(curves.master, curves.green, g)
                b = curveAt(curves.master, curves.blue, b)
            }
            if (look != null && lookTarget != null) {
                lookTarget(look.id, r, g, b, lookTarget)
                r += (lookTarget[0] - r) * look.amount
                g += (lookTarget[1] - g) * look.amount
                b += (lookTarget[2] - b) * look.amount
            }
            if (vignetteContext != null) {
                val gain = vignetteContext.gain(originX + x, absY, r, g, b)
                r *= gain
                g *= gain
                b *= gain
            }
            if (grain != null && grainCell != null) {
                val noise = grainNoise(grain, grainCell, originX + x, absY)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val envelope = 0.35f +
                    0.65f * (1f - abs(luma.coerceIn(0f, 1f) * 2f - 1f))
                val delta = noise * grain.amount * 0.12f * envelope
                r += delta
                g += delta
                b += delta
            }
            require(
                r.isFinite() && g.isFinite() && b.isFinite() && a.isFinite()
            ) {
                "photo.finishing produced a non-finite pixel at ($x, $absY)"
            }
            out[i] = r
            out[i + 1] = g
            out[i + 2] = b
            out[i + 3] = a
        }
    }
    return LinearRgbaFrame.fromOwned(frame.size, out)
}

/**
 * White balance as linear RGB gains: temperature normalizes onto an
 * asymmetric t (6500 K neutral, 5500 K span above, 4500 K below), tint
 * onto u in -1..1; each channel is an exponential stop of
 * `±(.35 t + .10 u)` — red/blue oppose, green answers tint alone.
 */
private fun whiteBalanceGains(wb: PhotoWhiteBalance): FloatArray =
    WhiteBalanceModel.gains(wb)

/**
 * One linear channel through the master curve and then its own, both in the
 * display encoding the curves are drawn in. A channel with two neutral curves
 * stays untouched rather than round-tripping through the transfer function.
 */
internal fun curveAt(master: PhotoToneCurve, channel: PhotoToneCurve, linear: Float): Float {
    val masterNeutral = master.isNeutral
    val channelNeutral = channel.isNeutral
    if (masterNeutral && channelNeutral) return linear
    var encoded = curveEncode(linear)
    if (!masterNeutral) encoded = master.spline.evaluate(encoded)
    if (!channelNeutral) encoded = channel.spline.evaluate(encoded)
    return curveDecode(encoded)
}

/** The sRGB encode, unclamped so out-of-range pixels keep their distance. */
internal fun curveEncode(linear: Float): Float =
    if (linear <= 0.0031308f) 12.92f * linear
    else 1.055f * linear.pow(1f / 2.4f) - 0.055f

internal fun curveDecode(encoded: Float): Float =
    if (encoded <= 0.04045f) encoded / 12.92f
    else ((encoded + 0.055f) / 1.055f).pow(2.4f)

/**
 * The settled look transforms — each maps straight linear RGB onto its
 * graded target in [out]; the caller blends `rgb -> target` by the
 * recipe amount. Every grade works in the same linear space as the
 * surrounding stages, luma is always the Rec.709 display luma, and no
 * transform clamps — out-of-range input extrapolates honestly.
 */
private fun lookTarget(
    id: PhotoLookId,
    r: Float,
    g: Float,
    b: Float,
    out: FloatArray
) {
    when (id) {
        PhotoLookId.None -> {
            out[0] = r
            out[1] = g
            out[2] = b
        }
        PhotoLookId.Clean -> {
            val luma = luma709(r, g, b)
            out[0] = (luma + (r - luma) * 1.08f - 0.18f) * 1.06f + 0.18f
            out[1] = (luma + (g - luma) * 1.08f - 0.18f) * 1.06f + 0.18f
            out[2] = (luma + (b - luma) * 1.08f - 0.18f) * 1.06f + 0.18f
        }
        PhotoLookId.WarmFade -> {
            val wr = r * 1.08f
            val wg = g * 1.02f
            val wb = b * 0.90f
            val l = luma709(wr, wg, wb)
            out[0] = (l + (wr - l) * 0.90f) * 0.88f + 0.035f
            out[1] = (l + (wg - l) * 0.90f) * 0.88f + 0.035f
            out[2] = (l + (wb - l) * 0.90f) * 0.88f + 0.035f
        }
        PhotoLookId.Cool -> {
            val cr = r * 0.92f
            val cg = g * 1.01f
            val cb = b * 1.10f
            val l = luma709(cr, cg, cb)
            out[0] = l + (cr - l) * 1.04f
            out[1] = l + (cg - l) * 1.04f
            out[2] = l + (cb - l) * 1.04f
        }
        PhotoLookId.Mono -> {
            val luma = luma709(r, g, b)
            out[0] = luma
            out[1] = luma
            out[2] = luma
        }
        PhotoLookId.Cinematic -> {
            val br = (r - 0.18f) * 1.08f + 0.18f
            val bg = (g - 0.18f) * 1.08f + 0.18f
            val bb = (b - 0.18f) * 1.08f + 0.18f
            val w = smoothStep(0.35f, 0.75f, luma709(br, bg, bb))
            out[0] = br * 0.92f + (br * 1.08f - br * 0.92f) * w
            out[1] = bg * 1.04f + (bg * 1.02f - bg * 1.04f) * w
            out[2] = bb * 1.10f + (bb * 0.92f - bb * 1.10f) * w
        }
        PhotoLookId.Vivid -> tintInto(r, g, b, 1f, 1f, 1f, 1.35f, 0f, 1.10f, out)
        PhotoLookId.Pastel ->
            tintInto(r, g, b, 1.04f, 1f, 0.98f, 0.72f, 0.045f, 0.90f, out)
        PhotoLookId.Chrome ->
            tintInto(r, g, b, 1.02f, 1f, 1.03f, 1.12f, 0f, 1.18f, out)
        PhotoLookId.Golden ->
            tintInto(r, g, b, 1.10f, 1.03f, 0.86f, 1.06f, 0.01f, 1.02f, out)
        PhotoLookId.Desert ->
            tintInto(r, g, b, 1.12f, 0.99f, 0.80f, 0.95f, 0.015f, 1.04f, out)
        PhotoLookId.Forest ->
            tintInto(r, g, b, 0.94f, 1.08f, 0.93f, 1.05f, 0f, 1.03f, out)
        PhotoLookId.Rose ->
            tintInto(r, g, b, 1.10f, 0.95f, 1.01f, 0.98f, 0.02f, 0.98f, out)
        PhotoLookId.BlueHour ->
            tintInto(r, g, b, 0.86f, 0.97f, 1.16f, 1f, 0.02f, 1.02f, out)
        PhotoLookId.Vintage ->
            tintInto(r, g, b, 1.06f, 0.98f, 0.86f, 0.82f, 0.05f, 0.90f, out)
        PhotoLookId.Fade ->
            tintInto(r, g, b, 1.02f, 1f, 0.99f, 0.88f, 0.06f, 0.86f, out)
        PhotoLookId.Matte ->
            tintInto(r, g, b, 1f, 1f, 1f, 0.80f, 0.08f, 0.82f, out)
        PhotoLookId.Bleach ->
            tintInto(r, g, b, 1.02f, 1f, 0.98f, 0.35f, 0.01f, 1.25f, out)
        PhotoLookId.TealOrange -> {
            // Teal shadows into orange highlights, weighted by luma.
            val l = luma709(r, g, b)
            val w = smoothStep(0.30f, 0.70f, l)
            val sr = r * 0.92f
            val sg = g * 1.02f
            val sb = b * 1.10f
            val hr = r * 1.10f
            val hg = g * 1.0f
            val hb = b * 0.90f
            out[0] = sr + (hr - sr) * w
            out[1] = sg + (hg - sg) * w
            out[2] = sb + (hb - sb) * w
        }
        PhotoLookId.CinemaGreen -> {
            val l = luma709(r, g, b)
            val w = smoothStep(0.30f, 0.70f, l)
            val sr = (r - 0.18f) * 1.06f + 0.18f
            val sg = (g - 0.18f) * 1.06f + 0.18f
            val sb = (b - 0.18f) * 1.06f + 0.18f
            val cr = sr * 0.95f
            val cg = sg * 1.05f
            val cb = sb * 0.98f
            val hr = sr * 1.06f
            val hg = sg * 1.02f
            val hb = sb * 0.94f
            out[0] = cr + (hr - cr) * w
            out[1] = cg + (hg - cg) * w
            out[2] = cb + (hb - cb) * w
        }
        PhotoLookId.Sepia -> {
            val l = luma709(r, g, b)
            out[0] = l * 1.07f + 0.03f
            out[1] = l * 0.93f + 0.02f
            out[2] = l * 0.74f + 0.01f
        }
        PhotoLookId.Noir -> {
            val l = luma709(r, g, b)
            val c = (l - 0.18f) * 1.30f + 0.18f
            out[0] = c
            out[1] = c
            out[2] = c
        }
    }
}

/**
 * A gain-tint-saturation-contrast-lift grade: the four knobs every
 * extended colour or film look is built from. Gains and the contrast
 * pivot operate in linear light like the rest of the finishing stage; the
 * lift is a straight linear offset so faded looks keep measurable blacks.
 */
private fun tintInto(
    r: Float,
    g: Float,
    b: Float,
    gainR: Float,
    gainG: Float,
    gainB: Float,
    saturation: Float,
    lift: Float,
    contrast: Float,
    out: FloatArray
) {
    val tr = r * gainR
    val tg = g * gainG
    val tb = b * gainB
    val l = luma709(tr, tg, tb)
    out[0] = (l + (tr - l) * saturation - 0.18f) * contrast + 0.18f + lift
    out[1] = (l + (tg - l) * saturation - 0.18f) * contrast + 0.18f + lift
    out[2] = (l + (tb - l) * saturation - 0.18f) * contrast + 0.18f + lift
}

private fun luma709(r: Float, g: Float, b: Float): Float =
    0.2126f * r + 0.7152f * g + 0.0722f * b

/** Precomputed vignette geometry over the full output frame. */
private class VignetteContext(
    vignette: PhotoVignette,
    fullSize: PixelSize
) {
    private val aspectW = max(1f, fullSize.width.toFloat() / fullSize.height)
    private val aspectH = max(1f, fullSize.height.toFloat() / fullSize.width)
    private val width = fullSize.width.toFloat()
    private val height = fullSize.height.toFloat()
    private val roundnessMix = (vignette.roundness + 1f) / 2f
    private val start = 0.15f + vignette.midpoint * 0.65f
    private val end = min(1.5f, start + 0.05f + vignette.feather * 0.65f)
    private val baseStops = vignette.amount * 2f
    private val highlights = vignette.highlights

    fun gain(x: Int, absY: Int, r: Float, g: Float, b: Float): Float {
        val nx = (2f * (x + 0.5f) / width - 1f) * aspectW
        val ny = (2f * (absY + 0.5f) / height - 1f) * aspectH
        val round = sqrt(nx * nx + ny * ny)
        val square = max(abs(nx), abs(ny))
        val distance =
            square + (round - square) * roundnessMix
        val mask = smoothStep(start, end, distance)
        if (mask == 0f) return 1f
        val luma = max(0f, 0.2126f * r + 0.7152f * g + 0.0722f * b)
        val protection = if (baseStops < 0f) {
            1f - highlights * smoothStep(0.6f, 1f, luma)
        } else {
            1f
        }
        return 2.0.pow((baseStops * mask * protection).toDouble()).toFloat()
    }
}

/** Hermite smoothstep clamped to [edge0, edge1] — the same ramp the
 * runtime shader's `smoothstep` evaluates. */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The grain sample at one absolute output pixel: a 32-bit avalanche
 * hash of the grain cell coordinates and the seed, mapped off the low
 * 24 bits into [-1, 1], blended toward the next cell's hash by the
 * fractional x inside the cell scaled by (1 - roughness) — roughness 1
 * stays blocky, 0 interpolates fully. Deterministic in position and
 * seed only — never in time or thread.
 */
private fun grainNoise(
    grain: PhotoGrain,
    cell: Int,
    x: Int,
    absY: Int
): Float {
    val cx = floor(x.toFloat() / cell).toInt()
    val cy = floor(absY.toFloat() / cell).toInt()
    val n0 = grainHash(cx, cy, grain.seed)
    if (grain.roughness >= 1f) return n0
    val n1 = grainHash(cx + 1, cy, grain.seed)
    val fraction = (x - cx * cell).toFloat() / cell
    return n0 + (n1 - n0) * fraction * (1f - grain.roughness)
}

/**
 * 32-bit integer avalanche hash: the coordinates and seed are mixed by
 * odd multipliers, then put through two rounds of xor-shift/multiply —
 * a documented variant of the lowbias32 finalizer. The low 24 bits map
 * linearly onto [-1, 1].
 */
private fun grainHash(cx: Int, cy: Int, seed: Int): Float {
    var h = cx * 0x8DA6B343.toInt()
    h = h xor (cy * 0xD8163841.toInt())
    h = h xor (seed * 0xCB1AB31F.toInt())
    h = h xor (h ushr 16)
    h *= 0x7FEB352D.toInt()
    h = h xor (h ushr 15)
    h *= 0x846CA68B.toInt()
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) * (1f / 8388607.5f) - 1f
}

/**
 * The colour grade's per-frame constants and pixel math, on straight linear
 * RGB with nothing clamped: vibrance, then the HSL bands, then the tonal
 * wheels. The runtime shader's `applyGrade` carries the same equations.
 */
private class ColourGradeContext(grade: PhotoColourGrade) {
    private val vibrance = grade.vibrance
    private val protectSkin = grade.protectSkin
    private val hslActive = !grade.hslIsNeutral
    private val hslHue = FloatArray(8) { grade.hsl[it].hue }
    private val hslSaturation = FloatArray(8) { grade.hsl[it].saturation }
    private val hslLuminance = FloatArray(8) { grade.hsl[it].luminance }
    private val wheelsActive = !grade.wheelsAreNeutral
    private val shadows = colourWheelStops(grade.shadows)
    private val midtones = colourWheelStops(grade.midtones)
    private val highlights = colourWheelStops(grade.highlights)

    fun apply(red: Float, green: Float, blue: Float, out: FloatArray) {
        var r = red
        var g = green
        var b = blue
        if (vibrance != 0f) {
            val high = max(r, max(g, b))
            if (high > 0f) {
                val chroma = high - min(r, min(g, b))
                var push = vibrance * (1f - (chroma / high).coerceIn(0f, 1f))
                if (protectSkin && chroma > 0f) {
                    val hue = hueDegrees(r, g, b, high, chroma)
                    val skin = (1f - abs(hue - 25f) / 25f).coerceIn(0f, 1f)
                    push *= 1f - 0.65f * skin
                }
                val luma = luma709(r, g, b)
                r = luma + (r - luma) * (1f + push)
                g = luma + (g - luma) * (1f + push)
                b = luma + (b - luma) * (1f + push)
            }
        }
        if (hslActive) {
            val high = max(r, max(g, b))
            val chroma = high - min(r, min(g, b))
            if (high > 0f && chroma > 0f) {
                val hue = hueDegrees(r, g, b, high, chroma)
                val weight = min(1f, chroma / high * 2.5f)
                val deltaHue = hslAt(hslHue, hue) * weight
                val deltaSaturation = hslAt(hslSaturation, hue) * weight
                val deltaLuminance = hslAt(hslLuminance, hue) * weight
                // Rotation about the grey axis, at most 30 degrees.
                val angle = deltaHue * 0.5235988f
                val c = cos(angle)
                val s = sin(angle) * 0.57735026f
                val k = (1f - c) / 3f
                val nr = r * (c + k) + g * (k - s) + b * (k + s)
                val ng = r * (k + s) + g * (c + k) + b * (k - s)
                val nb = r * (k - s) + g * (k + s) + b * (c + k)
                val luma = luma709(nr, ng, nb)
                val gain = 2f.pow(deltaLuminance)
                r = (luma + (nr - luma) * (1f + deltaSaturation)) * gain
                g = (luma + (ng - luma) * (1f + deltaSaturation)) * gain
                b = (luma + (nb - luma) * (1f + deltaSaturation)) * gain
            }
        }
        if (wheelsActive) {
            val tone = sqrt(luma709(r, g, b).coerceIn(0f, 1f))
            val shadowMask = (1f - tone) * (1f - tone)
            val highlightMask = tone * tone
            val midMask = 1f - shadowMask - highlightMask
            r *= 2f.pow(
                shadowMask * shadows[0] + midMask * midtones[0] +
                    highlightMask * highlights[0]
            )
            g *= 2f.pow(
                shadowMask * shadows[1] + midMask * midtones[1] +
                    highlightMask * highlights[1]
            )
            b *= 2f.pow(
                shadowMask * shadows[2] + midMask * midtones[2] +
                    highlightMask * highlights[2]
            )
        }
        out[0] = r
        out[1] = g
        out[2] = b
    }

    private fun hueDegrees(
        r: Float,
        g: Float,
        b: Float,
        high: Float,
        chroma: Float
    ): Float {
        val hue = when (high) {
            r -> 60f * (g - b) / chroma
            g -> 60f * ((b - r) / chroma + 2f)
            else -> 60f * ((r - g) / chroma + 4f)
        }
        return if (hue < 0f) hue + 360f else hue
    }

    private fun hslAt(values: FloatArray, hue: Float): Float = when {
        hue < 30f -> blendBands(values, 0, 1, hue / 30f)
        hue < 60f -> blendBands(values, 1, 2, (hue - 30f) / 30f)
        hue < 120f -> blendBands(values, 2, 3, (hue - 60f) / 60f)
        hue < 180f -> blendBands(values, 3, 4, (hue - 120f) / 60f)
        hue < 220f -> blendBands(values, 4, 5, (hue - 180f) / 40f)
        hue < 280f -> blendBands(values, 5, 6, (hue - 220f) / 60f)
        hue < 320f -> blendBands(values, 6, 7, (hue - 280f) / 40f)
        else -> blendBands(values, 7, 0, (hue - 320f) / 40f)
    }

    private fun blendBands(values: FloatArray, a: Int, b: Int, t: Float) =
        values[a] + (values[b] - values[a]) * t
}

/**
 * A wheel as per-channel exposure stops: the colour push opposes the three
 * channels by hue at up to half a stop, luminance adds up to one stop.
 */
private fun colourWheelStops(wheel: PhotoColourWheel): FloatArray {
    val angle = wheel.hueDegrees * 0.017453292f
    val push = wheel.amount * 0.5f
    return floatArrayOf(
        cos(angle) * push + wheel.luminance,
        cos(angle - 2.0943952f) * push + wheel.luminance,
        cos(angle - 4.1887903f) * push + wheel.luminance
    )
}
