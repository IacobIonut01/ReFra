/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.runtime.Stable
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia


/** Snapshot of the categories hub — collected app-side, forwarded as one value. */
@Stable
data class CategoriesState(
    val categoriesWithCount: List<CategoryWithMediaCount> = emptyList(),
    val distinctMediaCount: Int = 0,
    /** Timeline media — used to resolve category thumbnails by id. */
    val mediaState: MediaState<UriMedia> = MediaState(),
    /** A user-visible smart scan that includes categories is running. */
    val isScanning: Boolean = false,
    /** Overall scan progress in 0..1; null while the run has not reported progress yet. */
    val scanProgress: Float? = null,
    val scanProcessed: Int = 0,
    val scanTotal: Int = 0,
    /** Status of the active Smart search model; null until it is known. */
    val searchModelStatus: ModelStatus? = null,
    /**
     * Photos of the category whose cover picker is open (see
     * [CategoriesActions.onCoverPickerChanged]); null while they load.
     */
    val coverCandidates: List<UriMedia>? = null,
) {
    /** Categories can't be scored without an installed search model. */
    val needsSearchModel: Boolean
        get() = searchModelStatus != null && searchModelStatus != ModelStatus.READY

    val isModelDownloading: Boolean
        get() = searchModelStatus == ModelStatus.DOWNLOADING || searchModelStatus == ModelStatus.COPYING
}
