/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import kotlinx.serialization.Serializable

@Serializable
data class CloudServerConfig(
    val id: Long = 0L,
    val providerType: ProviderType,
    val serverUrl: String,
    val apiKey: String? = null,
    val username: String? = null,
    val password: String? = null,
    val displayName: String = "",
    val isActive: Boolean = true,
    val lastConnected: Long = 0L,
    val syncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val syncIntervalMinutes: Int = 360,
    // Backup options
    val cellularPhotos: Boolean = false,
    val cellularVideos: Boolean = false,
    val requireCharging: Boolean = false,
    val syncAlbums: Boolean = false,
    // Notification settings
    val showBackupTotalProgress: Boolean = true,
    val showBackupDetailProgress: Boolean = false,
    val notifyBackupFailures: Boolean = true,
    // Networking
    val autoUrlSwitch: Boolean = false,
    val localWifiSsid: String = "",
    val localServerUrl: String = "",
    val externalUrls: String = "[]",
    // Viewer settings
    val loadPreviewImage: Boolean = true,
    val loadOriginalImage: Boolean = false,
    val autoPlayVideos: Boolean = true,
    /** Retired: playback follows the global Loop videos setting. Kept for serialization compatibility. */
    val loopVideos: Boolean = false,
    val forceOriginalVideo: Boolean = false,
    // Advanced settings
    val verboseLogging: Boolean = false,
    val syncRemoteDeletions: Boolean = false,
    val preferRemoteImages: Boolean = false,
    val readOnlyMode: Boolean = false,
    // Download settings (remote -> local)
    val downloadRemoteEnabled: Boolean = false,
    val downloadVideos: Boolean = true,
    // Upload destination (local -> remote). Blank = provider default folder.
    val uploadBasePath: String = "",
    val uploadVideosPath: String = ""
)

/** Sentinel serverConfigId for the on-device "local" people provider. */
const val LOCAL_PEOPLE_CONFIG_ID = -1000L
