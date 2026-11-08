/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.timeline.components

/** Unknown totals stay indeterminate; stale or malformed worker counts cannot overfill the track. */
internal fun timelineBackupProgressFraction(processed: Int, total: Int): Float? =
    if (total <= 0) null else (processed.toFloat() / total).coerceIn(0f, 1f)
