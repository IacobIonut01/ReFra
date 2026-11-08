package com.dot.gallery.editor.core.model

enum class PhotoRawWhiteBalance { Camera, Auto, Daylight, Custom }

enum class PhotoRawDemosaic { Linear, Vng, Ppg, Ahd, Dcb }

enum class PhotoRawNoiseReduction { Off, Low, Medium, High }

enum class PhotoRawHighlights { Clip, Blend, Rebuild }

enum class PhotoRawColourSpace { Srgb, AdobeRgb, Wide, ProPhoto }

/**
 * How a RAW source becomes pixels, ahead of every other stage of the recipe. The
 * defaults are a neutral develop: camera white balance, sRGB, AHD. Ignored for
 * non-RAW sources. Fields below the demosaic split ([contrast] and on, except the
 * colour space) re-tone a decoded base; the rest need a new decode, which is what
 * [sharesBaseWith] tells a host.
 */
data class PhotoRawDevelop(
    val whiteBalance: PhotoRawWhiteBalance = PhotoRawWhiteBalance.Camera,
    val temperatureKelvin: Float = 5500f,
    /** Green to magenta, -1..1. */
    val tint: Float = 0f,
    /** Stops, -3..3. */
    val exposure: Float = 0f,
    /** Scene brightness, -2..2. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    /** 0..2. */
    val sharpen: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val noiseReduction: PhotoRawNoiseReduction = PhotoRawNoiseReduction.Off,
    val highlightRecovery: PhotoRawHighlights = PhotoRawHighlights.Clip,
    val colourSpace: PhotoRawColourSpace = PhotoRawColourSpace.Srgb,
    val demosaic: PhotoRawDemosaic = PhotoRawDemosaic.Ahd,
    val halfSize: Boolean = false
) {
    init {
        require(temperatureKelvin.isFinite() && temperatureKelvin in 2000f..12000f) {
            "temperature outside 2000..12000 K: $temperatureKelvin"
        }
        require(tint.isFinite() && tint in -1f..1f) { "tint outside -1..1: $tint" }
        require(exposure.isFinite() && exposure in -3f..3f) { "exposure outside -3..3: $exposure" }
        require(brightness.isFinite() && brightness in -2f..2f) {
            "brightness outside -2..2: $brightness"
        }
        require(sharpen.isFinite() && sharpen in 0f..2f) { "sharpen outside 0..2: $sharpen" }
        listOf(contrast, shadows, highlights, saturation, vibrance).forEach {
            require(it.isFinite() && it in -1f..1f) { "develop tone outside -1..1: $it" }
        }
    }

    val isNeutral: Boolean get() = this == PhotoRawDevelop()

    /** This recipe with every re-tonable field reset: the decode's identity. */
    val baseOnly: PhotoRawDevelop
        get() = copy(
            contrast = 0f,
            shadows = 0f,
            highlights = 0f,
            sharpen = 0f,
            saturation = 0f,
            vibrance = 0f
        )

    val hasTone: Boolean
        get() = contrast != 0f || shadows != 0f || highlights != 0f || sharpen != 0f ||
            saturation != 0f || vibrance != 0f

    fun sharesBaseWith(other: PhotoRawDevelop): Boolean = baseOnly == other.baseOnly
}

/**
 * True when only the develop stage differs from an untouched recipe. A TIFF is
 * streamed straight from the RAW decoder at full bit depth, so it can only carry
 * a develop; any other edit rules it out.
 */
fun PhotoEdits.isDevelopOnly(): Boolean = copy(develop = PhotoRawDevelop()) == PhotoEdits()
