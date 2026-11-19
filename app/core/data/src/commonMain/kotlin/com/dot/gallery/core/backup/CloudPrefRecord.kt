/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


// Albums, people, cloud preferences, and device UI write several record kinds into one section
// file, so each line carries a serial name discriminator.
@Serializable
sealed class CloudPrefRecord {
    @Serializable @kotlinx.serialization.SerialName("album_sync")
    data class AlbumSync(val value: CloudAlbumSyncRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("upload")
    data class Upload(val value: CloudUploadPrefRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("delete_local")
    data class DeleteLocal(val value: CloudDeleteLocalPrefRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("offline_pin")
    data class OfflinePin(val value: CloudOfflinePinRecord) : CloudPrefRecord()

    @Serializable @kotlinx.serialization.SerialName("free_space")
    data class FreeSpace(val value: FreeSpacePrefRecord) : CloudPrefRecord()
}
