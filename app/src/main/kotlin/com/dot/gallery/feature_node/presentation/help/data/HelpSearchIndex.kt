/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.Screen

/** The kind of thing a [HelpSearchItem] points at, used for grouping + ranking. */
enum class HelpSearchKind { TIP, SETTING, QUICK_ACTION, CHANGELOG }

/**
 * A flattened, resolved search entry. Strings are pre-resolved (not string-res)
 * so the fuzzy matcher can score them without a [Context].
 */
@Immutable
data class HelpSearchItem(
    val kind: HelpSearchKind,
    val id: String,
    val title: String,
    val subtitle: String,
    /** Extra searchable synonyms/keywords, space-joined. */
    val keywords: String,
    val icon: ImageVector?,
    /** Navigation target route (null = not directly navigable). */
    val route: String?,
) {
    /** Combined text the fuzzy matcher searches over. */
    val searchText: String get() = buildString {
        append(title)
        if (subtitle.isNotEmpty()) append(' ').append(subtitle)
        if (keywords.isNotEmpty()) append(' ').append(keywords)
    }
}

/**
 * Builds the unified in-memory search index consumed by both the Help screen
 * and the timeline media search. Built once from resources and remembered by
 * callers.
 */
object HelpSearchIndex {

    fun build(context: Context): List<HelpSearchItem> {
        val items = ArrayList<HelpSearchItem>(320)
        items += tipItems(context)
        val settingEntries = settingItems(context)
        val toggleEntries = toggleItems(context)
        items += settingEntries
        items += toggleEntries
        items += harvestedToggleItems(context, settingEntries + toggleEntries)
        items += quickActionItems(context)
        items += changelogItems(context)
        return items
    }

    /**
     * Toggles discovered at runtime from [SettingsSearchRegistry] (populated when the
     * user opens a settings sub-screen), deduped against the curated [settingItems] +
     * [toggleItems] by route+title. This auto-captures toggles that are not in the
     * curated catalog — including ones added in the future — without hand-editing.
     */
    private fun harvestedToggleItems(
        context: Context,
        existing: List<HelpSearchItem>,
    ): List<HelpSearchItem> {
        val seen = existing
            .filter { it.kind == HelpSearchKind.SETTING }
            .mapTo(HashSet()) { "${it.route}|${it.title}" }
        val section = context.getString(R.string.help_search_section_settings)
        val out = ArrayList<HelpSearchItem>()
        SettingsSearchRegistry.snapshot().forEach { (route, titles) ->
            titles.forEach { title ->
                if (seen.add("$route|$title")) {
                    out += HelpSearchItem(
                        kind = HelpSearchKind.SETTING,
                        id = "harvested_${route}_${title.hashCode()}",
                        title = title,
                        subtitle = section,
                        keywords = "setting toggle option preference $title",
                        icon = HelpCategory.SETTINGS_GENERAL.icon(),
                        route = route,
                    )
                }
            }
        }
        return out
    }

    /** Just the tip entries — used by the lightweight timeline integration. */
    fun buildTips(context: Context): List<HelpSearchItem> = tipItems(context)

