/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup


/**
 * Result of probing a picked archive. [encrypted] is the only signal the UI may use to decide
 * whether to ask for a password; it is derived from the archive bytes, never from the file name
 * or from which sections are present.
 */
data class BackupProbe(
    val format: BackupFormat,
    val encrypted: Boolean,
    val preview: BackupPreviewHeader? = null
) {
    val needsPassword: Boolean get() = encrypted
}
