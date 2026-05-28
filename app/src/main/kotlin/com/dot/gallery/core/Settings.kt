/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.dot.gallery.core

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Parcelable
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Constants.mosaicColumnsList
import com.dot.gallery.core.Settings.PREFERENCE_NAME
import com.dot.gallery.core.encryption.EncryptedDataStoreProvider
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.util.rememberPreference
import com.dot.gallery.core.util.rememberPreferenceSerializable
import com.dot.gallery.feature_node.domain.model.SearchHistory
import com.dot.gallery.feature_node.domain.model.SelectionSheetConfig
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCE_NAME)

/**
 * Returns the active [DataStore] — the encrypted store when the device supports
 * Android Keystore-backed AES-GCM, or the default plaintext store as a fallback.
 *
 * Encryption is always enabled automatically; there is no user-facing toggle.
 */
val Context.activeDataStore: DataStore<Preferences>
    get() {
        return try {
            EncryptedDataStoreProvider.getOrCreate(this)
        } catch (_: Exception) {
            // Device doesn't support hardware-backed keystore or encryption failed —
            // fall back to plaintext silently.
            StartupTracer.trace("activeDataStore.fallbackPlaintext") { }
            dataStore
        }
    }

object Settings {

    const val PREFERENCE_NAME = "settings"

    object Album {
        private val LAST_SORT = stringPreferencesKey("album_last_sort_obj")
        private val LAST_VIEW = stringPreferencesKey("album_last_view_obj")
        private val ALBUM_MEDIA_SORT = stringPreferencesKey("album_media_sort_obj")

        @Serializable
        @Parcelize
        data class LastSort(
            val orderType: OrderType,
            val kind: FilterKind
        ) : Parcelable

        @Serializable
        @Parcelize
        enum class ViewType : Parcelable {
            GRID, LIST
        }

        fun getAlbumMediaSortFlow(context: Context): Flow<LastSort> =
            context.activeDataStore.data.map { prefs ->
                prefs[ALBUM_MEDIA_SORT]?.let {
                    runCatching { Json.decodeFromString<LastSort>(it) }.getOrNull()
                } ?: LastSort(OrderType.Descending, FilterKind.DATE)
            }

        @Composable
        fun rememberLastSort() =
            rememberPreferenceSerializable(
                keyString = LAST_SORT,
                defaultValue = LastSort(OrderType.Descending, FilterKind.DATE)
            )

        @Composable
        fun rememberAlbumMediaSort() =
            rememberPreferenceSerializable(
                keyString = ALBUM_MEDIA_SORT,
                defaultValue = LastSort(OrderType.Descending, FilterKind.DATE)
            )

        @Composable
        fun rememberLastViewType() =
            rememberPreferenceSerializable(
                keyString = LAST_VIEW,
                defaultValue = ViewType.GRID
            )

        @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
        @Composable
        fun rememberAlbumGridSize(): MutableState<Int> {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val prefs = remember(context) {
                context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            }

            val windowSizeClass = (context as? Activity)?.let { calculateWindowSizeClass(it) }
            val defaultValue = remember(windowSizeClass) {
                albumCellsList.indexOf(
                    GridCells.Fixed(
                        when (windowSizeClass?.widthSizeClass) {
                            WindowWidthSizeClass.Expanded -> 5
                            else -> 2
                        }
                    )
                )
            }
            val orientation = LocalConfiguration.current.orientation
            val isLandscape by rememberedDerivedState(orientation, windowSizeClass) {
                orientation == Configuration.ORIENTATION_LANDSCAPE ||
                        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
            }
            val key by rememberedDerivedState {
                if (isLandscape) "album_grid_size_landscape" else "album_grid_size"
            }

            var storedSize = remember(prefs, key, defaultValue, orientation, windowSizeClass) {
                prefs.getInt(key, defaultValue)
            }

            return remember(storedSize) {
                object : MutableState<Int> {
                    override var value: Int
                        get() = storedSize
                        set(value) {
                            scope.launch {
                                prefs.edit {
                                    putInt(key, value)
                                    storedSize = value
                                }
                            }
                        }

                    override fun component1() = value
                    override fun component2(): (Int) -> Unit = { value = it }
                }
            }
        }

