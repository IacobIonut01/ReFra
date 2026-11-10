/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

@Serializable
data class OcrConfidenceCalibration(val scale: Double = 1.0, val offset: Double = 0.0)
