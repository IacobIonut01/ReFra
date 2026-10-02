/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.cloud.sync.cloudSyncScheduleChanged
import com.dot.gallery.core.image.thumbnail.LocalThumbnailStore
import com.github.panpf.sketch.sketch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CloudSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configDao: CloudServerConfigDao,
    private val urlResolver: ServerUrlResolver,
    private val providerInitializer: CloudProviderInitializer,
    private val cloudMediaCache: CloudMediaCache,
    private val syncScheduler: CloudSyncScheduler
) : ViewModel() {

    private val _cacheClearing = MutableStateFlow(false)
    val cacheClearing: StateFlow<Boolean> = _cacheClearing.asStateFlow()

    private val _config = MutableStateFlow<CloudServerConfigEntity?>(null)
    val config: StateFlow<CloudServerConfigEntity?> = _config.asStateFlow()

    private val _loadState = MutableStateFlow(AccountSettingsLoadState.LOADING)
    val loadState: StateFlow<AccountSettingsLoadState> = _loadState.asStateFlow()

    private var globalConfigs: List<CloudServerConfigEntity> = emptyList()

    fun loadConfig(configId: Long, allowGlobal: Boolean = false) {
        _loadState.value = AccountSettingsLoadState.LOADING
        _config.value = null
        globalConfigs = emptyList()
        viewModelScope.launch {
            if (configId > 0L) {
                val selected = configDao.getById(configId)
                if (selected == null) {
                    _loadState.value = AccountSettingsLoadState.ERROR
                } else {
                    _config.value = selected
                    CloudRuntimeSettings.apply(selected.toCloudServerConfig())
                    _loadState.value = AccountSettingsLoadState.READY
                }
                return@launch
            }

            if (!allowGlobal) {
                _loadState.value = AccountSettingsLoadState.ERROR
                return@launch
            }
            val active = configDao.getAll().first().filter { it.isActive }
            if (active.isEmpty()) {
                _loadState.value = AccountSettingsLoadState.ERROR
            } else {
                globalConfigs = active
                CloudRuntimeSettings.applyAll(active.map { it.toCloudServerConfig() })
                _config.value = active.first()
                _loadState.value = AccountSettingsLoadState.READY
            }
        }
    }

    /** The server URL currently in effect for the selected account on the current network. */
    fun effectiveUrl(): String? =
        _config.value?.toCloudServerConfig()?.let { urlResolver.effectiveUrl(it) }

    /** Whether the selected account's local URL is active on the configured local network. */
    fun isLocalActive(): Boolean {
        val cfg = _config.value?.toCloudServerConfig() ?: return false
        return cfg.autoUrlSwitch && cfg.localServerUrl.isNotBlank() &&
                urlResolver.isOnConfiguredLocalNetwork(cfg)
    }

    fun updateConfig(transform: CloudServerConfigEntity.() -> CloudServerConfigEntity) {
        val current = _config.value ?: return
        val targets = globalConfigs.ifEmpty { listOf(current) }
        val updates = targets.map { it to it.transform() }
        val updated = updates.first().second
        _config.value = updated
        if (globalConfigs.isNotEmpty()) globalConfigs = updates.map { it.second }
        updates.forEach { (_, after) ->
            CloudRuntimeSettings.apply(after.toCloudServerConfig())
        }
        viewModelScope.launch {
            updates.forEach { (_, after) ->
                configDao.update(after)
                // Re-apply the effective URL to each live provider so networking changes take
                // effect immediately. This is a no-op for unrelated settings.
                providerInitializer.reconfigureAccountAsync(after.id)
            }
            if (updates.any { (before, after) -> cloudSyncScheduleChanged(before, after) }) {
                syncScheduler.reconcile()
            }
        }
    }

    /**
     * Clears all cloud image caches: the two-tier offline cache (auto tier only — pinned
     * offline content is preserved), the shared Sketch memory/disk caches, Glide's disk cache,
     * and the zoom-original scratch dir. Returns to idle when done so the UI can re-enable the row.
     */
    fun clearImageCache() {
        if (_cacheClearing.value) return
        _cacheClearing.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { cloudMediaCache.clearAuto() }
                runCatching { context.sketch.memoryCache.clear() }
                runCatching { context.sketch.resultCache.clear() }
                runCatching { context.sketch.downloadCache.clear() }
                runCatching { Glide.get(context).clearDiskCache() }
                // #1276: the persistent thumbnail store lives in filesDir, not Glide's cache —
                // clear it under the same "clear image caches" semantic.
                runCatching { LocalThumbnailStore.clearShared(context) }
                runCatching { File(context.cacheDir, "cloud_zoom_originals").deleteRecursively() }
                runCatching { File(context.cacheDir, "cloud_http_cache").deleteRecursively() }
            }
            runCatching { Glide.get(context).clearMemory() }
            _cacheClearing.value = false
        }
    }
}

enum class AccountSettingsLoadState {
    LOADING,
    READY,
    ERROR
}
