/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.Serializable


/** Describes one streamed section file inside a schema-v3 archive. */
@Serializable
data class SectionDescriptor(
    val file: String = "",
    val count: Int = 0,
    /** Lower-case SHA-256 of the section file bytes. */
    val sha256: String = "",
    val bytes: Long = 0L
)
