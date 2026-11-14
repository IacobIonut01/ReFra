/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.dot.gallery.cloud.core.SyncState
import com.dot.gallery.domain.settings.SettingsKeys

/**
 * Per-grid hoist of the global flows every [MediaImage] cell would otherwise collect
 * individually (selection state, the favorite-icon preference, cloud sync states).
 *
 * Heavy grids (timeline, mosaic) collect these once at grid scope and provide them via
 * [LocalMediaCellState] so each cell becomes a pure reader — no per-cell
 * `collectAsStateWithLifecycle` coroutine/observer churn while cells recycle during a fast fling.
 */
@Immutable
data class MediaCellState(
    val selectionActive: Boolean,
    val selectedMedia: Set<Long>,
    val favoriteIconPosition: String,
    val cloudSyncStates: Map<Long, SyncState>,
    /** Local media ids that have at least one cloud backup (shows a small backup indicator). */
    val cloudBackedUpIds: Set<Long> = emptySet(),
    val resolutionBadges: Boolean = false,
    val resolutionBadgeMinMp: Int = SettingsKeys.resolutionBadgeMinMp.default,
)

/** Null by default: callers that don't provide it (search, picker) fall back to per-cell collection. */
val LocalMediaCellState = compositionLocalOf<MediaCellState?> { null }
