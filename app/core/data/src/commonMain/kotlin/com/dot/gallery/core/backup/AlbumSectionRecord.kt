/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class AlbumSectionRecord(
    val sourceId: Long,
    val label: String,
    val type: Int,
    val displayOrder: Int,
    val isVisible: Boolean,
    val isExpanded: Boolean,
    val sortKind: String,
    val sortOrder: String,
    val albums: List<AlbumRef> = emptyList()
)
