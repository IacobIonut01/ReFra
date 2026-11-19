/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class AlbumCoverRecord(
    val album: AlbumRef,
    val cover: MediaRef? = null,
    /** Original device URI; only used when the media ref cannot be resolved on the same device. */
    val coverUri: String? = null
)
