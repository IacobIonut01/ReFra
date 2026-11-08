/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable media identity used by every section record.
 *
 * Device-local MediaStore ids and cloud `globalMediaId`s are not stable across installs, so
 * exports store enough information to resolve the item again on the destination device.
 */
@Serializable
sealed class MediaRef {

    /**
     * A MediaStore item. `relativePath + label` is the primary key; [size] and [timestamp]
     * disambiguate files that share a name.
     */
    @Serializable
    @SerialName("local")
    data class Local(
        val relativePath: String,
        val label: String,
        val size: Long = 0L,
        val timestamp: Long = 0L,
        val albumLabel: String = "",
        /** Storage volume (`emulated/0`, `XXXX-XXXX`), when derivable from the absolute path. */
        val volume: String = ""
    ) : MediaRef()

    /**
     * A cloud item. [sourceAccountId] is `backupSourceAccountId(providerType, sourceConfigId)`;
     * restore maps it to the destination account before recomputing the global media id.
     */
    @Serializable
    @SerialName("cloud")
    data class Cloud(
        val providerType: String,
        val sourceAccountId: String,
        val remoteId: String,
        val label: String = ""
    ) : MediaRef()
}

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
