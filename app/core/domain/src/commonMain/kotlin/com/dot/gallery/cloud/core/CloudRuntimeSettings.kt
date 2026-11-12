/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Process-wide, account-qualified runtime view of cloud preferences.
 *
 * The persisted fields remain on [CloudServerConfig] for serialization compatibility. Runtime
 * consumers must resolve the `cfg` carried by a cloud media URI and then read only that account's
 * entry. A missing or legacy `cfg` receives defaults; it never inherits another account's values.
 */
object CloudRuntimeSettings {

    private val _settingsByConfigId =
        MutableStateFlow<Map<Long, CloudAccountRuntimeSettings>>(emptyMap())

    val settingsByConfigId: StateFlow<Map<Long, CloudAccountRuntimeSettings>> =
        _settingsByConfigId.asStateFlow()

    /** Replaces the runtime map, normally with every active config read during app startup. */
    fun applyAll(configs: Iterable<CloudServerConfig>) {
        _settingsByConfigId.value = configs
            .filter { it.id > 0L }
            .associate { it.id to it.toRuntimeSettings() }
    }

    /** Adds or updates one account without disturbing any other account's preferences. */
    fun apply(config: CloudServerConfig) {
        if (config.id <= 0L) return
        _settingsByConfigId.update { current ->
            current + (config.id to config.toRuntimeSettings())
        }
    }

    fun remove(configId: Long) {
        _settingsByConfigId.update { it - configId }
    }

    fun settingsFor(
        configId: Long,
        settings: Map<Long, CloudAccountRuntimeSettings> = _settingsByConfigId.value,
    ): CloudAccountRuntimeSettings = settings[configId] ?: CloudAccountRuntimeSettings()

    fun settingsForCloudUri(
        uriString: String,
        settings: Map<Long, CloudAccountRuntimeSettings> = _settingsByConfigId.value,
    ): CloudAccountRuntimeSettings {
        val configId = CloudUri.parse(uriString)?.configId?.takeIf { it > 0L }
        return configId?.let { settings[it] } ?: CloudAccountRuntimeSettings()
    }

    fun observe(configId: Long): Flow<CloudAccountRuntimeSettings> = settingsByConfigId
        .map { settingsFor(configId, it) }
        .distinctUntilChanged()

    /** Global diagnostics are enabled when any configured account requests verbose logging. */
    val verboseLoggingEnabled: Boolean
        get() = _settingsByConfigId.value.values.any { it.verboseLogging }
}

private fun CloudServerConfig.toRuntimeSettings() = CloudAccountRuntimeSettings(
    verboseLogging = verboseLogging,
    preferRemoteImages = preferRemoteImages,
    readOnlyMode = readOnlyMode,
    loadPreviewImage = loadPreviewImage,
    loadOriginalImage = loadOriginalImage,
    autoPlayVideos = autoPlayVideos,
    forceOriginalVideo = forceOriginalVideo,
)
