/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CloudUploadPrefRecord(
    val sourceAccountId: String,
    val providerType: String,
    val album: AlbumRef,
    val albumLabel: String = "",
    val uploadEnabled: Boolean = false,
    val deleteLocalAfterUpload: Boolean = false,
    val customPath: String = ""
)
