/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.network

import com.dot.gallery.core.network.isConfiguredLocalNetwork
import com.dot.gallery.core.network.resolveLocalNetworkUrl
import com.dot.gallery.handover.HandoverConfig
import com.dot.gallery.handover.HandoverEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkUrlPolicyTest {

    @Test
    fun unreadableSsidFailsClosedToRemoteUrl() {
        assertFalse(
            isConfiguredLocalNetwork(
                targetSsid = HOME_SSID,
                currentSsid = null,
                onLocalNetwork = true,
            )
        )
        assertEquals(
            REMOTE_URL,
            resolveLocalNetworkUrl(
                remoteUrl = REMOTE_URL,
                localUrl = LOCAL_URL,
                localWifiSsid = HOME_SSID,
                autoUrlSwitch = true,
                onLocalNetwork = true,
                currentSsid = null,
            )
        )
        assertEquals(
            REMOTE_URL,
            handoverConfig().effectiveUrl(onLocalNetwork = true, currentSsid = null)
        )
    }

    @Test
    fun matchingSsidUsesLocalUrl() {
        assertTrue(
            isConfiguredLocalNetwork(
                targetSsid = HOME_SSID,
                currentSsid = HOME_SSID.uppercase(),
                onLocalNetwork = true,
            )
        )
        assertEquals(
            LOCAL_URL,
            handoverConfig().effectiveUrl(onLocalNetwork = true, currentSsid = HOME_SSID)
        )
    }

    @Test
    fun differentSsidUsesRemoteUrl() {
        assertEquals(
            REMOTE_URL,
            handoverConfig().effectiveUrl(onLocalNetwork = true, currentSsid = "Cafe Wi-Fi")
        )
    }

    @Test
    fun blankConfiguredSsidNeverMatches() {
        assertEquals(
            REMOTE_URL,
            resolveLocalNetworkUrl(
                remoteUrl = REMOTE_URL,
                localUrl = LOCAL_URL,
                localWifiSsid = "  ",
                autoUrlSwitch = true,
                onLocalNetwork = true,
                currentSsid = HOME_SSID,
            )
        )
    }

    @Test
    fun disabledOrIncompleteSwitchingUsesRemoteUrl() {
        assertEquals(
            REMOTE_URL,
            handoverConfig().copy(autoUrlSwitch = false)
                .effectiveUrl(onLocalNetwork = true, currentSsid = HOME_SSID)
        )
        assertEquals(
            REMOTE_URL,
            handoverConfig().copy(localUrl = "")
                .effectiveUrl(onLocalNetwork = true, currentSsid = HOME_SSID)
        )
        assertEquals(
            REMOTE_URL,
            handoverConfig().effectiveUrl(onLocalNetwork = false, currentSsid = HOME_SSID)
        )
    }

    private fun handoverConfig() = HandoverConfig(
        enabled = true,
        remoteUrl = REMOTE_URL,
        localUrl = LOCAL_URL,
        localWifiSsid = HOME_SSID,
        autoUrlSwitch = true,
        secret = "secret",
        engine = "",
        sink = HandoverEnvelope.SINK_BOTH,
        preferRemoteQueries = true,
    )

    private companion object {
        const val REMOTE_URL = "https://handover.example.com"
        const val LOCAL_URL = "http://192.168.1.20:8080"
        const val HOME_SSID = "Home Wi-Fi"
    }
}
