package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoWarp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The continuous warp plane of a photo: the rigid frame (upright source
 * after quarter turns and flips, before any crop) of [width] x [height]
 * pixels, warped about its centre by [warp].
 *
 * Coordinates are continuous pixel units on both sides — pixel `i`
 * covers `[i, i + 1)` and the frame spans `[0, width] x [0, height]`.
 * The crop selects an axis-aligned rectangle of this plane, so what the
 * crop tool frames is exactly what export writes.
 *
 * [toRigid] is the sampling direction (plane -> rigid frame): undo the
 * zoom, rotate, then the projective keystone divide. [toPlane] is its
 * exact inverse. These are the same equations [com.dot.gallery.editor.core.model.PhotoWarpMapping]
 * evaluates per output pixel.
 */
class WarpPlane(
    val width: Double,
    val height: Double,
    val warp: PhotoWarp
) {
    init {
        require(width > 0.0 && height > 0.0 && width.isFinite() && height.isFinite()) {
            "plane must have a positive finite size: ${width}x$height"
        }
    }

    private val cos = cos(warp.rotationDegrees.toDouble() * PI / 180.0)
    private val sin = sin(warp.rotationDegrees.toDouble() * PI / 180.0)
    private val kx = warp.horizontalPerspective.toDouble() * PERSPECTIVE_GAIN
    private val ky = warp.verticalPerspective.toDouble() * PERSPECTIVE_GAIN
    private val scale = warp.scale.toDouble()
    private val halfW = width / 2.0
    private val halfH = height / 2.0

    /** Plane point -> rigid-frame point, or null when the keystone degenerates. */
    fun toRigid(x: Double, y: Double): Vec2? {
        val px = (x - halfW) / scale
        val py = (y - halfH) / scale
        val rx = cos * px + sin * py
        val ry = -sin * px + cos * py
        val nx = rx / halfW
        val ny = ry / halfH
        val d = 1.0 - kx * nx - ky * ny
        if (d <= MIN_DENOMINATOR) return null
        return Vec2((nx / d + 1.0) * halfW, (ny / d + 1.0) * halfH)
    }

    /** Rigid-frame point -> plane point, or null outside the keystone's domain. */
    fun toPlane(x: Double, y: Double): Vec2? {
        val sx = x / halfW - 1.0
        val sy = y / halfH - 1.0
        val e = 1.0 + kx * sx + ky * sy
        if (e <= 0.0) return null
        val nx = sx / e
        val ny = sy / e
        // The forward image must satisfy the sampling direction's guard.
        if (1.0 - kx * nx - ky * ny <= MIN_DENOMINATOR) return null
        val rx = nx * halfW
        val ry = ny * halfH
        val px = cos * rx - sin * ry
        val py = sin * rx + cos * ry
        return Vec2(px * scale + halfW, py * scale + halfH)
    }

    /**
     * The warped photo's outline in the plane — the rigid frame's corners
     * carried through [toPlane]. A projective map keeps lines straight, so
     * the outline is exactly this convex quadrilateral (the legal keystone
     * range keeps every corner inside the domain).
     */
    fun photoOutline(): ConvexPolygon {
        val corners = listOf(
            Vec2(0.0, 0.0), Vec2(width, 0.0), Vec2(width, height), Vec2(0.0, height)
        ).map { c ->
            toPlane(c.x, c.y) ?: error("rigid corner outside the warp domain: $c")
        }
        return ConvexPolygon(corners)
    }

    /**
     * Where the crop may live: inside the warped photo and inside the plane
     * frame (the normalized crop is stored on `[0, 1]`).
     */
    fun validRegion(): ConvexPolygon =
        photoOutline().clipToRect(0.0, 0.0, width, height)

    companion object {
        /** Keystone gain: the model's ±100 percent maps to ±0.35. */
        const val PERSPECTIVE_GAIN = 0.0035
        /** Projective denominators at or below this sample transparent. */
        const val MIN_DENOMINATOR = 0.05
    }
}
