/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class BackupJobProgress(
    val section: BackupSection? = null,
    val current: Int = 0,
    val total: Int = 0,
    val phase: String = ""
)
