/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import com.dot.gallery.core.insight.Insight

/** Analyses one photo. Runs on the controller's dispatcher and must honour cancellation. */
interface InsightEngine<T> {
    /**
     * Returns what [target] contains. The quick pass ([thorough] false) reads the preview that is
     * already on screen; the thorough pass may decode a larger copy and tries harder.
     */
    suspend fun analyze(target: T, thorough: Boolean): List<Insight>

    /** Frees pooled memory. Called on the controller's dispatcher. */
    fun release()
}
