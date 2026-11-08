/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.settings

import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.domain.model.AlbumViewType
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.domain.model.StoryCardsConfig
import com.dot.gallery.domain.util.OrderType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Key table shared between the app and shared modules. Names must stay in sync
 * with the androidx `Preferences.Key`s inside app-side `Settings` — both read
 * the same underlying store rows.
 */
object SettingsKeys {
    val forceTheme = PrefKey.bool("force_theme", default = false)
    val darkMode = PrefKey.bool("dark_mode", default = false)
    val amoledMode = PrefKey.bool("amoled_mode", default = false)

    /** Mirrors `com.dot.gallery.ui.theme.THEME_SEED_SYSTEM` — domain cannot depend on ui. */
    val themeColorSeed = PrefKey.string("theme_color_seed", default = "system")
    val useSystemFont = PrefKey.bool("use_system_font", default = false)

    // Appearance / visual effects
    val allowBlur = PrefKey.bool("allow_blur", default = true)
    val allowGlass = PrefKey.bool("allow_glass", default = false)
    val autoContrast = PrefKey.bool("auto_contrast", default = false)
    val sharedElements = PrefKey.bool("shared_elements", default = true)
    /** Mirrors `MapAppearance.SYSTEM.storedValue` — stored as the enum's storedValue string. */
    val mapAppearance = PrefKey.string("map_appearance", default = "system")
    val cartoBasemapKey = PrefKey.string("carto_basemap_key", default = "")

    val slideshowIntervalSeconds = PrefKey.int("slideshow_interval_seconds", default = DEFAULT_SLIDESHOW_INTERVAL)
    val slideshowRandomOrder = PrefKey.bool("slideshow_random_order", default = false)
    val slideshowReverseOrder = PrefKey.bool("slideshow_reverse_order", default = false)
    val slideshowIncludeGifs = PrefKey.bool("slideshow_include_gifs", default = true)
    val slideshowIncludeVideos = PrefKey.bool("slideshow_include_videos", default = true)
    val slideshowLoop = PrefKey.bool("slideshow_loop", default = true)
    val slideshowTransition = PrefKey.serializable(
        "slideshow_transition", SlideshowTransition.FADE, SlideshowTransition.serializer()
    )
    val slideshowKenBurns = PrefKey.bool("slideshow_ken_burns", default = false)

    val metadataIsolationMode =
        PrefKey.string("metadata_isolation_mode", default = METADATA_ISOLATION_SHARED)
    val sandboxedDecode = PrefKey.bool("sandboxed_decode", default = false)
    val privateFolderEnabled = PrefKey.bool("private_folder_enabled", default = false)
    val privateFolderUri = PrefKey.string("private_folder_uri", default = "")

    val noClassification = PrefKey.bool("no_classification", default = false)
    val categoriesExcludeVideos = PrefKey.bool("categories_exclude_videos", default = false)

    // Albums
    val albumLastSort = PrefKey.serializable(
        "album_last_sort_obj", LastSort(OrderType.Descending, FilterKind.DATE), LastSort.serializer()
    )
    val albumMediaSort = PrefKey.serializable(
        "album_media_sort_obj", LastSort(OrderType.Descending, FilterKind.DATE), LastSort.serializer()
    )
    val albumViewType = PrefKey.serializable(
        "album_last_view_obj", AlbumViewType.GRID, AlbumViewType.serializer()
    )
    /** Album ids in the order the user arranged them; empty means the sort applies to every album. */
    val albumCustomOrder = PrefKey.serializable(
        "album_custom_order", emptyList<Long>(), ListSerializer(Long.serializer())
    )
    // Selection sheet
    val selectionTitles = PrefKey.bool("selection_titles", default = true)

