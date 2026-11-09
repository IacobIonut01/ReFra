/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/** Something found in a photo, located by a quad in normalised display coordinates. */
@Immutable
sealed interface Insight {
    val kind: InsightKind
    val quad: ImageQuad
}
