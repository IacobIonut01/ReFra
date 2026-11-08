/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.injection.injectViewModel

@Composable
fun SettingsHandoverScreen(
    viewModel: HandoverSettingsViewModel = injectViewModel { handoverSettingsViewModel }
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val remoteUrl by viewModel.remoteUrl.collectAsStateWithLifecycle()
    val localUrl by viewModel.localUrl.collectAsStateWithLifecycle()
    val wifiSsid by viewModel.localWifiSsid.collectAsStateWithLifecycle()
    val autoSwitch by viewModel.autoUrlSwitch.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val sink by viewModel.sink.collectAsStateWithLifecycle()
    val preferRemoteQueries by viewModel.preferRemoteQueries.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val secretSet by viewModel.secretSet.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val knownEngines by viewModel.knownEngines.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()

    HandoverSettingsContent(
        state = HandoverSettingsState(
            enabled = enabled,
            remoteUrl = remoteUrl,
            localUrl = localUrl,
            wifiSsid = wifiSsid,
            autoSwitch = autoSwitch,
            engine = engine,
            sink = sink,
            preferRemoteQueries = preferRemoteQueries,
            authMode = authMode,
            secretSet = secretSet,
            status = status,
            knownEngines = knownEngines,
            probe = probe,
        ),
        actions = HandoverSettingsActions(
            onEnabledChange = viewModel::setEnabled,
            onAutoSwitchChange = viewModel::setAutoUrlSwitch,
            onWifiSsid = viewModel::setLocalWifiSsid,
            onRemoteUrl = viewModel::setRemoteUrl,
            onLocalUrl = viewModel::setLocalUrl,
            onSecret = viewModel::setSecret,
            onEngine = viewModel::setEngine,
            onSink = viewModel::setSink,
            onPreferRemoteQueriesChange = viewModel::setPreferRemoteQueries,
            onAuthMode = viewModel::setAuthMode,
            onTestConnection = viewModel::testConnection,
            onClearProbe = viewModel::clearProbe,
        ),
    )
}
