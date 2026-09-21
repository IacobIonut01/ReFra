/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dot.gallery.BuildConfig
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ml.DownloadInfo
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.workers.cancelModelDownload
import com.dot.gallery.core.workers.downloadModels
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature_node.data.data_source.SmartScanDao
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.SmartScanPhaseEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanRunEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class ModelManagementAction(val enabled: Boolean) {
    DELETE(true),
    CANCEL_DOWNLOAD(true),
    DOWNLOAD(true),
    COPYING(false),
    INSTALLED_OFFLINE(false),
    UNAVAILABLE_OFFLINE(false),
}

/**
 * [canInstall] answers "could the user (re)install models at all" — network downloads with
 * INTERNET permission, or a local asset copy on bundled (withML) builds. Delete is only
 * offered when re-install is possible.
 */
internal fun resolveModelManagementAction(
    status: ModelStatus,
    canInstall: Boolean,
): ModelManagementAction = when (status) {
    ModelStatus.COPYING -> ModelManagementAction.COPYING
    ModelStatus.READY -> if (canInstall) {
        ModelManagementAction.DELETE
    } else {
        ModelManagementAction.INSTALLED_OFFLINE
    }
    ModelStatus.DOWNLOADING -> if (canInstall) {
        ModelManagementAction.CANCEL_DOWNLOAD
    } else {
        ModelManagementAction.UNAVAILABLE_OFFLINE
    }
    ModelStatus.ERROR, ModelStatus.NOT_INSTALLED -> if (canInstall) {
        ModelManagementAction.DOWNLOAD
    } else {
        ModelManagementAction.UNAVAILABLE_OFFLINE
    }
}

