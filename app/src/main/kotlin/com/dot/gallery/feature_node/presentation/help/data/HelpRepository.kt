/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.ZoomIn
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.Screen

object HelpRepository {

    fun getAllTips(): List<HelpTip> = ALL_TIPS
    fun getTipsByCategory(category: HelpCategory): List<HelpTip> = ALL_TIPS.filter { it.category == category }
    fun getTip(id: String): HelpTip? = ALL_TIPS.find { it.id == id }
    fun getFeaturedTips(): List<HelpTip> = listOf("search_ai", "ai_categories").mapNotNull { getTip(it) }

    /** Changelog is loaded from the assets/changelog markdown files via [ChangelogRepository]. */
    fun getCurrentRelease(context: Context): ReleaseNotes? = ChangelogRepository.getCurrent(context)
    fun getAllReleases(context: Context): List<ReleaseNotes> = ChangelogRepository.getAll(context)

    fun getGetStartedCategories() = listOf(
        HelpCategory.GET_STARTED_BASICS, HelpCategory.GET_STARTED_NAVIGATION, HelpCategory.GET_STARTED_PERSONALIZATION
    )
    fun getMakeMostCategories() = listOf(
        HelpCategory.TIMELINE_ALBUMS, HelpCategory.VIEWING, HelpCategory.VIEWER_ACTIONS,
        HelpCategory.VIEWER_SETTINGS, HelpCategory.EDITING, HelpCategory.SEARCH,
        HelpCategory.AI_FEATURES, HelpCategory.ALBUMS, HelpCategory.VAULT,
        HelpCategory.CLOUD_SYNC
    )
    fun getExploreMoreCategories() = listOf(
        HelpCategory.FAVORITES_TRASH, HelpCategory.LOCATIONS, HelpCategory.METADATA,
        HelpCategory.SETTINGS_APPEARANCE, HelpCategory.SETTINGS_GENERAL, HelpCategory.SETTINGS_NAVIGATION,
        HelpCategory.SETTINGS_SMART, HelpCategory.SETTINGS_SECURITY, HelpCategory.GESTURES,
        HelpCategory.SELECTION_ACTIONS, HelpCategory.ACCESSIBILITY
    )

