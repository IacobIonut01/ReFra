/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.domain

/**
 * Navigation targets the help flow can request — resolved to concrete
 * `Screen.*` routes by the app-side wrapper (`toScreenRoute()`). The module
 * never hard-codes route strings.
 */
sealed interface HelpRoute {

    /** Static destinations backed by a `Screen` data object in the app. */
    enum class Target : HelpRoute {
        TIMELINE,
        ALBUMS,
        SEARCH,
        LIBRARY,
        CATEGORIES,
        CATEGORIES_SETTINGS,
        FAVORITES,
        TRASHED,
        IGNORED,
        LOCATIONS,
        VAULT,
        SETUP,
        SETTINGS,
        SETTINGS_APPEARANCE,
        SETTINGS_GENERAL,
        SETTINGS_NAVIGATION,
        SETTINGS_MEDIA_VIEWER,
        SETTINGS_TIMELINE_ALBUMS,
        SETTINGS_SECURITY,
        SETTINGS_SELECTION_ACTIONS,
        SETTINGS_SMART_FEATURES,
        SETTINGS_CLOUD_INDEXING,
        SETTINGS_BACKUP,
        COLOR_PALETTE,
        DATE_FORMAT,
        AI_MODELS_MANAGER,
        TEXT_RECOGNITION,
        EDIT_BACKUPS_VIEWER,
        SLIDESHOW_SETTINGS,
        STORY_CARDS_SETTINGS,
        DEVELOPER,
        CLOUD_ACCOUNTS,
        CLOUD_BACKUP_DASHBOARD,
        CLOUD_DESTINATIONS,
        SHARED_LINKS,
        FREE_UP_SPACE,
        PEOPLE_LIST,
        SYNC_STATUS,
        WHATS_NEW,
    }

    /** Internal help destination: a tip detail screen (tipId argument). */
    data class Tip(val tipId: String) : HelpRoute

    /** Internal help destination: a tutorial category screen (category argument). */
    data class Category(val category: HelpCategory) : HelpRoute

    /**
     * Opaque, already-resolved route string — used for harvested settings
     * entries whose route was registered by the owning screen at runtime.
     */
    data class Raw(val route: String) : HelpRoute
}
