/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


// Albums, people, cloud preferences, and device UI write several record kinds into one section
// file, so each line carries a serial name discriminator.
@Serializable
sealed class AlbumRecord {
    @Serializable @kotlinx.serialization.SerialName("pin")
    data class Pin(val value: AlbumPinRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("lock")
    data class Lock(val value: AlbumLockRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("ignored")
    data class Ignored(val value: IgnoredAlbumRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("cover")
    data class Cover(val value: AlbumCoverRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("group")
    data class Group(val value: AlbumGroupRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("section")
    data class Section(val value: AlbumSectionRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("merged")
    data class Merged(val value: MergedSubfolderRecord) : AlbumRecord()

    @Serializable @kotlinx.serialization.SerialName("timeline")
    data class Timeline(val value: TimelineSettingsRecord) : AlbumRecord()
}
