/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.ai_models_manager
import com.dot.gallery.feature.help.generated.resources.album_sections_title
import com.dot.gallery.feature.help.generated.resources.allow_gif_animation_title
import com.dot.gallery.feature.help.generated.resources.allow_vibrations
import com.dot.gallery.feature.help.generated.resources.amoled_mode_title
import com.dot.gallery.feature.help.generated.resources.auto_contrast
import com.dot.gallery.feature.help.generated.resources.auto_hide_navigationbar
import com.dot.gallery.feature.help.generated.resources.auto_hide_on_video_play
import com.dot.gallery.feature.help.generated.resources.auto_hide_searchbar
import com.dot.gallery.feature.help.generated.resources.auto_open_search_keyboard
import com.dot.gallery.feature.help.generated.resources.auto_play_video
import com.dot.gallery.feature.help.generated.resources.backup_export
import com.dot.gallery.feature.help.generated.resources.backup_import
import com.dot.gallery.feature.help.generated.resources.categories
import com.dot.gallery.feature.help.generated.resources.categories_exclude_videos
import com.dot.gallery.feature.help.generated.resources.change_app_logo
import com.dot.gallery.feature.help.generated.resources.change_app_name
import com.dot.gallery.feature.help.generated.resources.cloud_free_space_auto
import com.dot.gallery.feature.help.generated.resources.dark_media_viewer_title
import com.dot.gallery.feature.help.generated.resources.date_header
import com.dot.gallery.feature.help.generated.resources.date_headers_title
import com.dot.gallery.feature.help.generated.resources.default_image_editor
import com.dot.gallery.feature.help.generated.resources.default_video_editor
import com.dot.gallery.feature.help.generated.resources.disable_smoothing_title
import com.dot.gallery.feature.help.generated.resources.edit_backups
import com.dot.gallery.feature.help.generated.resources.fancy_blur
import com.dot.gallery.feature.help.generated.resources.full_brightness_view_title
import com.dot.gallery.feature.help.generated.resources.group_burst_sequences_title
import com.dot.gallery.feature.help.generated.resources.group_cloud_local_title
import com.dot.gallery.feature.help.generated.resources.group_edited_copies_title
import com.dot.gallery.feature.help.generated.resources.group_method_title
import com.dot.gallery.feature.help.generated.resources.group_raw_jpg_title
import com.dot.gallery.feature.help.generated.resources.group_similar_media_title
import com.dot.gallery.feature.help.generated.resources.hdr_video_playback
import com.dot.gallery.feature.help.generated.resources.help_cat_personalization
import com.dot.gallery.feature.help.generated.resources.help_cat_selection_actions
import com.dot.gallery.feature.help.generated.resources.help_cat_settings_appearance
import com.dot.gallery.feature.help.generated.resources.help_cat_settings_general
import com.dot.gallery.feature.help.generated.resources.help_cat_settings_navigation
import com.dot.gallery.feature.help.generated.resources.help_cat_settings_security
import com.dot.gallery.feature.help.generated.resources.help_cat_settings_smart
import com.dot.gallery.feature.help.generated.resources.help_cat_timeline_albums
import com.dot.gallery.feature.help.generated.resources.help_cat_viewer_settings
import com.dot.gallery.feature.help.generated.resources.help_search_section_settings
import com.dot.gallery.feature.help.generated.resources.location_categories_title
import com.dot.gallery.feature.help.generated.resources.long_press_action_title
import com.dot.gallery.feature.help.generated.resources.map_appearance_title
import com.dot.gallery.feature.help.generated.resources.media_type_albums_setting_title
import com.dot.gallery.feature.help.generated.resources.merge_albums_by_name
import com.dot.gallery.feature.help.generated.resources.old_navbar
import com.dot.gallery.feature.help.generated.resources.refresh_metadata
import com.dot.gallery.feature.help.generated.resources.secure_mode_title
import com.dot.gallery.feature.help.generated.resources.security_encryption_status
import com.dot.gallery.feature.help.generated.resources.security_metadata_isolation
import com.dot.gallery.feature.help.generated.resources.security_private_folder
import com.dot.gallery.feature.help.generated.resources.security_sandboxed_decode
import com.dot.gallery.feature.help.generated.resources.set_default_screen
import com.dot.gallery.feature.help.generated.resources.settings_dark_mode_title
import com.dot.gallery.feature.help.generated.resources.settings_follow_system_theme_title
import com.dot.gallery.feature.help.generated.resources.settings_title
import com.dot.gallery.feature.help.generated.resources.settings_trash_confirmation_title
import com.dot.gallery.feature.help.generated.resources.settings_trash_title
import com.dot.gallery.feature.help.generated.resources.shared_elements
import com.dot.gallery.feature.help.generated.resources.show_date_header
import com.dot.gallery.feature.help.generated.resources.show_favorite_button
import com.dot.gallery.feature.help.generated.resources.show_selection_titles
import com.dot.gallery.feature.help.generated.resources.slideshow_include_gifs
import com.dot.gallery.feature.help.generated.resources.slideshow_include_videos
import com.dot.gallery.feature.help.generated.resources.slideshow_interval
import com.dot.gallery.feature.help.generated.resources.slideshow_ken_burns
import com.dot.gallery.feature.help.generated.resources.slideshow_loop
import com.dot.gallery.feature.help.generated.resources.slideshow_random
import com.dot.gallery.feature.help.generated.resources.slideshow_reverse
import com.dot.gallery.feature.help.generated.resources.slideshow_transition
import com.dot.gallery.feature.help.generated.resources.smart_features_include_ignored_albums
import com.dot.gallery.feature.help.generated.resources.smart_scan_manager_title
import com.dot.gallery.feature.help.generated.resources.timeline_layout_type
import com.dot.gallery.feature.help.generated.resources.use_system_font_title
import com.dot.gallery.feature.help.generated.resources.vault_encrypt_behavior
import com.dot.gallery.feature.help.generated.resources.vault_gate_lock_all
import com.dot.gallery.feature.help.generated.resources.version_name_format
import com.dot.gallery.feature.help.generated.resources.video_surface_rebind
import com.dot.gallery.feature.help.generated.resources.visual_search_title

