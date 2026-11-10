/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

/** What the text indexer may run with right now; [active] only with both switches on and a ready pack. */
internal data class OcrIndexSettings(val enabled: Boolean, val indexLibrary: Boolean, val packs: List<String>) {
    val active: Boolean get() = enabled && indexLibrary && packs.isNotEmpty()
}
