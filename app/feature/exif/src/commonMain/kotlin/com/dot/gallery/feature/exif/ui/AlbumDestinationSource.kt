/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

/** (volume, relativePath, mimeType) of one local item in a move or copy selection. */
data class AlbumDestinationSource(
    val volume: String,
    val relativePath: String,
    val mimeType: String,
)
