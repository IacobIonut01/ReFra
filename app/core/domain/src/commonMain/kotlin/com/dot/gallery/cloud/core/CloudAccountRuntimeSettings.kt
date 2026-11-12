/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update


/** Runtime-only viewer and advanced preferences for one cloud account. */
data class CloudAccountRuntimeSettings(
    val verboseLogging: Boolean = false,
    val preferRemoteImages: Boolean = false,
    val readOnlyMode: Boolean = false,
    val loadPreviewImage: Boolean = true,
    val loadOriginalImage: Boolean = false,
    val autoPlayVideos: Boolean = true,
    val forceOriginalVideo: Boolean = false,
)