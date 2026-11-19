/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class PrivateFolderRecord(
    val uri: String,
    val enabled: Boolean,
    val gateMode: String? = null
)
