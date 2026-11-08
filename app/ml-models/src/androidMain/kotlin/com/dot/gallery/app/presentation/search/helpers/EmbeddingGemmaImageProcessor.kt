/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search.helpers

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Port of the transformers.js `Gemma4ImageProcessor` used by EmbeddingGemma 2.
 *
 * The vision encoder consumes raw image *patches* instead of a fixed-size CHW tensor:
 * the image is resized (aspect-ratio preserving, snapped to multiples of
 * [POOLING_KERNEL]×[PATCH_SIZE]) to fit a soft-token budget, split into 16×16
 * patches flattened in (dy, dx, channel) order, rescaled by 1/255 (no mean/std), and
 * padded with zeros up to `maxPatches`. Positions are (col, row) patch-grid ids,
 * padded with -1. The number of soft tokens the backbone must receive as
 * placeholder tokens is `numPatches / POOLING_KERNEL²`.
 */
object EmbeddingGemmaImageProcessor {

    const val PATCH_SIZE = 16
    const val POOLING_KERNEL = 3
    const val PATCH_DIM = PATCH_SIZE * PATCH_SIZE * 3 // 768

    /**
     * Soft-token budget for still images. Upstream defaults to 280 (image_seq_length),
     * but the vision encoder cost is linear in patches and it dominates indexing time:
     * measured ~3.7s vs ~1.7s per image (4 CPU threads) at 280 vs 140 soft tokens.
     * 140 is the model's own video-per-frame budget — a supported operating point —
     * and measured retrieval rankings were identical at 280/140/64, so this halves
     * indexing time for free. Lower further (e.g. 96) if indexing is still too slow.
     */
    const val IMAGE_MAX_SOFT_TOKENS = 140

    /** Soft-token budget per video frame (video_processor.max_soft_tokens). */
    const val VIDEO_MAX_SOFT_TOKENS = 140

    private const val SIDE_MULTIPLE = PATCH_SIZE * POOLING_KERNEL // 48

    fun maxPatches(maxSoftTokens: Int): Int = maxSoftTokens * POOLING_KERNEL * POOLING_KERNEL

    /**
     * Aspect-ratio-preserving target size: fits `maxPatches * patch²` pixels, each side
     * snapped *down* to a multiple of [SIDE_MULTIPLE]. Returns (height, width).
     */
    fun targetSize(height: Int, width: Int, maxPatches: Int): IntArray {
        val targetPx = maxPatches.toDouble() * PATCH_SIZE * PATCH_SIZE
        val factor = sqrt(targetPx / (height.toDouble() * width))
        var targetH = (floor(factor * height) / SIDE_MULTIPLE).toInt() * SIDE_MULTIPLE
        var targetW = (floor(factor * width) / SIDE_MULTIPLE).toInt() * SIDE_MULTIPLE
        val maxSide = (maxPatches / (POOLING_KERNEL * POOLING_KERNEL)) * SIDE_MULTIPLE
        if (targetH == 0 && targetW == 0) {
            targetH = SIDE_MULTIPLE
            targetW = SIDE_MULTIPLE
        } else if (targetH == 0) {
            targetH = SIDE_MULTIPLE
            targetW = minOf(floor(width.toDouble() / height).toInt() * SIDE_MULTIPLE, maxSide)
        } else if (targetW == 0) {
            targetW = SIDE_MULTIPLE
            targetH = minOf(floor(height.toDouble() / width).toInt() * SIDE_MULTIPLE, maxSide)
        }
        return intArrayOf(targetH, targetW)
    }

    class Patchified(
        /** [maxPatches * PATCH_DIM] floats, zero-padded. */
        val pixelValues: FloatArray,
        /** [maxPatches * 2] (col,row) pairs, -1 padded. */
        val positionIds: LongArray,
        val numPatches: Int,
        /** Soft tokens the backbone needs as placeholders: numPatches / POOLING_KERNEL². */
        val numSoftTokens: Int,
    )

    /**
     * Patchify ARGB pixels ([width]×[height], one int per pixel, row-major) into
     * [Patchified]. Pixel values are rescaled by 1/255 without normalization.
     */
    fun patchifyPixels(pixels: IntArray, width: Int, height: Int, maxPatches: Int): Patchified {
        require(pixels.size >= width * height) { "pixel buffer smaller than w*h" }
        val patchesH = height / PATCH_SIZE
        val patchesW = width / PATCH_SIZE
        val numPatches = patchesH * patchesW
        require(numPatches <= maxPatches) { "image exceeds the patch budget" }
        val data = FloatArray(maxPatches * PATCH_DIM)
        val positions = LongArray(maxPatches * 2) { -1L }
        var out = 0
        var posIdx = 0
        for (ph in 0 until patchesH) {
            for (pw in 0 until patchesW) {
                for (dy in 0 until PATCH_SIZE) {
                    val row = (ph * PATCH_SIZE + dy) * width + pw * PATCH_SIZE
                    for (dx in 0 until PATCH_SIZE) {
                        val argb = pixels[row + dx]
                        data[out++] = ((argb shr 16) and 0xFF) / 255f
                        data[out++] = ((argb shr 8) and 0xFF) / 255f
                        data[out++] = (argb and 0xFF) / 255f
                    }
                }
                positions[posIdx++] = pw.toLong()
                positions[posIdx++] = ph.toLong()
            }
        }
        return Patchified(
            pixelValues = data,
            positionIds = positions,
            numPatches = numPatches,
            numSoftTokens = numPatches / (POOLING_KERNEL * POOLING_KERNEL),
        )
    }

    /**
     * Resize [bitmap] to the processor's snapped target size for [maxSoftTokens]
     * and patchify it. The returned bitmap is recycled if a scaled copy was made —
     * callers keep ownership of the input.
     */
    fun process(bitmap: Bitmap, maxSoftTokens: Int = IMAGE_MAX_SOFT_TOKENS): Patchified {
        val maxPatches = maxPatches(maxSoftTokens)
        val (targetH, targetW) = targetSize(bitmap.height, bitmap.width, maxPatches)
        val scaled = if (targetH != bitmap.height || targetW != bitmap.width) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else bitmap
        try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            return patchifyPixels(pixels, scaled.width, scaled.height, maxPatches)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
