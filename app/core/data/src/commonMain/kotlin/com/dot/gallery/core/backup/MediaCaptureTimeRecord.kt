/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class MediaCaptureTimeRecord(
    val captureTimestampMillis: Long,
    val origin: String,
    val sourceModifiedSeconds: Long,
    val sourceTakenTimestampMillis: Long? = null,
    val sourceSize: Long,
    val sourcePath: String,
    val updatedAtMillis: Long
)
