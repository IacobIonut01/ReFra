/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.scrollbar

import androidx.compose.runtime.Stable

/**
 * A single coalesced scroll request. The owning composable observes
 * [GridScrollbarController.scrollTarget] through a `snapshotFlow` + `collectLatest`
 * so that a fast thumb drag only ever commits the *latest* position instead of
 * queuing a `scrollToItem` per pointer event (the root cause of fast-scroll lag).
 */
@Stable
internal data class GridScrollTarget(
    /** Where the drag wants the top of the viewport, in item units (see the controller). */
    val progress: Float,
)
