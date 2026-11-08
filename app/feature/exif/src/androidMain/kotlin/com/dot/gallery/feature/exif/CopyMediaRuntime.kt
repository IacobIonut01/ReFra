/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif

import com.dot.gallery.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * App-provided backend for [CopyMediaViewModel]: the WorkManager copy pipelines
 * (`MediaCopyWorker`, `CloudAlbumCopyWorker`) stay app-side while the ViewModel
 * lives in this module.
 */
interface CopyMediaRuntime {

    /** Enqueues media copies; returns the task ids (one per chunked request). */
    suspend fun <T : Media> enqueueMediaCopy(items: List<Pair<T, String>>): List<String>

    /** Observes the given copy task ids as decoded snapshots. */
    fun observeMediaCopy(taskIds: List<String>): Flow<List<LocalCopySnapshot>>

    /** Enqueues an album copy for [requestId]; returns the task id. */
    suspend fun enqueueCloudAlbumCopy(requestId: String): String

    /** Observes a cloud-album-copy task as decoded snapshots. */
    fun observeCloudAlbumCopy(taskId: String): Flow<CloudAlbumCopySnapshot>
}
