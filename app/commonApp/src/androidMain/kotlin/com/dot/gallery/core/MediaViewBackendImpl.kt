/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core

import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.core.work.WorkSignals
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.workers.RotateMediaWorker
import com.dot.gallery.core.workers.rotateImage
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.feature.viewer.MediaViewBackend
import com.dot.gallery.feature.viewer.MediaViewViewModel.RotationStage
import com.dot.gallery.feature.viewer.PreparedVisualSearchImage
import com.dot.gallery.feature.viewer.RotateUpdate
import com.dot.gallery.app.presentation.mediaview.VisualSearchLauncher
import com.dot.gallery.feature.viewer.VisualSearchStage
import dev.brewkits.kmpworkmanager.background.domain.BackgroundTaskScheduler
import dev.brewkits.kmpworkmanager.background.domain.TaskState
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

@Singleton
class MediaViewBackendImpl @Inject constructor(
    private val scheduler: BackgroundTaskScheduler,
    private val localPeopleProvider: LocalPeopleProvider,
    private val visualSearchLauncher: VisualSearchLauncher,
) : MediaViewBackend {

    override fun peopleForMedia(mediaId: Long): Flow<List<PersonInfo>> =
        localPeopleProvider.getMediaPeople(mediaId)

    override suspend fun removeMediaFromPerson(
        personId: String,
        mediaIds: List<Long>,
    ): Boolean = localPeopleProvider.removeMediaFromPerson(personId, mediaIds)

    override fun rotateMedia(
        media: Media,
        degrees: Int,
        forceCopy: Boolean,
    ): Flow<RotateUpdate> = flow {
        val id = scheduler.rotateImage(media, degrees, forceCopy)
        emitAll(
            WorkSignals.observe(id, RotateMediaWorker.WORKER_NAME)
                .transformWhile { info ->
                    if (!info.isFinished) {
                        val status = info.progress.getInt(RotateMediaWorker.KEY_STATUS, -1)
                        // Terminal statuses land in progress just before the task finishes;
                        // they are not stages and must not flash "Preparing…".
                        if (status >= 0 && status != RotateMediaWorker.Status.COMPLETED &&
                            status != RotateMediaWorker.Status.FAILED
                        ) emit(RotateUpdate.Stage(stageForStatus(status)))
                        true
                    } else {
                        emit(
                            if (info.state is TaskState.Succeeded) {
                                RotateUpdate.Succeeded(info.output?.getString(RotateMediaWorker.KEY_RESULT_URI))
                            } else {
                                RotateUpdate.Failed(info.output?.getString(RotateMediaWorker.KEY_MESSAGE))
                            }
                        )
                        false
                    }
                }
        )
    }

    private fun stageForStatus(status: Int): RotationStage = when (status) {
        RotateMediaWorker.Status.ROTATING -> RotationStage.ROTATING
        RotateMediaWorker.Status.SAVING -> RotationStage.SAVING
        RotateMediaWorker.Status.UPLOADING -> RotationStage.UPLOADING
        else -> RotationStage.DECODING
    }

    override suspend fun prepareVisualSearch(
        media: Media,
        metadata: MediaMetadata?,
        currentVault: Vault?,
        positionMs: Long?,
        convertTo: ImageReencoder.ImageWriteFormat?,
        onStage: (VisualSearchStage, Int?) -> Unit,
    ): PreparedVisualSearchImage = visualSearchLauncher.prepare(
        media = media,
        metadata = metadata,
        currentVault = currentVault,
        positionMs = positionMs,
        convertTo = convertTo,
        onStage = onStage,
    )
}