        private val HIDE_TIMELINE_ON_ALBUM = booleanPreferencesKey("hide_timeline_on_album")

        @Composable
        fun rememberHideTimelineOnAlbum() =
            rememberPreference(key = HIDE_TIMELINE_ON_ALBUM, defaultValue = false)

        val MERGE_ALBUMS_BY_NAME = booleanPreferencesKey("merge_albums_by_name")

        @Composable
        fun rememberMergeAlbumsByName() =
            rememberPreference(key = MERGE_ALBUMS_BY_NAME, defaultValue = true)
    }

    object Search {
        val HISTORY_V2 = stringPreferencesKey("search_history_v2")

        val EMPTY_HISTORY = Json.encodeToString(emptyList<SearchHistory>())

        suspend fun addHistory(context: Context, query: String) {
            context.activeDataStore.edit { preferences ->
                val currentHistory = preferences[HISTORY_V2] ?: EMPTY_HISTORY
                val historyList = Json.decodeFromString<List<SearchHistory>>(currentHistory).apply {
                    filter { it.query != query }
                }
                val newHistory = SearchHistory(System.currentTimeMillis(), query)
                val updatedHistory = (historyList + newHistory).sortedByDescending { it.timestamp }
                preferences[HISTORY_V2] = Json.encodeToString(updatedHistory)
            }
        }

        suspend fun addImageHistory(context: Context, mediaId: Long, mediaLabel: String, mediaUri: String) {
            context.activeDataStore.edit { preferences ->
                val currentHistory = preferences[HISTORY_V2] ?: EMPTY_HISTORY
                val historyList = Json.decodeFromString<List<SearchHistory>>(currentHistory)
                    .filter { it.mediaId != mediaId }
                val newEntry = SearchHistory(
                    timestamp = System.currentTimeMillis(),
                    query = mediaLabel,
                    mediaId = mediaId,
                    mediaLabel = mediaLabel,
                    mediaUri = mediaUri
                )
                val updatedHistory = (historyList + newEntry).sortedByDescending { it.timestamp }
                preferences[HISTORY_V2] = Json.encodeToString(updatedHistory)
            }
        }

        suspend fun removeHistory(context: Context, query: String) {
            context.activeDataStore.edit { preferences ->
                val currentHistory = preferences[HISTORY_V2] ?: EMPTY_HISTORY
                val historyList = Json.decodeFromString<List<SearchHistory>>(currentHistory)
                printDebug("Current history: $historyList")
                printDebug("Removing query: $query")
                val updatedHistory = historyList.toMutableList().apply {
                    removeIf { searchHistory ->
                        printDebug("Checking: ${searchHistory.query} == $query")
                        searchHistory.query == query && searchHistory.mediaId == null
                    }
                }
                printDebug("Updated history: $updatedHistory")
                preferences[HISTORY_V2] = Json.encodeToString(updatedHistory)
            }
        }

        suspend fun removeImageHistory(context: Context, mediaId: Long) {
            context.activeDataStore.edit { preferences ->
                val currentHistory = preferences[HISTORY_V2] ?: EMPTY_HISTORY
                val historyList = Json.decodeFromString<List<SearchHistory>>(currentHistory)
                val updatedHistory = historyList.filter { it.mediaId != mediaId }
                preferences[HISTORY_V2] = Json.encodeToString(updatedHistory)
            }
        }

        @Composable
        fun rememberSearchHistory() = rememberPreferenceSerializable(
            keyString = HISTORY_V2,
            defaultValue = emptyList<SearchHistory>()
        )

    }

    object Misc {
        private val USER_CHOICE_MEDIA_MANAGER = booleanPreferencesKey("use_media_manager")

        @RequiresApi(Build.VERSION_CODES.S)
        @Composable
        fun rememberIsMediaManager() =
            rememberPreference(
                key = USER_CHOICE_MEDIA_MANAGER, defaultValue = MediaStore.canManageMedia(
                    LocalContext.current
                )
            )

        private val ENABLE_TRASH = booleanPreferencesKey("enable_trashcan")

        @Composable
        fun rememberTrashEnabled() =
            rememberPreference(key = ENABLE_TRASH, defaultValue = SdkCompat.supportsTrash)

        fun getTrashEnabled(context: Context) =
            context.activeDataStore.data.map { it[ENABLE_TRASH] ?: SdkCompat.supportsTrash }

