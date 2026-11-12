/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import com.dot.gallery.core.presentation.components.HomeShortcutSupport
import com.dot.gallery.domain.model.AlbumSection

data class SettingsTimelineAlbumsState(
    /** `SdkCompat.supportsFavorites` — gates the favorite-icon + search-bar-fav rows. */
    val supportsFavorites: Boolean = true,
    /** Which choices the Timeline shortcut slot offers on this platform. */
    val homeShortcutSupport: HomeShortcutSupport = HomeShortcutSupport(favorites = supportsFavorites),
    /** `AlbumsViewModel.sectionsFlow` — rendered by the album-sections detail pane. */
    val albumSections: List<AlbumSection> = emptyList(),
    /** sectionId -> album count, derived from `LocalMediaDistributor.albumsFlow`. */
    val albumCountBySection: Map<Long, Int> = emptyMap(),
)
