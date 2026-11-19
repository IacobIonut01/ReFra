/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui


data class MetadataFileInfo(
    val name: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Anything [com.dot.gallery.core.presentation.components.LocalMediaImageRenderer] can load. */
    val thumbnailModel: Any? = null,
)
