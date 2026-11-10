/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/** What the packs screen shows for one pack. */
sealed interface OcrPackState {
    data object NotInstalled : OcrPackState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : OcrPackState
    /** Every model is in app storage and verified. */
    data object Installed : OcrPackState
    /** At least one model is read from a picked file (offline builds, sideloading); all verified. */
    data object Linked : OcrPackState
    /** A linked file was moved, changed or its read access was removed. */
    data object NeedsReadding : OcrPackState
    data class Failed(val reason: String) : OcrPackState

    val isReady: Boolean get() = this == Installed || this == Linked
}