        private val ENABLE_TRASH_CONFIRMATION = booleanPreferencesKey("enable_trashcan_confirmation")

        @Composable
        fun rememberTrashConfirmationEnabled() =
            rememberPreference(key = ENABLE_TRASH_CONFIRMATION, defaultValue = true)

        private val LAST_SCREEN = stringPreferencesKey("last_screen")

        @Composable
        fun rememberLastScreen() =
            rememberPreference(key = LAST_SCREEN, defaultValue = Screen.TimelineScreen())

        private val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")

        @Composable
        fun rememberLastSeenVersion() =
            rememberPreference(key = LAST_SEEN_VERSION, defaultValue = "")

        private val FORCED_LAST_SCREEN = booleanPreferencesKey("forced_last_screen")

        @Composable
        fun rememberForcedLastScreen() =
            rememberPreference(key = FORCED_LAST_SCREEN, defaultValue = false)

        @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
        @Composable
        fun rememberGridSize(): MutableState<Int> {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val prefs = remember(context) {
                context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            }
            val windowSizeClass =
                if (context is Activity) calculateWindowSizeClass(context) else null
            val defaultValue = remember(windowSizeClass) {
                cellsList.indexOf(
                    GridCells.Fixed(
                        when (windowSizeClass?.widthSizeClass) {
                            WindowWidthSizeClass.Expanded -> 6
                            else -> 4
                        }
                    )
                )
            }

            val orientation = LocalConfiguration.current.orientation
            val isLandscape by rememberedDerivedState(orientation, windowSizeClass) {
                orientation == Configuration.ORIENTATION_LANDSCAPE ||
                        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
            }

            val key by rememberedDerivedState {
                if (isLandscape) "media_grid_size_landscape" else "media_grid_size"
            }

            var storedSize = remember(prefs, key, defaultValue, orientation, windowSizeClass) {
                prefs.getInt(key, defaultValue)
            }

            return remember(storedSize) {
                object : MutableState<Int> {
                    override var value: Int
                        get() = storedSize
                        set(value) {
                            scope.launch {
                                prefs.edit {
                                    putInt(key, value)
                                    storedSize = value
                                }
                            }
                        }

                    override fun component1() = value
                    override fun component2(): (Int) -> Unit = { value = it }
                }
            }
        }

        @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
        @Composable
        fun rememberMosaicGridSize(): MutableState<Int> {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val prefs = remember(context) {
                context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
            }
            val windowSizeClass =
                if (context is Activity) calculateWindowSizeClass(context) else null

            val defaultValue = remember(windowSizeClass) {
                mosaicColumnsList.indexOf(
                    when (windowSizeClass?.widthSizeClass) {
                        WindowWidthSizeClass.Expanded -> 6
                        else -> 4
                    }
                )
            }

            val orientation = LocalConfiguration.current.orientation
            val isLandscape by rememberedDerivedState(orientation, windowSizeClass) {
                orientation == Configuration.ORIENTATION_LANDSCAPE ||
                        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
            }

            val key by rememberedDerivedState {
                if (isLandscape) "mosaic_grid_size_landscape" else "mosaic_grid_size"
            }

            var storedSize = remember(prefs, key, defaultValue, orientation, windowSizeClass) {
                prefs.getInt(key, defaultValue)
            }

            return remember(storedSize) {
                object : MutableState<Int> {
                    override var value: Int
                        get() = storedSize
                        set(value) {
                            scope.launch {
                                prefs.edit {
                                    putInt(key, value)
                                    storedSize = value
                                }
                            }
                        }

                    override fun component1() = value
                    override fun component2(): (Int) -> Unit = { value = it }
                }
            }
        }

        private val FORCE_THEME = booleanPreferencesKey("force_theme")

        @Composable
        fun rememberForceTheme() =
            rememberPreference(key = FORCE_THEME, defaultValue = false)

        private val DARK_MODE = booleanPreferencesKey("dark_mode")

        @Composable
        fun rememberIsDarkMode() =
            rememberPreference(key = DARK_MODE, defaultValue = false)

        private val AMOLED_MODE = booleanPreferencesKey("amoled_mode")

        @Composable
        fun rememberIsAmoledMode() =
            rememberPreference(key = AMOLED_MODE, defaultValue = false)

