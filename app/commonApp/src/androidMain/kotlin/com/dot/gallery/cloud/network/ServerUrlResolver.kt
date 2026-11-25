/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.network

import android.content.Context
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.core.network.isConfiguredLocalNetwork
import com.dot.gallery.core.network.resolveLocalNetworkUrl
import com.dot.gallery.core.presentation.util.currentWifiSsid
import com.dot.gallery.core.presentation.util.isOnLocalNetwork
import javax.inject.Inject
import com.dot.gallery.core.injection.Singleton


/**
 * Resolves the effective server URL for a [CloudServerConfig] based on the current network.
 *
 * When [CloudServerConfig.autoUrlSwitch] is enabled and a [CloudServerConfig.localServerUrl]
 * is configured, the local URL is used while the device is on the configured local network:
 *  - a [CloudServerConfig.localWifiSsid] MUST be set — switching only ever happens on that
 *    named network. A blank SSID means no local network is configured, so switching is
 *    effectively disabled and the external URL is always used.
 *  - if the SSID is unreadable (no location permission or disabled location services on
 *    Android 10+), the network cannot be verified and the external URL is used.
 *
 * Otherwise the externally-reachable [CloudServerConfig.serverUrl] is used unchanged.
 */
@Singleton
class ServerUrlResolver @Inject constructor(
    private val context: Context
) {

    /** Returns a copy of [config] whose [CloudServerConfig.serverUrl] is the effective URL. */
    fun resolve(config: CloudServerConfig): CloudServerConfig {
        val effective = effectiveUrl(config)
        return if (effective == config.serverUrl) config else config.copy(serverUrl = effective)
    }

    /** Computes the effective base URL without copying the config. */
    fun effectiveUrl(config: CloudServerConfig): String = resolveLocalNetworkUrl(
        remoteUrl = config.serverUrl,
        localUrl = config.localServerUrl,
        localWifiSsid = config.localWifiSsid,
        autoUrlSwitch = config.autoUrlSwitch,
        onLocalNetwork = context.isOnLocalNetwork(),
        currentSsid = context.currentWifiSsid(),
    )

    /** Whether the device is currently on the local network the [config] targets. */
    fun isOnConfiguredLocalNetwork(config: CloudServerConfig): Boolean = isConfiguredLocalNetwork(
        targetSsid = config.localWifiSsid,
        currentSsid = context.currentWifiSsid(),
        onLocalNetwork = context.isOnLocalNetwork(),
    )
}
