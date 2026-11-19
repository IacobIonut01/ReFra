/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


// Albums, people, cloud preferences, and device UI write several record kinds into one section
// file, so each line carries a serial name discriminator.
@Serializable
sealed class DeviceUiRecord {
    @Serializable @kotlinx.serialization.SerialName("grid")
    data class Grid(val value: GridSizeRecord) : DeviceUiRecord()

    @Serializable @kotlinx.serialization.SerialName("widget")
    data class Widget(val value: WidgetRecord) : DeviceUiRecord()

    @Serializable @kotlinx.serialization.SerialName("private_folder")
    data class PrivateFolder(val value: PrivateFolderRecord) : DeviceUiRecord()
}
