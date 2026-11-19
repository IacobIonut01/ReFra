/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/** Portable album identity. */
@Serializable
sealed class AlbumRef {

    @Serializable
    @SerialName("local")
    data class Local(
        val label: String,
        val relativePath: String = "",
        val volume: String? = null,
        /** Device-local bucket id. Tried first on the same install, never trusted across devices. */
        val bucketId: Long = 0L
    ) : AlbumRef()

    @Serializable
    @SerialName("cloud")
    data class Cloud(
        val providerType: String,
        val sourceAccountId: String,
        val remoteAlbumId: String,
        val label: String = ""
    ) : AlbumRef()
}
