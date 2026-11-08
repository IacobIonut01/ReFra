/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.cloud.sync.CloudAlbumCopyWorker
import com.dot.gallery.cloud.sync.enqueueCloudAlbumCopy
import com.dot.gallery.core.work.WorkInfoLite
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.core.work.isFinished
import com.dot.gallery.core.workers.MediaCopyWorker
import com.dot.gallery.core.workers.copyMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.exif.CloudAlbumCopySnapshot
import com.dot.gallery.feature.exif.CopyMediaRuntime
import com.dot.gallery.feature.exif.CopyWorkState
import com.dot.gallery.feature.exif.LocalCopySnapshot
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class CopyMediaRuntimeImpl @Inject constructor(
    private val scheduler: BackgroundTaskScheduler,
) : CopyMediaRuntime {

    override suspend fun <T : Media> enqueueMediaCopy(items: List<Pair<T, String>>): List<String> =
        scheduler.copyMedia(*items.toTypedArray())

    override fun observeMediaCopy(taskIds: List<String>): Flow<List<LocalCopySnapshot>> {
        if (taskIds.isEmpty()) return flowOf(emptyList())
        return combine(
            taskIds.map { id -> WorkSignals.observe(id, MediaCopyWorker.WORKER_NAME) }
        ) { infos -> infos.map(::toLocalSnapshot).toList() }
    }

    override suspend fun enqueueCloudAlbumCopy(requestId: String): String =
        scheduler.enqueueCloudAlbumCopy(requestId)

    override fun observeCloudAlbumCopy(taskId: String): Flow<CloudAlbumCopySnapshot> =
        // The album-copy worker scopes progress AND output under the task id itself.
        WorkSignals.observe(taskId, taskId).map { info ->
            val data = if (info.isFinished) info.output else info.progress
            CloudAlbumCopySnapshot(
                state = info.state.toCopyWorkState(),
                requestId = data?.getString(CloudAlbumCopyWorker.KEY_REQUEST_ID),
                mode = data?.getString(CloudAlbumCopyWorker.KEY_MODE),
                destinationProvider = data?.getString(CloudAlbumCopyWorker.KEY_DESTINATION_PROVIDER),
                destinationConfigId = data?.getLong(CloudAlbumCopyWorker.KEY_DESTINATION_CONFIG_ID, 0L)
                    ?.takeIf { it != 0L },
                destinationRemoteAlbumId =
                    data?.getString(CloudAlbumCopyWorker.KEY_DESTINATION_REMOTE_ALBUM_ID),
                destinationAlbumId = data?.getLong(CloudAlbumCopyWorker.KEY_DESTINATION_ALBUM_ID, 0L)
                    ?.takeIf { it != 0L },
                destinationLabel = data?.getString(CloudAlbumCopyWorker.KEY_DESTINATION_LABEL),
                targetRemoteId = data?.getString(CloudAlbumCopyWorker.KEY_TARGET_REMOTE_ID),
                total = data?.getIntOrNull(CloudAlbumCopyWorker.KEY_TOTAL),
                completed = data?.getIntOrNull(CloudAlbumCopyWorker.KEY_COMPLETED),
                alreadyPresent = data?.getIntOrNull(CloudAlbumCopyWorker.KEY_ALREADY_PRESENT),
                failed = data?.getIntOrNull(CloudAlbumCopyWorker.KEY_FAILED),
                current = data?.getIntOrNull(CloudAlbumCopyWorker.KEY_CURRENT),
                retryable = data?.getBoolean(CloudAlbumCopyWorker.KEY_RETRYABLE, false) ?: false,
                message = data?.getString(CloudAlbumCopyWorker.KEY_MESSAGE),
            )
        }

    private fun toLocalSnapshot(info: WorkInfoLite): LocalCopySnapshot {
        val data = if (info.isFinished) info.output else info.progress
        return LocalCopySnapshot(
            state = info.state.toCopyWorkState(),
            progressPercent = data?.getIntOrNull("progress"),
            targetUri = data?.getString(MediaCopyWorker.KEY_TARGET_URI),
        )
    }

    private fun TaskState.toCopyWorkState(): CopyWorkState = when (this) {
        is TaskState.Enqueued -> CopyWorkState.ENQUEUED
        is TaskState.Running -> CopyWorkState.RUNNING
        is TaskState.Succeeded -> CopyWorkState.SUCCEEDED
        is TaskState.Failed -> CopyWorkState.FAILED
        is TaskState.Cancelled -> CopyWorkState.CANCELLED
        else -> CopyWorkState.BLOCKED
    }
}
