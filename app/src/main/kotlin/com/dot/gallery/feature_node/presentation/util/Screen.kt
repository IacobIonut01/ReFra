/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
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

        fun idAndQuery() = "${route}_search?mediaId={mediaId}"

        fun idAndQuery(id: Long) = "${route}_search?mediaId=$id"

        fun idAndCategory() = "$route?mediaId={mediaId}&category={category}"

        fun idAndCategory(id: Long, category: String) = "$route?mediaId=$id&category=$category"
        
        // New ID-based category navigation
        fun idAndCategoryId() = "$route?mediaId={mediaId}&categoryId={categoryId}"

        fun idAndCategoryId(id: Long, categoryId: Long) = "$route?mediaId=$id&categoryId=$categoryId"

        fun idAndLocation() = "$route?mediaId={mediaId}&gpsLocationNameCity={gpsLocationNameCity}&gpsLocationNameCountry={gpsLocationNameCountry}"

        fun idAndLocation(id: Long, gpsLocationNameCity: String, gpsLocationNameCountry: String) = "$route?mediaId=$id&gpsLocationNameCity=$gpsLocationNameCity&gpsLocationNameCountry=$gpsLocationNameCountry"
    }

    data object LocationTimelineScreen : Screen("location_timeline_screen") {

        fun location() = "$route?gpsLocationNameCity={gpsLocationNameCity}&gpsLocationNameCountry={gpsLocationNameCountry}"

        fun location(gpsLocationNameCity: String, gpsLocationNameCountry: String) = "$route?gpsLocationNameCity=$gpsLocationNameCity&gpsLocationNameCountry=$gpsLocationNameCountry"

    }

    data object TrashedScreen : Screen("trashed_screen")
    data object FavoriteScreen : Screen("favorite_screen")

    data object SettingsScreen : Screen("settings_screen")
    data object SettingsThemeScreen : Screen("settings_theme_screen")
    data object ColorPaletteScreen : Screen("color_palette_screen")
    data object SettingsGeneralScreen : Screen("settings_general_screen")
    data object SettingsCustomizationScreen : Screen("settings_customization_screen")
    data object SettingsSmartFeaturesScreen : Screen("settings_smart_features_screen")
    data object EditBackupsViewerScreen : Screen("edit_backups_viewer_screen")

    data object IgnoredScreen : Screen("ignored_screen")

    data object SetupScreen: Screen("setup_screen")

    data object VaultScreen : Screen("vault_screen")

    data object LibraryScreen : Screen("library_screen")

    data object CategoriesScreen : Screen("categories_screen")
    
    data object CategoriesSettingsScreen : Screen("categories_settings_screen")
    
    data object LocationsScreen : Screen("locations_screen")

    data object CategoryViewScreen : Screen("category_view_screen") {

        fun category() = "$route?category={category}"

        fun category(string: String) = "$route?category=$string"
        
        // New ID-based routing for the new category system
        fun categoryId() = "$route?categoryId={categoryId}"
        
        fun categoryId(id: Long) = "$route?categoryId=$id"

    }
    
    data object CategoryEditScreen : Screen("category_edit_screen") {
        fun categoryId() = "$route?categoryId={categoryId}"
        
        fun categoryId(id: Long) = "$route?categoryId=$id"
    }

    data object AddCategoryScreen : Screen("add_category_screen")
    
    data object EditCategoryScreen : Screen("edit_category_screen") {
        fun route() = "$route?categoryId={categoryId}"
        fun categoryId(id: Long) = "$route?categoryId=$id"
    }

    data object DateFormatScreen : Screen("date_format_screen")

    data object SearchScreen : Screen("search_screen")

    data object MetadataViewScreen : Screen("metadata_view_screen") {

        fun uriAndType() = "$route?mediaUri={mediaUri}&isVideo={isVideo}"

        fun uriAndType(mediaUri: String, isVideo: Boolean) =
            "$route?mediaUri=$mediaUri&isVideo=$isVideo"
    }

    operator fun invoke() = route
}
