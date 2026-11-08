package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.model.PhotoColourWheel
import com.dot.gallery.editor.core.model.PhotoFinishing
import com.dot.gallery.editor.core.model.PhotoLookId
import com.dot.gallery.editor.core.model.PhotoToneCurve
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import kotlin.math.cos
import kotlin.math.pow

/**
 * Uniform marshalling for the finishing stage of the runtime shader:
 * turns a [PhotoFinishing] recipe into the scalar/vector values the
 * generated shader consumes. The per-pixel math lives once in the
 * shader source and once in the CPU processor — this is only parameter
 * derivation: white balance collapses to its three channel gains and
 * each curve uploads its spline's segments.
 *
 * The runtime-shader path supports white balance, curves, look and
 * vignette — grain's integer-avalanche hash has no portable bit-exact
 * shader form, so [PhotoShaderRenderer] hosts gate the shader off while
 * grain is non-zero and let the CPU preview draw it honestly.
 */
internal class PhotoShaderFinishing(finishing: PhotoFinishing) {

    val whiteBalanceActive = !finishing.whiteBalance.isNeutral

    /** Linear RGB gains — the same formula the CPU processor applies. */
    val whiteBalanceGains = whiteBalanceGains(finishing.whiteBalance)

    val gradeVibrance = finishing.grade.vibrance
    val gradeProtectSkin = finishing.grade.protectSkin
    val gradeHslActive = !finishing.grade.hslIsNeutral

    /** One (hue, saturation, luminance) triple per band, in band order. */
    val gradeHsl = finishing.grade.hsl.map {
        floatArrayOf(it.hue, it.saturation, it.luminance)
    }
    val gradeWheelsActive = !finishing.grade.wheelsAreNeutral
    val gradeShadows = wheelStops(finishing.grade.shadows)
    val gradeMidtones = wheelStops(finishing.grade.midtones)
    val gradeHighlights = wheelStops(finishing.grade.highlights)

    val curvesActive = !finishing.curves.isNeutral
    val curveMaster = PhotoShaderCurve(finishing.curves.master)
    val curveRed = PhotoShaderCurve(finishing.curves.red)
    val curveGreen = PhotoShaderCurve(finishing.curves.green)
    val curveBlue = PhotoShaderCurve(finishing.curves.blue)

    /** The look's shader id + blend. The ids are a fixed contract with
     * the shader's `lookTarget` — never enum ordinals, so reordering or
     * extending [com.dot.gallery.editor.core.model.PhotoLookId] cannot
     * silently paint the wrong grade. */
    val lookId = shaderLookId(finishing.look.id)
    val lookAmount = finishing.look.amount

    val vignetteActive = finishing.vignette.amount != 0f
    val vignetteAmount = finishing.vignette.amount
    val vignetteMidpoint = finishing.vignette.midpoint
    val vignetteFeather = finishing.vignette.feather
    val vignetteRoundness = finishing.vignette.roundness
    val vignetteHighlights = finishing.vignette.highlights

    /** Per-channel exposure stops — the same formula the CPU processor applies. */
    private fun wheelStops(wheel: PhotoColourWheel): FloatArray {
        val angle = wheel.hueDegrees * 0.017453292f
        val push = wheel.amount * 0.5f
        return floatArrayOf(
            cos(angle) * push + wheel.luminance,
            cos(angle - 2.0943952f) * push + wheel.luminance,
            cos(angle - 4.1887903f) * push + wheel.luminance
        )
    }

    private fun whiteBalanceGains(wb: PhotoWhiteBalance): FloatArray {
        val t = if (wb.temperatureKelvin >= 6500) {
            (wb.temperatureKelvin - 6500) / 5500
        } else {
            (wb.temperatureKelvin - 6500) / 4500
        }
        val u = wb.tint / 100f
        return floatArrayOf(
            2.0.pow(0.35 * t + 0.10 * u).toFloat(),
            2.0.pow(-0.20 * u).toFloat(),
            2.0.pow(-0.35 * t + 0.10 * u).toFloat()
        )
    }
}

/**
 * One curve's uniforms: [starts] holds [PHOTO_SHADER_CURVE_SEGMENTS] segment
 * starts with unused slots past any input, [coefficients] four terms per slot,
 * and [ends] the first and last point as (x0, y0, xn, yn).
 */
internal class PhotoShaderCurve(curve: PhotoToneCurve) {
    val active = !curve.isNeutral
    private val spline = curve.spline
    val ends = floatArrayOf(spline.firstX, spline.firstY, spline.lastX, spline.lastY)
    val starts = FloatArray(PHOTO_SHADER_CURVE_SEGMENTS) { 2f }.also {
        spline.starts.copyInto(it)
    }
    val coefficients = FloatArray(PHOTO_SHADER_CURVE_SEGMENTS * 4).also {
        spline.coefficients.copyInto(it)
    }
}

/**
 * The shader's look-id contract. The numeric ids match the `lookTarget`
 * branches in the runtime shader source one-for-one; changing one side
 * without the other paints the wrong grade. `PhotoLookId.None` is 0, the
 * shader's neutral gate.
 */
internal fun shaderLookId(id: PhotoLookId): Int = when (id) {
    PhotoLookId.None -> 0
    PhotoLookId.Clean -> 1
    PhotoLookId.Vivid -> 2
    PhotoLookId.Pastel -> 3
    PhotoLookId.Chrome -> 4
    PhotoLookId.WarmFade -> 5
    PhotoLookId.Cool -> 6
    PhotoLookId.Golden -> 7
    PhotoLookId.Desert -> 8
    PhotoLookId.Forest -> 9
    PhotoLookId.Rose -> 10
    PhotoLookId.BlueHour -> 11
    PhotoLookId.Cinematic -> 12
    PhotoLookId.TealOrange -> 13
    PhotoLookId.CinemaGreen -> 14
    PhotoLookId.Vintage -> 15
    PhotoLookId.Fade -> 16
    PhotoLookId.Matte -> 17
    PhotoLookId.Bleach -> 18
    PhotoLookId.Sepia -> 19
    PhotoLookId.Mono -> 20
    PhotoLookId.Noir -> 21
}
