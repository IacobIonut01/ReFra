/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui


data class BackupHubActions(
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onCleanNow: () -> Unit,
    val onAutoCleanChange: (Boolean) -> Unit
)