    // Media viewer
    val fullBrightnessView = PrefKey.bool("full_brightness_view", default = false)
    val mediaViewDateHeader = PrefKey.bool("media_view_date_header", default = true)
    val tapSidesToNavigate = PrefKey.bool("tap_sides_to_navigate", default = false)
    val tapSidesPromptShown = PrefKey.bool("tap_sides_to_navigate_prompt_shown", default = false)
    val showFavoriteButton = PrefKey.bool("show_favorite_button", default = true)
    val defaultImageEditor = PrefKey.string("default_image_editor", default = EDITOR_BUILTIN)
    /**
     * [EDITOR_STUDIO], [EDITOR_BUILTIN] for "ask every time" (video has no
     * built-in editor), or the package name of an installed video editor.
     */
    val defaultVideoEditor = PrefKey.string("default_video_editor", default = EDITOR_STUDIO)
    val darkMediaViewer = PrefKey.bool("dark_media_viewer", default = true)
    val disableSmoothing = PrefKey.bool("disable_smoothing", default = false)
    /** Legacy boolean superseded by [longPressAction]; still read to derive its default. */
    val longPressCutout = PrefKey.bool("long_press_cutout", default = false)
    /** Empty means unset — resolve through [resolveLongPressAction] with [longPressCutout]. */
    val longPressAction = PrefKey.string("long_press_action", default = "")
    val reencodeQualityMode = PrefKey.string("reencode_quality_mode", default = REENCODE_MODE_AUTO)
    val reencodeLossyQuality = PrefKey.int("reencode_lossy_quality", default = 90)
    val reencodeJxlEffort = PrefKey.int("reencode_jxl_effort", default = 7)
    val autoHideOnVideoPlay = PrefKey.bool("auto_hide_on_video_play", default = true)
    val videoAutoplay = PrefKey.bool("video_autoplay", default = true)
    /** App default is `SdkCompat.isSamsung` — wrapper resolves via `withDefault`. */
    val videoSurfaceRebind = PrefKey.bool("video_surface_rebind", default = false)
    val videoHdrPlayback = PrefKey.bool("video_hdr_playback", default = true)
    val visualSearchEnabled = PrefKey.bool("visual_search_enabled", default = true)
    val visualSearchProvider = PrefKey.string("visual_search_provider", default = VISUAL_SEARCH_PROVIDER_AUTO)
    val visualSearchPosition = PrefKey.string("visual_search_position", default = VISUAL_SEARCH_POSITION_TOP)
    val visualSearchAllowVault = PrefKey.bool("visual_search_allow_vault", default = true)
    val visualSearchConvertMode = PrefKey.string("visual_search_convert", default = VISUAL_SEARCH_CONVERT_ASK)
    val visualSearchConvertFormat = PrefKey.string("visual_search_convert_format", default = VISUAL_SEARCH_FORMAT_JPEG)

    // General
    /** App default is `MediaStore.canManageMedia(context)` — wrapper resolves via `withDefault`. */
    val useMediaManager = PrefKey.bool("use_media_manager", default = false)
    val trashEnabled = PrefKey.bool("enable_trashcan", default = true)
    val trashConfirmEnabled = PrefKey.bool("enable_trashcan_confirmation", default = true)
    val trashConfirmationEnabled = PrefKey.bool("enable_trashcan_confirmation", default = true)
    val cloudDeleteScope = PrefKey.string("cloud_delete_scope", default = DELETE_SCOPE_ASK)
    val secureMode = PrefKey.bool("secure_mode", default = false)
    val allowVibrations = PrefKey.bool("allow_vibrations", default = true)
    val appNameAlias = PrefKey.string("app_name_alias", default = ALIAS_REFRA)
    val appLogoAlias = PrefKey.string("app_logo_alias", default = ALIAS_REFRA)
    val vaultEncryptBehavior = PrefKey.string("vault_encrypt_behavior", default = ENCRYPT_ASK)
    val syncOnAppOpen = PrefKey.bool("cloud_sync_on_app_open", default = true)
    val setupCompletedVersion = PrefKey.int("setup_completed_version", default = 0)
    val headerBannerDismissed = PrefKey.bool("header_banner_dismissed", default = false)

