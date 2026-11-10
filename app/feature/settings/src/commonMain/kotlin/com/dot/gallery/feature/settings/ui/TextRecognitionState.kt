/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/**
 * Intelligence Hub › Text recognition. [canEnable] is false until a pack is ready; it cannot be turned
 * on then, and the options that depend on it are inactive. [installedPackIds] are in catalog order.
 */
@Immutable
data class TextRecognitionState(
    val canEnable: Boolean,
    val enabled: Boolean,
    val onRequest: Boolean,
    val indexLibrary: Boolean,
    val installedPackIds: List<String>,
    val modelBytes: Long,
    val indexBytes: Long,
    val indexedPhotos: Int,
)
