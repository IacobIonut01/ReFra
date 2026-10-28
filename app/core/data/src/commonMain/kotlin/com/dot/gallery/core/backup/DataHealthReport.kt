/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable

/** Result of one reconciliation pass, shown in the Backup & restore hub. */
@Serializable
data class DataHealthReport(
    val checkedAt: Long = 0L,
    val cleanRun: Boolean = false,
    val categories: Int = 0,
    val metadata: Int = 0,
    val embeddings: Int = 0,
    val captureTime: Int = 0,
    val faces: Int = 0,
    val collections: Int = 0,
    val editBackups: Int = 0,
    val scanned: Int = 0,
    val staleVaultCredentials: Int = 0,
    val error: String? = null
) {
    val total: Int
        get() = categories + metadata + embeddings + captureTime + faces +
            collections + editBackups + staleVaultCredentials

    val isEmpty: Boolean get() = total == 0
}
