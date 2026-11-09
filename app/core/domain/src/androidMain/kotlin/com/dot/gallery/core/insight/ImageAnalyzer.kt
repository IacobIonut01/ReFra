/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

/**
 * One analysis step over a photo's luminance frame: codes today, text (OCR) later. Analyzers run
 * one after another on the insight thread and share a single [LuminanceFrame], so an analyzer
 * must not keep the frame or its buffer after [analyze] returns.
 */
interface ImageAnalyzer {
    val kind: InsightKind

    /**
     * Reads [frame]. [thorough] asks for the slower settings (rotation, inverted codes, harder
     * search) used on a second pass or when the person asked for a scan.
     */
    suspend fun analyze(frame: LuminanceFrame, thorough: Boolean): List<Insight>
}
