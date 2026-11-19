/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class IgnoredAlbumRecord(
    val sourceId: Long = 0L,
    val label: String? = null,
    val wildcard: String? = null,
    val albums: List<AlbumRef> = emptyList(),
    val location: Int = 0,
    val matchedAlbums: List<String> = emptyList()
)
