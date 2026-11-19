/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class MediaMetadataVideoRecord(
    val durationMs: Long? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val frameRate: Float? = null,
    val bitRate: Int? = null
)