    // Developer options
    val developerMode = PrefKey.bool("developer_mode", default = false)
    val devLogMinLevel = PrefKey.string("dev_log_min_level", default = "DEBUG")
    val devLogBreadcrumbs = PrefKey.bool("dev_log_breadcrumbs", default = true)

    // Story cards / viewer
    val storyCardsConfig = PrefKey.serializable(
        "story_cards_config", StoryCardsConfig(), StoryCardsConfig.serializer()
    )
    val storyViewerAutoAdvance = PrefKey.bool("story_viewer_auto_advance", default = true)
    val storyViewerDurationSeconds = PrefKey.string("story_viewer_duration_seconds", default = "5")

    // Timeline & albums
    val timelineGroupByMonth = PrefKey.bool("timeline_group_by_month", default = false)
    val timelineGroupByYear = PrefKey.bool("timeline_group_by_year", default = false)
    val timelineLayoutType = PrefKey.string("timeline_layout_type", default = LAYOUT_MOSAIC)
    val timelineSort = PrefKey.serializable(
        "timeline_sort_obj",
        default = LastSort(OrderType.Descending, FilterKind.DATE),
        serializer = LastSort.serializer(),
    )
    val groupSimilarMedia = PrefKey.bool("group_similar_media", default = true)
    val groupRawJpg = PrefKey.bool("group_raw_jpg", default = true)
    val groupEditedCopies = PrefKey.bool("group_edited_copies", default = true)
    val groupBurstSequences = PrefKey.bool("group_burst_sequences", default = true)
    val groupCloudLocal = PrefKey.bool("group_cloud_local", default = true)
    val allowGifAnimation = PrefKey.bool("allow_gif_animation", default = true)
    val showFilterButton = PrefKey.bool("show_filter_button", default = true)
    val showSearchBarFavButton = PrefKey.bool("show_searchbar_favorite_button", default = true)
    val favoriteIconPosition = PrefKey.string("favorite_icon_position", default = FAV_ICON_BOTTOM_END)
    val albumGroupByDate = PrefKey.bool("album_group_by_date", default = true)
    val mergeAlbumsByName = PrefKey.bool("merge_albums_by_name", default = true)
    val albumSectionsEnabled = PrefKey.bool("album_sections_enabled", default = false)
    val showMediaTypeAlbums = PrefKey.bool("show_media_type_albums", default = true)
    val showPinnedAlbums = PrefKey.bool("show_pinned_albums", default = true)
    val updateModifiedDate = PrefKey.bool("update_modified_date", default = false)
    val hideTimelineOnAlbum = PrefKey.bool("hide_timeline_on_album", default = false)
    val timelineGroupByDate = PrefKey.bool("timeline_group_by_date", default = true)
    val favoritesGroupByDate = PrefKey.bool("favorites_group_by_date", default = true)
    val vaultGroupByDate = PrefKey.bool("vault_group_by_date", default = true)
    val cloudArchiveGroupByDate = PrefKey.bool("cloud_archive_group_by_date", default = false)
    val locationGroupByDate = PrefKey.bool("location_group_by_date", default = true)
    val timelineGroupMethod = PrefKey.string("timeline_group_method", default = GROUP_NORMAL)
    val albumsGroupMethod = PrefKey.string("albums_group_method", default = GROUP_NORMAL)
    val favoritesGroupMethod = PrefKey.string("favorites_group_method", default = GROUP_NORMAL)
    val vaultGroupMethod = PrefKey.string("vault_group_method", default = GROUP_NORMAL)
    val cloudArchiveGroupMethod = PrefKey.string("cloud_archive_group_method", default = GROUP_NORMAL)
    val locationGroupMethod = PrefKey.string("location_group_method", default = GROUP_NORMAL)
    /** Month headers on the main timeline open with a card of the month's best photos. */
    val timelineMonthHighlights = PrefKey.bool("timeline_month_highlights", default = true)
    /** Month highlight cards carry a play button that starts a slideshow from the month. */
    val timelineMonthRecaps = PrefKey.bool("timeline_month_recaps", default = true)
    /** Timeline headers name where the photos were taken. */
    val timelineHeaderPlaces = PrefKey.bool("timeline_header_places", default = true)
    /** The timeline's sticky date header floats as a pill that opens the months view. */
    val timelineFloatingDateLabel = PrefKey.bool("timeline_floating_date_label", default = true)
    val showLocationCategories = PrefKey.bool("library_show_location_categories", default = true)
    val hiddenSections = PrefKey.stringSet("library_hidden_sections")

