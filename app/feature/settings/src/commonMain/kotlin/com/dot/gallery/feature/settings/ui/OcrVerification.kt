/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/** The result of Add model files: every picked file, and the packs that became ready. */
@Immutable
data class OcrVerification(
    val files: List<OcrVerifiedFile>,
    val readyPackIds: List<String>,
)
