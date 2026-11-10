/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

@Serializable
data class OcrRecognizerContract(
    val ctcBlankIndex: Int = 0,
    val dictionaryArtifact: String,
    val outputInterpretation: String,
    val confidenceCalibration: OcrConfidenceCalibration = OcrConfidenceCalibration(),
)
