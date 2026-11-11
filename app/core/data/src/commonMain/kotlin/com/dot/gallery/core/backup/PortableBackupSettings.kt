/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.backup

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Explicit allowlist for preferences that represent portable user choices.
 *
 * Anything not listed here is deliberately excluded. In particular, this prevents backups from
 * carrying vault credentials and lockout state, setup/operational markers, search history, or
 * keys introduced later without an explicit portability review.
 */
object PortableBackupSettings {
    private val booleanNames = setOf(
        "hide_timeline_on_album",
        "album_group_by_date",
        "merge_albums_by_name",
        "album_sections_enabled",
        "show_pinned_albums",
        "show_media_type_albums",
        "update_modified_date",
        "slideshow_random_order",
        "slideshow_reverse_order",
        "slideshow_include_gifs",
        "slideshow_include_videos",
        "slideshow_loop",
        "slideshow_ken_burns",
        "smart_features_include_ignored_albums",
        "enable_trashcan",
        "enable_trashcan_confirmation",
        "force_theme",
        "dark_mode",
        "amoled_mode",
        "secure_mode",
        "timeline_group_by_month",
        "timeline_group_by_year",
        "group_similar_media",
        "group_raw_jpg",
        "group_edited_copies",
        "group_burst_sequences",
        "group_cloud_local",
        "use_system_font",
        "allow_blur",
        "auto_contrast",
        "disable_smoothing",
        "long_press_cutout",
        "old_navbar",
        "allow_vibrations",
        "auto_hide_searchbar",
        "auto_open_search_keyboard",
        "auto_hide_navigationbar",
        "full_brightness_view",
        "tap_sides_to_navigate",
        "auto_hide_on_video_play",
        "no_classification",
        "categories_exclude_videos",
        "video_autoplay",
        "video_surface_rebind",
        "shared_elements",
        "media_view_date_header",
        "selection_titles",
        "show_favorite_button",
        "show_searchbar_favorite_button",
        "show_filter_button",
        "show_home_favorites_button",
        "show_home_select_button",
        "favorites_group_by_date",
        "timeline_group_by_date",
        "timeline_month_highlights",
        "timeline_month_recaps",
        "timeline_header_places",
        "timeline_floating_date_label",
        "vault_group_by_date",
        "cloud_archive_group_by_date",
        "location_group_by_date",
        "allow_gif_animation",
        "story_viewer_auto_advance",
        "sandboxed_decode",
        "cloud_offline_cache_on_view",
        "cloud_offline_cache_wifi_only",
        // Portable user choices added with the full-data backup allowlist review.
        "allow_glass",
        "dark_media_viewer",
        "video_hdr_playback",
        "use_media_manager",
        "cloud_sync_on_app_open",
        "library_show_location_categories",
        "gallery_dual_pane",
        "visual_search_enabled",
        "visual_search_allow_vault",
        // Text in photos: only the choices. "ocr_enabled", "ocr_packs" and
        // "ocr_external_model_uris" stay out, so a restore never turns reading on without a
        // pack on this device or carries file grants that do not exist here.
        "ocr_index_library"
    )

    private val intNames = setOf(
        "slideshow_interval_seconds",
        "reencode_lossy_quality",
        "reencode_jxl_effort",
        "cloud_offline_budget_mb"
    )

    private val stringNames = setOf(
        "album_last_sort_obj",
        "album_last_view_obj",
        "album_custom_order",
        "album_media_sort_obj",
        "slideshow_transition",
        "library_shortcuts_layout",
        "theme_color_seed",
        "reencode_quality_mode",
        "frame_export_format",
        "date_header_format",
        "extended_date_header_format",
        "exif_date_format",
        "extended_date_format",
        "default_date_format",
        "weekly_date_format",
        "selection_sheet_config",
        "viewer_action_bar_config",
        "app_name_alias",
        "app_logo_alias",
        "favorite_icon_position",
        "timeline_group_method",
        "albums_group_method",
        "favorites_group_method",
        "vault_group_method",
        "cloud_archive_group_method",
        "location_group_method",
        "map_appearance",
        "timeline_layout_type",
        "default_image_editor",
        "default_video_editor",
        "long_press_action",
        "story_cards_config",
        "story_viewer_duration_seconds",
        "metadata_isolation_mode",
        "vault_encrypt_behavior",
        "cloud_delete_scope",
        "visual_search_provider",
        "visual_search_position",
        "visual_search_convert",
        "visual_search_convert_format",
        "ocr_mode"
    )

    private val stringSetNames = setOf(
        "library_hidden_sections",
        "smart_features_index_on_device_providers",
        "smart_features_removed_model_groups"
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    private val allowedTypes: Map<String, PortableSettingType> = buildMap {
        booleanNames.forEach { put(it, PortableSettingType.BOOLEAN) }
        intNames.forEach { put(it, PortableSettingType.INT) }
        stringNames.forEach { put(it, PortableSettingType.STRING) }
        stringSetNames.forEach { put(it, PortableSettingType.STRING_SET) }
    }

    fun typeFor(name: String): PortableSettingType? = allowedTypes[name]

    fun encode(name: String, value: Any): SettingValue? = when (typeFor(name)) {
        PortableSettingType.BOOLEAN -> (value as? Boolean)?.let { SettingValue("b", it.toString()) }
        PortableSettingType.INT -> (value as? Int)?.let { SettingValue("i", it.toString()) }
        PortableSettingType.LONG -> (value as? Long)?.let { SettingValue("l", it.toString()) }
        PortableSettingType.FLOAT -> (value as? Float)?.takeIf { it.isFinite() }
            ?.let { SettingValue("f", it.toString()) }
        PortableSettingType.DOUBLE -> (value as? Double)?.takeIf { it.isFinite() }
            ?.let { SettingValue("d", it.toString()) }
        PortableSettingType.STRING -> (value as? String)?.let { SettingValue("s", it) }
        PortableSettingType.STRING_SET -> (value as? Set<*>)?.let { raw ->
            SettingValue(
                PortableSettingType.STRING_SET.code,
                json.encodeToString(stringListSerializer, raw.mapNotNull { it as? String })
            )
        }
        null -> null
    }

    fun decode(name: String, setting: SettingValue): DecodedPortableSetting? {
        val expectedType = typeFor(name) ?: return null
        if (setting.type != expectedType.code) return null
        val decoded = when (expectedType) {
            PortableSettingType.BOOLEAN -> when (setting.value) {
                "true" -> true
                "false" -> false
                else -> return null
            }
            PortableSettingType.INT -> setting.value.toIntOrNull() ?: return null
            PortableSettingType.LONG -> setting.value.toLongOrNull() ?: return null
            PortableSettingType.FLOAT -> setting.value.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: return null
            PortableSettingType.DOUBLE -> setting.value.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return null
            PortableSettingType.STRING -> setting.value
            PortableSettingType.STRING_SET ->
                runCatching { json.decodeFromString(stringListSerializer, setting.value).toSet() }
                    .getOrNull() ?: return null
        }
        return DecodedPortableSetting(expectedType, decoded)
    }
}
