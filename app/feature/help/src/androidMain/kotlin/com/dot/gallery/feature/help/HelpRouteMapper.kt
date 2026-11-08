/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help

import com.dot.gallery.feature.help.domain.HelpRoute
import com.dot.gallery.core.presentation.util.Screen

/**
 * Resolves a module [HelpRoute] to the concrete `Screen.*` route string the
 * app's event handler navigates with.
 */
fun HelpRoute.toScreenRoute(): String = when (this) {
    is HelpRoute.Tip -> Screen.TutorialDetailScreen.tipId(tipId)
    is HelpRoute.Category -> Screen.TutorialCategoryScreen.category(category.name)
    is HelpRoute.Raw -> route
    is HelpRoute.Target -> when (this) {
        HelpRoute.Target.TIMELINE -> Screen.TimelineScreen()
        HelpRoute.Target.ALBUMS -> Screen.LibraryScreen()
        HelpRoute.Target.SEARCH -> Screen.SearchScreen()
        HelpRoute.Target.LIBRARY -> Screen.LibraryScreen()
        HelpRoute.Target.CATEGORIES -> Screen.CategoriesScreen()
        HelpRoute.Target.CATEGORIES_SETTINGS -> Screen.CategoriesSettingsScreen()
        HelpRoute.Target.FAVORITES -> Screen.FavoriteScreen()
        HelpRoute.Target.TRASHED -> Screen.TrashedScreen()
        HelpRoute.Target.IGNORED -> Screen.IgnoredScreen()
        HelpRoute.Target.LOCATIONS -> Screen.LocationsScreen()
        HelpRoute.Target.VAULT -> Screen.VaultScreen()
        HelpRoute.Target.SETUP -> Screen.SetupScreen()
        HelpRoute.Target.SETTINGS -> Screen.SettingsScreen()
        HelpRoute.Target.SETTINGS_APPEARANCE -> Screen.SettingsAppearanceScreen()
        HelpRoute.Target.SETTINGS_GENERAL -> Screen.SettingsGeneralScreen()
        HelpRoute.Target.SETTINGS_NAVIGATION -> Screen.SettingsNavigationScreen()
        HelpRoute.Target.SETTINGS_MEDIA_VIEWER -> Screen.SettingsMediaViewerScreen()
        HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS -> Screen.SettingsTimelineAlbumsScreen()
        HelpRoute.Target.SETTINGS_SECURITY -> Screen.SettingsSecurityScreen()
        HelpRoute.Target.SETTINGS_SELECTION_ACTIONS -> Screen.SettingsSelectionActionsScreen()
        HelpRoute.Target.SETTINGS_SMART_FEATURES -> Screen.SettingsSmartFeaturesScreen()
        HelpRoute.Target.SETTINGS_CLOUD_INDEXING -> Screen.SettingsCloudIndexingScreen()
        HelpRoute.Target.SETTINGS_BACKUP -> Screen.SettingsBackupScreen()
        HelpRoute.Target.COLOR_PALETTE -> Screen.ColorPaletteScreen()
        HelpRoute.Target.DATE_FORMAT -> Screen.DateFormatScreen()
        HelpRoute.Target.AI_MODELS_MANAGER -> Screen.AIModelsManagerScreen()
        HelpRoute.Target.EDIT_BACKUPS_VIEWER -> Screen.EditBackupsViewerScreen()
        HelpRoute.Target.SLIDESHOW_SETTINGS -> Screen.SlideshowSettingsScreen()
        HelpRoute.Target.STORY_CARDS_SETTINGS -> Screen.StoryCardsSettingsScreen()
        HelpRoute.Target.DEVELOPER -> Screen.DeveloperScreen()
        HelpRoute.Target.CLOUD_ACCOUNTS -> Screen.CloudAccountsScreen()
        HelpRoute.Target.CLOUD_BACKUP_DASHBOARD -> Screen.CloudBackupDashboardScreen()
        HelpRoute.Target.CLOUD_DESTINATIONS -> Screen.CloudDestinationsScreen()
        HelpRoute.Target.SHARED_LINKS -> Screen.SharedLinksScreen()
        HelpRoute.Target.FREE_UP_SPACE -> Screen.FreeUpSpaceScreen()
        HelpRoute.Target.PEOPLE_LIST -> Screen.PeopleListScreen()
        HelpRoute.Target.SYNC_STATUS -> Screen.CloudBackupDashboardScreen()
        HelpRoute.Target.WHATS_NEW -> Screen.WhatsNewScreen()
    }
}
