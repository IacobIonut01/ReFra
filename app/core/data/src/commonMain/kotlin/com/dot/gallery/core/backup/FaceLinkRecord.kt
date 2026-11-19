/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class FaceLinkRecord(
    val media: MediaRef,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val personId: String,
    /** `INCLUDE` or `EXCLUDE`, mirroring `FaceLinkKind`. */
    val kind: String,
    val createdAt: Long = 0L
)
