/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.policy

/**
 * Packs to mark as "Suggested" for the person's languages. Only a hint: nothing is selected or
 * downloaded from it. [unsupported] lists language keys no pack can read (shown, never substituted).
 */
data class OcrPackSuggestion(
    val packIds: Set<String>,
    val unsupported: Set<String>,
)