/**
 * Builds the unified in-memory search index consumed by both the Help screen
 * and the timeline media search.
 *
 * String resolution goes through compose-resources `getString` (suspend) so the
 * index works without a `Context`; callers run it in a coroutine (`produceState`
 * in composables, `viewModelScope` in the search ViewModel).
 */
object HelpSearchIndex {

    suspend fun build(
        releases: List<ReleaseNotes>,
        settingsEntries: Map<String, List<String>> = SettingsSearchRegistry.snapshot(),
    ): List<HelpSearchItem> {
        val items = ArrayList<HelpSearchItem>(320)
        items += tipItems()
        val settingEntries = settingItems()
        val toggleEntries = toggleItems()
        items += settingEntries
        items += toggleEntries
        items += harvestedToggleItems(settingsEntries, settingEntries + toggleEntries)
        items += quickActionItems()
        items += changelogItems(releases)
        return items
    }

    /**
     * Toggles discovered at runtime from [settingsEntries] (populated when the
     * user opens a settings sub-screen), deduped against the curated [settingItems] +
     * [toggleItems] by route+title. This auto-captures toggles that are not in the
     * curated catalog — including ones added in the future — without hand-editing.
     */
    private suspend fun harvestedToggleItems(
        settingsEntries: Map<String, List<String>>,
        existing: List<HelpSearchItem>,
    ): List<HelpSearchItem> {
        val seen = existing
            .filter { it.kind == HelpSearchKind.SETTING }
            .mapTo(HashSet()) { "${it.route}|${it.title}" }
        val section = getString(Res.string.help_search_section_settings)
        val out = ArrayList<HelpSearchItem>()
        settingsEntries.forEach { (route, titles) ->
            titles.forEach { title ->
                if (seen.add("$route|$title")) {
                    out += HelpSearchItem(
                        kind = HelpSearchKind.SETTING,
                        id = "harvested_${route}_${title.hashCode()}",
                        title = title,
                        subtitle = section,
                        keywords = "setting toggle option preference $title",
                        icon = HelpCategory.SETTINGS_GENERAL.icon(),
                        route = HelpRoute.Raw(route),
                    )
                }
            }
        }
        return out
    }

    /** Just the tip entries — used by the lightweight timeline integration. */
    suspend fun buildTips(): List<HelpSearchItem> = tipItems()

