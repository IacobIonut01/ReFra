/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.components

import androidx.compose.runtime.Stable
import com.dot.gallery.domain.model.UriMedia


/**
 * Flat, renderer-agnostic view of a category tile on the search screen —
 * the app shell maps its category-plus-thumbnail model into this.
 */
@Stable
data class CategoryCarouselItem(
    val id: Long,
    val name: String,
    val mediaCount: Int,
    val thumbnailMedia: UriMedia?,
)
