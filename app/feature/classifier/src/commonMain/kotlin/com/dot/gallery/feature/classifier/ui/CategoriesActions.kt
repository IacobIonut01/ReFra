/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui

import androidx.compose.runtime.Immutable
import com.dot.gallery.domain.model.Category


/**
 * Callbacks [CategoriesContent] reports to the app shell. Route strings stay
 * app-side: parameterized destinations get dedicated lambdas.
 */
@Immutable
data class CategoriesActions(
    /** Open a category's media page. */
    val onOpenCategory: (categoryId: Long) -> Unit,
    /** Open the category editor in edit mode (actions sheet). */
    val onEditCategory: (categoryId: Long) -> Unit,
    /** Open the creation wizard (top-bar add, dashed tile, empty state). */
    val onCreateCategory: () -> Unit,
    /** Open the categories settings page (status banner). */
    val onOpenSettings: () -> Unit,
    /** Pin or unpin a category (actions sheet). */
    val onTogglePin: (categoryId: Long, pinned: Boolean) -> Unit = { _, _ -> },
    /** Delete a category after the confirmation dialog. */
    val onDeleteCategory: (categoryId: Long) -> Unit = {},
    /** Add one of [Category.DEFAULT_CATEGORIES] from the empty state. */
    val onAddSuggestion: (template: Category) -> Unit = {},
    /** Add every default category from the empty state. */
    val onAddAllSuggestions: () -> Unit = {},
    /** Stop the running classification. */
    val onPauseScan: () -> Unit = {},
    /** The cover picker opened for a category (null = closed), so the shell loads its photos. */
    val onCoverPickerChanged: (categoryId: Long?) -> Unit = {},
    /** Use a photo as the cover; null goes back to the automatic best match. */
    val onSetCover: (categoryId: Long, mediaId: Long?) -> Unit = { _, _ -> },
    /** Open Smart search setup when no search model is installed. */
    val onSetUpSmartSearch: () -> Unit = {},
)
