/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class AlbumGroupRecord(
    val sourceId: Long,
    val label: String,
    val timestamp: Long = 0L,
    val albums: List<AlbumRef> = emptyList()
)
