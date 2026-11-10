/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/** What a pack row on the Language packs screen shows and offers. */
@Immutable
sealed interface OcrPackStatus {
    /** Not on the device. [downloadBytes] is what a download costs; null when downloads are unavailable. */
    data class NotAdded(val downloadBytes: Long?) : OcrPackStatus
    data class Downloading(val doneBytes: Long, val totalBytes: Long) : OcrPackStatus
    /** In app storage. [includesFinder] when this row also carries the shared text finder's size. */
    data class Installed(val bytes: Long, val includesFinder: Boolean) : OcrPackStatus
    /** Read from a picked file; verified. */
    data object Linked : OcrPackStatus
    /** A picked file was moved, changed or its access removed. */
    data object NeedsReadding : OcrPackStatus
    data object Failed : OcrPackStatus

    val onDevice: Boolean get() = this is Installed || this == Linked || this == NeedsReadding
}