        const val THEME_SEED_SYSTEM = "system"
        const val THEME_SEED_NEUTRAL = "neutral"
        private val THEME_COLOR_SEED = stringPreferencesKey("theme_color_seed")

        @Composable
        fun rememberThemeColorSeed() =
            rememberPreference(key = THEME_COLOR_SEED, defaultValue = THEME_SEED_SYSTEM)

        private val SECURE_MODE = booleanPreferencesKey("secure_mode")

        @Composable
        fun rememberSecureMode() =
            rememberPreference(key = SECURE_MODE, defaultValue = false)

        fun getSecureMode(context: Context) =
            context.activeDataStore.data.map { it[SECURE_MODE] ?: false }

        private val TIMELINE_GROUP_BY_MONTH = booleanPreferencesKey("timeline_group_by_month")

        @Composable
        fun rememberTimelineGroupByMonth() =
            rememberPreference(key = TIMELINE_GROUP_BY_MONTH, defaultValue = false)

        val GROUP_SIMILAR_MEDIA = booleanPreferencesKey("group_similar_media")

        @Composable
        fun rememberGroupSimilarMedia() =
            rememberPreference(key = GROUP_SIMILAR_MEDIA, defaultValue = true)

        val GROUP_RAW_JPG = booleanPreferencesKey("group_raw_jpg")

        @Composable
        fun rememberGroupRawJpg() =
            rememberPreference(key = GROUP_RAW_JPG, defaultValue = true)

        val GROUP_EDITED_COPIES = booleanPreferencesKey("group_edited_copies")

        @Composable
        fun rememberGroupEditedCopies() =
            rememberPreference(key = GROUP_EDITED_COPIES, defaultValue = true)

        val GROUP_BURST_SEQUENCES = booleanPreferencesKey("group_burst_sequences")

        @Composable
        fun rememberGroupBurstSequences() =
            rememberPreference(key = GROUP_BURST_SEQUENCES, defaultValue = true)

        private val ALLOW_BLUR = booleanPreferencesKey("allow_blur")

        @Composable
        fun rememberAllowBlur() = rememberPreference(key = ALLOW_BLUR, defaultValue = SdkCompat.supportsBlur)

        private val AUTO_CONTRAST = booleanPreferencesKey("auto_contrast")

        @Composable
        fun rememberAutoContrast() = rememberPreference(key = AUTO_CONTRAST, defaultValue = false)

        private val OLD_NAVBAR = booleanPreferencesKey("old_navbar")

        @Composable
        fun rememberOldNavbar() = rememberPreference(key = OLD_NAVBAR, defaultValue = false)

        private val ALLOW_VIBRATIONS = booleanPreferencesKey("allow_vibrations")

        fun allowVibrations(context: Context) =
            context.activeDataStore.data.map { it[ALLOW_VIBRATIONS] ?: true }

        @Composable
        fun rememberAllowVibrations() =
            rememberPreference(key = ALLOW_VIBRATIONS, defaultValue = true)

        private val AUTO_HIDE_SEARCHBAR = booleanPreferencesKey("auto_hide_searchbar")

        @Composable
        fun rememberAutoHideSearchBar() =
            rememberPreference(key = AUTO_HIDE_SEARCHBAR, defaultValue = true)

        private val AUTO_HIDE_NAVIGATIONBAR = booleanPreferencesKey("auto_hide_navigationbar")

        @Composable
        fun rememberAutoHideNavBar() =
            rememberPreference(key = AUTO_HIDE_NAVIGATIONBAR, defaultValue = true)

        private val AUDIO_FOCUS = booleanPreferencesKey("audio_focus")

        @Composable
        fun rememberAudioFocus() =
            rememberPreference(key = AUDIO_FOCUS, defaultValue = true)

        private val FULL_BRIGHTNESS_VIEW = booleanPreferencesKey("full_brightness_view")

        @Composable
        fun rememberFullBrightnessView() =
            rememberPreference(key = FULL_BRIGHTNESS_VIEW, defaultValue = false)

        private val AUTO_HIDE_ON_VIDEO_PLAY = booleanPreferencesKey("auto_hide_on_video_play")

