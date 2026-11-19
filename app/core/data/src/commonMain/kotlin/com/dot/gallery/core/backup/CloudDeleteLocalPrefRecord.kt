/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class CloudDeleteLocalPrefRecord(
    val album: AlbumRef,
    val albumLabel: String = "",
    val enabled: Boolean = false
)
