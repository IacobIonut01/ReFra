/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Stable

@Stable
class SmartScanDetailActions(
    val onMetadata: () -> Unit = {},
    val onEmbeddings: () -> Unit = {},
    val onCategories: () -> Unit = {},
    val onPersons: () -> Unit = {},
    val onText: () -> Unit = {},
    val onAll: () -> Unit = {},
    val onFullRefresh: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