    /**
     * Every settings screen as a directly-navigable SETTING entry. Titles reuse
     * the existing `help_cat_*` category strings and the real [Screen] routes, so
     * search jumps straight into the live settings screen.
     */
    private fun settingItems(context: Context): List<HelpSearchItem> {
        data class Entry(val id: String, val titleRes: Int, val route: String, val category: HelpCategory)
        val entries = listOf(
            Entry("settings_root", R.string.settings_title, Screen.SettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Entry("settings_appearance", R.string.help_cat_settings_appearance, Screen.SettingsAppearanceScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Entry("settings_general", R.string.help_cat_settings_general, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Entry("settings_timeline_albums", R.string.help_cat_timeline_albums, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Entry("settings_media_viewer", R.string.help_cat_viewer_settings, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Entry("settings_navigation", R.string.help_cat_settings_navigation, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            Entry("settings_smart", R.string.help_cat_settings_smart, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Entry("settings_security", R.string.help_cat_settings_security, Screen.SettingsSecurityScreen(), HelpCategory.SETTINGS_SECURITY),
            Entry("settings_selection_actions", R.string.help_cat_selection_actions, Screen.SettingsSelectionActionsScreen(), HelpCategory.SELECTION_ACTIONS),
            Entry("settings_color_palette", R.string.help_cat_personalization, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Entry("settings_ai_models", R.string.ai_models_manager, Screen.AIModelsManagerScreen(), HelpCategory.SETTINGS_SMART),
        )
        return entries.map { e ->
            HelpSearchItem(
                kind = HelpSearchKind.SETTING,
                id = e.id,
                title = context.getString(e.titleRes),
                subtitle = context.getString(R.string.help_search_section_settings),
                keywords = "settings preferences options ${context.getString(e.titleRes)}",
                icon = e.category.icon(),
                route = e.route,
            )
        }
    }

    /**
     * Curated per-toggle SETTING entries so search lands on an individual control
     * (e.g. "AMOLED", "ken burns", "secure mode") and deep-links to the sub-screen
     * that owns it. Titles reuse the exact string resources each settings sub-screen
     * renders, so labels stay in sync with the UI and localisation.
     */
    private fun toggleItems(context: Context): List<HelpSearchItem> {
        data class Toggle(val titleRes: Int, val route: String, val category: HelpCategory)
        val settingsSection = context.getString(R.string.help_search_section_settings)
        val toggles = listOf(
            // General
            Toggle(R.string.settings_trash_title, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.settings_trash_confirmation_title, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.secure_mode_title, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.allow_vibrations, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.change_app_name, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.change_app_logo, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.vault_encrypt_behavior, Screen.SettingsGeneralScreen(), HelpCategory.SETTINGS_GENERAL),
            // Appearance and color
            Toggle(R.string.settings_follow_system_theme_title, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.settings_dark_mode_title, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.amoled_mode_title, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.fancy_blur, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.auto_contrast, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.shared_elements, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.use_system_font_title, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            Toggle(R.string.map_appearance_title, Screen.ColorPaletteScreen(), HelpCategory.SETTINGS_APPEARANCE),
            // Navigation
            Toggle(R.string.set_default_screen, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            Toggle(R.string.old_navbar, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            Toggle(R.string.auto_hide_searchbar, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            Toggle(R.string.auto_hide_navigationbar, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            Toggle(R.string.show_selection_titles, Screen.SettingsNavigationScreen(), HelpCategory.SETTINGS_NAVIGATION),
            // Media viewer
            Toggle(R.string.full_brightness_view_title, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.show_date_header, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.show_favorite_button, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.default_image_editor, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.disable_smoothing_title, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.long_press_cutout_title, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.auto_hide_on_video_play, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.auto_play_video, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.video_surface_rebind, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            Toggle(R.string.visual_search_title, Screen.SettingsMediaViewerScreen(), HelpCategory.VIEWER_SETTINGS),
            // Security
            Toggle(R.string.security_metadata_isolation, Screen.SettingsSecurityScreen(), HelpCategory.SETTINGS_SECURITY),
            Toggle(R.string.security_sandboxed_decode, Screen.SettingsSecurityScreen(), HelpCategory.SETTINGS_SECURITY),
            Toggle(R.string.security_encryption_status, Screen.SettingsSecurityScreen(), HelpCategory.SETTINGS_SECURITY),
            Toggle(R.string.security_private_folder, Screen.SettingsSecurityScreen(), HelpCategory.SETTINGS_SECURITY),
            // Timeline & albums
            Toggle(R.string.timeline_layout_type, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_similar_media_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.allow_gif_animation_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_raw_jpg_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_edited_copies_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_burst_sequences_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_cloud_local_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.merge_albums_by_name, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.album_sections_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.pinned_albums_as_grid_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.media_type_albums_setting_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.date_headers_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.group_method_title, Screen.SettingsTimelineAlbumsScreen(), HelpCategory.TIMELINE_ALBUMS),
            Toggle(R.string.date_header, Screen.DateFormatScreen(), HelpCategory.TIMELINE_ALBUMS),
            // Slideshow
            Toggle(R.string.slideshow_interval, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_transition, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_ken_burns, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_random, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_reverse, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_loop, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_include_gifs, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.slideshow_include_videos, Screen.SlideshowSettingsScreen(), HelpCategory.SETTINGS_GENERAL),
            // Smart features
            Toggle(R.string.smart_features_include_ignored_albums, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Toggle(R.string.ai_models_manager, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Toggle(R.string.categories, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Toggle(R.string.smart_scan_manager_title, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Toggle(R.string.refresh_metadata, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            Toggle(R.string.edit_backups, Screen.SettingsSmartFeaturesScreen(), HelpCategory.SETTINGS_SMART),
            // Backup
            Toggle(R.string.backup_export, Screen.SettingsBackupScreen(), HelpCategory.SETTINGS_GENERAL),
            Toggle(R.string.backup_import, Screen.SettingsBackupScreen(), HelpCategory.SETTINGS_GENERAL),
        )
        return toggles.map { t ->
            val title = context.getString(t.titleRes)
            HelpSearchItem(
                kind = HelpSearchKind.SETTING,
                id = "toggle_${context.resources.getResourceEntryName(t.titleRes)}",
                title = title,
                subtitle = settingsSection,
                keywords = "setting toggle option preference $title",
                icon = t.category.icon(),
                route = t.route,
            )
        }
    }

    private fun tipItems(context: Context): List<HelpSearchItem> =
        HelpRepository.getAllTips().map { tip ->
            val keywords = buildString {
                tip.keywords.forEach { append(context.getString(it)).append(' ') }
                tip.pages.forEach { append(context.getString(it.title)).append(' ') }
            }.trim()
            HelpSearchItem(
                kind = HelpSearchKind.TIP,
                id = tip.id,
                title = context.getString(tip.title),
                subtitle = context.getString(tip.subtitle),
                keywords = keywords,
                icon = tip.icon.vector,
                route = Screen.TutorialDetailScreen.tipId(tip.id),
            )
        }

    /**
     * Live-navigation shortcuts. Derived from every tip that declares a
     * [QuickAction] plus tips that carry a [HelpTip.deepLink], so search can
     * jump straight to the real screen/setting.
     */
    private fun quickActionItems(context: Context): List<HelpSearchItem> {
        val out = ArrayList<HelpSearchItem>()
        HelpRepository.getAllTips().forEach { tip ->
            tip.quickActions.forEach { qa ->
                out += HelpSearchItem(
                    kind = HelpSearchKind.QUICK_ACTION,
                    id = "${tip.id}#${qa.route}",
                    title = context.getString(qa.label),
                    subtitle = context.getString(tip.title),
                    keywords = context.getString(tip.subtitle),
                    icon = qa.icon,
                    route = qa.route,
                )
            }
            if (tip.deepLink != null && tip.quickActions.isEmpty()) {
                out += HelpSearchItem(
                    kind = HelpSearchKind.QUICK_ACTION,
                    id = "${tip.id}#deeplink",
                    title = context.getString(tip.title),
                    subtitle = context.getString(tip.subtitle),
                    keywords = "",
                    icon = tip.icon.vector,
                    route = tip.deepLink,
                )
            }
        }
        return out
    }

    private fun changelogItems(context: Context): List<HelpSearchItem> =
        HelpRepository.getAllReleases(context).map { release ->
            HelpSearchItem(
                kind = HelpSearchKind.CHANGELOG,
                id = "release_${release.versionCode}",
                title = context.getString(R.string.version_name_format, release.versionName),
                subtitle = release.releaseDate,
                keywords = release.searchKeywords(),
                icon = Icons.Outlined.NewReleases,
                route = Screen.WhatsNewScreen(),
            )
        }
}
