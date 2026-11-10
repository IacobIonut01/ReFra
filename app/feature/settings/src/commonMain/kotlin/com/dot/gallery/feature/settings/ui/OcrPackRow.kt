/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Immutable

/**
 * One pack on the Language packs screen. [languages] are CLDR names in the UI language, already
 * sorted; [ownLanguages] are those among them the person uses, which stay visible when the list is
 * shortened. [suggested] is only a hint; nothing is preselected.
 */
@Immutable
data class OcrPackRow(
    val id: String,
    val languages: List<String>,
    val ownLanguages: Set<String> = emptySet(),
    val suggested: Boolean = false,
    val status: OcrPackStatus,
)
