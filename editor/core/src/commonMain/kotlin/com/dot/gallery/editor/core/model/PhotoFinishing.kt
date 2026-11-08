package com.dot.gallery.editor.core.model

/**
 * The finishing stage of a photo recipe — white balance, colour grade, tone
 * curves, look, vignette and grain applied after colour, geometry and layer
 * compositing. A neutral finishing is bit-preserving.
 */
data class PhotoFinishing(
    val curves: PhotoCurves = PhotoCurves(),
    val whiteBalance: PhotoWhiteBalance = PhotoWhiteBalance(),
    val vignette: PhotoVignette = PhotoVignette(),
    val grain: PhotoGrain = PhotoGrain(),
    val look: PhotoLook = PhotoLook(),
    val grade: PhotoColourGrade = PhotoColourGrade()
) {
    val isNeutral: Boolean
        get() = curves.isNeutral && whiteBalance.isNeutral &&
            grade.isNeutral && vignette.isNeutral && grain.isNeutral &&
            look.isNeutral
}
