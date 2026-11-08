package com.dot.gallery.editor.core.model

/**
 * The four photo tone curves — [master] bends all three channels
 * together, then [red]/[green]/[blue] bend their own channel. A neutral
 * set performs no arithmetic.
 */
data class PhotoCurves(
    val master: PhotoToneCurve = PhotoToneCurve(),
    val red: PhotoToneCurve = PhotoToneCurve(),
    val green: PhotoToneCurve = PhotoToneCurve(),
    val blue: PhotoToneCurve = PhotoToneCurve()
) {
    val isNeutral: Boolean
        get() = master.isNeutral && red.isNeutral &&
            green.isNeutral && blue.isNeutral
}
