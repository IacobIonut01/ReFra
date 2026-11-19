/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class MediaMetadataFlagsRecord(
    val isNightMode: Boolean = false,
    val isPanorama: Boolean = false,
    val isPhotosphere: Boolean = false,
    val isLongExposure: Boolean = false,
    val isMotionPhoto: Boolean = false
)
