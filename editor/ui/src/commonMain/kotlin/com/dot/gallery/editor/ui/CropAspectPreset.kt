package com.dot.gallery.editor.ui

import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.math.abs

/**
 * The Crop & straighten tool's aspect strip. Ratios are *displayed*
 * ratios — width / height of the crop as the user sees it on the warp
 * plane — given in their landscape form ([long]:[short]); the tool's
 * orientation toggle takes the reciprocal. Free locks nothing; Original
 * follows the photo's own (turned) frame.
 *
 * The preset is UI-only state (see [PhotoToolUiState]) and never part of
 * the recipe: it decides which ratio the handles preserve and what a
 * preset click fits.
 */
internal enum class CropAspectPreset(val long: Int, val short: Int) {
    Free(0, 0),
    Original(0, 0),
    Square(1, 1),
    FourFive(5, 4),
    ThreeTwo(3, 2),
    FourThree(4, 3),
    Wide(16, 9);

    val isFixedRatio: Boolean get() = this != Free && this != Original
    val isSquare: Boolean get() = this == Square

    /**
     * The displayed ratio for a plane of [planeWidth] x [planeHeight]
     * (pixels) in [portrait] orientation, or null for Free.
     */
    fun ratio(planeWidth: Double, planeHeight: Double, portrait: Boolean): Double? = when (this) {
        Free -> null
        Original -> {
            val r = planeWidth / planeHeight
            val landscape = maxOf(r, 1.0 / r)
            if (portrait) 1.0 / landscape else landscape
        }
        else -> {
            val r = long.toDouble() / short.toDouble()
            if (portrait) 1.0 / r else r
        }
    }

    /** "4:5" / "5:4" style label for the fixed ratios. */
    fun ratioLabel(portrait: Boolean): String =
        if (portrait) "$short:$long" else "$long:$short"

    companion object {
        /** The orientation a plane shows by default (square photos open portrait, the common 4:5 form). */
        fun portraitOf(planeWidth: Double, planeHeight: Double) = planeHeight >= planeWidth

        /**
         * The preset and orientation matching [geometry]'s displayed crop
         * within 0.5 %: a whole-frame crop resolves to Original, any other
         * unmatched ratio to Free.
         */
        fun derive(geometry: PhotoGeometry, upright: PixelSize): Pair<CropAspectPreset, Boolean> {
            val cp = CropFitting.planeOf(upright, geometry)
            val rect = cp.rectOf(geometry.crop)
            val defaultPortrait = portraitOf(cp.width, cp.height)
            if (rect.width <= 0.0 || rect.height <= 0.0) return Original to defaultPortrait
            val ratio = rect.aspect
            // A square crop has no orientation of its own: keep the default.
            val portrait = if (abs(ratio - 1.0) <= 0.005) defaultPortrait else ratio < 1.0
            for (preset in listOf(Original, Square, FourFive, ThreeTwo, FourThree, Wide)) {
                val r = preset.ratio(cp.width, cp.height, portrait) ?: continue
                if (abs(r - ratio) / r <= 0.005) {
                    return preset to portrait
                }
            }
            return Free to defaultPortrait
        }
    }
}
