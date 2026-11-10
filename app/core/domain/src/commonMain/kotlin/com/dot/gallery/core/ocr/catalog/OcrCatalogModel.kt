/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/** One pinned model: a Hugging Face repository at a fixed commit, with verified artifacts. */
@Serializable
data class OcrCatalogModel(
    val id: String,
    val role: OcrModelRole,
    val repository: String,
    val commit: String,
    val dependencies: List<String> = emptyList(),
    val license: String,
    val artifacts: List<OcrArtifact>,
    val tensorContract: OcrTensorContract,
    val preprocessing: OcrPreprocessing,
    val detectorContract: OcrDetectorContract? = null,
    val recognizerContract: OcrRecognizerContract? = null,
) {
    /** The ONNX file, the only artifact that is downloaded or picked by the user. */
    val onnx: OcrArtifact get() = artifacts.first { it.fileName.endsWith(".onnx") }

    /** The bundled dictionary of a recognizer. */
    val dictionary: OcrArtifact? get() = artifacts.firstOrNull { it.delivery == OcrArtifactDelivery.BUNDLED }
}
