/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/**
 * The Language packs screen. Rows are in catalog order; the screen groups them into On this device,
 * Suggested for your languages and More languages. [unavailableLanguages] are CLDR names of the
 * languages no pack reads, sorted.
 */
@Immutable
data class OcrPacksState(
    val downloadsAllowed: Boolean,
    val packs: List<OcrPackRow>,
    val unavailableLanguages: List<String>,
    val verification: OcrVerification? = null,
)
