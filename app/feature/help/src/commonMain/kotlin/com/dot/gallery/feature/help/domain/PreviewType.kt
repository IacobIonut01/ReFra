/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.domain

/**
 * The kind of live preview a [TutorialPage] renders. The concrete composable
 * per type is supplied by the platform wrapper (it embeds real app screens on
 * Android) — the feature module only owns the enum and the dispatch slot.
 */
enum class PreviewType {
    AI_SEARCH,
    AI_CATEGORIES,
    TIMELINE_GRID,
    ALBUM_GRID,
    MEDIA_VIEWER,
    PHOTO_EDITOR_CROP,
    PHOTO_EDITOR_FILTERS,
    PHOTO_EDITOR_MARKUP,
    VAULT_LOCK,
    SEARCH_BAR,
    FAVORITES_GRID,
    TRASH_GRID,
    THEME_PICKER,
    COLOR_PALETTE,
    LOCATION_MAP,
    EXIF_VIEWER,
    PINCH_ZOOM_GRID,
    COLLECTION_VIEW,
    NAV_BAR_PREVIEW,
    SETTINGS_GENERAL,
    SLIDESHOW,
    MOTION_PHOTO,
    VIDEO_CONTROLS,
    SUBJECT_CUTOUT,
    CASTING,
    PANORAMA,
    CODE_SCAN,
    NONE
}
