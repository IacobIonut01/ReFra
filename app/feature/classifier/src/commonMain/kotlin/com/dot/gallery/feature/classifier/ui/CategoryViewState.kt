/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.runtime.Stable
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.UriMedia


/**
 * Snapshot for [CategoryViewContent]. The screen has two route shapes:
 * - legacy string route — [legacyCategory] holds the category name;
 * - id route — [categoryId] holds the id and [categoryName] is the resolved
 *   display name (loaded app-side once [CategoryViewActions.onLoadCategory]
 *   runs).
 */
@Stable
data class CategoryViewState(
    /** Non-null → legacy string route is active. */
    val legacyCategory: String? = null,
    val categoryId: Long = -1L,
    /** Resolved category name for the id route (`category?.name`). */
    val categoryName: String = "",
    /** Full category on the id route; drives the header (description, examples, strictness). */
    val category: Category? = null,
    /** Cover photo shown in the header; falls back to the category glyph. */
    val cover: UriMedia? = null,
    /** Header date range (first, last), already formatted app-side. */
    val dateRange: Pair<String, String>? = null,
    /** A user-visible category scan is still running. */
    val isScanning: Boolean = false,
    /** Scan progress in 0..1; null when unknown. */
    val scanProgress: Float? = null,
) {
    val isLegacy: Boolean get() = legacyCategory != null

    /** Title shown in the media screen app bar. */
    val displayName: String get() = legacyCategory ?: categoryName

    /** MediaScreen `target` — drives grid caching/shared-element keys. */
    val target: String get() =
        if (isLegacy) "category_${legacyCategory.orEmpty()}" else "category_id_$categoryId"
}