    /**
     * Every settings screen as a directly-navigable SETTING entry. Titles reuse
     * the existing `help_cat_*` category strings and the real screen routes, so
     * search jumps straight into the live settings screen.
     */
    private suspend fun settingItems(): List<HelpSearchItem> {
        data class Entry(
            val id: String,
            val titleRes: StringResource,
            val route: HelpRoute,
            val category: HelpCategory
        )
        val entries = listOf(
            Entry("settings_root", Res.string.settings_title, HelpRoute.Target.SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Entry("settings_appearance", Res.string.help_cat_settings_appearance, HelpRoute.Target.SETTINGS_APPEARANCE, HelpCategory.SETTINGS_APPEARANCE),
            Entry("settings_general", Res.string.help_cat_settings_general, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Entry("settings_timeline_albums", Res.string.help_cat_timeline_albums, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Entry("settings_media_viewer", Res.string.help_cat_viewer_settings, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Entry("settings_navigation", Res.string.help_cat_settings_navigation, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            Entry("settings_smart", Res.string.help_cat_settings_smart, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Entry("settings_security", Res.string.help_cat_settings_security, HelpRoute.Target.SETTINGS_SECURITY, HelpCategory.SETTINGS_SECURITY),
            Entry("settings_selection_actions", Res.string.help_cat_selection_actions, HelpRoute.Target.SETTINGS_SELECTION_ACTIONS, HelpCategory.SELECTION_ACTIONS),
            Entry("settings_color_palette", Res.string.help_cat_personalization, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Entry("settings_ai_models", Res.string.ai_models_manager, HelpRoute.Target.AI_MODELS_MANAGER, HelpCategory.SETTINGS_SMART),
        )
        val section = getString(Res.string.help_search_section_settings)
        return entries.map { e ->
            val title = getString(e.titleRes)
            HelpSearchItem(
                kind = HelpSearchKind.SETTING,
                id = e.id,
                title = title,
                subtitle = section,
                keywords = "settings preferences options $title",
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
    private suspend fun toggleItems(): List<HelpSearchItem> {
        data class Toggle(val titleRes: StringResource, val route: HelpRoute, val category: HelpCategory)
        val settingsSection = getString(Res.string.help_search_section_settings)
        val toggles = listOf(
            // General
            Toggle(Res.string.settings_trash_title, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.settings_trash_confirmation_title, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.secure_mode_title, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.allow_vibrations, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.change_app_name, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.change_app_logo, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.vault_encrypt_behavior, HelpRoute.Target.SETTINGS_GENERAL, HelpCategory.SETTINGS_GENERAL),
            // Appearance and color
            Toggle(Res.string.settings_follow_system_theme_title, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.settings_dark_mode_title, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.amoled_mode_title, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.fancy_blur, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.auto_contrast, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.shared_elements, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.use_system_font_title, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            Toggle(Res.string.map_appearance_title, HelpRoute.Target.COLOR_PALETTE, HelpCategory.SETTINGS_APPEARANCE),
            // Navigation
            Toggle(Res.string.set_default_screen, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            Toggle(Res.string.old_navbar, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            Toggle(Res.string.auto_open_search_keyboard, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            Toggle(Res.string.auto_hide_navigationbar, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            Toggle(Res.string.show_selection_titles, HelpRoute.Target.SETTINGS_NAVIGATION, HelpCategory.SETTINGS_NAVIGATION),
            // Media viewer
            Toggle(Res.string.full_brightness_view_title, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.show_date_header, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.show_favorite_button, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.default_image_editor, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.default_video_editor, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.disable_smoothing_title, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.long_press_action_title, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.auto_hide_on_video_play, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.auto_play_video, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.video_surface_rebind, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.hdr_video_playback, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.visual_search_title, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            Toggle(Res.string.dark_media_viewer_title, HelpRoute.Target.SETTINGS_MEDIA_VIEWER, HelpCategory.VIEWER_SETTINGS),
            // Security
            Toggle(Res.string.security_metadata_isolation, HelpRoute.Target.SETTINGS_SECURITY, HelpCategory.SETTINGS_SECURITY),
            Toggle(Res.string.security_sandboxed_decode, HelpRoute.Target.SETTINGS_SECURITY, HelpCategory.SETTINGS_SECURITY),
            Toggle(Res.string.security_encryption_status, HelpRoute.Target.SETTINGS_SECURITY, HelpCategory.SETTINGS_SECURITY),
            Toggle(Res.string.security_private_folder, HelpRoute.Target.SETTINGS_SECURITY, HelpCategory.SETTINGS_SECURITY),
            Toggle(Res.string.vault_gate_lock_all, HelpRoute.Target.VAULT, HelpCategory.VAULT),
            // Timeline & albums
            Toggle(Res.string.timeline_layout_type, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.auto_hide_searchbar, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_similar_media_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.allow_gif_animation_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_raw_jpg_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_edited_copies_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_burst_sequences_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_cloud_local_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.merge_albums_by_name, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.album_sections_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.media_type_albums_setting_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.date_headers_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.location_categories_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.group_method_title, HelpRoute.Target.SETTINGS_TIMELINE_ALBUMS, HelpCategory.TIMELINE_ALBUMS),
            Toggle(Res.string.date_header, HelpRoute.Target.DATE_FORMAT, HelpCategory.TIMELINE_ALBUMS),
            // Slideshow
            Toggle(Res.string.slideshow_interval, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_transition, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_ken_burns, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_random, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_reverse, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_loop, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_include_gifs, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.slideshow_include_videos, HelpRoute.Target.SLIDESHOW_SETTINGS, HelpCategory.SETTINGS_GENERAL),
            // Smart features
            Toggle(Res.string.smart_features_include_ignored_albums, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.ai_models_manager, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.categories, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.smart_scan_manager_title, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.categories_exclude_videos, HelpRoute.Target.CATEGORIES_SETTINGS, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.refresh_metadata, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            Toggle(Res.string.edit_backups, HelpRoute.Target.SETTINGS_SMART_FEATURES, HelpCategory.SETTINGS_SMART),
            // Cloud
            Toggle(Res.string.cloud_free_space_auto, HelpRoute.Target.FREE_UP_SPACE, HelpCategory.CLOUD_SYNC),
            // Backup
            Toggle(Res.string.backup_export, HelpRoute.Target.SETTINGS_BACKUP, HelpCategory.SETTINGS_GENERAL),
            Toggle(Res.string.backup_import, HelpRoute.Target.SETTINGS_BACKUP, HelpCategory.SETTINGS_GENERAL),
        )
        return toggles.map { t ->
            val title = getString(t.titleRes)
            HelpSearchItem(
                kind = HelpSearchKind.SETTING,
                id = "toggle_$title",
                title = title,
                subtitle = settingsSection,
                keywords = "setting toggle option preference $title",
                icon = t.category.icon(),
                route = t.route,
            )
        }
    }

    private suspend fun tipItems(): List<HelpSearchItem> =
        HelpRepository.getAllTips().map { tip ->
            val keywords = buildString {
                tip.keywords.forEach { append(getString(it)).append(' ') }
                tip.pages.forEach { append(getString(it.title)).append(' ') }
            }.trim()
            HelpSearchItem(
                kind = HelpSearchKind.TIP,
                id = tip.id,
                title = getString(tip.title),
                subtitle = getString(tip.subtitle),
                keywords = keywords,
                icon = tip.icon.vector,
                route = HelpRoute.Tip(tip.id),
            )
        }

    /**
     * Live-navigation shortcuts. Derived from every tip that declares a
     * [QuickAction] plus tips that carry a [HelpTip.deepLink], so search can
     * jump straight to the real screen/setting.
     */
    private suspend fun quickActionItems(): List<HelpSearchItem> {
        val out = ArrayList<HelpSearchItem>()
        HelpRepository.getAllTips().forEach { tip ->
            tip.quickActions.forEach { qa ->
                out += HelpSearchItem(
                    kind = HelpSearchKind.QUICK_ACTION,
                    id = "${tip.id}#${qa.route}",
                    title = getString(qa.label),
                    subtitle = getString(tip.title),
                    keywords = getString(tip.subtitle),
                    icon = qa.icon,
                    route = qa.route,
                )
            }
            if (tip.deepLink != null && tip.quickActions.isEmpty()) {
                out += HelpSearchItem(
                    kind = HelpSearchKind.QUICK_ACTION,
                    id = "${tip.id}#deeplink",
                    title = getString(tip.title),
                    subtitle = getString(tip.subtitle),
                    keywords = "",
                    icon = tip.icon.vector,
                    route = tip.deepLink,
                )
            }
        }
        return out
    }

    private suspend fun changelogItems(releases: List<ReleaseNotes>): List<HelpSearchItem> =
        releases.map { release ->
            HelpSearchItem(
                kind = HelpSearchKind.CHANGELOG,
                id = "release_${release.versionCode}",
                title = getString(Res.string.version_name_format, release.versionName),
                subtitle = release.releaseDate,
                keywords = release.searchKeywords(),
                icon = Icons.Outlined.NewReleases,
                route = HelpRoute.Target.WHATS_NEW,
            )
        }
}
