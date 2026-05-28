/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.dot.gallery.core.ml.DownloadInfo
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.workers.cancelModelDownload
import com.dot.gallery.core.workers.downloadModels
import com.dot.gallery.core.workers.forceMetadataCollect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartFeaturesViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val workManager: WorkManager
) : ViewModel() {

    val modelStatus: StateFlow<ModelStatus> = modelManager.status
    val downloadProgress: StateFlow<Float> = modelManager.downloadProgress
    val errorMessage: StateFlow<String?> = modelManager.errorMessage

    val downloadInfo: StateFlow<DownloadInfo> = modelManager.downloadInfo

    val installedSize: Long get() = modelManager.getInstalledSize()

    fun getFileInfos(): List<ModelFileInfo> = modelManager.getFileInfos()

    val hasInternetPermission: Boolean get() = modelManager.hasInternetPermission
    val areAiFeaturesAvailable: Boolean get() = modelManager.areAiFeaturesAvailable

    val isMetadataWorkerRunning: StateFlow<Boolean> = workManager.getWorkInfosFlow(
        WorkQuery.fromUniqueWorkNames("MetadataCollection")
    ).map { infos ->
        infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val metadataProgress: StateFlow<Int> = workManager.getWorkInfosFlow(
        WorkQuery.fromUniqueWorkNames("MetadataCollection")
    ).map { infos ->
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?.progress?.getInt("progress", -1) ?: -1
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = -1
    )

    fun downloadModels() {
        if (!modelManager.hasInternetPermission) return
        workManager.downloadModels()
    }

    fun cancelDownload() {
        workManager.cancelModelDownload()
        viewModelScope.launch {
            modelManager.deleteModels()
        }
    }

    fun deleteModels() {
        viewModelScope.launch {
            modelManager.deleteModels()
        }
    }

    fun refreshMetadata() {
        workManager.forceMetadataCollect()
    }
}
