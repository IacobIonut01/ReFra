/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

/** Callbacks of the Language packs screen; pack callbacks take the pack id. */
data class OcrPacksActions(
    val onDownload: (String) -> Unit = {},
    val onCancelDownload: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onAddFiles: () -> Unit = {},
    val onOpenPackList: () -> Unit = {},
    val onDismissVerification: () -> Unit = {},
)
