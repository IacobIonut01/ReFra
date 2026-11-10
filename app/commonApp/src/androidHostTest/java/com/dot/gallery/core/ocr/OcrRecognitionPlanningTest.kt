/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.engine.OcrDetectorInput
import com.dot.gallery.core.ocr.engine.OcrPackRouter
import com.dot.gallery.core.ocr.engine.OcrRecognitionBatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRecognitionPlanningTest {
    @Test
    fun detectorReadsTheOverviewAtItsOwnResolution() {
        // The model's 960 px default halved a 2048 px overview; the PP-OCRv5 pipeline keeps it.
        assertEquals(2048 to 1536, OcrDetectorInput.size(2048, 1536))
        assertEquals(1024 to 768, OcrDetectorInput.size(1030, 770))
    }

    @Test
    fun detectorScalesUpTinyImagesAndCapsHugeOnes() {
        // Short side 30 becomes 64: 100 × 30 → 213 × 64, rounded to multiples of 32.
        assertEquals(224 to 64, OcrDetectorInput.size(100, 30))
        assertEquals(4000 to 3008, OcrDetectorInput.size(8000, 6000))
    }

    @Test
    fun fullSizeDetectionRunsOnlyForThinBoxes() {
        // A 2048 px photo seen at 960 px (scale 0.47): a 40 px box is 18.8 px there, a 20 px box 9.4 px.
        val scale = 960f / 2048f
        assertFalse(OcrDetectorInput.needsFinePass(listOf(40f, 60f), scale))
        assertTrue(OcrDetectorInput.needsFinePass(listOf(40f, 20f), scale))
        // No text found at the coarse scale, or an image already at or below it: no second pass.
        assertFalse(OcrDetectorInput.needsFinePass(emptyList(), scale))
        assertFalse(OcrDetectorInput.needsFinePass(listOf(5f), 1f))
        // The cap the engine passes in still applies.
        // 1536 × 960 / 2048 = 720, rounded to the nearest multiple of 32.
        assertEquals(960 to 736, OcrDetectorInput.size(2048, 1536, maxSide = 960))
    }

    @Test
    fun recognizerBatchesAreSortedByWidthAndBounded() {
        val widths = listOf(100, 900, 50, 300, 320, 310, 200, 2000)
        assertEquals(
            listOf(listOf(2, 0, 6, 3, 5, 4), listOf(1), listOf(7)),
            OcrRecognitionBatches.plan(widths, minimumWidth = 320),
        )
    }

    @Test
    fun everyCropIsInExactlyOneBatch() {
        val widths = List(40) { (it * 37) % 700 + 20 }
        val batches = OcrRecognitionBatches.plan(widths, minimumWidth = 320)
        assertEquals(widths.indices.toList(), batches.flatten().sorted())
        assertTrue(batches.all { batch ->
            batch.size <= OcrRecognitionBatches.MAX_BATCH &&
                batch.size * maxOf(320, batch.maxOf(widths::get)) <= OcrRecognitionBatches.COLUMN_BUDGET
        })
    }

    @Test
    fun thePackThatReadsMostLinesGoesFirst() {
        val router = OcrPackRouter(listOf("latin", "cjk", "korean", "arabic"))
        // At most the first pack and two more.
        assertEquals(listOf("latin", "cjk", "korean"), router.order())
        router.record("cjk")
        router.record("cjk")
        router.record("latin")
        assertEquals(listOf("cjk", "latin", "korean"), router.order())
    }

    @Test
    fun onlyUnsureLinesReachAnotherPackOrATurn() {
        assertTrue(OcrPackRouter.needsAnotherPack(null))
        assertTrue(OcrPackRouter.needsAnotherPack(0.79f))
        assertFalse(OcrPackRouter.needsAnotherPack(0.80f))
        assertTrue(OcrPackRouter.triesFlip(0.5f))
        assertFalse(OcrPackRouter.triesFlip(0.9f))
        assertTrue(OcrPackRouter.prefersFlipped(upright = 0.5f, flipped = 0.9f))
        assertFalse(OcrPackRouter.prefersFlipped(upright = 0.5f, flipped = 0.52f))
    }
}
