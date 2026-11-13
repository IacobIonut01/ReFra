/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Stable

/**
 * State for [SmartFeaturesScreenContent]. The wrapper resolves entity-driven
 * summaries ([scanSummary], [handoverSummary]) so the content composable never
 * touches `SmartScanPlan`, Room entities, or `ModelManager`.
 */
@Stable
data class SmartFeaturesScreenState(
    val aiAvailable: Boolean = false,
    val includeIgnoredAlbums: Boolean = false,
    val scanRunning: Boolean = false,
    val scanSummary: String = "",
    val hasCloudProviders: Boolean = false,
    val handoverEnabled: Boolean = false,
    val handoverSummary: String = "",
)
