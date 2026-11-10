/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.repository

import com.dot.gallery.core.ocr.domain.NormalizedQuad

/**
 * Where a search query was found in one photo: the best matching [line] and the [words] in it that
 * match. [words] is empty for scripts without spaces, where the whole line is highlighted.
 * [aspectRatio] is the source width over height the quads are normalized to.
 */
data class OcrMatchRegion(val line: NormalizedQuad, val words: List<NormalizedQuad>, val aspectRatio: Float)
