/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.model

/** Result of checking a picked file against the catalog. */
sealed interface OcrFileMatch {
    /** Size and SHA-256 match the `.onnx` of [modelId]. */
    data class Accepted(val modelId: String) : OcrFileMatch
    /** The size matches [modelId] but the contents do not: altered or incomplete. */
    data class Altered(val modelId: String) : OcrFileMatch
    /** Not a file the catalog knows. */
    data object Unknown : OcrFileMatch
}