    // region Get Started: Basics
    private val BASICS_TIPS = listOf(
        HelpTip(
            id = "basics_timeline", title = R.string.help_tip_basics_timeline_title,
            subtitle = R.string.help_tip_basics_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_basics_timeline_p1_title, description = R.string.help_tip_basics_timeline_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_basics_timeline_p2_title, description = R.string.help_tip_basics_timeline_p2_desc, steps = listOf(R.string.help_tip_basics_timeline_p2_s1, R.string.help_tip_basics_timeline_p2_s2, R.string.help_tip_basics_timeline_p2_s3, R.string.help_tip_basics_timeline_p2_s4, R.string.help_tip_basics_timeline_p2_s5), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_timeline, Icons.Outlined.Collections, Screen.TimelineScreen()))
        ),
        HelpTip(
            id = "basics_view_media", title = R.string.help_tip_basics_view_media_title,
            subtitle = R.string.help_tip_basics_view_media_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_basics_view_media_p1_title, description = R.string.help_tip_basics_view_media_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_basics_view_media_p2_title, description = R.string.help_tip_basics_view_media_p2_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_basics_view_media_p3_title, description = R.string.help_tip_basics_view_media_p3_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_select_media", title = R.string.help_tip_basics_select_media_title,
            subtitle = R.string.help_tip_basics_select_media_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_basics_select_media_p1_title, description = R.string.help_tip_basics_select_media_p1_desc, steps = listOf(R.string.help_tip_basics_select_media_p1_s1, R.string.help_tip_basics_select_media_p1_s2, R.string.help_tip_basics_select_media_p1_s3, R.string.help_tip_basics_select_media_p1_s4), previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_basics_select_media_p2_title, description = R.string.help_tip_basics_select_media_p2_desc)
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_share", title = R.string.help_tip_basics_share_title,
            subtitle = R.string.help_tip_basics_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_basics_share_p1_title, description = R.string.help_tip_basics_share_p1_desc, steps = listOf(R.string.help_tip_basics_share_p1_s1, R.string.help_tip_basics_share_p1_s2, R.string.help_tip_basics_share_p1_s3, R.string.help_tip_basics_share_p1_s4))
            ), sinceVersion = "4.0.0"
        ),
        HelpTip(
            id = "basics_copy_clipboard", title = R.string.help_tip_basics_copy_clipboard_title,
            subtitle = R.string.help_tip_basics_copy_clipboard_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections),
            category = HelpCategory.GET_STARTED_BASICS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_basics_copy_clipboard_p1_title, description = R.string.help_tip_basics_copy_clipboard_p1_desc, steps = listOf(R.string.help_tip_basics_copy_clipboard_p1_s1, R.string.help_tip_basics_copy_clipboard_p1_s2, R.string.help_tip_basics_copy_clipboard_p1_s3, R.string.help_tip_basics_copy_clipboard_p1_s4))
            ), sinceVersion = "4.1.1"
        ),
        HelpTip(id = "basics_setup", title = R.string.help_tip_basics_setup_title, subtitle = R.string.help_tip_basics_setup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_BASICS,
            deepLink = Screen.SetupScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_basics_setup_p1_title, description = R.string.help_tip_basics_setup_p1_desc)), sinceVersion = "5.1.0")
    )
    // endregion

    // region Get Started: Navigation
    private val NAVIGATION_TIPS = listOf(
        HelpTip(id = "nav_bottom_bar", title = R.string.help_tip_nav_bottom_bar_title, subtitle = R.string.help_tip_nav_bottom_bar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_bottom_bar_p1_title, description = R.string.help_tip_nav_bottom_bar_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_nav_bottom_bar_p2_title, description = R.string.help_tip_nav_bottom_bar_p2_desc)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_albums", title = R.string.help_tip_nav_albums_title, subtitle = R.string.help_tip_nav_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_albums_p1_title, description = R.string.help_tip_nav_albums_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = R.string.help_tip_nav_albums_p2_title, description = R.string.help_tip_nav_albums_p2_desc, steps = listOf(R.string.help_tip_nav_albums_p2_s1, R.string.help_tip_nav_albums_p2_s2, R.string.help_tip_nav_albums_p2_s3, R.string.help_tip_nav_albums_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_albums, Icons.Outlined.Collections, Screen.AlbumsScreen()))),
        HelpTip(id = "nav_search", title = R.string.help_tip_nav_search_title, subtitle = R.string.help_tip_nav_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_search_p1_title, description = R.string.help_tip_nav_search_p1_desc, previewType = PreviewType.SEARCH_BAR),
                TutorialPage(title = R.string.help_tip_nav_search_p2_title, description = R.string.help_tip_nav_search_p2_desc, previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_search, Icons.Outlined.ImageSearch, Screen.SearchScreen()))),
        HelpTip(id = "nav_old_navbar", title = R.string.help_tip_nav_old_navbar_title, subtitle = R.string.help_tip_nav_old_navbar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_old_navbar_p1_title, description = R.string.help_tip_nav_old_navbar_p1_desc, steps = listOf(R.string.help_tip_nav_old_navbar_p1_s1, R.string.help_tip_nav_old_navbar_p1_s2), previewType = PreviewType.NAV_BAR_PREVIEW)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_library", title = R.string.help_tip_nav_library_title, subtitle = R.string.help_tip_nav_library_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_library_p1_title, description = R.string.help_tip_nav_library_p1_desc)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_library, Icons.Outlined.PhotoLibrary, Screen.LibraryScreen()))),
        HelpTip(id = "nav_launch_screen", title = R.string.help_tip_nav_launch_screen_title, subtitle = R.string.help_tip_nav_launch_screen_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_launch_screen_p1_title, description = R.string.help_tip_nav_launch_screen_p1_desc, steps = listOf(R.string.help_tip_nav_launch_screen_p1_s1, R.string.help_tip_nav_launch_screen_p1_s2, R.string.help_tip_nav_launch_screen_p1_s3))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "nav_auto_hide_bars", title = R.string.help_tip_nav_auto_hide_bars_title, subtitle = R.string.help_tip_nav_auto_hide_bars_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GET_STARTED_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_nav_auto_hide_bars_p1_title, description = R.string.help_tip_nav_auto_hide_bars_p1_desc, steps = listOf(R.string.help_tip_nav_auto_hide_bars_p1_s1, R.string.help_tip_nav_auto_hide_bars_p1_s2, R.string.help_tip_nav_auto_hide_bars_p1_s3))
            ), sinceVersion = "4.0.0")
    )
    // endregion

    // region Get Started: Personalization
    private val PERSONALIZATION_TIPS = listOf(
        HelpTip(id = "personalize_theme", title = R.string.help_tip_personalize_theme_title, subtitle = R.string.help_tip_personalize_theme_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_personalize_theme_p1_title, description = R.string.help_tip_personalize_theme_p1_desc, previewType = PreviewType.THEME_PICKER),
                TutorialPage(title = R.string.help_tip_personalize_theme_p2_title, description = R.string.help_tip_personalize_theme_p2_desc, steps = listOf(R.string.help_tip_personalize_theme_p2_s1, R.string.help_tip_personalize_theme_p2_s2, R.string.help_tip_personalize_theme_p2_s3), previewType = PreviewType.THEME_PICKER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_colors", title = R.string.help_tip_personalize_colors_title, subtitle = R.string.help_tip_personalize_colors_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.ColorPaletteScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_personalize_colors_p1_title, description = R.string.help_tip_personalize_colors_p1_desc, previewType = PreviewType.COLOR_PALETTE),
                TutorialPage(title = R.string.help_tip_personalize_colors_p2_title, description = R.string.help_tip_personalize_colors_p2_desc, steps = listOf(R.string.help_tip_personalize_colors_p2_s1, R.string.help_tip_personalize_colors_p2_s2, R.string.help_tip_personalize_colors_p2_s3, R.string.help_tip_personalize_colors_p2_s4), previewType = PreviewType.COLOR_PALETTE)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "personalize_grid", title = R.string.help_tip_personalize_grid_title, subtitle = R.string.help_tip_personalize_grid_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomIn), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_personalize_grid_p1_title, description = R.string.help_tip_personalize_grid_p1_desc, previewType = PreviewType.PINCH_ZOOM_GRID),
                TutorialPage(title = R.string.help_tip_personalize_grid_p2_title, description = R.string.help_tip_personalize_grid_p2_desc, previewType = PreviewType.PINCH_ZOOM_GRID)
            ), sinceVersion = "4.0.1"),
        HelpTip(id = "personalize_amoled", title = R.string.help_tip_personalize_amoled_title, subtitle = R.string.help_tip_personalize_amoled_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_personalize_amoled_p1_title, description = R.string.help_tip_personalize_amoled_p1_desc, steps = listOf(R.string.help_tip_personalize_amoled_p1_s1, R.string.help_tip_personalize_amoled_p1_s2, R.string.help_tip_personalize_amoled_p1_s3, R.string.help_tip_personalize_amoled_p1_s4), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_dark_mode", title = R.string.help_tip_personalize_dark_mode_title, subtitle = R.string.help_tip_personalize_dark_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_personalize_dark_mode_p1_title, description = R.string.help_tip_personalize_dark_mode_p1_desc, previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_blur", title = R.string.help_tip_personalize_blur_title, subtitle = R.string.help_tip_personalize_blur_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_personalize_blur_p1_title, description = R.string.help_tip_personalize_blur_p1_desc, steps = listOf(R.string.help_tip_personalize_blur_p1_s1, R.string.help_tip_personalize_blur_p1_s2, R.string.help_tip_personalize_blur_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_shared_elements", title = R.string.help_tip_personalize_shared_elements_title, subtitle = R.string.help_tip_personalize_shared_elements_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_personalize_shared_elements_p1_title, description = R.string.help_tip_personalize_shared_elements_p1_desc, steps = listOf(R.string.help_tip_personalize_shared_elements_p1_s1, R.string.help_tip_personalize_shared_elements_p1_s2, R.string.help_tip_personalize_shared_elements_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "personalize_app_name", title = R.string.help_tip_personalize_app_name_title, subtitle = R.string.help_tip_personalize_app_name_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.GET_STARTED_PERSONALIZATION,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_personalize_app_name_p1_title, description = R.string.help_tip_personalize_app_name_p1_desc, steps = listOf(R.string.help_tip_personalize_app_name_p1_s1, R.string.help_tip_personalize_app_name_p1_s2, R.string.help_tip_personalize_app_name_p1_s3, R.string.help_tip_personalize_app_name_p1_s4))), sinceVersion = "4.1.1")
    )
    // endregion

    // region Timeline & Albums
    private val TIMELINE_ALBUM_TIPS = listOf(
        HelpTip(id = "timeline_group_month", title = R.string.help_tip_timeline_group_month_title, subtitle = R.string.help_tip_timeline_group_month_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(TutorialPage(title = R.string.help_tip_timeline_group_month_p1_title, description = R.string.help_tip_timeline_group_month_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "timeline_layout_type", title = R.string.help_tip_timeline_layout_type_title, subtitle = R.string.help_tip_timeline_layout_type_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_timeline_layout_type_p1_title, description = R.string.help_tip_timeline_layout_type_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_timeline_layout_type_p2_title, description = R.string.help_tip_timeline_layout_type_p2_desc, steps = listOf(R.string.help_tip_timeline_layout_type_p2_s1, R.string.help_tip_timeline_layout_type_p2_s2, R.string.help_tip_timeline_layout_type_p2_s3, R.string.help_tip_timeline_layout_type_p2_s4), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "timeline_group_similar", title = R.string.help_tip_timeline_group_similar_title, subtitle = R.string.help_tip_timeline_group_similar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_timeline_group_similar_p1_title, description = R.string.help_tip_timeline_group_similar_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_timeline_group_similar_p2_title, description = R.string.help_tip_timeline_group_similar_p2_desc, steps = listOf(R.string.help_tip_timeline_group_similar_p2_s1, R.string.help_tip_timeline_group_similar_p2_s2, R.string.help_tip_timeline_group_similar_p2_s3, R.string.help_tip_timeline_group_similar_p2_s4))
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "timeline_gif_animation", title = R.string.help_tip_timeline_gif_animation_title, subtitle = R.string.help_tip_timeline_gif_animation_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_timeline_gif_animation_p1_title, description = R.string.help_tip_timeline_gif_animation_p1_desc, steps = listOf(R.string.help_tip_timeline_gif_animation_p1_s1, R.string.help_tip_timeline_gif_animation_p1_s2, R.string.help_tip_timeline_gif_animation_p1_s3))), sinceVersion = "4.1.2"),
        HelpTip(id = "albums_hide_timeline", title = R.string.help_tip_albums_hide_timeline_title, subtitle = R.string.help_tip_albums_hide_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_albums_hide_timeline_p1_title, description = R.string.help_tip_albums_hide_timeline_p1_desc, steps = listOf(R.string.help_tip_albums_hide_timeline_p1_s1, R.string.help_tip_albums_hide_timeline_p1_s2, R.string.help_tip_albums_hide_timeline_p1_s3), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_merge_by_name", title = R.string.help_tip_albums_merge_by_name_title, subtitle = R.string.help_tip_albums_merge_by_name_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_albums_merge_by_name_p1_title, description = R.string.help_tip_albums_merge_by_name_p1_desc, steps = listOf(R.string.help_tip_albums_merge_by_name_p1_s1, R.string.help_tip_albums_merge_by_name_p1_s2, R.string.help_tip_albums_merge_by_name_p1_s3), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.2.0"),
        HelpTip(id = "timeline_modified_date", title = R.string.help_tip_timeline_modified_date_title, subtitle = R.string.help_tip_timeline_modified_date_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarMonth), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_timeline_modified_date_p1_title, description = R.string.help_tip_timeline_modified_date_p1_desc, steps = listOf(R.string.help_tip_timeline_modified_date_p1_s1, R.string.help_tip_timeline_modified_date_p1_s2, R.string.help_tip_timeline_modified_date_p1_s3))), sinceVersion = "5.1.4"),
        HelpTip(id = "timeline_date_header", title = R.string.help_tip_timeline_date_header_title, subtitle = R.string.help_tip_timeline_date_header_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_timeline_date_header_p1_title, description = R.string.help_tip_timeline_date_header_p1_desc, steps = listOf(R.string.help_tip_timeline_date_header_p1_s1, R.string.help_tip_timeline_date_header_p1_s2, R.string.help_tip_timeline_date_header_p1_s3))), sinceVersion = "4.1.0"),
        HelpTip(id = "timeline_fav_icon", title = R.string.help_tip_timeline_fav_icon_title, subtitle = R.string.help_tip_timeline_fav_icon_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_timeline_fav_icon_p1_title, description = R.string.help_tip_timeline_fav_icon_p1_desc, steps = listOf(R.string.help_tip_timeline_fav_icon_p1_s1, R.string.help_tip_timeline_fav_icon_p1_s2, R.string.help_tip_timeline_fav_icon_p1_s3))), sinceVersion = "4.1.1"),
        HelpTip(id = "timeline_filter", title = R.string.help_tip_timeline_filter_title, subtitle = R.string.help_tip_timeline_filter_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FilterList), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_timeline_filter_p1_title, description = R.string.help_tip_timeline_filter_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_timeline_filter_p2_title, description = R.string.help_tip_timeline_filter_p2_desc, steps = listOf(R.string.help_tip_timeline_filter_p2_s1, R.string.help_tip_timeline_filter_p2_s2, R.string.help_tip_timeline_filter_p2_s3, R.string.help_tip_timeline_filter_p2_s4), previewType = PreviewType.TIMELINE_GRID)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "animated_formats", title = R.string.help_tip_animated_formats_title, subtitle = R.string.help_tip_animated_formats_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoLibrary), category = HelpCategory.TIMELINE_ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_animated_formats_p1_title, description = R.string.help_tip_animated_formats_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_animated_formats_p2_title, description = R.string.help_tip_animated_formats_p2_desc)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "story_cards", title = R.string.help_tip_story_cards_title, subtitle = R.string.help_tip_story_cards_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Slideshow), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.StoryCardsSettingsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_story_cards_p1_title, description = R.string.help_tip_story_cards_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_story_cards_p2_title, description = R.string.help_tip_story_cards_p2_desc, steps = listOf(R.string.help_tip_story_cards_p2_s1, R.string.help_tip_story_cards_p2_s2, R.string.help_tip_story_cards_p2_s3, R.string.help_tip_story_cards_p2_s4))
            ), sinceVersion = "4.3.0"),
        HelpTip(id = "timeline_group_period", title = R.string.help_tip_timeline_group_period_title, subtitle = R.string.help_tip_timeline_group_period_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarMonth), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_timeline_group_period_p1_title, description = R.string.help_tip_timeline_group_period_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_timeline_group_period_p2_title, description = R.string.help_tip_timeline_group_period_p2_desc, steps = listOf(R.string.help_tip_timeline_group_period_p2_s1, R.string.help_tip_timeline_group_period_p2_s2, R.string.help_tip_timeline_group_period_p2_s3, R.string.help_tip_timeline_group_period_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "media_type_albums", title = R.string.help_tip_media_type_albums_title, subtitle = R.string.help_tip_media_type_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoLibrary), category = HelpCategory.TIMELINE_ALBUMS,
            deepLink = Screen.SettingsTimelineAlbumsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_media_type_albums_p1_title, description = R.string.help_tip_media_type_albums_p1_desc, steps = listOf(R.string.help_tip_media_type_albums_p1_s1, R.string.help_tip_media_type_albums_p1_s2, R.string.help_tip_media_type_albums_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Viewing
    private val VIEWING_TIPS = listOf(
        HelpTip(id = "view_zoom", title = R.string.help_tip_view_zoom_title, subtitle = R.string.help_tip_view_zoom_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomIn), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_zoom_p1_title, description = R.string.help_tip_view_zoom_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_view_zoom_p2_title, description = R.string.help_tip_view_zoom_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_video", title = R.string.help_tip_view_video_title, subtitle = R.string.help_tip_view_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_video_p1_title, description = R.string.help_tip_view_video_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_view_video_p2_title, description = R.string.help_tip_view_video_p2_desc, steps = listOf(R.string.help_tip_view_video_p2_s1, R.string.help_tip_view_video_p2_s2, R.string.help_tip_view_video_p2_s3, R.string.help_tip_view_video_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_details", title = R.string.help_tip_view_details_title, subtitle = R.string.help_tip_view_details_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_details_p1_title, description = R.string.help_tip_view_details_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = R.string.help_tip_view_details_p2_title, description = R.string.help_tip_view_details_p2_desc, previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_swipe", title = R.string.help_tip_view_swipe_title, subtitle = R.string.help_tip_view_swipe_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = R.string.help_tip_view_swipe_p1_title, description = R.string.help_tip_view_swipe_p1_desc, previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "4.0.0"),
        HelpTip(id = "view_tap_navigation", title = R.string.help_tip_view_tap_navigation_title, subtitle = R.string.help_tip_view_tap_navigation_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Speed), category = HelpCategory.VIEWING,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_view_tap_navigation_p1_title, description = R.string.help_tip_view_tap_navigation_p1_desc, steps = listOf(R.string.help_tip_view_tap_navigation_p1_s1, R.string.help_tip_view_tap_navigation_p1_s2, R.string.help_tip_view_tap_navigation_p1_s3, R.string.help_tip_view_tap_navigation_p1_s4), previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "5.1.3"),
        HelpTip(id = "view_panorama", title = R.string.help_tip_view_panorama_title, subtitle = R.string.help_tip_view_panorama_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Panorama), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = R.string.help_tip_view_panorama_p1_title, description = R.string.help_tip_view_panorama_p1_desc, previewType = PreviewType.PANORAMA)), sinceVersion = "4.1.0"),
        HelpTip(id = "view_motion_photo", title = R.string.help_tip_view_motion_photo_title, subtitle = R.string.help_tip_view_motion_photo_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            pages = listOf(TutorialPage(title = R.string.help_tip_view_motion_photo_p1_title, description = R.string.help_tip_view_motion_photo_p1_desc, previewType = PreviewType.MOTION_PHOTO)), sinceVersion = "4.1.0"),
        HelpTip(id = "motion_photo_frames", title = R.string.help_tip_motion_photo_frames_title, subtitle = R.string.help_tip_motion_photo_frames_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Movie), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_motion_photo_frames_p1_title, description = R.string.help_tip_motion_photo_frames_p1_desc, steps = listOf(R.string.help_tip_motion_photo_frames_p1_s1, R.string.help_tip_motion_photo_frames_p1_s2, R.string.help_tip_motion_photo_frames_p1_s3, R.string.help_tip_motion_photo_frames_p1_s4), previewType = PreviewType.MOTION_PHOTO)
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "view_full_brightness", title = R.string.help_tip_view_full_brightness_title, subtitle = R.string.help_tip_view_full_brightness_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_view_full_brightness_p1_title, description = R.string.help_tip_view_full_brightness_p1_desc, steps = listOf(R.string.help_tip_view_full_brightness_p1_s1, R.string.help_tip_view_full_brightness_p1_s2, R.string.help_tip_view_full_brightness_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "view_group_members", title = R.string.help_tip_view_group_members_title, subtitle = R.string.help_tip_view_group_members_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_group_members_p1_title, description = R.string.help_tip_view_group_members_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_view_group_members_p2_title, description = R.string.help_tip_view_group_members_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "view_lock_image", title = R.string.help_tip_view_lock_image_title, subtitle = R.string.help_tip_view_lock_image_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_lock_image_p1_title, description = R.string.help_tip_view_lock_image_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_view_lock_image_p2_title, description = R.string.help_tip_view_lock_image_p2_desc, steps = listOf(R.string.help_tip_view_lock_image_p2_s1, R.string.help_tip_view_lock_image_p2_s2, R.string.help_tip_view_lock_image_p2_s3, R.string.help_tip_view_lock_image_p2_s4), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "view_video_subtitles", title = R.string.help_tip_video_subtitles_title, subtitle = R.string.help_tip_video_subtitles_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Subtitles), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_video_subtitles_p1_title, description = R.string.help_tip_video_subtitles_p1_desc, previewType = PreviewType.MEDIA_VIEWER),
                TutorialPage(title = R.string.help_tip_video_subtitles_p2_title, description = R.string.help_tip_video_subtitles_p2_desc, steps = listOf(R.string.help_tip_video_subtitles_p2_s1, R.string.help_tip_video_subtitles_p2_s2, R.string.help_tip_video_subtitles_p2_s3), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "view_video_zoom", title = R.string.help_tip_video_zoom_title, subtitle = R.string.help_tip_video_zoom_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomIn), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_video_zoom_p1_title, description = R.string.help_tip_video_zoom_p1_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "view_cutout", title = R.string.help_tip_view_cutout_title, subtitle = R.string.help_tip_view_cutout_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoFixHigh), category = HelpCategory.VIEWING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_view_cutout_p1_title, description = R.string.help_tip_view_cutout_p1_desc, steps = listOf(R.string.help_tip_view_cutout_p1_s1, R.string.help_tip_view_cutout_p1_s2, R.string.help_tip_view_cutout_p1_s3, R.string.help_tip_view_cutout_p1_s4), previewType = PreviewType.SUBJECT_CUTOUT)
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "slideshow_play", title = R.string.help_tip_slideshow_play_title, subtitle = R.string.help_tip_slideshow_play_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Slideshow), category = HelpCategory.VIEWING,
            deepLink = Screen.SlideshowSettingsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_slideshow_play_p1_title, description = R.string.help_tip_slideshow_play_p1_desc, steps = listOf(R.string.help_tip_slideshow_play_p1_s1, R.string.help_tip_slideshow_play_p1_s2, R.string.help_tip_slideshow_play_p1_s3), previewType = PreviewType.SLIDESHOW)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Viewer Actions & Settings
    private val VIEWER_ACTION_TIPS = listOf(
        HelpTip(id = "action_share", title = R.string.help_tip_action_share_title, subtitle = R.string.help_tip_action_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_share_p1_title, description = R.string.help_tip_action_share_p1_desc, steps = listOf(R.string.help_tip_action_share_p1_s1, R.string.help_tip_action_share_p1_s2, R.string.help_tip_action_share_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_edit", title = R.string.help_tip_action_edit_title, subtitle = R.string.help_tip_action_edit_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_edit_p1_title, description = R.string.help_tip_action_edit_p1_desc, steps = listOf(R.string.help_tip_action_edit_p1_s1, R.string.help_tip_action_edit_p1_s2, R.string.help_tip_action_edit_p1_s3, R.string.help_tip_action_edit_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_hide_vault", title = R.string.help_tip_action_hide_vault_title, subtitle = R.string.help_tip_action_hide_vault_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_action_hide_vault_p1_title, description = R.string.help_tip_action_hide_vault_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = R.string.help_tip_action_hide_vault_p2_title, description = R.string.help_tip_action_hide_vault_p2_desc, steps = listOf(R.string.help_tip_action_hide_vault_p2_s1, R.string.help_tip_action_hide_vault_p2_s2, R.string.help_tip_action_hide_vault_p2_s3, R.string.help_tip_action_hide_vault_p2_s4), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "action_copy_to_album", title = R.string.help_tip_action_copy_to_album_title, subtitle = R.string.help_tip_action_copy_to_album_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FolderCopy), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_copy_to_album_p1_title, description = R.string.help_tip_action_copy_to_album_p1_desc, steps = listOf(R.string.help_tip_action_copy_to_album_p1_s1, R.string.help_tip_action_copy_to_album_p1_s2, R.string.help_tip_action_copy_to_album_p1_s3, R.string.help_tip_action_copy_to_album_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "action_move_to_album", title = R.string.help_tip_action_move_to_album_title, subtitle = R.string.help_tip_action_move_to_album_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.FolderCopy), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_move_to_album_p1_title, description = R.string.help_tip_action_move_to_album_p1_desc, steps = listOf(R.string.help_tip_action_move_to_album_p1_s1, R.string.help_tip_action_move_to_album_p1_s2, R.string.help_tip_action_move_to_album_p1_s3, R.string.help_tip_action_move_to_album_p1_s4, R.string.help_tip_action_move_to_album_p1_s5))), sinceVersion = "5.1.3"),
        HelpTip(id = "action_add_collection", title = R.string.help_tip_action_add_collection_title, subtitle = R.string.help_tip_action_add_collection_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_add_collection_p1_title, description = R.string.help_tip_action_add_collection_p1_desc, previewType = PreviewType.COLLECTION_VIEW)), sinceVersion = "4.2.0"),
        HelpTip(id = "action_view_all_metadata", title = R.string.help_tip_action_view_all_metadata_title, subtitle = R.string.help_tip_action_view_all_metadata_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_action_view_all_metadata_p1_title, description = R.string.help_tip_action_view_all_metadata_p1_desc, previewType = PreviewType.EXIF_VIEWER)), sinceVersion = "4.1.3"),
        HelpTip(id = "action_cast", title = R.string.help_tip_viewer_cast_title, subtitle = R.string.help_tip_viewer_cast_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cast), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_viewer_cast_p1_title, description = R.string.help_tip_viewer_cast_p1_desc, previewType = PreviewType.CASTING),
                TutorialPage(title = R.string.help_tip_viewer_cast_p2_title, description = R.string.help_tip_viewer_cast_p2_desc, steps = listOf(R.string.help_tip_viewer_cast_p2_s1, R.string.help_tip_viewer_cast_p2_s2, R.string.help_tip_viewer_cast_p2_s3, R.string.help_tip_viewer_cast_p2_s4), previewType = PreviewType.CASTING)
            ), sinceVersion = "4.2.1"),
        HelpTip(id = "action_save_motion_video", title = R.string.help_tip_action_save_motion_video_title, subtitle = R.string.help_tip_action_save_motion_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Movie), category = HelpCategory.VIEWER_ACTIONS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_action_save_motion_video_p1_title, description = R.string.help_tip_action_save_motion_video_p1_desc, steps = listOf(R.string.help_tip_action_save_motion_video_p1_s1, R.string.help_tip_action_save_motion_video_p1_s2, R.string.help_tip_action_save_motion_video_p1_s3, R.string.help_tip_action_save_motion_video_p1_s4), previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.0.0")
    )

    private val VIEWER_SETTINGS_TIPS = listOf(
        HelpTip(id = "viewer_default_editor", title = R.string.help_tip_viewer_default_editor_title, subtitle = R.string.help_tip_viewer_default_editor_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_viewer_default_editor_p1_title, description = R.string.help_tip_viewer_default_editor_p1_desc, steps = listOf(R.string.help_tip_viewer_default_editor_p1_s1, R.string.help_tip_viewer_default_editor_p1_s2, R.string.help_tip_viewer_default_editor_p1_s3)),
                TutorialPage(title = R.string.help_tip_viewer_default_editor_p2_title, description = R.string.help_tip_viewer_default_editor_p2_desc)
            ), sinceVersion = "4.1.2"),
        HelpTip(id = "viewer_auto_play", title = R.string.help_tip_viewer_auto_play_title, subtitle = R.string.help_tip_viewer_auto_play_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_viewer_auto_play_p1_title, description = R.string.help_tip_viewer_auto_play_p1_desc, steps = listOf(R.string.help_tip_viewer_auto_play_p1_s1, R.string.help_tip_viewer_auto_play_p1_s2))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_auto_hide_video", title = R.string.help_tip_viewer_auto_hide_video_title, subtitle = R.string.help_tip_viewer_auto_hide_video_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_viewer_auto_hide_video_p1_title, description = R.string.help_tip_viewer_auto_hide_video_p1_desc, steps = listOf(R.string.help_tip_viewer_auto_hide_video_p1_s1, R.string.help_tip_viewer_auto_hide_video_p1_s2, R.string.help_tip_viewer_auto_hide_video_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_date_header", title = R.string.help_tip_viewer_date_header_title, subtitle = R.string.help_tip_viewer_date_header_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_viewer_date_header_p1_title, description = R.string.help_tip_viewer_date_header_p1_desc, steps = listOf(R.string.help_tip_viewer_date_header_p1_s1, R.string.help_tip_viewer_date_header_p1_s2, R.string.help_tip_viewer_date_header_p1_s3))), sinceVersion = "4.1.0"),
        HelpTip(id = "viewer_fav_button", title = R.string.help_tip_viewer_fav_button_title, subtitle = R.string.help_tip_viewer_fav_button_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_viewer_fav_button_p1_title, description = R.string.help_tip_viewer_fav_button_p1_desc, steps = listOf(R.string.help_tip_viewer_fav_button_p1_s1, R.string.help_tip_viewer_fav_button_p1_s2, R.string.help_tip_viewer_fav_button_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "viewer_auto_contrast", title = R.string.help_tip_viewer_auto_contrast_title, subtitle = R.string.help_tip_viewer_auto_contrast_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Contrast), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_viewer_auto_contrast_p1_title, description = R.string.help_tip_viewer_auto_contrast_p1_desc, steps = listOf(R.string.help_tip_viewer_auto_contrast_p1_s1, R.string.help_tip_viewer_auto_contrast_p1_s2, R.string.help_tip_viewer_auto_contrast_p1_s3))), sinceVersion = "4.2.1"),
        HelpTip(id = "video_options_popup", title = R.string.help_tip_video_options_popup_title, subtitle = R.string.help_tip_video_options_popup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.VideoSettings), category = HelpCategory.VIEWER_SETTINGS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_video_options_popup_p1_title, description = R.string.help_tip_video_options_popup_p1_desc, previewType = PreviewType.VIDEO_CONTROLS)
            ), sinceVersion = "4.3.0"),
        HelpTip(id = "video_fix_black", title = R.string.help_tip_video_fix_black_title, subtitle = R.string.help_tip_video_fix_black_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Movie), category = HelpCategory.VIEWER_SETTINGS,
            deepLink = Screen.SettingsMediaViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_video_fix_black_p1_title, description = R.string.help_tip_video_fix_black_p1_desc)
            ), sinceVersion = "5.0.3")
    )
    // endregion

    // region Editing
    private val EDITING_TIPS = listOf(
        HelpTip(id = "edit_crop", title = R.string.help_tip_edit_crop_title, subtitle = R.string.help_tip_edit_crop_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_crop_p1_title, description = R.string.help_tip_edit_crop_p1_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = R.string.help_tip_edit_crop_p2_title, description = R.string.help_tip_edit_crop_p2_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = R.string.help_tip_edit_crop_p3_title, description = R.string.help_tip_edit_crop_p3_desc, steps = listOf(R.string.help_tip_edit_crop_p3_s1, R.string.help_tip_edit_crop_p3_s2, R.string.help_tip_edit_crop_p3_s3, R.string.help_tip_edit_crop_p3_s4, R.string.help_tip_edit_crop_p3_s5), previewType = PreviewType.PHOTO_EDITOR_CROP)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_rotate", title = R.string.help_tip_edit_rotate_title, subtitle = R.string.help_tip_edit_rotate_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_rotate_p1_title, description = R.string.help_tip_edit_rotate_p1_desc, previewType = PreviewType.PHOTO_EDITOR_CROP),
                TutorialPage(title = R.string.help_tip_edit_rotate_p2_title, description = R.string.help_tip_edit_rotate_p2_desc, steps = listOf(R.string.help_tip_edit_rotate_p2_s1, R.string.help_tip_edit_rotate_p2_s2, R.string.help_tip_edit_rotate_p2_s3, R.string.help_tip_edit_rotate_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_filters", title = R.string.help_tip_edit_filters_title, subtitle = R.string.help_tip_edit_filters_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_filters_p1_title, description = R.string.help_tip_edit_filters_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = R.string.help_tip_edit_filters_p2_title, description = R.string.help_tip_edit_filters_p2_desc, steps = listOf(R.string.help_tip_edit_filters_p2_s1, R.string.help_tip_edit_filters_p2_s2, R.string.help_tip_edit_filters_p2_s3, R.string.help_tip_edit_filters_p2_s4, R.string.help_tip_edit_filters_p2_s5), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_effects", title = R.string.help_tip_edit_effects_title, subtitle = R.string.help_tip_edit_effects_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoFixHigh), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_effects_p1_title, description = R.string.help_tip_edit_effects_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = R.string.help_tip_edit_effects_p2_title, description = R.string.help_tip_edit_effects_p2_desc, steps = listOf(R.string.help_tip_edit_effects_p2_s1, R.string.help_tip_edit_effects_p2_s2, R.string.help_tip_edit_effects_p2_s3, R.string.help_tip_edit_effects_p2_s4), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "edit_adjustments", title = R.string.help_tip_edit_adjustments_title, subtitle = R.string.help_tip_edit_adjustments_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_adjustments_p1_title, description = R.string.help_tip_edit_adjustments_p1_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS),
                TutorialPage(title = R.string.help_tip_edit_adjustments_p2_title, description = R.string.help_tip_edit_adjustments_p2_desc, previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_markup", title = R.string.help_tip_edit_markup_title, subtitle = R.string.help_tip_edit_markup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Draw), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_markup_p1_title, description = R.string.help_tip_edit_markup_p1_desc, previewType = PreviewType.PHOTO_EDITOR_MARKUP),
                TutorialPage(title = R.string.help_tip_edit_markup_p2_title, description = R.string.help_tip_edit_markup_p2_desc, previewType = PreviewType.PHOTO_EDITOR_MARKUP),
                TutorialPage(title = R.string.help_tip_edit_markup_p3_title, description = R.string.help_tip_edit_markup_p3_desc, steps = listOf(R.string.help_tip_edit_markup_p3_s1, R.string.help_tip_edit_markup_p3_s2, R.string.help_tip_edit_markup_p3_s3, R.string.help_tip_edit_markup_p3_s4, R.string.help_tip_edit_markup_p3_s5, R.string.help_tip_edit_markup_p3_s6), previewType = PreviewType.PHOTO_EDITOR_MARKUP)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_save", title = R.string.help_tip_edit_save_title, subtitle = R.string.help_tip_edit_save_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.EDITING,
            deepLink = Screen.EditBackupsViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_save_p1_title, description = R.string.help_tip_edit_save_p1_desc),
                TutorialPage(title = R.string.help_tip_edit_save_p2_title, description = R.string.help_tip_edit_save_p2_desc, steps = listOf(R.string.help_tip_edit_save_p2_s1, R.string.help_tip_edit_save_p2_s2, R.string.help_tip_edit_save_p2_s3, R.string.help_tip_edit_save_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "edit_backups", title = R.string.help_tip_edit_backups_title, subtitle = R.string.help_tip_edit_backups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.EDITING,
            deepLink = Screen.EditBackupsViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_edit_backups_p1_title, description = R.string.help_tip_edit_backups_p1_desc),
                TutorialPage(title = R.string.help_tip_edit_backups_p2_title, description = R.string.help_tip_edit_backups_p2_desc, steps = listOf(R.string.help_tip_edit_backups_p2_s1, R.string.help_tip_edit_backups_p2_s2, R.string.help_tip_edit_backups_p2_s3, R.string.help_tip_edit_backups_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "raw_develop", title = R.string.help_tip_raw_develop_title, subtitle = R.string.help_tip_raw_develop_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Edit), category = HelpCategory.EDITING,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_raw_develop_p1_title, description = R.string.help_tip_raw_develop_p1_desc, steps = listOf(R.string.help_tip_raw_develop_p1_s1, R.string.help_tip_raw_develop_p1_s2, R.string.help_tip_raw_develop_p1_s3, R.string.help_tip_raw_develop_p1_s4), previewType = PreviewType.PHOTO_EDITOR_FILTERS)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Search
    private val SEARCH_TIPS = listOf(
        HelpTip(id = "search_text", title = R.string.help_tip_search_text_title, subtitle = R.string.help_tip_search_text_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_search_text_p1_title, description = R.string.help_tip_search_text_p1_desc, previewType = PreviewType.SEARCH_BAR),
                TutorialPage(title = R.string.help_tip_search_text_p2_title, description = R.string.help_tip_search_text_p2_desc, previewType = PreviewType.SEARCH_BAR)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "search_ai", title = R.string.help_tip_search_ai_title, subtitle = R.string.help_tip_search_ai_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            deepLink = Screen.AIModelsManagerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_search_ai_p1_title, description = R.string.help_tip_search_ai_p1_desc, previewType = PreviewType.AI_SEARCH),
                TutorialPage(title = R.string.help_tip_search_ai_p2_title, description = R.string.help_tip_search_ai_p2_desc, previewType = PreviewType.AI_SEARCH),
                TutorialPage(title = R.string.help_tip_search_ai_p3_title, description = R.string.help_tip_search_ai_p3_desc, steps = listOf(R.string.help_tip_search_ai_p3_s1, R.string.help_tip_search_ai_p3_s2, R.string.help_tip_search_ai_p3_s3, R.string.help_tip_search_ai_p3_s4), actionLabel = R.string.help_action_open_settings, previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "search_ignored_albums", title = R.string.help_tip_search_ignored_albums_title, subtitle = R.string.help_tip_search_ignored_albums_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            deepLink = Screen.SettingsSmartFeaturesScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_search_ignored_albums_p1_title, description = R.string.help_tip_search_ignored_albums_p1_desc, steps = listOf(R.string.help_tip_search_ignored_albums_p1_s1, R.string.help_tip_search_ignored_albums_p1_s2, R.string.help_tip_search_ignored_albums_p1_s3, R.string.help_tip_search_ignored_albums_p1_s4), previewType = PreviewType.AI_SEARCH)
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "search_history", title = R.string.help_tip_search_history_title, subtitle = R.string.help_tip_search_history_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            pages = listOf(TutorialPage(title = R.string.help_tip_search_history_p1_title, description = R.string.help_tip_search_history_p1_desc, previewType = PreviewType.SEARCH_BAR)), sinceVersion = "4.0.0"),
        HelpTip(id = "search_indexing", title = R.string.help_tip_search_indexing_title, subtitle = R.string.help_tip_search_indexing_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            pages = listOf(TutorialPage(title = R.string.help_tip_search_indexing_p1_title, description = R.string.help_tip_search_indexing_p1_desc)), sinceVersion = "4.1.0"),
        HelpTip(id = "search_metadata", title = R.string.help_tip_search_metadata_title, subtitle = R.string.help_tip_search_metadata_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.SEARCH,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_search_metadata_p1_title, description = R.string.help_tip_search_metadata_p1_desc, previewType = PreviewType.SEARCH_BAR)
            ), sinceVersion = "4.2.3")
    )
    // endregion

    // region AI Features
    private val AI_TIPS = listOf(
        HelpTip(id = "ai_categories", title = R.string.help_tip_ai_categories_title, subtitle = R.string.help_tip_ai_categories_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_ai_categories_p1_title, description = R.string.help_tip_ai_categories_p1_desc, previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = R.string.help_tip_ai_categories_p2_title, description = R.string.help_tip_ai_categories_p2_desc, steps = listOf(R.string.help_tip_ai_categories_p2_s1, R.string.help_tip_ai_categories_p2_s2, R.string.help_tip_ai_categories_p2_s3, R.string.help_tip_ai_categories_p2_s4), previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = R.string.help_tip_ai_categories_p3_title, description = R.string.help_tip_ai_categories_p3_desc, previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "4.1.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_categories, Icons.Outlined.AutoAwesome, Screen.CategoriesScreen()))),
        HelpTip(id = "ai_create_category", title = R.string.help_tip_ai_create_category_title, subtitle = R.string.help_tip_ai_create_category_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_ai_create_category_p1_title, description = R.string.help_tip_ai_create_category_p1_desc, previewType = PreviewType.AI_CATEGORIES),
                TutorialPage(title = R.string.help_tip_ai_create_category_p2_title, description = R.string.help_tip_ai_create_category_p2_desc, steps = listOf(R.string.help_tip_ai_create_category_p2_s1, R.string.help_tip_ai_create_category_p2_s2, R.string.help_tip_ai_create_category_p2_s3, R.string.help_tip_ai_create_category_p2_s4, R.string.help_tip_ai_create_category_p2_s5), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "ai_models", title = R.string.help_tip_ai_models_title, subtitle = R.string.help_tip_ai_models_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudDownload), category = HelpCategory.AI_FEATURES,
            deepLink = Screen.AIModelsManagerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_ai_models_p1_title, description = R.string.help_tip_ai_models_p1_desc),
                TutorialPage(title = R.string.help_tip_ai_models_p2_title, description = R.string.help_tip_ai_models_p2_desc, steps = listOf(R.string.help_tip_ai_models_p2_s1, R.string.help_tip_ai_models_p2_s2, R.string.help_tip_ai_models_p2_s3, R.string.help_tip_ai_models_p2_s4), actionLabel = R.string.help_action_open_settings)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "ai_indexing", title = R.string.help_tip_ai_indexing_title, subtitle = R.string.help_tip_ai_indexing_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.AI_FEATURES,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_ai_indexing_p1_title, description = R.string.help_tip_ai_indexing_p1_desc),
                TutorialPage(title = R.string.help_tip_ai_indexing_p2_title, description = R.string.help_tip_ai_indexing_p2_desc)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "people_grouping", title = R.string.help_tip_people_grouping_title, subtitle = R.string.help_tip_people_grouping_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.AI_FEATURES,
            deepLink = Screen.PeopleListScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_people_grouping_p1_title, description = R.string.help_tip_people_grouping_p1_desc, steps = listOf(R.string.help_tip_people_grouping_p1_s1, R.string.help_tip_people_grouping_p1_s2, R.string.help_tip_people_grouping_p1_s3, R.string.help_tip_people_grouping_p1_s4), previewType = PreviewType.AI_CATEGORIES)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Albums & Collections
    private val ALBUM_TIPS = listOf(
        HelpTip(id = "albums_browse", title = R.string.help_tip_albums_browse_title, subtitle = R.string.help_tip_albums_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_browse_p1_title, description = R.string.help_tip_albums_browse_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = R.string.help_tip_albums_browse_p2_title, description = R.string.help_tip_albums_browse_p2_desc, previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_pin", title = R.string.help_tip_albums_pin_title, subtitle = R.string.help_tip_albums_pin_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(TutorialPage(title = R.string.help_tip_albums_pin_p1_title, description = R.string.help_tip_albums_pin_p1_desc, steps = listOf(R.string.help_tip_albums_pin_p1_s1, R.string.help_tip_albums_pin_p1_s2, R.string.help_tip_albums_pin_p1_s3, R.string.help_tip_albums_pin_p1_s4), previewType = PreviewType.ALBUM_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_ignore", title = R.string.help_tip_albums_ignore_title, subtitle = R.string.help_tip_albums_ignore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_ignore_p1_title, description = R.string.help_tip_albums_ignore_p1_desc, steps = listOf(R.string.help_tip_albums_ignore_p1_s1, R.string.help_tip_albums_ignore_p1_s2, R.string.help_tip_albums_ignore_p1_s3)),
                TutorialPage(title = R.string.help_tip_albums_ignore_p2_title, description = R.string.help_tip_albums_ignore_p2_desc)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_groups", title = R.string.help_tip_albums_groups_title, subtitle = R.string.help_tip_albums_groups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_groups_p1_title, description = R.string.help_tip_albums_groups_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = R.string.help_tip_albums_groups_p2_title, description = R.string.help_tip_albums_groups_p2_desc, steps = listOf(R.string.help_tip_albums_groups_p2_s1, R.string.help_tip_albums_groups_p2_s2, R.string.help_tip_albums_groups_p2_s3, R.string.help_tip_albums_groups_p2_s4, R.string.help_tip_albums_groups_p2_s5), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "albums_sections", title = R.string.help_tip_albums_sections_title, subtitle = R.string.help_tip_albums_sections_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_sections_p1_title, description = R.string.help_tip_albums_sections_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = R.string.help_tip_albums_sections_p2_title, description = R.string.help_tip_albums_sections_p2_desc, steps = listOf(R.string.help_tip_albums_sections_p2_s1, R.string.help_tip_albums_sections_p2_s2, R.string.help_tip_albums_sections_p2_s3, R.string.help_tip_albums_sections_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "albums_collections", title = R.string.help_tip_albums_collections_title, subtitle = R.string.help_tip_albums_collections_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_collections_p1_title, description = R.string.help_tip_albums_collections_p1_desc, previewType = PreviewType.COLLECTION_VIEW),
                TutorialPage(title = R.string.help_tip_albums_collections_p2_title, description = R.string.help_tip_albums_collections_p2_desc, steps = listOf(R.string.help_tip_albums_collections_p2_s1, R.string.help_tip_albums_collections_p2_s2, R.string.help_tip_albums_collections_p2_s3, R.string.help_tip_albums_collections_p2_s4), previewType = PreviewType.COLLECTION_VIEW)
            ), sinceVersion = "4.2.0"),
        HelpTip(id = "albums_thumbnails", title = R.string.help_tip_albums_thumbnails_title, subtitle = R.string.help_tip_albums_thumbnails_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_thumbnails_p1_title, description = R.string.help_tip_albums_thumbnails_p1_desc, previewType = PreviewType.ALBUM_GRID),
                TutorialPage(title = R.string.help_tip_albums_thumbnails_p2_title, description = R.string.help_tip_albums_thumbnails_p2_desc, steps = listOf(R.string.help_tip_albums_thumbnails_p2_s1, R.string.help_tip_albums_thumbnails_p2_s2, R.string.help_tip_albums_thumbnails_p2_s3, R.string.help_tip_albums_thumbnails_p2_s4), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "albums_picker_search", title = R.string.help_tip_album_picker_search_title, subtitle = R.string.help_tip_album_picker_search_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ImageSearch), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_album_picker_search_p1_title, description = R.string.help_tip_album_picker_search_p1_desc)
            ), sinceVersion = "4.2.3"),
        HelpTip(id = "albums_ignored_manage", title = R.string.help_tip_albums_ignored_manage_title, subtitle = R.string.help_tip_albums_ignored_manage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ALBUMS,
            deepLink = Screen.IgnoredScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_albums_ignored_manage_p1_title, description = R.string.help_tip_albums_ignored_manage_p1_desc, steps = listOf(R.string.help_tip_albums_ignored_manage_p1_s1, R.string.help_tip_albums_ignored_manage_p1_s2, R.string.help_tip_albums_ignored_manage_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "album_cover", title = R.string.help_tip_album_cover_title, subtitle = R.string.help_tip_album_cover_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.PhotoLibrary), category = HelpCategory.ALBUMS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_album_cover_p1_title, description = R.string.help_tip_album_cover_p1_desc, steps = listOf(R.string.help_tip_album_cover_p1_s1, R.string.help_tip_album_cover_p1_s2, R.string.help_tip_album_cover_p1_s3), previewType = PreviewType.ALBUM_GRID)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Vault
    private val VAULT_TIPS = listOf(
        HelpTip(id = "vault_setup", title = R.string.help_tip_vault_setup_title, subtitle = R.string.help_tip_vault_setup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_vault_setup_p1_title, description = R.string.help_tip_vault_setup_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = R.string.help_tip_vault_setup_p2_title, description = R.string.help_tip_vault_setup_p2_desc, steps = listOf(R.string.help_tip_vault_setup_p2_s1, R.string.help_tip_vault_setup_p2_s2, R.string.help_tip_vault_setup_p2_s3, R.string.help_tip_vault_setup_p2_s4), previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = R.string.help_tip_vault_setup_p3_title, description = R.string.help_tip_vault_setup_p3_desc, previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_add", title = R.string.help_tip_vault_add_title, subtitle = R.string.help_tip_vault_add_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_vault_add_p1_title, description = R.string.help_tip_vault_add_p1_desc, previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = R.string.help_tip_vault_add_p2_title, description = R.string.help_tip_vault_add_p2_desc, steps = listOf(R.string.help_tip_vault_add_p2_s1, R.string.help_tip_vault_add_p2_s2, R.string.help_tip_vault_add_p2_s3, R.string.help_tip_vault_add_p2_s4), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_restore", title = R.string.help_tip_vault_restore_title, subtitle = R.string.help_tip_vault_restore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(TutorialPage(title = R.string.help_tip_vault_restore_p1_title, description = R.string.help_tip_vault_restore_p1_desc, previewType = PreviewType.VAULT_LOCK)), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_video_streaming", title = R.string.help_tip_vault_video_streaming_title, subtitle = R.string.help_tip_vault_video_streaming_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(TutorialPage(title = R.string.help_tip_vault_video_streaming_p1_title, description = R.string.help_tip_vault_video_streaming_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "vault_overhaul", title = R.string.help_tip_vault_overhaul_title, subtitle = R.string.help_tip_vault_overhaul_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.VAULT,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_vault_overhaul_p1_title, description = R.string.help_tip_vault_overhaul_p1_desc, steps = listOf(R.string.help_tip_vault_overhaul_p1_s1, R.string.help_tip_vault_overhaul_p1_s2, R.string.help_tip_vault_overhaul_p1_s3), previewType = PreviewType.VAULT_LOCK),
                TutorialPage(title = R.string.help_tip_vault_overhaul_p2_title, description = R.string.help_tip_vault_overhaul_p2_desc, steps = listOf(R.string.help_tip_vault_overhaul_p2_s1, R.string.help_tip_vault_overhaul_p2_s2, R.string.help_tip_vault_overhaul_p2_s3), previewType = PreviewType.VAULT_LOCK)
            ), sinceVersion = "4.2.1",
            quickActions = listOf(QuickAction(R.string.help_qa_open_vault, Icons.Outlined.Lock, Screen.VaultScreen())))
    )
    // endregion

    // region Cloud & Sync
    private val CLOUD_TIPS = listOf(
        HelpTip(id = "cloud_connect", title = R.string.help_tip_cloud_connect_title, subtitle = R.string.help_tip_cloud_connect_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cloud), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudAccountsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_connect_p1_title, description = R.string.help_tip_cloud_connect_p1_desc),
                TutorialPage(title = R.string.help_tip_cloud_connect_p2_title, description = R.string.help_tip_cloud_connect_p2_desc, steps = listOf(R.string.help_tip_cloud_connect_p2_s1, R.string.help_tip_cloud_connect_p2_s2, R.string.help_tip_cloud_connect_p2_s3, R.string.help_tip_cloud_connect_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_nextcloud_login", title = R.string.help_tip_cloud_nextcloud_login_title, subtitle = R.string.help_tip_cloud_nextcloud_login_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cloud), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudAccountsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_nextcloud_login_p1_title, description = R.string.help_tip_cloud_nextcloud_login_p1_desc, steps = listOf(R.string.help_tip_cloud_nextcloud_login_p1_s1, R.string.help_tip_cloud_nextcloud_login_p1_s2, R.string.help_tip_cloud_nextcloud_login_p1_s3, R.string.help_tip_cloud_nextcloud_login_p1_s4))
            ), sinceVersion = "5.1.1"),
        HelpTip(id = "cloud_timeline", title = R.string.help_tip_cloud_timeline_title, subtitle = R.string.help_tip_cloud_timeline_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudDone), category = HelpCategory.CLOUD_SYNC,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_timeline_p1_title, description = R.string.help_tip_cloud_timeline_p1_desc, previewType = PreviewType.TIMELINE_GRID),
                TutorialPage(title = R.string.help_tip_cloud_timeline_p2_title, description = R.string.help_tip_cloud_timeline_p2_desc, previewType = PreviewType.MEDIA_VIEWER)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_backup", title = R.string.help_tip_cloud_backup_title, subtitle = R.string.help_tip_cloud_backup_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudUpload), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudBackupDashboardScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_backup_p1_title, description = R.string.help_tip_cloud_backup_p1_desc),
                TutorialPage(title = R.string.help_tip_cloud_backup_p2_title, description = R.string.help_tip_cloud_backup_p2_desc, steps = listOf(R.string.help_tip_cloud_backup_p2_s1, R.string.help_tip_cloud_backup_p2_s2, R.string.help_tip_cloud_backup_p2_s3, R.string.help_tip_cloud_backup_p2_s4))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_explore", title = R.string.help_tip_cloud_explore_title, subtitle = R.string.help_tip_cloud_explore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Hub), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudBackupDashboardScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_explore_p1_title, description = R.string.help_tip_cloud_explore_p1_desc),
                TutorialPage(title = R.string.help_tip_cloud_explore_p2_title, description = R.string.help_tip_cloud_explore_p2_desc)
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "cloud_add_server", title = R.string.help_tip_cloud_add_server_title, subtitle = R.string.help_tip_cloud_add_server_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cloud), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudAccountsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_cloud_add_server_p1_title, description = R.string.help_tip_cloud_add_server_p1_desc, steps = listOf(R.string.help_tip_cloud_add_server_p1_s1, R.string.help_tip_cloud_add_server_p1_s2, R.string.help_tip_cloud_add_server_p1_s3, R.string.help_tip_cloud_add_server_p1_s4))
            ), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_destinations", title = R.string.help_tip_cloud_destinations_title, subtitle = R.string.help_tip_cloud_destinations_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudUpload), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudDestinationsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_destinations_p1_title, description = R.string.help_tip_cloud_destinations_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_provider_settings", title = R.string.help_tip_cloud_provider_settings_title, subtitle = R.string.help_tip_cloud_provider_settings_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Cloud), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudBackupDashboardScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_provider_settings_p1_title, description = R.string.help_tip_cloud_provider_settings_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_shared_links", title = R.string.help_tip_cloud_shared_links_title, subtitle = R.string.help_tip_cloud_shared_links_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Hub), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.SharedLinksScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_shared_links_p1_title, description = R.string.help_tip_cloud_shared_links_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_sync_status", title = R.string.help_tip_cloud_sync_status_title, subtitle = R.string.help_tip_cloud_sync_status_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CloudDone), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.CloudBackupDashboardScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_sync_status_p1_title, description = R.string.help_tip_cloud_sync_status_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_people", title = R.string.help_tip_cloud_people_title, subtitle = R.string.help_tip_cloud_people_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.PeopleListScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_people_p1_title, description = R.string.help_tip_cloud_people_p1_desc)), sinceVersion = "5.1.0"),
        HelpTip(id = "cloud_free_up_space", title = R.string.help_tip_cloud_free_up_space_title, subtitle = R.string.help_tip_cloud_free_up_space_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Storage), category = HelpCategory.CLOUD_SYNC,
            deepLink = Screen.FreeUpSpaceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_cloud_free_up_space_p1_title, description = R.string.help_tip_cloud_free_up_space_p1_desc)), sinceVersion = "5.1.0")
    )
    // endregion

    // region Favorites & Trash
    private val FAV_TRASH_TIPS = listOf(
        HelpTip(id = "fav_add", title = R.string.help_tip_fav_add_title, subtitle = R.string.help_tip_fav_add_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(TutorialPage(title = R.string.help_tip_fav_add_p1_title, description = R.string.help_tip_fav_add_p1_desc, previewType = PreviewType.FAVORITES_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "fav_browse", title = R.string.help_tip_fav_browse_title, subtitle = R.string.help_tip_fav_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(TutorialPage(title = R.string.help_tip_fav_browse_p1_title, description = R.string.help_tip_fav_browse_p1_desc, previewType = PreviewType.FAVORITES_GRID)), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_favorites, Icons.Outlined.Collections, Screen.FavoriteScreen()))),
        HelpTip(id = "trash_delete", title = R.string.help_tip_trash_delete_title, subtitle = R.string.help_tip_trash_delete_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_trash_delete_p1_title, description = R.string.help_tip_trash_delete_p1_desc, previewType = PreviewType.TRASH_GRID),
                TutorialPage(title = R.string.help_tip_trash_delete_p2_title, description = R.string.help_tip_trash_delete_p2_desc, steps = listOf(R.string.help_tip_trash_delete_p2_s1, R.string.help_tip_trash_delete_p2_s2, R.string.help_tip_trash_delete_p2_s3, R.string.help_tip_trash_delete_p2_s4), previewType = PreviewType.TRASH_GRID)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_trash, Icons.Outlined.Collections, Screen.TrashedScreen()))),
        HelpTip(id = "trash_enable", title = R.string.help_tip_trash_enable_title, subtitle = R.string.help_tip_trash_enable_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.FAVORITES_TRASH,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_trash_enable_p1_title, description = R.string.help_tip_trash_enable_p1_desc, steps = listOf(R.string.help_tip_trash_enable_p1_s1, R.string.help_tip_trash_enable_p1_s2, R.string.help_tip_trash_enable_p1_s3, R.string.help_tip_trash_enable_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "trash_fav_redesign", title = R.string.help_tip_trash_fav_redesign_title, subtitle = R.string.help_tip_trash_fav_redesign_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.FAVORITES_TRASH,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_trash_fav_redesign_p1_title, description = R.string.help_tip_trash_fav_redesign_p1_desc, previewType = PreviewType.TRASH_GRID)
            ), sinceVersion = "4.2.3")
    )
    // endregion

    // region Locations
    private val LOCATION_TIPS = listOf(
        HelpTip(id = "location_browse", title = R.string.help_tip_location_browse_title, subtitle = R.string.help_tip_location_browse_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LocationOn), category = HelpCategory.LOCATIONS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_location_browse_p1_title, description = R.string.help_tip_location_browse_p1_desc, previewType = PreviewType.LOCATION_MAP),
                TutorialPage(title = R.string.help_tip_location_browse_p2_title, description = R.string.help_tip_location_browse_p2_desc, previewType = PreviewType.LOCATION_MAP)
            ), sinceVersion = "4.0.0",
            quickActions = listOf(QuickAction(R.string.help_qa_open_locations, Icons.Outlined.LocationOn, Screen.LocationsScreen()))),
        HelpTip(id = "location_viewer", title = R.string.help_tip_location_viewer_title, subtitle = R.string.help_tip_location_viewer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.LocationOn), category = HelpCategory.LOCATIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_location_viewer_p1_title, description = R.string.help_tip_location_viewer_p1_desc)), sinceVersion = "4.0.0")
    )
    // endregion

    // region Metadata
    private val METADATA_TIPS = listOf(
        HelpTip(id = "exif_view", title = R.string.help_tip_exif_view_title, subtitle = R.string.help_tip_exif_view_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_exif_view_p1_title, description = R.string.help_tip_exif_view_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = R.string.help_tip_exif_view_p2_title, description = R.string.help_tip_exif_view_p2_desc, previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_edit", title = R.string.help_tip_exif_edit_title, subtitle = R.string.help_tip_exif_edit_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_exif_edit_p1_title, description = R.string.help_tip_exif_edit_p1_desc, previewType = PreviewType.EXIF_VIEWER),
                TutorialPage(title = R.string.help_tip_exif_edit_p2_title, description = R.string.help_tip_exif_edit_p2_desc, steps = listOf(R.string.help_tip_exif_edit_p2_s1, R.string.help_tip_exif_edit_p2_s2, R.string.help_tip_exif_edit_p2_s3, R.string.help_tip_exif_edit_p2_s4), previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_delete_location", title = R.string.help_tip_exif_delete_location_title, subtitle = R.string.help_tip_exif_delete_location_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            pages = listOf(TutorialPage(title = R.string.help_tip_exif_delete_location_p1_title, description = R.string.help_tip_exif_delete_location_p1_desc, steps = listOf(R.string.help_tip_exif_delete_location_p1_s1, R.string.help_tip_exif_delete_location_p1_s2, R.string.help_tip_exif_delete_location_p1_s3, R.string.help_tip_exif_delete_location_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "exif_refresh", title = R.string.help_tip_exif_refresh_title, subtitle = R.string.help_tip_exif_refresh_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            deepLink = Screen.SettingsSmartFeaturesScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_exif_refresh_p1_title, description = R.string.help_tip_exif_refresh_p1_desc, steps = listOf(R.string.help_tip_exif_refresh_p1_s1, R.string.help_tip_exif_refresh_p1_s2, R.string.help_tip_exif_refresh_p1_s3, R.string.help_tip_exif_refresh_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "metadata_sanitize", title = R.string.help_tip_metadata_sanitize_title, subtitle = R.string.help_tip_metadata_sanitize_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.EditNote), category = HelpCategory.METADATA,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_metadata_sanitize_p1_title, description = R.string.help_tip_metadata_sanitize_p1_desc, steps = listOf(R.string.help_tip_metadata_sanitize_p1_s1, R.string.help_tip_metadata_sanitize_p1_s2, R.string.help_tip_metadata_sanitize_p1_s3, R.string.help_tip_metadata_sanitize_p1_s4), previewType = PreviewType.EXIF_VIEWER)
            ), sinceVersion = "5.1.0")
    )
    // endregion

    // region Settings
    private val SETTINGS_APPEARANCE_TIPS = listOf(
        HelpTip(id = "settings_color_palette", title = R.string.help_tip_settings_color_palette_title, subtitle = R.string.help_tip_settings_color_palette_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = Screen.ColorPaletteScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_settings_color_palette_p1_title, description = R.string.help_tip_settings_color_palette_p1_desc, previewType = PreviewType.COLOR_PALETTE),
                TutorialPage(title = R.string.help_tip_settings_color_palette_p2_title, description = R.string.help_tip_settings_color_palette_p2_desc, steps = listOf(R.string.help_tip_settings_color_palette_p2_s1, R.string.help_tip_settings_color_palette_p2_s2, R.string.help_tip_settings_color_palette_p2_s3), previewType = PreviewType.COLOR_PALETTE)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_dark_mode", title = R.string.help_tip_settings_dark_mode_title, subtitle = R.string.help_tip_settings_dark_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_dark_mode_p1_title, description = R.string.help_tip_settings_dark_mode_p1_desc, steps = listOf(R.string.help_tip_settings_dark_mode_p1_s1, R.string.help_tip_settings_dark_mode_p1_s2, R.string.help_tip_settings_dark_mode_p1_s3), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_amoled", title = R.string.help_tip_settings_amoled_title, subtitle = R.string.help_tip_settings_amoled_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Palette), category = HelpCategory.SETTINGS_APPEARANCE,
            deepLink = Screen.SettingsAppearanceScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_amoled_p1_title, description = R.string.help_tip_settings_amoled_p1_desc, steps = listOf(R.string.help_tip_settings_amoled_p1_s1, R.string.help_tip_settings_amoled_p1_s2, R.string.help_tip_settings_amoled_p1_s3, R.string.help_tip_settings_amoled_p1_s4), previewType = PreviewType.THEME_PICKER)), sinceVersion = "4.0.0")
    )

    private val SETTINGS_GENERAL_TIPS = listOf(
        HelpTip(id = "settings_trash_toggle", title = R.string.help_tip_settings_trash_toggle_title, subtitle = R.string.help_tip_settings_trash_toggle_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_trash_toggle_p1_title, description = R.string.help_tip_settings_trash_toggle_p1_desc, steps = listOf(R.string.help_tip_settings_trash_toggle_p1_s1, R.string.help_tip_settings_trash_toggle_p1_s2, R.string.help_tip_settings_trash_toggle_p1_s3), previewType = PreviewType.SETTINGS_GENERAL)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_trash_confirm", title = R.string.help_tip_settings_trash_confirm_title, subtitle = R.string.help_tip_settings_trash_confirm_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_trash_confirm_p1_title, description = R.string.help_tip_settings_trash_confirm_p1_desc, steps = listOf(R.string.help_tip_settings_trash_confirm_p1_s1, R.string.help_tip_settings_trash_confirm_p1_s2, R.string.help_tip_settings_trash_confirm_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_secure_mode", title = R.string.help_tip_settings_secure_mode_title, subtitle = R.string.help_tip_settings_secure_mode_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Lock), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_secure_mode_p1_title, description = R.string.help_tip_settings_secure_mode_p1_desc, steps = listOf(R.string.help_tip_settings_secure_mode_p1_s1, R.string.help_tip_settings_secure_mode_p1_s2, R.string.help_tip_settings_secure_mode_p1_s3, R.string.help_tip_settings_secure_mode_p1_s4), previewType = PreviewType.SETTINGS_GENERAL)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_vibrations", title = R.string.help_tip_settings_vibrations_title, subtitle = R.string.help_tip_settings_vibrations_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SettingsGeneralScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_vibrations_p1_title, description = R.string.help_tip_settings_vibrations_p1_desc, steps = listOf(R.string.help_tip_settings_vibrations_p1_s1, R.string.help_tip_settings_vibrations_p1_s2, R.string.help_tip_settings_vibrations_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_backup_restore", title = R.string.help_tip_settings_backup_restore_title, subtitle = R.string.help_tip_settings_backup_restore_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SettingsBackupScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_settings_backup_restore_p1_title, description = R.string.help_tip_settings_backup_restore_p1_desc),
                TutorialPage(title = R.string.help_tip_settings_backup_restore_p2_title, description = R.string.help_tip_settings_backup_restore_p2_desc, steps = listOf(R.string.help_tip_settings_backup_restore_p2_s1, R.string.help_tip_settings_backup_restore_p2_s2, R.string.help_tip_settings_backup_restore_p2_s3, R.string.help_tip_settings_backup_restore_p2_s4)),
                TutorialPage(title = R.string.help_tip_settings_backup_restore_p3_title, description = R.string.help_tip_settings_backup_restore_p3_desc, steps = listOf(R.string.help_tip_settings_backup_restore_p3_s1, R.string.help_tip_settings_backup_restore_p3_s2, R.string.help_tip_settings_backup_restore_p3_s3))
            ), sinceVersion = "5.0.0"),
        HelpTip(id = "settings_slideshow", title = R.string.help_tip_settings_slideshow_title, subtitle = R.string.help_tip_settings_slideshow_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Slideshow), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.SlideshowSettingsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_slideshow_p1_title, description = R.string.help_tip_settings_slideshow_p1_desc, steps = listOf(R.string.help_tip_settings_slideshow_p1_s1, R.string.help_tip_settings_slideshow_p1_s2, R.string.help_tip_settings_slideshow_p1_s3))), sinceVersion = "5.1.0"),
        HelpTip(id = "settings_date_format", title = R.string.help_tip_settings_date_format_title, subtitle = R.string.help_tip_settings_date_format_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.CalendarMonth), category = HelpCategory.SETTINGS_GENERAL,
            deepLink = Screen.DateFormatScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_date_format_p1_title, description = R.string.help_tip_settings_date_format_p1_desc, steps = listOf(R.string.help_tip_settings_date_format_p1_s1, R.string.help_tip_settings_date_format_p1_s2, R.string.help_tip_settings_date_format_p1_s3))), sinceVersion = "5.1.0")
    )

    private val SETTINGS_NAV_TIPS = listOf(
        HelpTip(id = "settings_launch_screen", title = R.string.help_tip_settings_launch_screen_title, subtitle = R.string.help_tip_settings_launch_screen_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_launch_screen_p1_title, description = R.string.help_tip_settings_launch_screen_p1_desc, steps = listOf(R.string.help_tip_settings_launch_screen_p1_s1, R.string.help_tip_settings_launch_screen_p1_s2, R.string.help_tip_settings_launch_screen_p1_s3))), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_old_navbar", title = R.string.help_tip_settings_old_navbar_title, subtitle = R.string.help_tip_settings_old_navbar_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_old_navbar_p1_title, description = R.string.help_tip_settings_old_navbar_p1_desc, steps = listOf(R.string.help_tip_settings_old_navbar_p1_s1, R.string.help_tip_settings_old_navbar_p1_s2, R.string.help_tip_settings_old_navbar_p1_s3), previewType = PreviewType.NAV_BAR_PREVIEW)), sinceVersion = "4.0.0"),
        HelpTip(id = "settings_selection_titles", title = R.string.help_tip_settings_selection_titles_title, subtitle = R.string.help_tip_settings_selection_titles_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = Screen.SettingsNavigationScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_settings_selection_titles_p1_title, description = R.string.help_tip_settings_selection_titles_p1_desc, steps = listOf(R.string.help_tip_settings_selection_titles_p1_s1, R.string.help_tip_settings_selection_titles_p1_s2, R.string.help_tip_settings_selection_titles_p1_s3, R.string.help_tip_settings_selection_titles_p1_s4))), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_selection_actions", title = R.string.help_tip_settings_selection_actions_title, subtitle = R.string.help_tip_settings_selection_actions_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SETTINGS_NAVIGATION,
            deepLink = Screen.SettingsSelectionActionsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_settings_selection_actions_p1_title, description = R.string.help_tip_settings_selection_actions_p1_desc),
                TutorialPage(title = R.string.help_tip_settings_selection_actions_p2_title, description = R.string.help_tip_settings_selection_actions_p2_desc, steps = listOf(R.string.help_tip_settings_selection_actions_p2_s1, R.string.help_tip_settings_selection_actions_p2_s2, R.string.help_tip_settings_selection_actions_p2_s3, R.string.help_tip_settings_selection_actions_p2_s4))
            ), sinceVersion = "4.0.0")
    )

    private val SETTINGS_SMART_TIPS = listOf(
        HelpTip(id = "settings_ai_models", title = R.string.help_tip_settings_ai_models_title, subtitle = R.string.help_tip_settings_ai_models_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.AutoAwesome), category = HelpCategory.SETTINGS_SMART,
            deepLink = Screen.AIModelsManagerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_settings_ai_models_p1_title, description = R.string.help_tip_settings_ai_models_p1_desc),
                TutorialPage(title = R.string.help_tip_settings_ai_models_p2_title, description = R.string.help_tip_settings_ai_models_p2_desc, steps = listOf(R.string.help_tip_settings_ai_models_p2_s1, R.string.help_tip_settings_ai_models_p2_s2, R.string.help_tip_settings_ai_models_p2_s3), actionLabel = R.string.help_action_open_settings)
            ), sinceVersion = "4.1.0"),
        HelpTip(id = "settings_edit_backups", title = R.string.help_tip_settings_edit_backups_title, subtitle = R.string.help_tip_settings_edit_backups_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.SettingsBackupRestore), category = HelpCategory.SETTINGS_SMART,
            deepLink = Screen.EditBackupsViewerScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_settings_edit_backups_p1_title, description = R.string.help_tip_settings_edit_backups_p1_desc),
                TutorialPage(title = R.string.help_tip_settings_edit_backups_p2_title, description = R.string.help_tip_settings_edit_backups_p2_desc, steps = listOf(R.string.help_tip_settings_edit_backups_p2_s1, R.string.help_tip_settings_edit_backups_p2_s2, R.string.help_tip_settings_edit_backups_p2_s3))
            ), sinceVersion = "4.0.0")
    )

    private val SETTINGS_SECURITY_TIPS = listOf(
        HelpTip(id = "security_sandbox", title = R.string.help_tip_security_sandbox_title, subtitle = R.string.help_tip_security_sandbox_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Security), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = Screen.SettingsSecurityScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_security_sandbox_p1_title, description = R.string.help_tip_security_sandbox_p1_desc),
                TutorialPage(title = R.string.help_tip_security_sandbox_p2_title, description = R.string.help_tip_security_sandbox_p2_desc, steps = listOf(R.string.help_tip_security_sandbox_p2_s1, R.string.help_tip_security_sandbox_p2_s2, R.string.help_tip_security_sandbox_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_encrypted_storage", title = R.string.help_tip_security_encrypted_storage_title, subtitle = R.string.help_tip_security_encrypted_storage_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Storage), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = Screen.SettingsSecurityScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_security_encrypted_storage_p1_title, description = R.string.help_tip_security_encrypted_storage_p1_desc),
                TutorialPage(title = R.string.help_tip_security_encrypted_storage_p2_title, description = R.string.help_tip_security_encrypted_storage_p2_desc, steps = listOf(R.string.help_tip_security_encrypted_storage_p2_s1, R.string.help_tip_security_encrypted_storage_p2_s2, R.string.help_tip_security_encrypted_storage_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_private_folder", title = R.string.help_tip_security_private_folder_title, subtitle = R.string.help_tip_security_private_folder_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Shield), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = Screen.SettingsSecurityScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_security_private_folder_p1_title, description = R.string.help_tip_security_private_folder_p1_desc),
                TutorialPage(title = R.string.help_tip_security_private_folder_p2_title, description = R.string.help_tip_security_private_folder_p2_desc, steps = listOf(R.string.help_tip_security_private_folder_p2_s1, R.string.help_tip_security_private_folder_p2_s2, R.string.help_tip_security_private_folder_p2_s3))
            ), sinceVersion = "4.2.2"),
        HelpTip(id = "security_advanced_protection", title = R.string.help_tip_security_aapm_title, subtitle = R.string.help_tip_security_aapm_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Security), category = HelpCategory.SETTINGS_SECURITY,
            deepLink = Screen.SettingsSecurityScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_security_aapm_p1_title, description = R.string.help_tip_security_aapm_p1_desc),
                TutorialPage(title = R.string.help_tip_security_aapm_p2_title, description = R.string.help_tip_security_aapm_p2_desc, steps = listOf(R.string.help_tip_security_aapm_p2_s1, R.string.help_tip_security_aapm_p2_s2, R.string.help_tip_security_aapm_p2_s3))
            ), sinceVersion = "5.0.0")
    )
    // endregion

    // region Gestures & Selection
    private val GESTURE_TIPS = listOf(
        HelpTip(id = "gesture_pinch_grid", title = R.string.help_tip_gesture_pinch_grid_title, subtitle = R.string.help_tip_gesture_pinch_grid_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.ZoomIn), category = HelpCategory.GESTURES,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_gesture_pinch_grid_p1_title, description = R.string.help_tip_gesture_pinch_grid_p1_desc, previewType = PreviewType.PINCH_ZOOM_GRID),
                TutorialPage(title = R.string.help_tip_gesture_pinch_grid_p2_title, description = R.string.help_tip_gesture_pinch_grid_p2_desc, previewType = PreviewType.PINCH_ZOOM_GRID)
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_swipe_viewer", title = R.string.help_tip_gesture_swipe_viewer_title, subtitle = R.string.help_tip_gesture_swipe_viewer_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = R.string.help_tip_gesture_swipe_viewer_p1_title, description = R.string.help_tip_gesture_swipe_viewer_p1_desc, previewType = PreviewType.MEDIA_VIEWER)), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_long_press", title = R.string.help_tip_gesture_long_press_title, subtitle = R.string.help_tip_gesture_long_press_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = R.string.help_tip_gesture_long_press_p1_title, description = R.string.help_tip_gesture_long_press_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0"),
        HelpTip(id = "gesture_multi_select", title = R.string.help_tip_gesture_multi_select_title, subtitle = R.string.help_tip_gesture_multi_select_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.GESTURES,
            pages = listOf(TutorialPage(title = R.string.help_tip_gesture_multi_select_p1_title, description = R.string.help_tip_gesture_multi_select_p1_desc, previewType = PreviewType.TIMELINE_GRID)), sinceVersion = "4.0.0")
    )

    private val SELECTION_TIPS = listOf(
        HelpTip(id = "selection_sheet", title = R.string.help_tip_selection_sheet_title, subtitle = R.string.help_tip_selection_sheet_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(
                TutorialPage(title = R.string.help_tip_selection_sheet_p1_title, description = R.string.help_tip_selection_sheet_p1_desc),
                TutorialPage(title = R.string.help_tip_selection_sheet_p2_title, description = R.string.help_tip_selection_sheet_p2_desc, steps = listOf(R.string.help_tip_selection_sheet_p2_s1, R.string.help_tip_selection_sheet_p2_s2, R.string.help_tip_selection_sheet_p2_s3, R.string.help_tip_selection_sheet_p2_s4))
            ), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_batch_share", title = R.string.help_tip_selection_batch_share_title, subtitle = R.string.help_tip_selection_batch_share_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_selection_batch_share_p1_title, description = R.string.help_tip_selection_batch_share_p1_desc, steps = listOf(R.string.help_tip_selection_batch_share_p1_s1, R.string.help_tip_selection_batch_share_p1_s2, R.string.help_tip_selection_batch_share_p1_s3, R.string.help_tip_selection_batch_share_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_batch_delete", title = R.string.help_tip_selection_batch_delete_title, subtitle = R.string.help_tip_selection_batch_delete_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SELECTION_ACTIONS,
            pages = listOf(TutorialPage(title = R.string.help_tip_selection_batch_delete_p1_title, description = R.string.help_tip_selection_batch_delete_p1_desc, steps = listOf(R.string.help_tip_selection_batch_delete_p1_s1, R.string.help_tip_selection_batch_delete_p1_s2, R.string.help_tip_selection_batch_delete_p1_s3, R.string.help_tip_selection_batch_delete_p1_s4))), sinceVersion = "4.0.0"),
        HelpTip(id = "selection_right_align", title = R.string.help_tip_selection_right_align_title, subtitle = R.string.help_tip_selection_right_align_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.SELECTION_ACTIONS,
            deepLink = Screen.SettingsSelectionActionsScreen(),
            pages = listOf(TutorialPage(title = R.string.help_tip_selection_right_align_p1_title, description = R.string.help_tip_selection_right_align_p1_desc, steps = listOf(R.string.help_tip_selection_right_align_p1_s1, R.string.help_tip_selection_right_align_p1_s2, R.string.help_tip_selection_right_align_p1_s3))), sinceVersion = "4.2.1")
    )
    // endregion

    // region Accessibility
    private val ACCESSIBILITY_TIPS = listOf(
        HelpTip(id = "accessibility_font", title = R.string.help_tip_accessibility_font_title, subtitle = R.string.help_tip_accessibility_font_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = R.string.help_tip_accessibility_font_p1_title, description = R.string.help_tip_accessibility_font_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_media_picker", title = R.string.help_tip_advanced_media_picker_title, subtitle = R.string.help_tip_advanced_media_picker_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = R.string.help_tip_advanced_media_picker_p1_title, description = R.string.help_tip_advanced_media_picker_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_standalone", title = R.string.help_tip_advanced_standalone_title, subtitle = R.string.help_tip_advanced_standalone_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = R.string.help_tip_advanced_standalone_p1_title, description = R.string.help_tip_advanced_standalone_p1_desc)), sinceVersion = "4.0.0"),
        HelpTip(id = "advanced_wallpaper", title = R.string.help_tip_advanced_wallpaper_title, subtitle = R.string.help_tip_advanced_wallpaper_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Collections), category = HelpCategory.ACCESSIBILITY,
            pages = listOf(TutorialPage(title = R.string.help_tip_advanced_wallpaper_p1_title, description = R.string.help_tip_advanced_wallpaper_p1_desc, steps = listOf(R.string.help_tip_advanced_wallpaper_p1_s1, R.string.help_tip_advanced_wallpaper_p1_s2, R.string.help_tip_advanced_wallpaper_p1_s3, R.string.help_tip_advanced_wallpaper_p1_s4, R.string.help_tip_advanced_wallpaper_p1_s5))), sinceVersion = "4.0.0"),
        HelpTip(id = "accessibility_remote_settings", title = R.string.help_tip_accessibility_remote_settings_title, subtitle = R.string.help_tip_accessibility_remote_settings_subtitle,
            icon = HelpIcon.ofVector(Icons.Outlined.Settings), category = HelpCategory.ACCESSIBILITY,
            deepLink = Screen.SettingsScreen(),
            pages = listOf(
                TutorialPage(title = R.string.help_tip_accessibility_remote_settings_p1_title, description = R.string.help_tip_accessibility_remote_settings_p1_desc, steps = listOf(R.string.help_tip_accessibility_remote_settings_p1_s1, R.string.help_tip_accessibility_remote_settings_p1_s2, R.string.help_tip_accessibility_remote_settings_p1_s3, R.string.help_tip_accessibility_remote_settings_p1_s4), previewType = PreviewType.SETTINGS_GENERAL)
            ), sinceVersion = "5.1.1")
    )
    // endregion

    // region Aggregation
    private val ALL_TIPS: List<HelpTip> = BASICS_TIPS + NAVIGATION_TIPS + PERSONALIZATION_TIPS +
        TIMELINE_ALBUM_TIPS + VIEWING_TIPS + VIEWER_ACTION_TIPS + VIEWER_SETTINGS_TIPS +
        EDITING_TIPS + SEARCH_TIPS + AI_TIPS + ALBUM_TIPS + VAULT_TIPS + CLOUD_TIPS +
        FAV_TRASH_TIPS + LOCATION_TIPS + METADATA_TIPS +
        SETTINGS_APPEARANCE_TIPS + SETTINGS_GENERAL_TIPS + SETTINGS_NAV_TIPS + SETTINGS_SMART_TIPS +
        SETTINGS_SECURITY_TIPS + GESTURE_TIPS + SELECTION_TIPS + ACCESSIBILITY_TIPS

    // endregion
}