        @Composable
        fun rememberAutoHideOnVideoPlay() =
            rememberPreference(key = AUTO_HIDE_ON_VIDEO_PLAY, defaultValue = true)

        val NO_CLASSIFICATION = booleanPreferencesKey("no_classification")

        @Composable
        fun rememberNoClassification() =
            rememberPreference(key = NO_CLASSIFICATION, defaultValue = false)

        val DATE_HEADER_FORMAT = stringPreferencesKey("date_header_format")

        @Composable
        fun rememberDateHeaderFormat() =
            rememberPreference(
                key = DATE_HEADER_FORMAT,
                defaultValue = Constants.HEADER_DATE_FORMAT
            )

        val EXTENDED_DATE_HEADER_FORMAT = stringPreferencesKey("extended_date_header_format")

        @Composable
        fun rememberExtendedDateHeaderFormat() =
            rememberPreference(
                key = EXTENDED_DATE_HEADER_FORMAT,
                defaultValue = Constants.EXTENDED_HEADER_DATE_FORMAT
            )

        val EXIF_DATE_FORMAT = stringPreferencesKey("exif_date_format")

        @Composable
        fun rememberExifDateFormat() =
            rememberPreference(key = EXIF_DATE_FORMAT, defaultValue = Constants.EXIF_DATE_FORMAT)

        val EXTENDED_DATE_FORMAT = stringPreferencesKey("extended_date_format")

        @Composable
        fun rememberExtendedDateFormat() =
            rememberPreference(
                key = EXTENDED_DATE_FORMAT,
                defaultValue = Constants.EXTENDED_DATE_FORMAT
            )

        val DEFAULT_DATE_FORMAT = stringPreferencesKey("default_date_format")

        @Composable
        fun rememberDefaultDateFormat() =
            rememberPreference(
                key = DEFAULT_DATE_FORMAT,
                defaultValue = Constants.DEFAULT_DATE_FORMAT
            )

        val WEEKLY_DATE_FORMAT = stringPreferencesKey("weekly_date_format")

        @Composable
        fun rememberWeeklyDateFormat() =
            rememberPreference(
                key = WEEKLY_DATE_FORMAT,
                defaultValue = Constants.WEEKLY_DATE_FORMAT
            )

        fun <T> getSetting(context: Context, key: Preferences.Key<T>, defaultValue: T) =
            context.activeDataStore.data.map { it[key] ?: defaultValue }

        private val VIDEO_AUTOPLAY = booleanPreferencesKey("video_autoplay")

        @Composable
        fun rememberVideoAutoplay() =
            rememberPreference(key = VIDEO_AUTOPLAY, defaultValue = true)

        private val SHARED_ELEMENTS = booleanPreferencesKey("shared_elements")

        @Composable
        fun rememberSharedElements() =
            rememberPreference(key = SHARED_ELEMENTS, defaultValue = true)

        private val MEDIA_VIEW_DATE_HEADER = booleanPreferencesKey("media_view_date_header")

        @Composable
        fun rememberShowMediaViewDateHeader() =
            rememberPreference(key = MEDIA_VIEW_DATE_HEADER, defaultValue = true)

        private val SELECTION_TITLES = booleanPreferencesKey("selection_titles")

        @Composable
        fun rememberShowSelectionTitles() =
            rememberPreference(key = SELECTION_TITLES, defaultValue = true)

        private val SELECTION_SHEET_CONFIG = stringPreferencesKey("selection_sheet_config")

        @Composable
        fun rememberSelectionSheetConfig() =
            rememberPreferenceSerializable(
                keyString = SELECTION_SHEET_CONFIG,
                defaultValue = SelectionSheetConfig()
            )

        const val ALIAS_REFRA = "ReFra"
        const val ALIAS_GALLERY = "Gallery"
        private val APP_NAME_ALIAS = stringPreferencesKey("app_name_alias")

        @Composable
        fun rememberAppNameAlias() =
            rememberPreference(key = APP_NAME_ALIAS, defaultValue = ALIAS_REFRA)

        const val FAV_ICON_DISABLED = "disabled"
        const val FAV_ICON_BOTTOM_END = "bottom_end"
        const val FAV_ICON_BOTTOM_START = "bottom_start"
        const val FAV_ICON_TOP_END = "top_end"
        const val FAV_ICON_TOP_START = "top_start"
        private val FAVORITE_ICON_POSITION = stringPreferencesKey("favorite_icon_position")

