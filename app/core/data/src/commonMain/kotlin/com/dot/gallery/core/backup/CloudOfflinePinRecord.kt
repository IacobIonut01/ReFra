/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CloudOfflinePinRecord(
    val sourceAccountId: String,
    val providerType: String,
    val label: String = "",
    val createdAt: Long = 0L
)
