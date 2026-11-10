/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

@Serializable
data class OcrPreprocessing(
    val colorOrder: String,
    val scale: Double = 1.0 / 255.0,
    val mean: List<Double>,
    val std: List<Double>,
    val resize: OcrResize,
)
