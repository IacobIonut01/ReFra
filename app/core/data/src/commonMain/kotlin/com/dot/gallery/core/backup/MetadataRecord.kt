/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class MetadataRecord(
    val media: MediaRef,
    val core: MediaMetadataCoreRecord? = null,
    val video: MediaMetadataVideoRecord? = null,
    val flags: MediaMetadataFlagsRecord? = null,
    val captureTime: MediaCaptureTimeRecord? = null
)
