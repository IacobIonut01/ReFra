package com.dot.gallery.editor.core.model

import kotlin.math.ceil

/**
 * The spatial stage of a photo recipe — local-contrast Details and the
 * masked Lens blur applied after layer compositing and before the
 * finishing stage. A neutral spatial stage is bit-preserving.
 *
 * [requiredHalo] is the source-read margin an export band needs around
 * its output rows: 1 px for the Details 3x3, `ceil(strength * 16)` for
 * the blur disk — and because the blur taps read the *Details output*,
 * a combined recipe needs the two margins composed: the blur's 16 px
 * reach plus the one row the Details 3x3 needs beyond it.
 */
data class PhotoSpatialEffects(
    val details: PhotoDetails = PhotoDetails(),
    val lensBlur: PhotoLensBlur = PhotoLensBlur()
) {
    val isNeutral: Boolean
        get() = details.isNeutral && lensBlur.isNeutral

    val requiredHalo: Int
        get() = (if (details.isNeutral) 0 else DETAILS_HALO) +
            if (lensBlur.isNeutral) 0 else {
                ceil(lensBlur.strength * MAX_LENS_RADIUS).toInt()
            }

    companion object {
        /**
         * The spatial stage's declared maximum halo: the blur disk's
         * 16-pixel maximum reach plus the Details pass's one-pixel
         * neighbourhood when both stages are active.
         */
        const val MAX_HALO = 17
        const val MAX_LENS_RADIUS = 16f
        private const val DETAILS_HALO = 1
    }
}
