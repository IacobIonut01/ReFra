/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class SearchHistoryRecord(
    val timestamp: Long,
    val query: String,
    val media: MediaRef? = null,
    val mediaLabel: String? = null,
    val mediaUri: String? = null
)
