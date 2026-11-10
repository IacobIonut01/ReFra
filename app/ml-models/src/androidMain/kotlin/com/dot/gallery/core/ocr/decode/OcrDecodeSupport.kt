/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.dot.gallery.core.ocr.domain.OcrLimits
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun validateMaxLongEdge(maxLongEdge: Int) {
    require(maxLongEdge in 1..OcrLimits.OVERVIEW_MAX_DIMENSION)
}

internal fun validateRegion(source: OcrSourceRect, width: Int, height: Int) {
    require(source.right <= width && source.bottom <= height)
}

internal fun fitWithin(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
    if (width <= maxLongEdge && height <= maxLongEdge) return width to height
    val scale = maxLongEdge.toDouble() / maxOf(width, height)
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}

internal fun Bitmap.ownedArgbScaled(maxLongEdge: Int): Bitmap? {
    val target = fitWithin(width, height, maxLongEdge)
    val copy = copy(Bitmap.Config.ARGB_8888, false) ?: return null
    return copy.resizeOwned(target.first, target.second)
}

internal fun Bitmap.resizeOwned(width: Int, height: Int): Bitmap {
    if (this.width == width && this.height == height && config == Bitmap.Config.ARGB_8888) return this
    var scaled: Bitmap? = null
    try {
        val resized = Bitmap.createScaledBitmap(this, width, height, true)
        scaled = resized
        if (resized !== this) recycle()
        if (resized.config == Bitmap.Config.ARGB_8888) return resized
        val converted = resized.copy(Bitmap.Config.ARGB_8888, false)
        if (converted != null) {
            resized.recycle()
            return converted
        }
        return resized
    } catch (error: Throwable) {
        scaled?.takeIf { it !== this && !it.isRecycled }?.recycle()
        takeUnless(Bitmap::isRecycled)?.recycle()
        throw error
    }
}

internal fun powerOfTwoSample(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    val ratio = maxOf(width.toDouble() / targetWidth, height.toDouble() / targetHeight)
    if (ratio <= 1.0) return 1
    val required = ceil(ratio).toInt()
    var sample = 1
    while (sample < required && sample <= Int.MAX_VALUE / 2) sample = sample shl 1
    return sample
}

internal fun applyExifToTile(bitmap: Bitmap, orientation: Int): Bitmap {
    val values = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_90 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_TRANSVERSE -> floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        else -> return bitmap
    }
    val matrix = Matrix().apply { setValues(values) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

internal fun orientedToRaw(
    source: OcrSourceRect,
    rawWidth: Int,
    rawHeight: Int,
    orientation: Int,
): Rect = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
        Rect(rawWidth - source.right, source.top, rawWidth - source.left, source.bottom)
    ExifInterface.ORIENTATION_ROTATE_180 ->
        Rect(rawWidth - source.right, rawHeight - source.bottom, rawWidth - source.left, rawHeight - source.top)
    ExifInterface.ORIENTATION_FLIP_VERTICAL ->
        Rect(source.left, rawHeight - source.bottom, source.right, rawHeight - source.top)
    ExifInterface.ORIENTATION_TRANSPOSE ->
        Rect(source.top, source.left, source.bottom, source.right)
    ExifInterface.ORIENTATION_ROTATE_90 ->
        Rect(source.top, rawHeight - source.right, source.bottom, rawHeight - source.left)
    ExifInterface.ORIENTATION_TRANSVERSE ->
        Rect(rawWidth - source.bottom, rawHeight - source.right, rawWidth - source.top, rawHeight - source.left)
    ExifInterface.ORIENTATION_ROTATE_270 ->
        Rect(rawWidth - source.bottom, source.left, rawWidth - source.top, source.right)
    else -> Rect(source.left, source.top, source.right, source.bottom)
}
