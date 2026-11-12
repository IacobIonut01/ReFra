/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.ProviderType


@Entity(
    tableName = "cloud_server_config",
    indices = [Index(value = ["providerType"])]
)
data class CloudServerConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val providerType: ProviderType,
    val serverUrl: String,
    val apiKey: String? = null,
    val username: String? = null,
    val encryptedPassword: String? = null,
    val displayName: String = "",
    val isActive: Boolean = true,
    val lastConnected: Long = 0L,
    val syncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val syncIntervalMinutes: Int = 360,
    val syncFolders: String = "",
    // Backup options
    @ColumnInfo(defaultValue = "0") val cellularPhotos: Boolean = false,
    @ColumnInfo(defaultValue = "0") val cellularVideos: Boolean = false,
    @ColumnInfo(defaultValue = "0") val requireCharging: Boolean = false,
    @ColumnInfo(defaultValue = "0") val syncAlbums: Boolean = false,
    // Notification settings
    @ColumnInfo(defaultValue = "1") val showBackupTotalProgress: Boolean = true,
    @ColumnInfo(defaultValue = "0") val showBackupDetailProgress: Boolean = false,
    @ColumnInfo(defaultValue = "1") val notifyBackupFailures: Boolean = true,
    // Networking
    @ColumnInfo(defaultValue = "0") val autoUrlSwitch: Boolean = false,
    @ColumnInfo(defaultValue = "") val localWifiSsid: String = "",
    @ColumnInfo(defaultValue = "") val localServerUrl: String = "",
    @ColumnInfo(defaultValue = "[]") val externalUrls: String = "[]",
    // Viewer settings
    @ColumnInfo(defaultValue = "1") val loadPreviewImage: Boolean = true,
    @ColumnInfo(defaultValue = "0") val loadOriginalImage: Boolean = false,
    @ColumnInfo(defaultValue = "1") val autoPlayVideos: Boolean = true,
    // Retired: playback follows the global Loop videos setting. Kept so the schema and old backups stay unchanged.
    @ColumnInfo(defaultValue = "0") val loopVideos: Boolean = false,
    @ColumnInfo(defaultValue = "0") val forceOriginalVideo: Boolean = false,
    // Advanced settings
    @ColumnInfo(defaultValue = "0") val verboseLogging: Boolean = false,
    @ColumnInfo(defaultValue = "0") val syncRemoteDeletions: Boolean = false,
    @ColumnInfo(defaultValue = "0") val preferRemoteImages: Boolean = false,
    @ColumnInfo(defaultValue = "0") val readOnlyMode: Boolean = false,
    // Download settings (remote -> local)
    @ColumnInfo(defaultValue = "0") val downloadRemoteEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val downloadVideos: Boolean = true,
    // Upload destination (local -> remote). Blank = provider default folder.
    @ColumnInfo(defaultValue = "") val uploadBasePath: String = "",
    @ColumnInfo(defaultValue = "") val uploadVideosPath: String = ""
) {
    fun toCloudServerConfig(): CloudServerConfig = CloudServerConfig(
        id = id,
        providerType = providerType,
        serverUrl = serverUrl,
        apiKey = apiKey,
        username = username,
        password = encryptedPassword,
        displayName = displayName,
        isActive = isActive,
        lastConnected = lastConnected,
        syncEnabled = syncEnabled,
        wifiOnly = wifiOnly,
        syncIntervalMinutes = syncIntervalMinutes,
        cellularPhotos = cellularPhotos,
        cellularVideos = cellularVideos,
        requireCharging = requireCharging,
        syncAlbums = syncAlbums,
        showBackupTotalProgress = showBackupTotalProgress,
        showBackupDetailProgress = showBackupDetailProgress,
        notifyBackupFailures = notifyBackupFailures,
        autoUrlSwitch = autoUrlSwitch,
        localWifiSsid = localWifiSsid,
        localServerUrl = localServerUrl,
        externalUrls = externalUrls,
        loadPreviewImage = loadPreviewImage,
        loadOriginalImage = loadOriginalImage,
        autoPlayVideos = autoPlayVideos,
        loopVideos = loopVideos,
        forceOriginalVideo = forceOriginalVideo,
        verboseLogging = verboseLogging,
        syncRemoteDeletions = syncRemoteDeletions,
        preferRemoteImages = preferRemoteImages,
        readOnlyMode = readOnlyMode,
        downloadRemoteEnabled = downloadRemoteEnabled,
        downloadVideos = downloadVideos,
        uploadBasePath = uploadBasePath,
        uploadVideosPath = uploadVideosPath
    )

    companion object {
        fun fromCloudServerConfig(config: CloudServerConfig, encryptedPwd: String? = null) =
            CloudServerConfigEntity(
                id = config.id,
                providerType = config.providerType,
                serverUrl = config.serverUrl,
                apiKey = config.apiKey,
                username = config.username,
                encryptedPassword = encryptedPwd,
                displayName = config.displayName,
                isActive = config.isActive,
                lastConnected = config.lastConnected,
                syncEnabled = config.syncEnabled,
                wifiOnly = config.wifiOnly,
                syncIntervalMinutes = config.syncIntervalMinutes,
                cellularPhotos = config.cellularPhotos,
                cellularVideos = config.cellularVideos,
                requireCharging = config.requireCharging,
                syncAlbums = config.syncAlbums,
                showBackupTotalProgress = config.showBackupTotalProgress,
                showBackupDetailProgress = config.showBackupDetailProgress,
                notifyBackupFailures = config.notifyBackupFailures,
                autoUrlSwitch = config.autoUrlSwitch,
                localWifiSsid = config.localWifiSsid,
                localServerUrl = config.localServerUrl,
                externalUrls = config.externalUrls,
                loadPreviewImage = config.loadPreviewImage,
                loadOriginalImage = config.loadOriginalImage,
                autoPlayVideos = config.autoPlayVideos,
                loopVideos = config.loopVideos,
                forceOriginalVideo = config.forceOriginalVideo,
                verboseLogging = config.verboseLogging,
                syncRemoteDeletions = config.syncRemoteDeletions,
                preferRemoteImages = config.preferRemoteImages,
                readOnlyMode = config.readOnlyMode,
                downloadRemoteEnabled = config.downloadRemoteEnabled,
                downloadVideos = config.downloadVideos,
                uploadBasePath = config.uploadBasePath,
                uploadVideosPath = config.uploadVideosPath
            )
    }
}
