/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

@Serializable
data class OcrTensorContract(
    val opset: Int,
    val inputs: List<OcrTensorMetadata>,
    val outputs: List<OcrTensorMetadata>,
)
