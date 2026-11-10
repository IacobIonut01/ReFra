/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/** A file of a model. [sizeBytes] and [sha256] must both match before the file is used. */
@Serializable
data class OcrArtifact(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val delivery: OcrArtifactDelivery = OcrArtifactDelivery.DOWNLOAD,
    /** Class-path resource of a [OcrArtifactDelivery.BUNDLED] artifact. */
    val resource: String? = null,
)