@HiltViewModel
class SmartFeaturesViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val workManager: WorkManager,
    private val smartScanScheduler: SmartScanScheduler,
    private val cloudServerConfigDao: CloudServerConfigDao,
    private val providerRegistry: ProviderRegistry,
    smartScanDao: SmartScanDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // Per-group observable state. UI screens pass the relevant [ModelGroup] (SEARCH for smart
    // search + categories, CUTOUT for subject cutout) so each feature is managed independently.
    fun modelStatus(group: ModelGroup): StateFlow<ModelStatus> = modelManager.status(group)
    fun downloadProgress(group: ModelGroup): StateFlow<Float> = modelManager.downloadProgress(group)
    fun errorMessage(group: ModelGroup): StateFlow<String?> = modelManager.errorMessage(group)
    fun downloadInfo(group: ModelGroup): StateFlow<DownloadInfo> = modelManager.downloadInfo(group)

    fun installedSize(group: ModelGroup): Long = modelManager.getInstalledSize(group)

    suspend fun getFileInfos(group: ModelGroup): List<ModelFileInfo> = modelManager.getFileInfos(group)

    val hasInternetPermission: Boolean get() = modelManager.hasInternetPermission
    val areAiFeaturesAvailable: Boolean get() = modelManager.areAiFeaturesAvailable

    /** Whether models can be (re)installed — via network download or bundled APK assets. */
    val canInstallModels: Boolean get() = modelManager.canInstallModels

    val includeIgnoredAlbums: StateFlow<Boolean> = Settings.SmartFeatures.includeIgnoredAlbums(context).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /** Provider types with at least one configured account. */
    val configuredCloudProviders: StateFlow<List<ProviderType>> = cloudServerConfigDao.getAll()
        .map { configs -> configs.map { it.providerType }.distinct() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Provider types opted into on-device indexing of their cached cloud media. */
    val indexOnDeviceProviders: StateFlow<Set<String>> =
        Settings.SmartFeatures.indexOnDeviceProviders(context).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /** Whether queries are delegated to SMART_SEARCH-capable servers. */
    val providerSmartSearch: StateFlow<Boolean> =
        Settings.SmartFeatures.providerSmartSearch(context).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /** Capabilities advertised by the registered provider for [type], if any instance exists. */
    fun capabilitiesOf(type: ProviderType): Set<ProviderCapability> =
        providerRegistry.get(type)?.capabilities ?: emptySet()

    val activeSmartScan: StateFlow<SmartScanRunEntity?> = smartScanDao.observeActiveRun()
        .map { run -> run?.takeIf { SmartScanPlan.shouldShowRun(it.userVisible, it.totalMedia) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val latestSmartScan: StateFlow<SmartScanRunEntity?> = smartScanDao.observeLatestRun().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSmartScanPhases: StateFlow<List<SmartScanPhaseEntity>> = activeSmartScan
        .flatMapLatest { run ->
            if (run == null) flowOf(emptyList()) else smartScanDao.observePhases(run.runId)
        }
        .map { phases ->
            phases.sortedBy { SmartScanPlan.orderedPhases.indexOf(it.phase) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun downloadModels(group: ModelGroup) {
        if (modelManagementAction(group) != ModelManagementAction.DOWNLOAD) return
        viewModelScope.launch {
            if (BuildConfig.ML_MODELS_BUNDLED) {
                // Bundled models are restored from the APK assets — instant and offline-capable.
                modelManager.installBundledModels(group)
                if (modelManager.isReady(group) || !modelManager.hasInternetPermission) return@launch
                // Bundled copy was incomplete — fall through and fetch the rest over the network.
            } else {
                // Explicit install intent: clear the persisted user-removal flag up front so the
                // group stays installed even if the download ultimately fails.
                modelManager.setRemoved(group, false)
            }
            workManager.downloadModels(group)
        }
    }

    fun cancelDownload(group: ModelGroup) {
        if (modelManagementAction(group) != ModelManagementAction.CANCEL_DOWNLOAD) return
        workManager.cancelModelDownload(group)
        viewModelScope.launch {
            modelManager.deleteModels(group)
        }
    }

    fun deleteModels(group: ModelGroup) {
        if (modelManagementAction(group) != ModelManagementAction.DELETE) return
        // Cancel any queued/in-flight download so it cannot resurrect the group
        // after deletion (issue #1229).
        workManager.cancelModelDownload(group)
        viewModelScope.launch {
            modelManager.deleteModels(group)
        }
    }

    private fun modelManagementAction(group: ModelGroup): ModelManagementAction =
        resolveModelManagementAction(modelManager.status(group).value, modelManager.canInstallModels)

    fun setIncludeIgnoredAlbums(include: Boolean) {
        viewModelScope.launch {
            Settings.SmartFeatures.setIncludeIgnoredAlbums(context, include)
            smartScanScheduler.fullRefresh()
        }
    }

    fun setIndexOnDeviceProvider(type: ProviderType, enabled: Boolean) {
        viewModelScope.launch {
            Settings.SmartFeatures.setIndexOnDeviceProvider(context, type, enabled)
            // Opting in needs a scan pass to pick up the newly indexable items;
            // opting out only shrinks the next run's candidate pool.
            if (enabled) smartScanScheduler.automatic(SmartScanFeature.ALL_MASK)
        }
    }

    fun setProviderSmartSearch(enabled: Boolean) {
        viewModelScope.launch {
            Settings.SmartFeatures.setProviderSmartSearch(context, enabled)
        }
    }

    fun refreshMetadata() = request(SmartScanFeature.METADATA.bit)

    fun refreshEmbeddings() = request(SmartScanFeature.EMBEDDINGS.bit)

    fun refreshCategories() = request(SmartScanFeature.CATEGORIES.bit)

    fun refreshPersons() = request(SmartScanFeature.PERSONS.bit)

    fun refreshAll() {
        viewModelScope.launch { smartScanScheduler.all(userVisible = true) }
    }

    fun fullRefresh() {
        viewModelScope.launch { smartScanScheduler.fullRefresh() }
    }

    fun cancelActiveScan() {
        val runId = activeSmartScan.value?.runId ?: return
        viewModelScope.launch { smartScanScheduler.cancel(runId) }
    }

    fun retryLatestScan() {
        viewModelScope.launch { smartScanScheduler.retryFailed(latestSmartScan.value?.runId) }
    }

    private fun request(features: Int) {
        viewModelScope.launch { smartScanScheduler.manual(features) }
    }
}
