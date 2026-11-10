/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.runtime.Immutable
import com.dot.gallery.core.insight.ImageQuad
import com.dot.gallery.domain.model.UriMedia

/**
 * A photo whose text matches the query. [line] is where the match is (the cell is cropped around
 * it) and [words] are the matching words to highlight; both are null/empty for server matches,
 * which carry a cloud badge instead ([fromServer]). [aspectRatio] is the photo's width over height,
 * needed to place the crop; 0 when unknown.
 */
@Immutable
data class TextPhotoMatch(
    val media: UriMedia,
    val aspectRatio: Float = 0f,
    val line: ImageQuad? = null,
    val words: List<ImageQuad> = emptyList(),
    val fromServer: Boolean = false,
)
