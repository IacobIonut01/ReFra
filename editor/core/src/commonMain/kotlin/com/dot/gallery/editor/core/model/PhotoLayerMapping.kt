package com.dot.gallery.editor.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The constants of [PhotoLayerMapping.sourcePoint]'s upright inverse. */
class PhotoLayerInverse internal constructor(
    val centerX: Double,
    val centerY: Double,
    val scale: Double,
    val cos: Double,
    val sin: Double,
    val uprightWidth: Double,
    val uprightHeight: Double,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean
)

/**
 * Continuous inverse placement of a layer onto the base output canvas.
 *
 * For a base output `W x H` and an upright layer source `w x h`, the
 * layer's default scale is `fit = min(W/w, H/h)` and its effective scale
 * is `fit * transform.scale` centred on `W/2 + translationX`,
 * `H/2 + translationY`. An output pixel index `(x, y)` — the formula adds
 * the .5 centre itself — inverse-maps into the layer's pre-EXIF upright
 * coordinates; flips then mirror around `w-1`/`h-1` and EXIF inversion
 * maps to raw stored coordinates, with the same `width-1` conventions as
 * [PhotoMapping].
 *
 * [sourcePoint] returns null — transparent layer contribution — when the
 * mapped point falls outside the upright domain `[-.5, w-.5] x
 * [-.5, h-.5]`; points inside the domain edge remain valid antialiased
 * samples.
 */
class PhotoLayerMapping(
    rawSize: PixelSize,
    private val orientation: Int,
    val outputSize: PixelSize,
    private val transform: PhotoLayerTransform
) {
    init {
        require(orientation in 1..8) {
            "EXIF orientation must be in 1..8: $orientation"
        }
    }

    private val rawW = rawSize.width.toDouble()
    private val rawH = rawSize.height.toDouble()

    /** Dimensions of the layer source after EXIF orientation is applied. */
    val uprightSize: PixelSize =
        if (orientation in 5..8) PixelSize(rawSize.height, rawSize.width) else rawSize

    private val w = uprightSize.width.toDouble()
    private val h = uprightSize.height.toDouble()

    /** The default fit-inside scale: `min(W/w, H/h)`. */
    val fitScale: Double = min(
        outputSize.width / w,
        outputSize.height / h
    )

    /** `fitScale * transform.scale` — the forward zoom of the layer. */
    val effectiveScale: Double = fitScale * transform.scale

    private val cx = outputSize.width / 2.0 + transform.translationX
    private val cy = outputSize.height / 2.0 + transform.translationY
    private val cos = cos(transform.rotationDegrees.toDouble() * PI / 180.0)
    private val sin = sin(transform.rotationDegrees.toDouble() * PI / 180.0)

    /**
     * Maps a base output pixel index `(x, y)` to the layer sample it
     * reads, or null when the point falls outside the upright domain.
     */
    fun sourcePoint(outputX: Double, outputY: Double): PhotoLayerSample? {
        require(
            outputX >= 0.0 && outputX < outputSize.width &&
                outputY >= 0.0 && outputY < outputSize.height
        ) {
            "output point ($outputX, $outputY) outside ${outputSize.width}x${outputSize.height}"
        }
        val dx = (outputX + .5 - cx) / effectiveScale
        val dy = (outputY + .5 - cy) / effectiveScale
        var u = cos * dx + sin * dy + w / 2 - .5
        var v = -sin * dx + cos * dy + h / 2 - .5
        if (transform.flipHorizontal) u = w - 1 - u
        if (transform.flipVertical) v = h - 1 - v
        if (u < -.5 || u > w - .5 || v < -.5 || v > h - .5) return null
        val sample = PhotoLayerSample(
            raw = rawPoint(u, v),
            normalizedX = ((u + .5) / w).coerceIn(0.0, 1.0),
            normalizedY = ((v + .5) / h).coerceIn(0.0, 1.0)
        )
        return sample
    }

    /**
     * The terms of [sourcePoint]'s upright inverse, for callers mapping every
     * pixel of a frame who cannot afford a [PhotoLayerSample] per pixel.
     */
    val inverse: PhotoLayerInverse
        get() = PhotoLayerInverse(
            centerX = cx,
            centerY = cy,
            scale = effectiveScale,
            cos = cos,
            sin = sin,
            uprightWidth = w,
            uprightHeight = h,
            flipHorizontal = transform.flipHorizontal,
            flipVertical = transform.flipVertical
        )

    /**
     * The forward counterpart of [sourcePoint]: an upright `(u, v)` maps
     * to its continuous output coordinate in the same `(x+.5, y+.5)`
     * pixel-centre space the inverse reads, so callers can paint the
     * layer's placement quad without inverting the transform. Flips
     * mirror inside the quad — the outline is translation, rotation and
     * scale only — and EXIF stays out: the user placed the *upright*
     * frame, so the upright corners define it.
     */
    fun outputPoint(uprightX: Double, uprightY: Double): ContinuousPoint {
        val a = uprightX - w / 2 + .5
        val b = uprightY - h / 2 + .5
        return ContinuousPoint(
            x = cx + effectiveScale * (cos * a - sin * b),
            y = cy + effectiveScale * (sin * a + cos * b)
        )
    }

    /** Upright `(u, v)` to raw stored coordinates, by EXIF orientation. */
    private fun rawPoint(u: Double, v: Double): ContinuousPoint =
        when (orientation) {
            1 -> ContinuousPoint(u, v)
            2 -> ContinuousPoint(rawW - 1 - u, v)
            3 -> ContinuousPoint(rawW - 1 - u, rawH - 1 - v)
            4 -> ContinuousPoint(u, rawH - 1 - v)
            5 -> ContinuousPoint(v, u)
            6 -> ContinuousPoint(v, rawH - 1 - u)
            7 -> ContinuousPoint(rawW - 1 - v, rawH - 1 - u)
            else -> ContinuousPoint(rawW - 1 - v, u)
        }
}
