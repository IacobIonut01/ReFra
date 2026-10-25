/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Stable

@Stable
class SmartFeaturesScreenActions(
    val onIncludeIgnoredAlbumsChange: (Boolean) -> Unit = {},
    val onOpenAiModels: () -> Unit = {},
    val onOpenCategories: () -> Unit = {},
    val onOpenCloudIndexing: () -> Unit = {},
    val onOpenScanManager: () -> Unit = {},
    val onOpenEditBackups: () -> Unit = {},
    val onOpenHandover: () -> Unit = {},
)