    // Search
    /** Mirrors `Settings.Search.HISTORY_V2` — JSON-encoded list of [SearchHistory]. */
    val searchHistory = PrefKey.serializable(
        "search_history_v2",
        default = emptyList<SearchHistory>(),
        serializer = ListSerializer(SearchHistory.serializer()),
    )

    // Navigation
    val lastSeenVersion = PrefKey.string("last_seen_version", default = "")
    val lastScreen = PrefKey.string("last_screen", default = LAUNCH_ROUTE_TIMELINE)
    val forcedLastScreen = PrefKey.bool("forced_last_screen", default = false)
    val oldNavbar = PrefKey.bool("old_navbar", default = false)
    val autoHideSearchBar = PrefKey.bool("auto_hide_searchbar", default = true)
    /** Large windows keep the gallery beside the viewer instead of opening it fullscreen. */
    val galleryDualPane = PrefKey.bool("gallery_dual_pane", default = true)
    /** Share of the window width given to the gallery pane while the viewer is beside it. */
    val galleryPaneFraction = PrefKey.float("gallery_pane_fraction", default = 0.45f)
    /** Width in dp of the settings list kept beside an open settings page. */
    val settingsPaneWidth = PrefKey.float("settings_pane_width", default = 360f)
    val autoOpenSearchKeyboard = PrefKey.bool("auto_open_search_keyboard", default = true)
    val autoHideNavBar = PrefKey.bool("auto_hide_navigationbar", default = true)

    // Date format overrides — blank = follow system (see )
    val rawDateHeaderFormat = PrefKey.string("date_header_format", default = "")
    val rawExtendedDateHeaderFormat = PrefKey.string("extended_date_header_format", default = "")
    val rawExifDateFormat = PrefKey.string("exif_date_format", default = "")
    val rawExtendedDateFormat = PrefKey.string("extended_date_format", default = "")
    val rawDefaultDateFormat = PrefKey.string("default_date_format", default = "")
    val rawWeeklyDateFormat = PrefKey.string("weekly_date_format", default = "")
}

/** Delete-scope values — mirror `Settings.Misc` constants. */
const val DELETE_SCOPE_ASK = "ask"
const val DELETE_SCOPE_DEVICE = "device"
const val DELETE_SCOPE_CLOUD = "cloud"
const val DELETE_SCOPE_BOTH = "both"

/** Launcher alias values — mirror `Settings.Misc` constants. */
const val ALIAS_REFRA = "ReFra"
const val ALIAS_GALLERY = "Gallery"
const val ALIAS_MONO = "Monochrome"
const val ALIAS_GALLERY_MONO = "Gallery Mono"

/** Vault encryption-on-export values — mirror `Settings.Vault` constants. */
const val ENCRYPT_ASK = "ask"
const val ENCRYPT_DELETE = "delete"
const val ENCRYPT_KEEP = "keep"

/** Launch-screen route values — mirror `Screen.*` routes (kept in sync by the app shell). */
const val LAUNCH_ROUTE_TIMELINE = "timeline_screen"
const val LAUNCH_ROUTE_ALBUMS = "albums_screen"
const val LAUNCH_ROUTE_LIBRARY = "library_screen"
const val LAUNCH_ROUTE_AUTO = "auto"

/** Albums merged into Library, so a stored Albums choice now opens Library. */
fun normalizeLaunchRoute(route: String): String =
    if (route == LAUNCH_ROUTE_ALBUMS) LAUNCH_ROUTE_LIBRARY else route

