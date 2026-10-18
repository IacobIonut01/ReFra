/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.search

import com.dot.gallery.app.presentation.search.helpers.EmbeddingGemmaImageProcessor
import com.dot.gallery.app.presentation.search.helpers.EmbeddingGemmaImageProcessor.PATCH_DIM
import com.dot.gallery.app.presentation.search.helpers.EmbeddingGemmaImageProcessor.PATCH_SIZE
import com.dot.gallery.app.presentation.search.helpers.EmbeddingGemmaImageProcessor.POOLING_KERNEL
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-Kotlin checks for the EmbeddingGemma 2 vision preprocessor: snapped resize
 * targets, (dy, dx, c) patch layout, (col, row) position ids, and zero/-1 padding.
 */
class EmbeddingGemmaImageProcessorTest {

    private val maxPatches = EmbeddingGemmaImageProcessor.maxPatches(
        EmbeddingGemmaImageProcessor.IMAGE_MAX_SOFT_TOKENS
    )

    /** Largest 48-multiple square side fitting the whole patch budget. */
    private val budgetSide =
        (sqrt(maxPatches.toDouble() * PATCH_SIZE * PATCH_SIZE).toInt() / 48) * 48

    @Test
    fun `target size snaps to side multiple and stays inside the patch budget`() {
        // Square image -> largest 48-multiple inside the patch budget.
        val (h, w) = EmbeddingGemmaImageProcessor.targetSize(2000, 2000, maxPatches)
        assertEquals(0, h % 48)
        assertEquals(0, w % 48)
        assertTrue(h.toLong() * w <= maxPatches.toLong() * PATCH_SIZE * PATCH_SIZE)
        assertTrue(h >= budgetSide) // should be close to budget, not tiny

        // Wide image: 2:1 aspect — floor keeps it inside the budget.
        val (h2, w2) = EmbeddingGemmaImageProcessor.targetSize(1000, 2000, maxPatches)
        assertEquals(0, h2 % 48)
        assertEquals(0, w2 % 48)
        assertTrue(h2.toLong() * w2 <= maxPatches.toLong() * PATCH_SIZE * PATCH_SIZE)
        assertTrue(w2 > h2)

        // Pathological tall image hits the floor-degenerate branch: width rounds to
        // zero, gets clamped to one side-multiple and height is capped by max side.
        val (h3, w3) = EmbeddingGemmaImageProcessor.targetSize(20000, 100, maxPatches)
        assertEquals(0, h3 % 48)
        assertEquals(0, w3 % 48)
        assertTrue(w3 >= 48)
        assertTrue(h3.toLong() * w3 <= maxPatches.toLong() * PATCH_SIZE * PATCH_SIZE)

        // Tiny images upscale to fill the patch budget (the reference does the same —
        // the factor is allowed above 1): 10x10 snaps up to the budget side.
        val (h4, w4) = EmbeddingGemmaImageProcessor.targetSize(10, 10, maxPatches)
        assertEquals(budgetSide, h4)
        assertEquals(budgetSide, w4)
    }

    @Test
    fun `patchify writes hwc order positions and soft-token count`() {
        // 32x16 image = 2 patches wide, 1 tall. First pixel of patch (0,0) is pure
        // red (255,0,0); the rest black. The patch row order is (dy,dx,c) so the
        // first three floats of patch 0 must be 1.0,0,0.
        val width = 32
        val height = 16
        val pixels = IntArray(width * height)
        pixels[0] = (0xFF shl 24) or (0xFF shl 16)
        val patchified = EmbeddingGemmaImageProcessor.patchifyPixels(pixels, width, height, maxPatches)
        assertEquals(2, patchified.numPatches)
        assertEquals(0, patchified.numSoftTokens) // 2 patches / 9 -> 0
        assertEquals(1.0f, patchified.pixelValues[0], 1e-6f)
        assertEquals(0.0f, patchified.pixelValues[1], 1e-6f)
        assertEquals(0.0f, patchified.pixelValues[2], 1e-6f)
        // Second patch's first pixel is at (0,16) — black.
        assertEquals(0.0f, patchified.pixelValues[PATCH_DIM], 1e-6f)
        // Positions are (col,row): patch0=(0,0), patch1=(1,0), then -1 padding.
        assertArrayEquals(longArrayOf(0, 0, 1, 0, -1, -1), patchified.positionIds.copyOf(6))
        // Padding rows of pixel_values stay zero.
        assertEquals(0.0f, patchified.pixelValues[PATCH_DIM * 2], 1e-6f)
    }

    @Test
    fun `patchify over budget is rejected`() {
        // 48x48 -> 9 patches; allow only 4.
        val pixels = IntArray(48 * 48)
        var threw = false
        try {
            EmbeddingGemmaImageProcessor.patchifyPixels(pixels, 48, 48, 4)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("expected IllegalArgumentException", threw)
    }

    @Test
    fun `soft tokens equal patches divided by pooling square`() {
        // 48x48 -> 9 patches -> exactly 1 soft token.
        val pixels = IntArray(48 * 48)
        val patchified = EmbeddingGemmaImageProcessor.patchifyPixels(pixels, 48, 48, maxPatches)
        assertEquals(9, patchified.numPatches)
        assertEquals(1, patchified.numSoftTokens)
        assertEquals(POOLING_KERNEL * POOLING_KERNEL, 9)
    }
}
