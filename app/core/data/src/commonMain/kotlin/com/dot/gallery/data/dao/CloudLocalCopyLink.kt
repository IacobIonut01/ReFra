/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

/**
 * Room POJO linking a local MediaStore copy to the cloud row that holds the
 * same bytes — used to carry local-only derived data (search embeddings) onto
 * the cloud copy before orphan cleanup removes the local row.
 */
data class CloudLocalCopyLink(
    val localUri: String,
    val cloudMediaId: Long,
    val cloudTimestampMillis: Long
)