        @Composable
        fun rememberFavoriteIconPosition() =
            rememberPreference(key = FAVORITE_ICON_POSITION, defaultValue = FAV_ICON_BOTTOM_END)

        private val SHOW_FAVORITE_BUTTON = booleanPreferencesKey("show_favorite_button")

        @Composable
        fun rememberShowFavoriteButton() =
            rememberPreference(key = SHOW_FAVORITE_BUTTON, defaultValue = true)

        private val SHOW_FILTER_BUTTON = booleanPreferencesKey("show_filter_button")

        @Composable
        fun rememberShowFilterButton() =
            rememberPreference(key = SHOW_FILTER_BUTTON, defaultValue = true)

        private val ALLOW_GIF_ANIMATION = booleanPreferencesKey("allow_gif_animation")

        @Composable
        fun rememberAllowGifAnimation() =
            rememberPreference(key = ALLOW_GIF_ANIMATION, defaultValue = true)

        const val LAYOUT_GRID = "grid"
        const val LAYOUT_MOSAIC = "mosaic"
        private val TIMELINE_LAYOUT_TYPE = stringPreferencesKey("timeline_layout_type")

        @Composable
        fun rememberTimelineLayoutType() =
            rememberPreference(key = TIMELINE_LAYOUT_TYPE, defaultValue = LAYOUT_MOSAIC)

        const val EDITOR_BUILTIN = "builtin"
        private val DEFAULT_IMAGE_EDITOR = stringPreferencesKey("default_image_editor")

        @Composable
        fun rememberDefaultImageEditor() =
            rememberPreference(key = DEFAULT_IMAGE_EDITOR, defaultValue = EDITOR_BUILTIN)

        private val HEADER_BANNER_DISMISSED = booleanPreferencesKey("header_banner_dismissed")

        @Composable
        fun rememberHeaderBannerDismissed() =
            rememberPreference(key = HEADER_BANNER_DISMISSED, defaultValue = false)

        private val STORY_CARDS_CONFIG = stringPreferencesKey("story_cards_config")

        @Composable
        fun rememberStoryCardsConfig() =
            rememberPreferenceSerializable(
                keyString = STORY_CARDS_CONFIG,
                defaultValue = com.dot.gallery.feature_node.domain.model.StoryCardsConfig()
            )

        fun getStoryCardsConfig(context: Context) =
            context.activeDataStore.data.map {
                it[STORY_CARDS_CONFIG]?.let { json ->
                    runCatching { Json.decodeFromString<com.dot.gallery.feature_node.domain.model.StoryCardsConfig>(json) }.getOrNull()
                } ?: com.dot.gallery.feature_node.domain.model.StoryCardsConfig()
            }

        private val STORY_VIEWER_AUTO_ADVANCE = booleanPreferencesKey("story_viewer_auto_advance")

        @Composable
        fun rememberStoryViewerAutoAdvance() =
            rememberPreference(key = STORY_VIEWER_AUTO_ADVANCE, defaultValue = true)

        private val STORY_VIEWER_DURATION_SECONDS = stringPreferencesKey("story_viewer_duration_seconds")

        @Composable
        fun rememberStoryViewerDuration() =
            rememberPreference(key = STORY_VIEWER_DURATION_SECONDS, defaultValue = "5")

    }

    object Security {
        const val METADATA_ISOLATION_SHARED = "shared"
        const val METADATA_ISOLATION_HYBRID = "hybrid"
        const val METADATA_ISOLATION_PER_FILE = "per_file"

        private val METADATA_ISOLATION_MODE = stringPreferencesKey("metadata_isolation_mode")

        @Composable
        fun rememberMetadataIsolationMode() =
            rememberPreference(key = METADATA_ISOLATION_MODE, defaultValue = METADATA_ISOLATION_SHARED)

        fun getMetadataIsolationMode(context: Context) =
            context.activeDataStore.data.map { it[METADATA_ISOLATION_MODE] ?: METADATA_ISOLATION_SHARED }

        private val SANDBOXED_DECODE = booleanPreferencesKey("sandboxed_decode")

        @Composable
        fun rememberSandboxedDecode() =
            rememberPreference(key = SANDBOXED_DECODE, defaultValue = false)

