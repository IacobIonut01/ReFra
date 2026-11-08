/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.runtime.Immutable


/** Callbacks [CategoryViewContent] reports to the app shell. */
@Immutable
data class CategoryViewActions(
    /** The route arg is unusable (empty legacy name / non-positive id) → navigate up. */
    val onInvalidCategory: () -> Unit,
    /** Load media + details for the id route (maps to `setCategoryId`). Unused on the legacy route. */
    val onLoadCategory: (categoryId: Long) -> Unit,
    /** Header Edit, Tune and Add example photos → CategoryEditorScreen.edit (app-side). */
    val onEditCategory: (categoryId: Long) -> Unit,
    /** Open media in the viewer (`idAndCategory`/`idAndCategoryId` routes — app-side). */
    val onOpenMedia: (mediaId: Long) -> Unit,
    /** Search-bar click → SearchScreen route (app-side). */
    val onSearchClick: () -> Unit,
    /** Pin or unpin the category from the header chips. */
    val onTogglePin: (categoryId: Long, pinned: Boolean) -> Unit = { _, _ -> },
    /** Delete the category after confirmation, then leave the page. */
    val onDeleteCategory: (categoryId: Long) -> Unit = {},
    /** Use a photo as the cover; null goes back to the automatic best match. */
    val onSetCover: (categoryId: Long, mediaId: Long?) -> Unit = { _, _ -> },
    /** Save a new strictness threshold (no-matches "Loosen strictness"). */
    val onChangeThreshold: (categoryId: Long, threshold: Float) -> Unit = { _, _ -> },
)
