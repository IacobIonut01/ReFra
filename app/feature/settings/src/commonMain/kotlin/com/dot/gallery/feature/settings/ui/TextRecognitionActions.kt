/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

/** Callbacks of the Text recognition screen. [onModeChange] takes an `OCR_MODE_*` value. */
data class TextRecognitionActions(
    val onEnabledChange: (Boolean) -> Unit = {},
    val onModeChange: (String) -> Unit = {},
    val onIndexLibraryChange: (Boolean) -> Unit = {},
    val onOpenPacks: () -> Unit = {},
    val onDeleteIndex: () -> Unit = {},
)