        fun getSandboxedDecode(context: Context) =
            context.activeDataStore.data.map { it[SANDBOXED_DECODE] ?: false }

        private val PRIVATE_FOLDER_ENABLED = booleanPreferencesKey("private_folder_enabled")

        @Composable
        fun rememberPrivateFolderEnabled() =
            rememberPreference(key = PRIVATE_FOLDER_ENABLED, defaultValue = false)

        private val PRIVATE_FOLDER_URI = stringPreferencesKey("private_folder_uri")

        @Composable
        fun rememberPrivateFolderUri() =
            rememberPreference(key = PRIVATE_FOLDER_URI, defaultValue = "")

        fun getPrivateFolderUri(context: Context) =
            context.activeDataStore.data.map { it[PRIVATE_FOLDER_URI] ?: "" }
    }

    object Vault {
        const val ENCRYPT_ASK = "ask"
        const val ENCRYPT_DELETE = "delete"
        const val ENCRYPT_KEEP = "keep"

        private val VAULT_ENCRYPT_BEHAVIOR = stringPreferencesKey("vault_encrypt_behavior")

        @Composable
        fun rememberVaultEncryptBehavior() =
            rememberPreference(key = VAULT_ENCRYPT_BEHAVIOR, defaultValue = ENCRYPT_ASK)
    }
}

sealed class Position {
    data object Top : Position()
    data object Middle : Position()
    data object Bottom : Position()
    data object Alone : Position()
}

sealed class PreferenceType {
    data object Seek : PreferenceType()
    data object Switch : PreferenceType()
    data object Header : PreferenceType()
    data object Default : PreferenceType()
    data object Album : PreferenceType()
}

