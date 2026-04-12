/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build

/**
 * On-demand image region decoder backed by [BitmapRegionDecoder].
 *
 * This class avoids loading the full panoramic image into memory by decoding
 * only the requested portion at a given sample size. The typical lifecycle is:
 *
 * 1. Construct with a [ContentResolver] and image [Uri].
 * 2. Call [initialize] to read dimensions and create the decoder.
 * 3. Call [loadBase] to get a low-resolution thumbnail for the full geometry.
 * 4. Call [loadRegion] repeatedly to decode the visible viewport at high resolution.
 * 5. Call [close] when the viewer is disposed.
 *
 * If the image format does not support region decoding (e.g. some WebP variants),
 * [initialize] will return `false`. In that case, [loadBaseFallback] can be used
 * to load the entire image via standard [BitmapFactory] with down-sampling.
 *
 * **Threading**: All methods must be called from the same background thread
 * (the `PanoramaDetailLoader` handler thread managed by [PanoramaGLSurfaceView]).
 *
 * @param contentResolver Used to open input streams for the image.
 * @param uri             Content URI of the panoramic image.
 *
 * @see PanoramaGLSurfaceView
 * @see DetailRegion
 */
internal class RegionLoader(
    private val contentResolver: ContentResolver,
    private val uri: Uri
) : PanoramaImageLoader {

    private var decoder: BitmapRegionDecoder? = null

    /** Full image width in pixels. */
    override var imageWidth: Int = 0
        private set

    /** Full image height in pixels. */
    override var imageHeight: Int = 0
        private set

    private var initialized = false

    /**
     * Initializes the decoder. Must be called from a background thread.
     * @return true if initialization succeeded.
     */
    override fun initialize(): Boolean {
        if (initialized) return decoder != null
        initialized = true

        PanoramaLog.d("initialize() uri=$uri")

        // Read dimensions first
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            PanoramaLog.e("initialize() failed to read dimensions", e)
            return false
        }
        imageWidth = opts.outWidth
        imageHeight = opts.outHeight
        PanoramaLog.d("initialize() imageSize=${imageWidth}x${imageHeight}")
        if (imageWidth <= 0 || imageHeight <= 0) {
            PanoramaLog.e("initialize() invalid dimensions: ${imageWidth}x${imageHeight}")
            return false
        }

        // Create region decoder
        try {
            decoder = contentResolver.openInputStream(uri)?.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(stream)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(stream, false)
                }
            }
        } catch (e: Exception) {
            PanoramaLog.e("initialize() BitmapRegionDecoder.newInstance failed", e)
            return false
        }
        PanoramaLog.d("initialize() decoder created: ${decoder != null}")
        return decoder != null
    }

    /**
     * Loads a low-resolution version of the entire image for use as a base texture.
     *
     * @param maxDimension Maximum width or height of the returned bitmap.
     */
    override fun loadBase(maxDimension: Int): Bitmap? {
        val d = decoder ?: run {
            PanoramaLog.w("loadBase() decoder is null")
            return null
        }
        var sampleSize = 1
        while (imageWidth / sampleSize > maxDimension || imageHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val bmp = d.decodeRegion(Rect(0, 0, imageWidth, imageHeight), opts)
            PanoramaLog.d("loadBase() decoded ${bmp?.width}x${bmp?.height} (sampleSize=$sampleSize)")
            bmp
        } catch (e: Exception) {
            PanoramaLog.e("loadBase() decodeRegion failed", e)
            null
        }
    }

    /**
     * Loads the entire image at the given [maxDimension] via regular BitmapFactory decode.
     * Used as a fallback when [BitmapRegionDecoder] fails to produce tiles.
     */
    fun loadBaseFallback(maxDimension: Int = BASE_TEXTURE_SIZE): Bitmap? {
        PanoramaLog.d("loadBaseFallback() uri=$uri maxDim=$maxDimension")
        return try {
            var sampleSize = 1
            while (imageWidth / sampleSize > maxDimension || imageHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                PanoramaLog.d("loadBaseFallback() decoded ${bmp?.width}x${bmp?.height}")
                bmp
            }
        } catch (e: Exception) {
            PanoramaLog.e("loadBaseFallback() failed", e)
            null
        }
    }

    /**
     * Loads a specific region of the image at high resolution.
     *
     * @param left Left pixel coordinate (0-based).
     * @param top Top pixel coordinate (0-based).
     * @param right Right pixel coordinate.
     * @param bottom Bottom pixel coordinate.
     * @param maxDimension Maximum dimension for the decoded region.
     *        If the region is larger, it will be downsampled to fit.
     */
    override fun loadRegion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        maxDimension: Int
    ): Bitmap? {
        val d = decoder ?: run {
            PanoramaLog.w("loadRegion() decoder is null")
            return null
        }
        val clampedLeft = left.coerceIn(0, imageWidth)
        val clampedTop = top.coerceIn(0, imageHeight)
        val clampedRight = right.coerceIn(0, imageWidth)
        val clampedBottom = bottom.coerceIn(0, imageHeight)

        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
            PanoramaLog.w("loadRegion() empty region: ($clampedLeft,$clampedTop)-($clampedRight,$clampedBottom)")
            return null
        }

        val regionW = clampedRight - clampedLeft
        val regionH = clampedBottom - clampedTop
        var sampleSize = 1
        while (regionW / sampleSize > maxDimension || regionH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            val bmp = d.decodeRegion(
                Rect(clampedLeft, clampedTop, clampedRight, clampedBottom),
                opts
            )
            PanoramaLog.d("loadRegion() ($clampedLeft,$clampedTop)-($clampedRight,$clampedBottom) => ${bmp?.width}x${bmp?.height} (sampleSize=$sampleSize)")
            bmp
        } catch (e: Exception) {
            PanoramaLog.e("loadRegion() decodeRegion failed", e)
            null
        }
    }

    override fun close() {
        PanoramaLog.d("close()")
        decoder?.recycle()
        decoder = null
    }

    companion object {
        /** Base texture max dimension — low-res placeholder for the full sphere. */
        const val BASE_TEXTURE_SIZE = 1024

        /** Detail texture max dimension — high-res region for the visible viewport. */
        const val DETAIL_TEXTURE_SIZE = 4096
    }
}
