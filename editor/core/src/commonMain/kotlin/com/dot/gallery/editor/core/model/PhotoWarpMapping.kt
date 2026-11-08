package com.dot.gallery.editor.core.model

import com.dot.gallery.editor.core.geometry.WarpPlane
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous inverse mapping for an active [PhotoWarp], composed with
 * [PhotoMapping]'s rigid turn/flip/EXIF chain.
 *
 * The warp acts on the **plane**: the whole rigid frame (upright source
 * after quarter turns and flips, crop ignored), warped about its centre
 * by [com.dot.gallery.editor.core.geometry.WarpPlane]. The crop is an
 * axis-aligned window of that plane — the [PhotoGeometry.crop] rectangle
 * carried through the turns and flips onto the plane's axes ([planeOffset]
 * is its top-left in plane pixels). So the photo moves beneath a fixed
 * crop window exactly as the crop tool shows it, and pixels outside the
 * crop can rotate into it.
 *
 * For an output pixel index `(x, y)` — the .5 centre is added here — the
 * plane point `planeOffset + (x + .5, y + .5)` is inverted (zoom out by
 * [effectiveScale], rotate back, projective divide) to a point on the
 * rigid frame, then un-flipped/un-turned on the full upright frame and
 * un-oriented into raw stored coordinates with the same `width-1`
 * mirroring as the integer path. A neutral warp therefore lands integer
 * outputs on exactly the raw pixels [PhotoMapping] picks.
 *
 * [sourcePoint] returns null — transparent output — when the projective
 * denominator degenerates (`d <= .05`) or the inverted point falls outside
 * the rigid frame `[-.5, W-.5] x [-.5, H-.5]`. The crop tool keeps the crop
 * inside the warped photo, so exported crops carry no transparent edges.
 */
class PhotoWarpMapping(
    rawSize: PixelSize,
    orientation: Int,
    geometry: PhotoGeometry
) {

    private val rigid = PhotoMapping(rawSize, orientation, geometry)
    private val warp = geometry.warp

    private val uprightW = rigid.uprightSize.width
    private val uprightH = rigid.uprightSize.height
    private val turns = geometry.quarterTurns

    /** The rigid frame (warp plane) size in pixels — the upright frame after the turns. */
    val planeSize: PixelSize =
        if (turns % 2 == 1) PixelSize(uprightH, uprightW) else PixelSize(uprightW, uprightH)

    private val w = planeSize.width.toDouble()
    private val h = planeSize.height.toDouble()
    private val outW = rigid.outputSize.width.toDouble()
    private val outH = rigid.outputSize.height.toDouble()
    private val cos = cos(warp.rotationDegrees.toDouble() * PI / 180.0)
    private val sin = sin(warp.rotationDegrees.toDouble() * PI / 180.0)
    private val kx = warp.horizontalPerspective.toDouble() * WarpPlane.PERSPECTIVE_GAIN
    private val ky = warp.verticalPerspective.toDouble() * WarpPlane.PERSPECTIVE_GAIN

    /** The zoom applied in plane space — the user's [PhotoWarp.scale]. */
    val effectiveScale: Double = warp.scale.toDouble()

    /**
     * The crop's top-left in plane pixels: the upright pixel crop carried
     * through the quarter turns, then mirrored by the flips.
     */
    val planeOffset: PixelOffset = run {
        val c = rigid.cropRect
        val (ox, oy) = when (turns) {
            0 -> c.left to c.top
            1 -> (uprightH - c.top - c.height) to c.left
            2 -> (uprightW - c.left - c.width) to (uprightH - c.top - c.height)
            else -> c.top to (uprightW - c.left - c.width)
        }
        val outWi = rigid.outputSize.width
        val outHi = rigid.outputSize.height
        PixelOffset(
            if (geometry.flipHorizontal) planeSize.width - ox - outWi else ox,
            if (geometry.flipVertical) planeSize.height - oy - outHi else oy
        )
    }

    private val offX = planeOffset.x.toDouble()
    private val offY = planeOffset.y.toDouble()

    /** Output dimensions — identical to the rigid [PhotoMapping.outputSize]. */
    val outputSize: PixelSize get() = rigid.outputSize

    /** Dimensions of the source after EXIF orientation is applied. */
    val uprightSize: PixelSize get() = rigid.uprightSize

    /** Pixel crop on the upright frame, half-open [left,right)x[top,bottom). */
    val cropRect: PixelRect get() = rigid.cropRect

    /** True only when both the rigid chain and the warp are pass-throughs. */
    val isIdentity: Boolean
        get() = rigid.isIdentity && warp.isNeutral

    /**
     * Maps an output pixel index `(x, y)` to the continuous raw source
     * coordinate it samples, or null when the output is transparent.
     */
    fun sourcePoint(x: Double, y: Double): ContinuousPoint? {
        require(x >= 0.0 && x < outW && y >= 0.0 && y < outH) {
            "output point ($x, $y) outside ${outW.toInt()}x${outH.toInt()}"
        }
        // Plane point relative to the plane centre, zoom undone.
        var px = offX + x + .5 - w / 2
        var py = offY + y + .5 - h / 2
        px /= effectiveScale
        py /= effectiveScale

        val rx = cos * px + sin * py
        val ry = -sin * px + cos * py

        val nx = rx / (w / 2)
        val ny = ry / (h / 2)
        val d = 1 - kx * nx - ky * ny
        if (d <= WarpPlane.MIN_DENOMINATOR) return null
        val sx = nx / d
        val sy = ny / d

        var ox = (sx + 1) * w / 2 - .5
        var oy = (sy + 1) * h / 2 - .5
        if (ox < -.5 || ox > w - .5 || oy < -.5 || oy > h - .5) return null

        // Continuous rigid chain on the full frame: un-flip -> un-turn -> un-orient.
        val geometry = rigid.geometry
        if (geometry.flipHorizontal) ox = w - 1 - ox
        if (geometry.flipVertical) oy = h - 1 - oy
        val uW = uprightW.toDouble()
        val uH = uprightH.toDouble()
        val u: Double
        val v: Double
        when (turns) {
            0 -> { u = ox; v = oy }
            1 -> { u = oy; v = uH - 1 - ox }
            2 -> { u = uW - 1 - ox; v = uH - 1 - oy }
            else -> { u = uW - 1 - oy; v = ox }
        }
        val rawW = rigid.rawSize.width.toDouble()
        val rawH = rigid.rawSize.height.toDouble()
        return when (rigid.orientation) {
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
}
