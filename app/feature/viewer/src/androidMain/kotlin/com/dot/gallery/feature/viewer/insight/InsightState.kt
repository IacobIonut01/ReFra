/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.runtime.Immutable
import com.dot.gallery.core.insight.Insight
import com.dot.gallery.core.insight.InsightKey

/** What the viewer shows for the settled photo. Only meaningful while [key] matches that photo. */
@Immutable
data class InsightState(
    val key: InsightKey? = null,
    val insights: List<Insight> = emptyList(),
    /** True while a scan the person asked for is running (drives the Scan button). */
    val manualScanRunning: Boolean = false,
)
