/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class FaceSuppressionRecord(
    val media: MediaRef,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val createdAt: Long = 0L
)
