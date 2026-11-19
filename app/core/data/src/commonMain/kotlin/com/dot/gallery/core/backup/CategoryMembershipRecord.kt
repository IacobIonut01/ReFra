/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CategoryMembershipRecord(
    val media: MediaRef,
    val similarityScore: Float = 0f,
    val addedAt: Long = 0L,
    val isManuallyAdded: Boolean = false,
    val resultRevision: String = ""
)
