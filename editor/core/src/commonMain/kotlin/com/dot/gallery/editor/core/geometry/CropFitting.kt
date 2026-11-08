package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PhotoWarp
import com.dot.gallery.editor.core.model.PixelSize

/**
 * The crop tool's geometry operations as pure functions of
 * [PhotoGeometry]: every result keeps the crop inside the warped photo
 * (no blank edges), so the tool never has to repair a recipe afterwards.
 *
 * `aspect` is the displayed crop ratio (width / height on the plane), or
 * null for Free — Free keeps the crop's current ratio when it has to be
 * refitted.
 */
object CropFitting {

    /** Smallest crop side the tool produces, in source pixels. */
    const val MIN_SIDE_PX = 48.0

    fun planeOf(upright: PixelSize, geometry: PhotoGeometry) = CropPlane(upright, geometry)

    /**
     * The minimum crop side for a photo: [MIN_SIDE_PX], but never more
     * than a quarter of its short side (tiny sources stay croppable).
     */
    fun minSide(upright: PixelSize): Double =
        minOf(MIN_SIDE_PX, minOf(upright.width, upright.height) / 4.0).coerceAtLeast(1.0)

    /**
     * The fitting region for [geometry]'s warp, in plane pixels: the
     * warped photo inside the frame, inset by [PIXEL_MARGIN] so the
     * outward floor/ceil rounding of the pixel crop can never reach a
     * transparent sample.
     */
    fun region(upright: PixelSize, geometry: PhotoGeometry): ConvexPolygon =
        fittingRegion(planeOf(upright, geometry).plane(geometry.warp))

    private fun fittingRegion(plane: WarpPlane): ConvexPolygon {
        val raw = plane.validRegion()
        // A neutral warp's region is the frame itself: no inset, so the
        // untouched full-frame crop stays exactly [0, 1].
        return if (plane.warp.isNeutral) raw else raw.inset(PIXEL_MARGIN)
    }

    /** Inset applied to warped regions, in plane pixels: one pixel of outward crop rounding plus the half pixel a bilinear sample reaches, with headroom. */
    const val PIXEL_MARGIN = 2.0

    fun planeRect(upright: PixelSize, geometry: PhotoGeometry): PlaneRect =
        planeOf(upright, geometry).rectOf(geometry.crop)

    /** True when the crop lies inside the warped photo. */
    fun isValid(upright: PixelSize, geometry: PhotoGeometry, tolerancePx: Double = 0.5): Boolean =
        InscribedCrop.contains(
            planeOf(upright, geometry).plane(geometry.warp).validRegion(),
            planeRect(upright, geometry), tolerancePx
        )

    /**
     * A new warp applied the way the tool presents it — the photo moves
     * beneath a fixed frame. The source point under [start]'s crop centre
     * stays under the centre, and the crop keeps [start]'s size where the
     * new region allows (so a straighten that returns to 0 regrows the crop
     * the same gesture shrank).
     */
    fun withWarp(
        upright: PixelSize,
        start: PhotoGeometry,
        warp: PhotoWarp,
        aspect: Double?
    ): PhotoGeometry {
        val cp = planeOf(upright, start)
        val oldPlane = cp.plane(start.warp)
        val newPlane = cp.plane(warp)
        val rect = cp.rectOf(start.crop)
        val centre = oldPlane.toRigid(rect.center.x, rect.center.y)
            ?.let { newPlane.toPlane(it.x, it.y) } ?: rect.center
        val ratio = aspect ?: rect.aspect
        val fitted = InscribedCrop.fit(fittingRegion(newPlane), ratio, centre, rect.height)
            ?: return start.copy(warp = warp)
        return start.copy(warp = warp, crop = cp.cropOf(fitted, minSide(upright)))
    }

    /** The largest crop at [aspect] nearest the current crop centre. */
    fun withAspect(upright: PixelSize, geometry: PhotoGeometry, aspect: Double): PhotoGeometry {
        val cp = planeOf(upright, geometry)
        val rect = cp.rectOf(geometry.crop)
        val fitted = InscribedCrop.fit(region(upright, geometry), aspect, rect.center)
            ?: return geometry
        return geometry.copy(crop = cp.cropOf(fitted, minSide(upright)))
    }

    /** The largest legal crop keeping the current ratio (Free) or [aspect]. */
    fun maximized(upright: PixelSize, geometry: PhotoGeometry, aspect: Double?): PhotoGeometry {
        val cp = planeOf(upright, geometry)
        val ratio = aspect ?: (cp.width / cp.height)
        val plane = cp.plane(geometry.warp)
        val centre = plane.toPlane(cp.width / 2.0, cp.height / 2.0)
            ?: Vec2(cp.width / 2.0, cp.height / 2.0)
        val fitted = InscribedCrop.fit(fittingRegion(plane), ratio, centre) ?: return geometry
        return geometry.copy(crop = cp.cropOf(fitted, minSide(upright)))
    }

    /** Re-seats an out-of-region crop: same centre preference and size cap. */
    fun constrained(upright: PixelSize, geometry: PhotoGeometry, aspect: Double?): PhotoGeometry {
        if (isValid(upright, geometry)) return geometry
        val cp = planeOf(upright, geometry)
        val rect = cp.rectOf(geometry.crop)
        val fitted = InscribedCrop.fit(
            region(upright, geometry), aspect ?: rect.aspect, rect.center, rect.height
        ) ?: return geometry
        return geometry.copy(crop = cp.cropOf(fitted, minSide(upright)))
    }

    /**
     * A proposed crop rectangle from a handle or move gesture, limited to
     * the furthest legal point between the last legal [from] and [to].
     */
    fun clampedRect(upright: PixelSize, geometry: PhotoGeometry, from: PlaneRect, to: PlaneRect): PlaneRect =
        InscribedCrop.clampToward(region(upright, geometry), from, to)

    /**
     * A 90° counter-clockwise turn that rotates what the user sees: the
     * warp is conjugated by the turn so the straighten/keystone keep their
     * visual effect, and the stored crop (upright fractions) turns with
     * the photo on its own.
     */
    fun rotatedLeft(geometry: PhotoGeometry): PhotoGeometry {
        val w = geometry.warp
        // Flips apply after the turns: with exactly one mirror active the
        // stored turn runs the other way to turn the visible result left.
        val delta = if (geometry.anyFlip()) 1 else 3
        return geometry.copy(
            quarterTurns = (geometry.quarterTurns + delta) % 4,
            warp = w.copy(
                // The warp lives on the plane after the flips, so turning
                // the visible result is always the same conjugation.
                verticalPerspective = -w.horizontalPerspective + 0f,
                horizontalPerspective = w.verticalPerspective
            )
        )
    }

    /** Mirror left-right of the visible result. */
    fun flippedHorizontally(geometry: PhotoGeometry): PhotoGeometry {
        val w = geometry.warp
        return geometry.copy(
            flipHorizontal = !geometry.flipHorizontal,
            warp = w.copy(
                rotationDegrees = -w.rotationDegrees + 0f,
                horizontalPerspective = -w.horizontalPerspective + 0f
            )
        )
    }

    /** Mirror top-bottom of the visible result. */
    fun flippedVertically(geometry: PhotoGeometry): PhotoGeometry {
        val w = geometry.warp
        return geometry.copy(
            flipVertical = !geometry.flipVertical,
            warp = w.copy(
                rotationDegrees = -w.rotationDegrees + 0f,
                verticalPerspective = -w.verticalPerspective + 0f
            )
        )
    }

    private fun PhotoGeometry.anyFlip() = flipHorizontal != flipVertical
}
