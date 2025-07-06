/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.util

sealed class Screen(val route: String) {
    data object TimelineScreen : Screen("timeline_screen")
    data object AlbumsScreen : Screen("albums_screen")

    data object AlbumViewScreen : Screen("album_view_screen") {

        fun albumAndName() = "$route?albumId={albumId}&albumName={albumName}"

    }
    data object MediaViewScreen : Screen("media_screen") {

        fun idAndTarget() = "$route?mediaId={mediaId}&target={target}"

        fun idAndAlbum() = "$route?mediaId={mediaId}&albumId={albumId}"

        fun idAndAlbum(id: Long, albumId: Long) = "$route?mediaId=$id&albumId=$albumId"

        fun idAndQuery() = "$route?mediaId={mediaId}&query={query}"

        fun idAndCategory() = "$route?mediaId={mediaId}&category={category}"

        fun idAndCategory(id: Long, category: String) = "$route?mediaId=$id&category=$category"
    }

    data object TrashedScreen : Screen("trashed_screen")
    data object FavoriteScreen : Screen("favorite_screen")

    data object SettingsScreen : Screen("settings_screen")
    data object SettingsThemeScreen : Screen("settings_theme_screen")
    data object SettingsGeneralScreen : Screen("settings_general_screen")
    data object SettingsCustomizationScreen : Screen("settings_customization_screen")
    data object SettingsSmartFeaturesScreen : Screen("settings_smart_features_screen")

    data object IgnoredScreen : Screen("ignored_screen")
    data object IgnoredSetupScreen : Screen("ignored_setup_screen")

    data object SetupScreen: Screen("setup_screen")

    data object VaultScreen : Screen("vault_screen")

    data object LibraryScreen : Screen("library_screen")

    data object CategoriesScreen : Screen("categories_screen")

    data object CategoryViewScreen : Screen("category_view_screen") {

        fun category() = "$route?category={category}"

        fun category(string: String) = "$route?category=$string"

    }

    data object DateFormatScreen : Screen("date_format_screen")

    operator fun invoke() = route
}