/** Slideshow interval bounds — mirror `Settings.Slideshow` constants. */
const val DEFAULT_SLIDESHOW_INTERVAL = 5
const val MIN_SLIDESHOW_INTERVAL = 1
const val MAX_SLIDESHOW_INTERVAL = 60

/** Timeline layout values — mirror `Settings.Misc.LAYOUT_*`. */
const val LAYOUT_GRID = "grid"
const val LAYOUT_MOSAIC = "mosaic"

/** Favorite-icon positions — mirror `Settings.Misc.FAV_ICON_*`. */
const val FAV_ICON_DISABLED = "disabled"
const val FAV_ICON_BOTTOM_END = "bottom_end"
const val FAV_ICON_BOTTOM_START = "bottom_start"
const val FAV_ICON_TOP_END = "top_end"
const val FAV_ICON_TOP_START = "top_start"

/** Group-method values — mirror `Settings.Misc.GROUP_*`. */
const val GROUP_NORMAL = "normal"
const val GROUP_MONTHLY = "monthly"
const val GROUP_YEARLY = "yearly"

/** Library section ids — mirror `Settings.Library.SECTION_*`. */
const val SECTION_PEOPLE = "people"
const val SECTION_CATEGORIES = "categories"

/** Metadata isolation modes — mirror `Settings.Security` constants. */
const val METADATA_ISOLATION_SHARED = "shared"
const val METADATA_ISOLATION_HYBRID = "hybrid"
const val METADATA_ISOLATION_PER_FILE = "per_file"
/** Visual-search provider sentinel — mirrors `Settings.Misc.VISUAL_SEARCH_PROVIDER_AUTO`. */
const val VISUAL_SEARCH_PROVIDER_AUTO = ""

/** Visual-search position values — mirror `Settings.Misc.VISUAL_SEARCH_POSITION_*`. */
const val VISUAL_SEARCH_POSITION_TOP = "top"
const val VISUAL_SEARCH_POSITION_BOTTOM = "bottom"

/** Visual-search conversion modes — mirror `Settings.Misc.VISUAL_SEARCH_CONVERT_*`. */
const val VISUAL_SEARCH_CONVERT_ASK = "ask"
const val VISUAL_SEARCH_CONVERT_ALWAYS = "always"
const val VISUAL_SEARCH_CONVERT_NEVER = "never"

/** Visual-search output formats — mirror `Settings.Misc.VISUAL_SEARCH_FORMAT_*`. */
const val VISUAL_SEARCH_FORMAT_JPEG = "jpeg"
const val VISUAL_SEARCH_FORMAT_PNG = "png"
const val VISUAL_SEARCH_FORMAT_WEBP = "webp"

/** Re-encode quality modes — mirror `Settings.Misc.REENCODE_MODE_*`. */
const val REENCODE_MODE_AUTO = "auto"
const val REENCODE_MODE_MANUAL = "manual"

/** Viewer long-press actions stored in [SettingsKeys.longPressAction]. */
const val LONG_PRESS_ACTION_ROTATE = "rotate"
const val LONG_PRESS_ACTION_CUTOUT = "cutout"
const val LONG_PRESS_ACTION_NONE = "none"

/**
 * Effective viewer long-press action. A missing or unknown stored value falls back to the legacy
 * `long_press_cutout` switch so users who enabled cut-out before the chooser existed keep it.
 */
fun resolveLongPressAction(storedValue: String?, legacyCutout: Boolean): String =
    when (storedValue) {
        LONG_PRESS_ACTION_ROTATE, LONG_PRESS_ACTION_CUTOUT, LONG_PRESS_ACTION_NONE -> storedValue
        else -> if (legacyCutout) LONG_PRESS_ACTION_CUTOUT else LONG_PRESS_ACTION_ROTATE
    }

/** Built-in editor sentinel — mirror `Settings.Misc.EDITOR_BUILTIN`. */
const val EDITOR_BUILTIN = "builtin"

/** New standalone ReFra editor sentinel — mirror `Settings.Misc.EDITOR_STUDIO`. */
const val EDITOR_STUDIO = "studio"
