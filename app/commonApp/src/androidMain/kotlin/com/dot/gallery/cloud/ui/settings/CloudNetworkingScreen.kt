/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.ui.rememberWifiSsidAccessState
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_networking


@Composable
fun CloudNetworkingScreen(configId: Long) {
    val settingsVm = injectViewModel<CloudSettingsViewModel> {cloudSettingsViewModel}
    val config by settingsVm.config.collectAsStateWithLifecycle()
    val loadState by settingsVm.loadState.collectAsStateWithLifecycle()

    LaunchedEffect(configId) { settingsVm.loadConfig(configId) }
    val loaded = config
    val wifiAccess = rememberWifiSsidAccessState(
        targetSsid = loaded?.localWifiSsid.orEmpty(),
        switchingConfigured = loaded?.let {
            it.autoUrlSwitch && it.localServerUrl.isNotBlank()
        } == true,
        onAccessRestored = { settingsVm.reapplyEffectiveUrl() },
    )
    if (loaded == null) {
        AccountSettingsStateScreen(stringResource(Res.string.cloud_networking), loadState)
        return
    }
    val effectiveUrl = remember(loaded, wifiAccess.ssidUnreadable) { settingsVm.effectiveUrl() }
    val localActive = remember(loaded, wifiAccess.ssidUnreadable) { settingsVm.isLocalActive() }

    CloudNetworkingContent(
        config = loaded,
        effectiveUrl = effectiveUrl?.ifBlank { null } ?: loaded.serverUrl,
        localActive = localActive,
        onUpdateConfig = { transform -> settingsVm.updateConfig(transform) },
        wifiAccess = wifiAccess,
    )
}
