/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.coroutines.flow.StateFlow

/** Feature-facing data-health surface; implemented in `:app:commonApp`. */
interface DataHealthController {
    val report: StateFlow<DataHealthReport?>
    val autoClean: StateFlow<Boolean>

    suspend fun refresh()

    suspend fun setAutoClean(enabled: Boolean)

    /** Enqueues a reconciliation pass and waits for it to finish before refreshing [report]. */
    suspend fun cleanNow()
}
