package com.dot.gallery.editor

import android.graphics.Bitmap
import com.dot.gallery.core.decoder.NativeRawDecoder
import com.dot.gallery.core.decoder.RawDemosaic
import com.dot.gallery.core.decoder.RawDevelopParams
import com.dot.gallery.core.decoder.RawHighlightMode
import com.dot.gallery.core.decoder.RawNoiseReduction
import com.dot.gallery.core.decoder.RawOrientation
import com.dot.gallery.core.decoder.RawOutputColorSpace
import com.dot.gallery.core.decoder.RawWhiteBalance
import com.dot.gallery.editor.android.EditorRawDecoder
import com.dot.gallery.editor.core.model.PhotoRawColourSpace
import com.dot.gallery.editor.core.model.PhotoRawDemosaic
import com.dot.gallery.editor.core.model.PhotoRawDevelop
import com.dot.gallery.editor.core.model.PhotoRawHighlights
import com.dot.gallery.editor.core.model.PhotoRawNoiseReduction
import com.dot.gallery.editor.core.model.PhotoRawWhiteBalance
import com.dot.gallery.editor.core.model.PixelSize

/** The old editor's LibRaw decoder behind the new editor's host seam. */
internal object GalleryRawDecoder : EditorRawDecoder {

    override fun size(data: ByteArray): PixelSize? =
        NativeRawDecoder.getSize(data)?.let { PixelSize(it.width, it.height) }

    override fun demosaic(data: ByteArray, develop: PhotoRawDevelop, halfSize: Boolean): Bitmap? =
        NativeRawDecoder.demosaic(
            data,
            develop.toParams().copy(halfSize = halfSize),
            RawOrientation.libRawUserFlip(data)
        )

    override fun applyTone(bitmap: Bitmap, develop: PhotoRawDevelop): Bitmap? =
        NativeRawDecoder.applyTone(bitmap, develop.toParams())

    override fun exportTiff(data: ByteArray, develop: PhotoRawDevelop, fd: Int, bits: Int): Boolean =
        NativeRawDecoder.exportTiff(
            data = data,
            params = develop.toParams(),
            fd = fd,
            bits = bits,
            userFlip = RawOrientation.libRawUserFlip(data)
        )
}

internal fun PhotoRawDevelop.toParams() = RawDevelopParams(
    whiteBalance = when (whiteBalance) {
        PhotoRawWhiteBalance.Camera -> RawWhiteBalance.CAMERA
        PhotoRawWhiteBalance.Auto -> RawWhiteBalance.AUTO
        PhotoRawWhiteBalance.Daylight -> RawWhiteBalance.DAYLIGHT
        PhotoRawWhiteBalance.Custom -> RawWhiteBalance.CUSTOM
    },
    wbTempKelvin = temperatureKelvin.toInt().takeIf {
        whiteBalance == PhotoRawWhiteBalance.Custom
    },
    wbTint = tint,
    exposureEv = exposure,
    brightness = brightness,
    contrast = contrast,
    saturation = saturation,
    vibrance = vibrance,
    shadows = shadows,
    highlightsTone = highlights,
    sharpen = sharpen,
    noiseReduction = when (noiseReduction) {
        PhotoRawNoiseReduction.Off -> RawNoiseReduction.OFF
        PhotoRawNoiseReduction.Low -> RawNoiseReduction.LOW
        PhotoRawNoiseReduction.Medium -> RawNoiseReduction.MEDIUM
        PhotoRawNoiseReduction.High -> RawNoiseReduction.HIGH
    },
    highlight = when (highlightRecovery) {
        PhotoRawHighlights.Clip -> RawHighlightMode.CLIP
        PhotoRawHighlights.Blend -> RawHighlightMode.BLEND
        PhotoRawHighlights.Rebuild -> RawHighlightMode.REBUILD
    },
    outputColorSpace = when (colourSpace) {
        PhotoRawColourSpace.Srgb -> RawOutputColorSpace.SRGB
        PhotoRawColourSpace.AdobeRgb -> RawOutputColorSpace.ADOBE_RGB
        PhotoRawColourSpace.Wide -> RawOutputColorSpace.WIDE_GAMUT
        PhotoRawColourSpace.ProPhoto -> RawOutputColorSpace.PROPHOTO
    },
    demosaic = when (demosaic) {
        PhotoRawDemosaic.Linear -> RawDemosaic.LINEAR
        PhotoRawDemosaic.Vng -> RawDemosaic.VNG
        PhotoRawDemosaic.Ppg -> RawDemosaic.PPG
        PhotoRawDemosaic.Ahd -> RawDemosaic.AHD
        PhotoRawDemosaic.Dcb -> RawDemosaic.DCB
    },
    halfSize = halfSize
)
