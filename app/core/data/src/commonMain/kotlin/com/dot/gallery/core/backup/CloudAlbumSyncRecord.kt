/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CloudAlbumSyncRecord(
    val sourceAccountId: String,
    val providerType: String,
    val remoteAlbumId: String,
    val albumName: String = "",
    val syncEnabled: Boolean = true
)
