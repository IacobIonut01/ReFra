package com.dot.gallery.editor.core.model

/**
 * White balance recipe: [temperatureKelvin] slides warm/cool across the
 * authored 2000..12000 K domain (6500 K is neutral), [tint] slides
 * green/magenta on the display -100..100 scale (0 is neutral).
 */
data class PhotoWhiteBalance(
    val temperatureKelvin: Float = 6500f,
    val tint: Float = 0f
) {
    init {
        require(
            temperatureKelvin.isFinite() &&
                temperatureKelvin in 2000f..12000f
        ) {
            "temperature must be finite inside 2000..12000 K: $temperatureKelvin"
        }
        require(tint.isFinite() && tint in -100f..100f) {
            "tint must be finite inside -100..100: $tint"
        }
    }

    val isNeutral: Boolean
        get() = temperatureKelvin == 6500f && tint == 0f
}
