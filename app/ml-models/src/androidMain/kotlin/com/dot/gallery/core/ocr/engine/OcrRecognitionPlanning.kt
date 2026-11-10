/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.engine

import kotlin.math.roundToInt

/**
 * The detector's input size, as the PP-OCRv5 pipeline sets it (`limit_side_len: 64`,
 * `limit_type: min`, `max_side_limit: 4000`): the image keeps its own resolution unless its short side
 * is under 64 (scaled up) or its long side is over 4000 (scaled down), then each side is rounded to a
 * multiple of 32. The model's own 960 px default shrank a 2048 px photo by half and lost small text.
 */
object OcrDetectorInput {
    const val MIN_SIDE = 64
    const val MAX_SIDE = 4_000

    fun size(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || maxSide < MIN_SIDE) return null
        var scale = if (minOf(width, height) < MIN_SIDE) MIN_SIDE.toDouble() / minOf(width, height) else 1.0
        var scaledWidth = width * scale
        var scaledHeight = height * scale
        if (maxOf(scaledWidth, scaledHeight) > maxSide) {
            scale = maxSide / maxOf(scaledWidth, scaledHeight)
            scaledWidth *= scale
            scaledHeight *= scale
        }
        return roundTo32(scaledWidth.toInt()) to roundTo32(scaledHeight.toInt())
    }

    /** Long side of the first, cheap detection pass (the detector model's own default). */
    const val COARSE_SIDE = 960

    /** A box whose short side is at most this many detector pixels holds text too small to trust. */
    const val SMALL_TEXT_PIXELS = 12f

    /**
     * Whether a photo needs the full-resolution detection pass after the coarse one: some box found
     * at the coarse scale is so thin that its text, and lines like it, are likely missed or misread.
     * On a Pixel 10 Pro, 13 px text in a 2048 px photo was read on 13 of 24 lines at 960 px and on
     * all 24 at full size, while full size cost about 4 times the memory. Photos with no box at
     * the coarse scale are not read again.
     *
     * [boxShortSides] are in pixels of the image the boxes were found in; [coarseScale] is the
     * coarse detector input size divided by that image's size.
     */
    fun needsFinePass(boxShortSides: List<Float>, coarseScale: Float): Boolean =
        coarseScale < 1f && boxShortSides.any { it * coarseScale <= SMALL_TEXT_PIXELS }

    private fun roundTo32(value: Int): Int = maxOf(32, (value / 32.0).roundToInt() * 32)
}

/**
 * Groups recognizer crops into batches like PaddleOCR: sorted by width so a batch pads little, at most
 * [MAX_BATCH] crops (the pipeline's `batch_size: 6`), and no wider in total than [COLUMN_BUDGET]
 * input columns, which bounds the output (time steps × dictionary size) for large dictionaries.
 */
object OcrRecognitionBatches {
    const val MAX_BATCH = 6
    const val COLUMN_BUDGET = MAX_BATCH * 320

    /** Batches of indices into [textWidths]; each batch's input width is its widest crop (at least [minimumWidth]). */
    fun plan(textWidths: List<Int>, minimumWidth: Int): List<List<Int>> {
        require(minimumWidth > 0)
        val order = textWidths.indices.sortedWith(compareBy({ textWidths[it] }, { it }))
        val batches = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        for (index in order) {
            val width = maxOf(minimumWidth, textWidths[index])
            val fits = current.size < MAX_BATCH && (current.size + 1) * width <= COLUMN_BUDGET
            if (current.isNotEmpty() && !fits) {
                batches += current
                current = ArrayList()
            }
            current += index
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }
}

/**
 * Chooses which packs read a line. The pack that has read most lines of the photo so far goes first
 * (a photo is mostly in one script; ties keep the caller's order); when it reads a line below
 * [ACCEPT_CONFIDENCE], up to [MAX_EXTRA_PACKS] more packs try and the most confident wins. Lines read
 * below [FLIP_BELOW] are tried again turned 180°, which upside-down text needs, and the turned
 * reading wins only by [FLIP_MARGIN].
 */
class OcrPackRouter(private val packIds: List<String>) {
    private val wins = HashMap<String, Int>()

    fun order(): List<String> = packIds.withIndex()
        .sortedWith(compareBy({ -(wins[it.value] ?: 0) }, { it.index }))
        .map { it.value }
        .take(1 + MAX_EXTRA_PACKS)

    fun record(packId: String) {
        wins[packId] = (wins[packId] ?: 0) + 1
    }

    companion object {
        const val ACCEPT_CONFIDENCE = 0.80f
        const val MAX_EXTRA_PACKS = 2
        const val FLIP_BELOW = 0.70f
        const val FLIP_MARGIN = 0.05f

        /** Whether a line read with [confidence] (null: nothing read) should be tried by the next pack. */
        fun needsAnotherPack(confidence: Float?): Boolean = confidence == null || confidence < ACCEPT_CONFIDENCE

        fun triesFlip(confidence: Float): Boolean = confidence < FLIP_BELOW

        fun prefersFlipped(upright: Float, flipped: Float): Boolean = flipped >= upright + FLIP_MARGIN
    }
}
