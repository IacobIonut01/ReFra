/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CategoryRecord(
    val sourceId: Long,
    val name: String,
    val searchTerms: String,
    /** Base64-encoded little-endian FloatArray bytes, or null when no embedding was cached. */
    val embedding: String? = null,
    val references: List<MediaRef> = emptyList(),
    val threshold: Float,
    val isUserCreated: Boolean,
    val isPinned: Boolean,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val memberships: List<CategoryMembershipRecord> = emptyList()
)
