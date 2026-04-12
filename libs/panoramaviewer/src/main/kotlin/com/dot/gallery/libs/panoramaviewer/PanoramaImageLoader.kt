/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.graphics.Bitmap
import java.io.Closeable

/**
 * Abstraction for loading panoramic image data into the [PanoramaViewer].
 *
 * Implement this interface to provide custom image loading logic — for example,
 * to load encrypted vault media, images from a network source, or images stored
 * in a custom format.
 *
 * ## Lifecycle
 *
 * 1. The library calls [initialize] once on a background thread.
 * 2. [loadBase] is called to obtain a low-resolution texture for the full geometry.
 * 3. [loadRegion] is called repeatedly as the user pans and zooms, each time
 *    requesting the visible viewport at high resolution.
 * 4. [close] is called when the viewer is disposed.
 *
 * ## Threading
 *
 * All methods are called on a dedicated background handler thread
 * (`PanoramaDetailLoader`). Implementations do **not** need to be thread-safe
 * but must not block the main thread.
 *
 * ## Example
 *
 * ```kotlin
 * class MyCustomLoader(private val data: ByteArray) : PanoramaImageLoader {
 *     private var decoder: BitmapRegionDecoder? = null
 *
 *     override var imageWidth: Int = 0; private set
 *     override var imageHeight: Int = 0; private set
 *
 *     override fun initialize(): Boolean {
 *         val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
 *         BitmapFactory.decodeByteArray(data, 0, data.size, opts)
 *         imageWidth = opts.outWidth
 *         imageHeight = opts.outHeight
 *         decoder = BitmapRegionDecoder.newInstance(data, 0, data.size)
 *         return decoder != null
 *     }
 *
 *     override fun loadBase(maxDimension: Int): Bitmap? { /* ... */ }
 *     override fun loadRegion(left: Int, top: Int, right: Int, bottom: Int, maxDimension: Int): Bitmap? { /* ... */ }
 *     override fun close() { decoder?.recycle() }
 * }
 * ```
 *
 * @see PanoramaViewer
 */
interface PanoramaImageLoader : Closeable {

    /** Full image width in pixels. Available after [initialize] returns `true`. */
    val imageWidth: Int

    /** Full image height in pixels. Available after [initialize] returns `true`. */
    val imageHeight: Int

    /**
     * Reads image dimensions and prepares the internal decoder.
     *
     * Called once on a background thread before any [loadBase] or [loadRegion] calls.
     *
     * @return `true` if the loader is ready to decode regions, `false` if
     *         region decoding is not supported for this image. When `false`,
     *         the library will call [loadBase] as a full-image fallback.
     */
    fun initialize(): Boolean

    /**
     * Loads a low-resolution version of the entire image.
     *
     * This is used as the base texture mapped onto the panorama geometry.
     * It should be fast and memory-efficient.
     *
     * @param maxDimension Maximum width or height of the returned bitmap.
     *                     The implementation should downsample to fit.
     * @return A downsampled bitmap, or `null` if loading failed.
     */
    fun loadBase(maxDimension: Int): Bitmap?

    /**
     * Loads a specific rectangular region of the image at high resolution.
     *
     * This is called repeatedly as the user pans or zooms to decode only the
     * visible viewport area. Coordinates are in full-image pixel space.
     *
     * @param left          Left pixel coordinate (0-based, inclusive).
     * @param top           Top pixel coordinate (0-based, inclusive).
     * @param right         Right pixel coordinate (exclusive).
     * @param bottom        Bottom pixel coordinate (exclusive).
     * @param maxDimension  Maximum dimension for the decoded region.
     *                      If the region is larger, downsample to fit.
     * @return The decoded region bitmap, or `null` if loading failed.
     */
    fun loadRegion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        maxDimension: Int
    ): Bitmap?
}
