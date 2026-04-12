/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.graphics.Bitmap

/**
 * Describes a high-resolution detail region decoded from the source image.
 *
 * The detail region is overlaid on the low-resolution base texture within the area
 * defined by the UV bounds. This allows the visible portion of the panorama to be
 * rendered at full resolution without loading the entire image into memory.
 *
 * UV coordinates are normalised to the range 0–1, where (0, 0) is the top-left
 * corner of the full source image and (1, 1) is the bottom-right.
 *
 * @property bitmap The decoded high-resolution region bitmap.
 * @property uMin Left UV bound of the region in the source image.
 * @property vMin Top UV bound of the region in the source image.
 * @property uMax Right UV bound of the region in the source image.
 * @property vMax Bottom UV bound of the region in the source image.
 *
 * @see RegionLoader.loadRegion
 */
internal data class DetailRegion(
    val bitmap: Bitmap,
    val uMin: Float,
    val vMin: Float,
    val uMax: Float,
    val vMax: Float
)
