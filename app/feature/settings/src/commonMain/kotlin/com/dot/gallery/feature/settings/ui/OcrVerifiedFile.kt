/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/**
 * One picked file in the verification sheet. [packId] is the pack the file belongs to (or matched by
 * size, for altered files); null for the shared text finder and for unknown files.
 */
@Immutable
data class OcrVerifiedFile(
    val name: String,
    val result: OcrVerifiedResult,
    val packId: String? = null,
)
