/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.albums.ui.components

import androidx.compose.ui.geometry.Rect

/** Visible item geometry in the lazy container's own coordinates. */
internal data class ReorderItem(val key: Any, val index: Int, val bounds: Rect)
