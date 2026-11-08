/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import kotlinx.serialization.Serializable


@Serializable
data class CloudLibraryState(
    val hasCloud: Boolean = false,
    val isConnected: Boolean = false,
    val connectedCapabilities: Set<ProviderCapability> = emptySet(),
    val hasCachedMedia: Boolean = false,
    val archivedCount: Int = 0,
    val sharedLinkCount: Int = 0,
    val totalCloudCount: Int = 0,
    val people: List<PersonInfo> = emptyList(),
    /**
     * Hidden local people — kept outside [people] so the Library row stays
     * visible-only while the section header remains reachable.
     */
    val hiddenPeopleCount: Int = 0,
    val hasArchive: Boolean = false,
    val hasMemories: Boolean = false,
    val hasShareLink: Boolean = false,
    val hasPeople: Boolean = false,
    val hasMap: Boolean = false
)
