/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.catalog

import kotlinx.serialization.Serializable

/** `LONG_EDGE` for the detector, `FIXED_HEIGHT_MAX_WIDTH` for recognizers. */
@Serializable
data class OcrResize(val mode: String, val width: Int, val height: Int, val preserveAspectRatio: Boolean)
