/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class EditBackupRecord(
    val media: MediaRef,
    val originalUri: String,
    val originalMimeType: String,
    val editTimestamp: Long,
    val fileName: String,
    val archiveSize: Long = -1L,
    val sha256: String = ""
)