sealed class SettingsEntity(
    open val icon: ImageVector? = null,
    open val iconUri: String? = null,
    open val iconRes: Int? = null,
    open val title: String,
    open val titleAnnotated: AnnotatedString? = null,
    open val summary: String? = null,
    open val summaryAnnotated: AnnotatedString? = null,
    open val rightText: String? = null,
    open val rightTextAnnotated: AnnotatedString? = null,
    val type: PreferenceType = PreferenceType.Default,
    open val enabled: Boolean = true,
    open val isChecked: Boolean? = null,
    open val onCheck: ((Boolean) -> Unit)? = null,
    open val onClick: (() -> Unit)? = null,
    open val onLongClick: (() -> Unit)? = null,
    open val onSwipeToDelete: (() -> Unit)? = null,
    open val minValue: Float? = null,
    open val currentValue: Float? = null,
    open val maxValue: Float? = null,
    open val step: Int = 1,
    open val valueMultiplier: Int = 1,
    open val seekSuffix: String? = null,
    open val onSeek: ((Float) -> Unit)? = null,
    open val screenPosition: Position = Position.Alone,
    open val horizontalLayout: Boolean = false,
    open val tag: Any? = null,
) {
    val isHeader = type == PreferenceType.Header

    @Stable
    data class Header(
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
    ) : SettingsEntity(
        title = title,
        titleAnnotated = titleAnnotated,
        type = PreferenceType.Header
    )

    @Stable
    data class Preference(
        override val icon: ImageVector? = null,
        override val iconUri: String? = null,
        override val iconRes: Int? = null,
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
        override val summary: String? = null,
        override val summaryAnnotated: AnnotatedString? = null,
        override val rightText: String? = null,
        override val rightTextAnnotated: AnnotatedString? = null,
        override val enabled: Boolean = true,
        override val screenPosition: Position = Position.Alone,
        override val horizontalLayout: Boolean = false,
        override val onClick: (() -> Unit)? = null,
        override val onLongClick: (() -> Unit)? = null,
        override val onSwipeToDelete: (() -> Unit)? = null,
        override val tag: Any? = null,
    ) : SettingsEntity(
        icon = icon,
        iconUri = iconUri,
        iconRes = iconRes,
        title = title,
        titleAnnotated = titleAnnotated,
        summary = summary,
        summaryAnnotated = summaryAnnotated,
        rightText = rightText,
        rightTextAnnotated = rightTextAnnotated,
        enabled = enabled,
        screenPosition = screenPosition,
        horizontalLayout = horizontalLayout,
        onClick = onClick,
        onLongClick = onLongClick,
        onSwipeToDelete = onSwipeToDelete,
        tag = tag,
        type = PreferenceType.Default
    )

    @Stable
    data class PreferenceExtra(
        override val icon: ImageVector? = null,
        override val iconUri: String? = null,
        override val iconRes: Int? = null,
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
        override val summary: String? = null,
        override val summaryAnnotated: AnnotatedString? = null,
        override val rightText: String? = null,
        override val rightTextAnnotated: AnnotatedString? = null,
        override val enabled: Boolean = true,
        override val screenPosition: Position = Position.Alone,
        override val horizontalLayout: Boolean = false,
        override val onClick: (() -> Unit)? = null,
    ) : SettingsEntity(
        icon = icon,
        iconUri = iconUri,
        iconRes = iconRes,
        title = title,
        titleAnnotated = titleAnnotated,
        summary = summary,
        summaryAnnotated = summaryAnnotated,
        rightText = rightText,
        rightTextAnnotated = rightTextAnnotated,
        enabled = enabled,
        screenPosition = screenPosition,
        horizontalLayout = horizontalLayout,
        onClick = onClick,
        type = PreferenceType.Default
    )

    @Stable
    data class SwitchPreference(
        override val icon: ImageVector? = null,
        override val iconUri: String? = null,
        override val iconRes: Int? = null,
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
        override val summary: String? = null,
        override val summaryAnnotated: AnnotatedString? = null,
        override val enabled: Boolean = true,
        override val screenPosition: Position = Position.Alone,
        override val isChecked: Boolean = false,
        override val onCheck: ((Boolean) -> Unit)? = null,
        override val onClick: (() -> Unit)? = null,
    ) : SettingsEntity(
        icon = icon,
        iconUri = iconUri,
        iconRes = iconRes,
        title = title,
        titleAnnotated = titleAnnotated,
        summary = summary,
        summaryAnnotated = summaryAnnotated,
        enabled = enabled,
        isChecked = isChecked,
        onCheck = onCheck,
        onClick = onClick,
        screenPosition = screenPosition,
        type = PreferenceType.Switch
    )

    @Stable
    data class SeekPreference(
        override val icon: ImageVector? = null,
        override val iconUri: String? = null,
        override val iconRes: Int? = null,
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
        override val summary: String? = null,
        override val summaryAnnotated: AnnotatedString? = null,
        override val enabled: Boolean = true,
        override val screenPosition: Position = Position.Alone,
        override val minValue: Float? = null,
        override val currentValue: Float? = null,
        override val maxValue: Float? = null,
        override val step: Int = 1,
        override val valueMultiplier: Int = 1,
        override val seekSuffix: String? = null,
        override val onSeek: ((Float) -> Unit)? = null,
    ) : SettingsEntity(
        icon = icon,
        iconUri = iconUri,
        iconRes = iconRes,
        title = title,
        titleAnnotated = titleAnnotated,
        summary = summary,
        summaryAnnotated = summaryAnnotated,
        enabled = enabled,
        screenPosition = screenPosition,
        minValue = minValue,
        currentValue = currentValue,
        maxValue = maxValue,
        step = step,
        valueMultiplier = valueMultiplier,
        seekSuffix = seekSuffix,
        onSeek = onSeek,
        type = PreferenceType.Seek
    )

    @Stable
    data class AlbumPreference(
        override val title: String,
        override val titleAnnotated: AnnotatedString? = null,
        override val summary: String? = null,
        override val summaryAnnotated: AnnotatedString? = null,
        override val enabled: Boolean = true,
        override val screenPosition: Position = Position.Alone,
        override val onClick: (() -> Unit)? = null,
        val albumUri: Any? = null,
        val secondaryAlbumUri: Any? = null,
        val albumLabel: String? = null,
        val albumCount: Int = 0,
        val matchedAlbumsCount: Int = 0,
        val isMultiple: Boolean = false,
        val isWildcard: Boolean = false,
    ) : SettingsEntity(
        title = title,
        titleAnnotated = titleAnnotated,
        summary = summary,
        summaryAnnotated = summaryAnnotated,
        enabled = enabled,
        screenPosition = screenPosition,
        onClick = onClick,
        type = PreferenceType.Album
    )
}
