/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

/** The parts of a lazy grid or list that drag-to-reorder needs. */
internal interface ReorderLayout {
    val items: List<ReorderItem>
    /** Visible area between the content paddings, in container coordinates. */
    val visibleStart: Float
    val visibleEnd: Float
    val firstVisibleIndex: Int
    val firstVisibleOffset: Int
    suspend fun scrollBy(pixels: Float): Float
    suspend fun scrollToItem(index: Int, offset: Int)
}
