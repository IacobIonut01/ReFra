/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.editor.bake

import android.graphics.Bitmap
import com.dot.gallery.feature.editor.model.Adjustment


/**
 * Replays a recorded [Adjustment] recipe onto a base bitmap.
 *
 * Interactive editing runs on a memory-safe downscaled proxy, but the *recipe*
 * ([EditViewModel.appliedAdjustments]) is resolution-independent, so the exact same list can be
 * re-applied to the full-resolution original at save time to produce a full-res result with no
 * downsampling. This is the non-tiled bake path; the tiled engine reuses the same recipe
 * but streams it in memory-bounded tiles.
 */
object EditReplay {

    /**
     * Applies [adjustments] in order to [base] and returns the result. Intermediate bitmaps are
     * recycled as the pipeline advances; [base] itself is never recycled (the caller owns it).
     * When [adjustments] is empty the returned bitmap *is* [base].
     */
    fun replay(base: Bitmap, adjustments: List<Adjustment>): Bitmap {
        var current = base
        for (adj in adjustments) {
            val next = adj.apply(current)
            if (next !== current) {
                if (current !== base && !current.isRecycled) current.recycle()
                current = next
            }
        }
        return current
    }

    /**
     * True when replaying [adjustments] on [proxyOriginal] reproduces [liveProxyResult] exactly.
     * Used as a fidelity guard before trusting the full-res bake: if the recorded recipe can't
     * reproduce what the user sees on the proxy, the recipe is incomplete and the save must not
     * silently write a different-looking file.
     */
    fun matchesLiveResult(
        proxyOriginal: Bitmap,
        adjustments: List<Adjustment>,
        liveProxyResult: Bitmap,
    ): Boolean {
        val replayed = replay(proxyOriginal, adjustments)
        return try {
            replayed.width == liveProxyResult.width &&
                replayed.height == liveProxyResult.height &&
                replayed.sameAs(liveProxyResult)
        } finally {
            if (replayed !== proxyOriginal && !replayed.isRecycled) replayed.recycle()
        }
    }
}
