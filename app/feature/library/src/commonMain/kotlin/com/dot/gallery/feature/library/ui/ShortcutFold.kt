/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui


/** A folded grid: the first [count] tiles stay visible, followed by a "Show more" tile of [toggle] size. */
data class ShortcutFold(val count: Int, val toggle: LibraryShortcutSize)
