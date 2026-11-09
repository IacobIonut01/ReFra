/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/**
 * Identifies one analysed image: the media id plus a version that changes when the file is
 * rewritten in place (the viewer's `timestamp:size` preview version).
 */
@Immutable
data class InsightKey(val mediaId: Long, val version: String)
