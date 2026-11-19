/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CollectionRecord(
    val sourceId: Long,
    val label: String,
    val cover: MediaRef? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val media: List<CollectionMediaRecord> = emptyList(),
    val albums: List<CollectionAlbumRecord> = emptyList(),
    val excluded: List<MediaRef> = emptyList()
)
