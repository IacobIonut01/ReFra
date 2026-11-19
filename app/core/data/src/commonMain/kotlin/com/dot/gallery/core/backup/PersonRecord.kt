/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


@Serializable
data class PersonRecord(
    val id: String,
    val name: String = "",
    val birthDate: String = "",
    val hidden: Boolean = false,
    val thumbnailMedia: MediaRef? = null
)
