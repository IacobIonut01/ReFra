/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import com.dot.gallery.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderType(val displayName: String, val isRemote: Boolean) {
    IMMICH("Immich", isRemote = true),
    OWNCLOUD("ownCloud", isRemote = true),
    LOCAL_PEOPLE("On-Device Faces", isRemote = false),
    LOCAL_OCR("On-Device OCR", isRemote = false),
    LOCAL_CLIP("On-Device Search", isRemote = false);

    val isIncludedInBuild: Boolean
        get() = when (this) {
            IMMICH -> BuildConfig.IMMICH_ENABLED
            OWNCLOUD -> BuildConfig.OWNCLOUD_ENABLED
            else -> true
        }

    companion object {
        fun remoteTypes(): List<ProviderType> = entries.filter { it.isRemote }
        fun availableRemoteTypes(): List<ProviderType> = entries.filter { it.isRemote && it.isIncludedInBuild }
        fun hasAnyRemoteProvider(): Boolean = entries.any { it.isRemote && it.isIncludedInBuild }
    }
}
