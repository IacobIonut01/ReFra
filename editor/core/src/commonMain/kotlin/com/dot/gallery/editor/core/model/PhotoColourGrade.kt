package com.dot.gallery.editor.core.model

/**
 * The eight selective colour bands of [PhotoColourGrade.hsl], in hue order.
 * [centerHue] is the band's peak in degrees; a pixel between two centres
 * blends their adjustments linearly.
 */
enum class PhotoHslBand(val centerHue: Float) {
    Red(0f),
    Orange(30f),
    Yellow(60f),
    Green(120f),
    Aqua(180f),
    Blue(220f),
    Purple(280f),
    Magenta(320f)
}

/** One band's selective adjustment; every field is normalized to -1..1. */
data class PhotoHslAdjustment(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
) {
    init {
        require(hue.isFinite() && hue in -1f..1f) {
            "hsl hue must be finite inside -1..1: $hue"
        }
        require(saturation.isFinite() && saturation in -1f..1f) {
            "hsl saturation must be finite inside -1..1: $saturation"
        }
        require(luminance.isFinite() && luminance in -1f..1f) {
            "hsl luminance must be finite inside -1..1: $luminance"
        }
    }

    val isNeutral: Boolean
        get() = hue == 0f && saturation == 0f && luminance == 0f
}

/**
 * One tonal region's colour wheel: the push's direction ([hueDegrees], 0 is
 * red and increasing runs through yellow and green), its strength
 * ([amount], 0..1) and the region's brightness ([luminance], -1..1).
 */
data class PhotoColourWheel(
    val hueDegrees: Float = 0f,
    val amount: Float = 0f,
    val luminance: Float = 0f
) {
    init {
        require(hueDegrees.isFinite() && hueDegrees in 0f..360f) {
            "wheel hue must be finite inside 0..360: $hueDegrees"
        }
        require(amount.isFinite() && amount in 0f..1f) {
            "wheel amount must be finite inside 0..1: $amount"
        }
        require(luminance.isFinite() && luminance in -1f..1f) {
            "wheel luminance must be finite inside -1..1: $luminance"
        }
    }

    val isNeutral: Boolean get() = amount == 0f && luminance == 0f
}

/**
 * The colour grade of the finishing stage: vibrance, per-band HSL and the
 * three tonal colour wheels. [protectSkin] only damps vibrance on skin hues,
 * so on its own it changes no pixel and does not count against [isNeutral].
 */
data class PhotoColourGrade(
    val vibrance: Float = 0f,
    val protectSkin: Boolean = false,
    val hsl: List<PhotoHslAdjustment> =
        List(PhotoHslBand.entries.size) { PhotoHslAdjustment() },
    val shadows: PhotoColourWheel = PhotoColourWheel(),
    val midtones: PhotoColourWheel = PhotoColourWheel(),
    val highlights: PhotoColourWheel = PhotoColourWheel()
) {
    init {
        require(vibrance.isFinite() && vibrance in -1f..1f) {
            "vibrance must be finite inside -1..1: $vibrance"
        }
        require(hsl.size == PhotoHslBand.entries.size) {
            "hsl holds one adjustment per band: ${hsl.size}"
        }
    }

    val hslIsNeutral: Boolean get() = hsl.all { it.isNeutral }

    val wheelsAreNeutral: Boolean
        get() = shadows.isNeutral && midtones.isNeutral && highlights.isNeutral

    val isNeutral: Boolean
        get() = vibrance == 0f && hslIsNeutral && wheelsAreNeutral

    fun adjustment(band: PhotoHslBand): PhotoHslAdjustment = hsl[band.ordinal]

    fun withAdjustment(
        band: PhotoHslBand,
        adjustment: PhotoHslAdjustment
    ): PhotoColourGrade = copy(
        hsl = hsl.toMutableList().also { it[band.ordinal] = adjustment }
    )
}
