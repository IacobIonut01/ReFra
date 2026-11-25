/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.network

/**
 * Whether the device is known to be on the configured local Wi-Fi network.
 *
 * A null [currentSsid] means the platform could not prove which network is active (for example,
 * Android hides the SSID without location access). Unknown is never a match: the local URL often
 * uses a private address that a hostile device can claim on an unrelated Wi-Fi or Ethernet network.
 */
fun isConfiguredLocalNetwork(
    targetSsid: String,
    currentSsid: String?,
    onLocalNetwork: Boolean,
): Boolean {
    val target = targetSsid.trim()
    return target.isNotEmpty() && onLocalNetwork &&
        currentSsid != null && currentSsid.equals(target, ignoreCase = true)
}

/**
 * Resolves a remote/local server pair, using the local URL only after the configured Wi-Fi
 * network has been positively identified. Every uncertain or unconfigured case fails closed to
 * [remoteUrl], which is returned exactly as supplied so callers keep their existing normalization.
 */
fun resolveLocalNetworkUrl(
    remoteUrl: String,
    localUrl: String,
    localWifiSsid: String,
    autoUrlSwitch: Boolean,
    onLocalNetwork: Boolean,
    currentSsid: String?,
): String {
    if (!autoUrlSwitch || localUrl.isBlank()) return remoteUrl
    if (!isConfiguredLocalNetwork(localWifiSsid, currentSsid, onLocalNetwork)) return remoteUrl
    return localUrl.trimEnd('/')
}
