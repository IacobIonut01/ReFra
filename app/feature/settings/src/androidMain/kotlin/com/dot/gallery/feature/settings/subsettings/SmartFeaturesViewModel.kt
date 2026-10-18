/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.subsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ml.DownloadInfo
import com.dot.gallery.core.ml.ModelFileInfo
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.util.clear
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.feature.settings.ui.ModelManagementAction
import com.dot.gallery.feature.settings.ui.resolveModelManagementAction
import com.dot.gallery.core.AppIdentity


class SmartFeaturesViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val modelDownloadRuntime: ModelDownloadRuntime,
    private val smartScanScheduler: SmartScanScheduler,
    private val cloudServerConfigDao: CloudServerConfigDao,
    private val providerRegistry: ProviderRegistry,
    smartScanDao: SmartScanDao,
    private val context: Context
) : ViewModel() {

    // Per-group observable state. UI screens pass the relevant [ModelGroup] (a SEARCH_*
    // engine for smart search + categories, CUTOUT for subject cutout, etc.) so each
    // feature is managed independently.
    fun modelStatus(group: ModelGroup): StateFlow<ModelStatus> = modelManager.status(group)
    fun downloadProgress(group: ModelGroup): StateFlow<Float> = modelManager.downloadProgress(group)
    fun errorMessage(group: ModelGroup): StateFlow<String?> = modelManager.errorMessage(group)
    fun downloadInfo(group: ModelGroup): StateFlow<DownloadInfo> = modelManager.downloadInfo(group)

    /** The search engine selected in the AI Models manager. */
    val activeSearchModel: StateFlow<SearchModel> = modelManager.activeSearchModelFlow

    /** Status of whichever search engine is currently selected. */
    fun activeSearchModelStatus(): StateFlow<ModelStatus> =
        modelManager.activeSearchModelStatus().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = modelManager.status(modelManager.activeSearchModel).value
        )

    /** Switch engines — the next Smart Scan re-embeds the library under the new model. */
    fun selectSearchModel(model: SearchModel) {
        viewModelScope.launch {
            modelManager.setActiveSearchModel(model)
            smartScanScheduler.automatic(
                SmartScanFeature.EMBEDDINGS.bit or SmartScanFeature.CATEGORIES.bit
            )
        }
    }

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
            if (AppIdentity.mlModelsBundled) {
                // Bundled models are restored from the APK assets — instant and offline-capable.
                modelManager.installBundledModels(group)
                if (modelManager.isReady(group) || !modelManager.hasInternetPermission) return@launch
                // Bundled copy was incomplete — fall through and fetch the rest over the network.
            } else {
                // Explicit install intent: clear the persisted user-removal flag up front so the
                // group stays installed even if the download ultimately fails.
                modelManager.setRemoved(group, false)
            }
            modelDownloadRuntime.enqueueModelDownload(group)
        }
    }

    fun cancelDownload(group: ModelGroup) {
        if (modelManagementAction(group) != ModelManagementAction.CANCEL_DOWNLOAD) return
        modelDownloadRuntime.cancelModelDownload(group)
        viewModelScope.launch {
            modelManager.deleteModels(group)
        }
    }

    fun deleteModels(group: ModelGroup) {
        if (modelManagementAction(group) != ModelManagementAction.DELETE) return
        // Cancel any queued/in-flight download so it cannot resurrect the group
        // after deletion (issue #1229).
        modelDownloadRuntime.cancelModelDownload(group)
        viewModelScope.launch {
            modelManager.deleteModels(group)
            // Deleting the active search engine would leave search pointed at missing
            // files — fall back to the other engine when it is installed.
            SearchModel.entries
                .firstOrNull { it.group == group && it == modelManager.activeSearchModel }
                ?.let { active ->
                    val fallback = SearchModel.entries.first { it != active }
                    if (modelManager.isReady(fallback.group)) {
                        modelManager.setActiveSearchModel(fallback)
                    }
                }
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
