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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Constants.mosaicColumnsList
import com.dot.gallery.core.Settings.Misc.CURRENT_SETUP_VERSION
import com.dot.gallery.core.Settings.Misc.rememberSetupCompletedVersion
import com.dot.gallery.core.Settings.PREFERENCE_NAME
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.feature_node.presentation.util.SystemDateFormatField
import com.dot.gallery.feature_node.presentation.util.systemDateTimePattern
import com.dot.gallery.core.encryption.EncryptedDataStoreProvider
import com.dot.gallery.core.encryption.EncryptionBackendState
import com.dot.gallery.core.encryption.EncryptionStateMonitor
import com.dot.gallery.core.metrics.StartupTracer
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.security.AdvancedProtectionMonitor
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.util.rememberPreference
import com.dot.gallery.core.util.rememberPreferenceSerializable
import com.dot.gallery.feature_node.domain.model.SearchHistory
import com.dot.gallery.feature_node.domain.model.SelectionSheetConfig
import com.dot.gallery.feature_node.domain.model.SlideshowTransition
import com.dot.gallery.feature_node.presentation.mediaview.slideshow.SlideshowConfig
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.presentation.library.components.LibraryShortcutPref
import com.dot.gallery.feature_node.presentation.location.MapAppearance
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
            EncryptedDataStoreProvider.getOrCreate(this).also {
                EncryptionStateMonitor.reportSettings(EncryptionBackendState.ENCRYPTED)
            }
        } catch (_: Exception) {
            // Device doesn't support hardware-backed keystore or encryption failed.
            // Keep the app usable, but expose the fallback accurately in Security settings.
            StartupTracer.trace("activeDataStore.fallbackPlaintext") { }
            EncryptionStateMonitor.reportSettings(EncryptionBackendState.PLAINTEXT_FALLBACK)
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
        private val ALBUM_GROUP_BY_DATE = booleanPreferencesKey("album_group_by_date")

        @Composable
        fun rememberHideTimelineOnAlbum() =
            rememberPreference(key = HIDE_TIMELINE_ON_ALBUM, defaultValue = false)

        /**
         * Dedicated album grouping preference. The one-time, idempotent migration preserves the
         * previous inverse `hide_timeline_on_album` behavior without coupling the two settings.
         */
        fun resolveAlbumGroupByDate(
            storedValue: Boolean?,
            legacyHideTimeline: Boolean,
        ): Boolean = storedValue ?: !legacyHideTimeline

        @Composable
        fun rememberAlbumGroupByDate(): MutableState<Boolean> {
            val context = LocalContext.current
            val state = rememberPreference(key = ALBUM_GROUP_BY_DATE, defaultValue = true)
            LaunchedEffect(context) {
                context.activeDataStore.edit { preferences ->
                    if (!preferences.contains(ALBUM_GROUP_BY_DATE)) {
                        preferences[ALBUM_GROUP_BY_DATE] = resolveAlbumGroupByDate(
                            storedValue = null,
                            legacyHideTimeline = preferences[HIDE_TIMELINE_ON_ALBUM] ?: false,
                        )
                    }
                }
            }
            return state
        }

        val MERGE_ALBUMS_BY_NAME = booleanPreferencesKey("merge_albums_by_name")

        @Composable
        fun rememberMergeAlbumsByName() =
            rememberPreference(key = MERGE_ALBUMS_BY_NAME, defaultValue = true)

        val ALBUM_SECTIONS_ENABLED = booleanPreferencesKey("album_sections_enabled")

        @Composable
        fun rememberAlbumSectionsEnabled() =
            rememberPreference(key = ALBUM_SECTIONS_ENABLED, defaultValue = false)

        val PINNED_ALBUMS_AS_GRID = booleanPreferencesKey("pinned_albums_as_grid")

        @Composable
        fun rememberPinnedAlbumsAsGrid() =
            rememberPreference(key = PINNED_ALBUMS_AS_GRID, defaultValue = false)

        val SHOW_MEDIA_TYPE_ALBUMS = booleanPreferencesKey("show_media_type_albums")

        @Composable
        fun rememberShowMediaTypeAlbums() =
            rememberPreference(key = SHOW_MEDIA_TYPE_ALBUMS, defaultValue = true)

        private val UPDATE_MODIFIED_DATE = booleanPreferencesKey("update_modified_date")

        internal fun resolveUpdateModifiedDate(storedValue: Boolean?): Boolean = storedValue ?: false

        fun updateModifiedDate(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { resolveUpdateModifiedDate(it[UPDATE_MODIFIED_DATE]) }

        @Composable
        fun rememberUpdateModifiedDate() =
            rememberPreference(key = UPDATE_MODIFIED_DATE, defaultValue = false)
    }

    object Slideshow {
        val INTERVAL_SECONDS = intPreferencesKey("slideshow_interval_seconds")
        val RANDOM_ORDER = booleanPreferencesKey("slideshow_random_order")
        val REVERSE_ORDER = booleanPreferencesKey("slideshow_reverse_order")
        val INCLUDE_GIFS = booleanPreferencesKey("slideshow_include_gifs")
        val INCLUDE_VIDEOS = booleanPreferencesKey("slideshow_include_videos")
        val LOOP = booleanPreferencesKey("slideshow_loop")
        val TRANSITION = stringPreferencesKey("slideshow_transition")
        val KEN_BURNS = booleanPreferencesKey("slideshow_ken_burns")

        const val DEFAULT_INTERVAL = 5
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 60

        @Composable
        fun rememberIntervalSeconds() =
            rememberPreference(key = INTERVAL_SECONDS, defaultValue = DEFAULT_INTERVAL)

        @Composable
        fun rememberRandomOrder() =
            rememberPreference(key = RANDOM_ORDER, defaultValue = false)

        @Composable
        fun rememberReverseOrder() =
            rememberPreference(key = REVERSE_ORDER, defaultValue = false)

        @Composable
        fun rememberIncludeGifs() =
            rememberPreference(key = INCLUDE_GIFS, defaultValue = true)

        @Composable
        fun rememberIncludeVideos() =
            rememberPreference(key = INCLUDE_VIDEOS, defaultValue = true)

        @Composable
        fun rememberLoop() =
            rememberPreference(key = LOOP, defaultValue = true)

        @Composable
        fun rememberTransition() =
            rememberPreferenceSerializable(
                keyString = TRANSITION,
                defaultValue = SlideshowTransition.FADE
            )

        @Composable
        fun rememberKenBurns() =
            rememberPreference(key = KEN_BURNS, defaultValue = false)

        /** Reads the full persisted config synchronously (used to seed the viewer once). */
        fun readConfig(context: Context): SlideshowConfig = runBlocking {
            val prefs = context.activeDataStore.data.first()
            SlideshowConfig(
                intervalSeconds = prefs[INTERVAL_SECONDS] ?: DEFAULT_INTERVAL,
                random = prefs[RANDOM_ORDER] ?: false,
                reverse = prefs[REVERSE_ORDER] ?: false,
                includeGifs = prefs[INCLUDE_GIFS] ?: true,
                includeVideos = prefs[INCLUDE_VIDEOS] ?: true,
                loop = prefs[LOOP] ?: true,
                transition = prefs[TRANSITION]?.let {
                    runCatching { Json.decodeFromString<SlideshowTransition>(it) }.getOrNull()
                } ?: SlideshowTransition.FADE,
                kenBurns = prefs[KEN_BURNS] ?: false
            )
        }
    }

    object Library {
        val SHORTCUTS_LAYOUT = stringPreferencesKey("library_shortcuts_layout")

        /**
         * Persisted, user-editable layout for the Library shortcut tiles
         * (order, per-tile span and visibility). Empty by default — the screen
         * derives a sensible default order for any shortcut not yet stored.
         */
        @Composable
        fun rememberShortcutsLayout() =
            rememberPreferenceSerializable(
                keyString = SHORTCUTS_LAYOUT,
                defaultValue = emptyList<LibraryShortcutPref>()
            )
    }

    object SmartFeatures {
        private val INCLUDE_IGNORED_ALBUMS = booleanPreferencesKey("smart_features_include_ignored_albums")

        fun includeIgnoredAlbums(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[INCLUDE_IGNORED_ALBUMS] ?: false }

        suspend fun setIncludeIgnoredAlbums(context: Context, include: Boolean) {
            context.activeDataStore.edit { it[INCLUDE_IGNORED_ALBUMS] = include }
        }
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

        private val TIMELINE_GROUP_BY_YEAR = booleanPreferencesKey("timeline_group_by_year")

        @Composable
        fun rememberTimelineGroupByYear() =
            rememberPreference(key = TIMELINE_GROUP_BY_YEAR, defaultValue = false)

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

        val GROUP_CLOUD_LOCAL = booleanPreferencesKey("group_cloud_local")

        @Composable
        fun rememberGroupCloudLocal() =
            rememberPreference(key = GROUP_CLOUD_LOCAL, defaultValue = true)

        private val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")

        @Composable
        fun rememberUseSystemFont() =
            rememberPreference(key = USE_SYSTEM_FONT, defaultValue = false)

        private val ALLOW_BLUR = booleanPreferencesKey("allow_blur")

        @Composable
        fun rememberAllowBlur() = rememberPreference(key = ALLOW_BLUR, defaultValue = SdkCompat.supportsBlur)

        private val AUTO_CONTRAST = booleanPreferencesKey("auto_contrast")

        @Composable
        fun rememberAutoContrast() = rememberPreference(key = AUTO_CONTRAST, defaultValue = false)

        private val DISABLE_SMOOTHING = booleanPreferencesKey("disable_smoothing")

        @Composable
        fun rememberDisableSmoothing() =
            rememberPreference(key = DISABLE_SMOOTHING, defaultValue = false)

        private val LONG_PRESS_CUTOUT = booleanPreferencesKey("long_press_cutout")

        // false (default): long-press rotates the image, cut-out is offered as an on-screen pill.
        // true: long-press starts a subject cut-out, rotate is offered as an on-screen pill.
        @Composable
        fun rememberLongPressCutout() =
            rememberPreference(key = LONG_PRESS_CUTOUT, defaultValue = false)

        // ==== Re-encode / save quality (format-preserving overwrite) ====
        // Mode: "auto" (JXL lossless where supported, else high quality) or "manual" (use sliders).
        private val REENCODE_QUALITY_MODE = stringPreferencesKey("reencode_quality_mode")
        const val REENCODE_MODE_AUTO = "auto"
        const val REENCODE_MODE_MANUAL = "manual"

        @Composable
        fun rememberReencodeQualityMode() =
            rememberPreference(key = REENCODE_QUALITY_MODE, defaultValue = REENCODE_MODE_AUTO)

        // Shared lossy quality (1..100) for JPEG/WebP/AVIF/HEIC when re-encoding.
        private val REENCODE_LOSSY_QUALITY = intPreferencesKey("reencode_lossy_quality")

        @Composable
        fun rememberReencodeLossyQuality() =
            rememberPreference(key = REENCODE_LOSSY_QUALITY, defaultValue = 90)

        // JXL encoder effort (1 = fastest .. 9 = slowest/best).
        private val REENCODE_JXL_EFFORT = intPreferencesKey("reencode_jxl_effort")

        @Composable
        fun rememberReencodeJxlEffort() =
            rememberPreference(key = REENCODE_JXL_EFFORT, defaultValue = 7)

        /**
         * Reads the persisted re-encode configuration synchronously (used by editor/rotate on IO
         * threads). [detectedQuality] is an optional per-file best-effort source-quality estimate
         * that overrides the configured lossy quality in AUTO mode.
         */
        fun getReencodeConfig(
            context: Context,
            detectedQuality: Int? = null
        ): ImageReencoder.ReencodeConfig = runBlocking {
            val prefs = context.activeDataStore.data.first()
            val mode = when (prefs[REENCODE_QUALITY_MODE]) {
                REENCODE_MODE_MANUAL -> ImageReencoder.QualityMode.MANUAL
                else -> ImageReencoder.QualityMode.AUTO
            }
            ImageReencoder.ReencodeConfig(
                mode = mode,
                lossyQuality = prefs[REENCODE_LOSSY_QUALITY] ?: 90,
                jxlEffort = prefs[REENCODE_JXL_EFFORT] ?: 7,
                jxlLossless = true,
                detectedQuality = detectedQuality
            )
        }

        private val FRAME_EXPORT_FORMAT = stringPreferencesKey("frame_export_format")

        suspend fun getFrameExportFormat(context: Context): String =
            context.activeDataStore.data.first()[FRAME_EXPORT_FORMAT] ?: "jpeg"

        suspend fun setFrameExportFormat(context: Context, value: String) {
            context.activeDataStore.edit { preferences ->
                preferences[FRAME_EXPORT_FORMAT] = value
            }
        }

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

        private val FULL_BRIGHTNESS_VIEW = booleanPreferencesKey("full_brightness_view")

        @Composable
        fun rememberFullBrightnessView() =
            rememberPreference(key = FULL_BRIGHTNESS_VIEW, defaultValue = false)

        private val TAP_SIDES_TO_NAVIGATE = booleanPreferencesKey("tap_sides_to_navigate")
        private val TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN =
            booleanPreferencesKey("tap_sides_to_navigate_prompt_shown")

        @Composable
        fun rememberTapSidesToNavigate() =
            rememberPreference(key = TAP_SIDES_TO_NAVIGATE, defaultValue = false)

        suspend fun claimTapSidesToNavigatePrompt(context: Context): Boolean {
            var shouldShow = false
            context.activeDataStore.edit { preferences ->
                if (!preferences.contains(TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN)) {
                    shouldShow = !preferences.contains(TAP_SIDES_TO_NAVIGATE)
                    preferences[TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN] = true
                }
            }
            return shouldShow
        }

        suspend fun releaseTapSidesToNavigatePrompt(context: Context) {
            context.activeDataStore.edit { it.remove(TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN) }
        }

        suspend fun markTapSidesToNavigatePromptShown(context: Context) {
            context.activeDataStore.edit { it[TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN] = true }
        }

        private val AUTO_HIDE_ON_VIDEO_PLAY = booleanPreferencesKey("auto_hide_on_video_play")

        @Composable
        fun rememberAutoHideOnVideoPlay() =
            rememberPreference(key = AUTO_HIDE_ON_VIDEO_PLAY, defaultValue = true)

        val NO_CLASSIFICATION = booleanPreferencesKey("no_classification")

        @Composable
        fun rememberNoClassification() =
            rememberPreference(key = NO_CLASSIFICATION, defaultValue = false)

        /**
         * Date/time format preferences store an empty string when the user has
         * not customized them. In that case the effective pattern is derived from
         * the system locale + 12/24-hour setting at read time (see #953).
         * Use rememberRaw*Format() in the editor (to distinguish system-follow
         * from a custom value); use remember*Format() everywhere else to obtain
         * the resolved, display-ready pattern.
         */
        @Composable
        private fun rememberResolvedDateFormat(
            key: Preferences.Key<String>,
            field: SystemDateFormatField
        ): State<String> {
            val raw by rememberPreference(key = key, defaultValue = "")
            val context = LocalContext.current
            // Subscribe to configuration so locale / 24h changes trigger recomposition.
            LocalConfiguration.current
            val resolved = if (raw.isBlank()) systemDateTimePattern(context, field) else raw
            return rememberUpdatedState(resolved)
        }

        val DATE_HEADER_FORMAT = stringPreferencesKey("date_header_format")

        @Composable
        fun rememberRawDateHeaderFormat() =
            rememberPreference(key = DATE_HEADER_FORMAT, defaultValue = "")

        @Composable
        fun rememberDateHeaderFormat() =
            rememberResolvedDateFormat(DATE_HEADER_FORMAT, SystemDateFormatField.HEADER)

        val EXTENDED_DATE_HEADER_FORMAT = stringPreferencesKey("extended_date_header_format")

        @Composable
        fun rememberRawExtendedDateHeaderFormat() =
            rememberPreference(key = EXTENDED_DATE_HEADER_FORMAT, defaultValue = "")

        @Composable
        fun rememberExtendedDateHeaderFormat() =
            rememberResolvedDateFormat(
                EXTENDED_DATE_HEADER_FORMAT,
                SystemDateFormatField.EXTENDED_HEADER
            )

        val EXIF_DATE_FORMAT = stringPreferencesKey("exif_date_format")

        @Composable
        fun rememberRawExifDateFormat() =
            rememberPreference(key = EXIF_DATE_FORMAT, defaultValue = "")

        @Composable
        fun rememberExifDateFormat() =
            rememberResolvedDateFormat(EXIF_DATE_FORMAT, SystemDateFormatField.EXIF)

        val EXTENDED_DATE_FORMAT = stringPreferencesKey("extended_date_format")

        @Composable
        fun rememberRawExtendedDateFormat() =
            rememberPreference(key = EXTENDED_DATE_FORMAT, defaultValue = "")

        @Composable
        fun rememberExtendedDateFormat() =
            rememberResolvedDateFormat(EXTENDED_DATE_FORMAT, SystemDateFormatField.EXTENDED)

        val DEFAULT_DATE_FORMAT = stringPreferencesKey("default_date_format")

        @Composable
        fun rememberRawDefaultDateFormat() =
            rememberPreference(key = DEFAULT_DATE_FORMAT, defaultValue = "")

        @Composable
        fun rememberDefaultDateFormat() =
            rememberResolvedDateFormat(DEFAULT_DATE_FORMAT, SystemDateFormatField.DEFAULT)

        val WEEKLY_DATE_FORMAT = stringPreferencesKey("weekly_date_format")

        @Composable
        fun rememberRawWeeklyDateFormat() =
            rememberPreference(key = WEEKLY_DATE_FORMAT, defaultValue = "")

        @Composable
        fun rememberWeeklyDateFormat() =
            rememberResolvedDateFormat(WEEKLY_DATE_FORMAT, SystemDateFormatField.WEEKLY)

        fun <T> getSetting(context: Context, key: Preferences.Key<T>, defaultValue: T) =
            context.activeDataStore.data.map { it[key] ?: defaultValue }

        private val VIDEO_AUTOPLAY = booleanPreferencesKey("video_autoplay")

        @Composable
        fun rememberVideoAutoplay() =
            rememberPreference(key = VIDEO_AUTOPLAY, defaultValue = true)

        private val VIDEO_SURFACE_REBIND = booleanPreferencesKey("video_surface_rebind")

        /**
         * Workaround for video [android.view.SurfaceView] blacking out when the
         * system bars are toggled while playing (#967). When enabled, the player
         * surface is re-bound on every system-bar visibility change. Defaults to on
         * for Samsung devices, where the issue is observed, and off elsewhere.
         */
        @Composable
        fun rememberVideoSurfaceRebind() =
            rememberPreference(key = VIDEO_SURFACE_REBIND, defaultValue = SdkCompat.isSamsung)

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

        private val APP_LOGO_ALIAS = stringPreferencesKey("app_logo_alias")

        @Composable
        fun rememberAppLogoAlias() =
            rememberPreference(key = APP_LOGO_ALIAS, defaultValue = ALIAS_REFRA)

        /**
         * Version of the first-launch setup wizard the user last completed. Bump
         * [CURRENT_SETUP_VERSION] whenever the wizard is reworked to force every existing
         * user through it again.
         */
        const val CURRENT_SETUP_VERSION = 1

        /**
         * TEMPORARY: while the setup wizard is still being built out, force it to appear on
         * every app launch regardless of whether it was completed before. Set this back to
         * `false` once the wizard is feature-complete so the normal version-based gating
         * (only show until [CURRENT_SETUP_VERSION] is completed) takes over.
         */
        const val FORCE_SETUP_WIZARD = false

        private val SETUP_COMPLETED_VERSION = intPreferencesKey("setup_completed_version")

        @Composable
        fun rememberSetupCompletedVersion() =
            rememberPreference(key = SETUP_COMPLETED_VERSION, defaultValue = 0)

        /**
         * Synchronously determine whether the setup wizard should be shown at startup.
         * Reading the value directly (rather than via [rememberSetupCompletedVersion], which
         * emits its default first and the stored value asynchronously) avoids briefly showing
         * the wizard before navigating to the real start screen.
         */
        fun isSetupNeeded(context: Context): Boolean {
            if (FORCE_SETUP_WIZARD) return true
            val completed = runBlocking {
                context.activeDataStore.data.first()[SETUP_COMPLETED_VERSION] ?: 0
            }
            return completed < CURRENT_SETUP_VERSION
        }

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

        private val SHOW_SEARCHBAR_FAVORITE_BUTTON = booleanPreferencesKey("show_searchbar_favorite_button")

        @Composable
        fun rememberShowSearchBarFavoriteButton() =
            rememberPreference(key = SHOW_SEARCHBAR_FAVORITE_BUTTON, defaultValue = true)

        private val SHOW_FILTER_BUTTON = booleanPreferencesKey("show_filter_button")

        @Composable
        fun rememberShowFilterButton() =
            rememberPreference(key = SHOW_FILTER_BUTTON, defaultValue = true)

        private val FAVORITES_GROUP_BY_DATE = booleanPreferencesKey("favorites_group_by_date")

        @Composable
        fun rememberFavoritesGroupByDate() =
            rememberPreference(key = FAVORITES_GROUP_BY_DATE, defaultValue = true)

        private val TIMELINE_GROUP_BY_DATE = booleanPreferencesKey("timeline_group_by_date")

        @Composable
        fun rememberTimelineGroupByDate() =
            rememberPreference(key = TIMELINE_GROUP_BY_DATE, defaultValue = true)

        private val VAULT_GROUP_BY_DATE = booleanPreferencesKey("vault_group_by_date")

        @Composable
        fun rememberVaultGroupByDate() =
            rememberPreference(key = VAULT_GROUP_BY_DATE, defaultValue = true)

        private val CLOUD_ARCHIVE_GROUP_BY_DATE = booleanPreferencesKey("cloud_archive_group_by_date")

        @Composable
        fun rememberCloudArchiveGroupByDate() =
            rememberPreference(key = CLOUD_ARCHIVE_GROUP_BY_DATE, defaultValue = false)

        private val LOCATION_GROUP_BY_DATE = booleanPreferencesKey("location_group_by_date")

        @Composable
        fun rememberLocationGroupByDate() =
            rememberPreference(key = LOCATION_GROUP_BY_DATE, defaultValue = true)

        const val GROUP_NORMAL = "normal"
        const val GROUP_MONTHLY = "monthly"
        const val GROUP_YEARLY = "yearly"

        private val TIMELINE_GROUP_METHOD = stringPreferencesKey("timeline_group_method")
        @Composable
        fun rememberTimelineGroupMethod() =
            rememberPreference(key = TIMELINE_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val ALBUMS_GROUP_METHOD = stringPreferencesKey("albums_group_method")
        @Composable
        fun rememberAlbumsGroupMethod() =
            rememberPreference(key = ALBUMS_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val FAVORITES_GROUP_METHOD = stringPreferencesKey("favorites_group_method")
        @Composable
        fun rememberFavoritesGroupMethod() =
            rememberPreference(key = FAVORITES_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val VAULT_GROUP_METHOD = stringPreferencesKey("vault_group_method")
        @Composable
        fun rememberVaultGroupMethod() =
            rememberPreference(key = VAULT_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val CLOUD_ARCHIVE_GROUP_METHOD = stringPreferencesKey("cloud_archive_group_method")
        @Composable
        fun rememberCloudArchiveGroupMethod() =
            rememberPreference(key = CLOUD_ARCHIVE_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val LOCATION_GROUP_METHOD = stringPreferencesKey("location_group_method")
        @Composable
        fun rememberLocationGroupMethod() =
            rememberPreference(key = LOCATION_GROUP_METHOD, defaultValue = GROUP_NORMAL)

        private val MAP_APPEARANCE = stringPreferencesKey("map_appearance")

        @Composable
        fun rememberMapAppearance(): MutableState<MapAppearance> {
            val stored = rememberPreference(
                key = MAP_APPEARANCE,
                defaultValue = MapAppearance.SYSTEM.storedValue
            )
            return remember(stored) {
                object : MutableState<MapAppearance> {
                    override var value: MapAppearance
                        get() = MapAppearance.fromStored(stored.value)
                        set(value) {
                            stored.value = value.storedValue
                        }

                    override fun component1(): MapAppearance = value
                    override fun component2(): (MapAppearance) -> Unit = { value = it }
                }
            }
        }

        private val CARTO_BASEMAP_KEY = stringPreferencesKey("carto_basemap_key")

        @Composable
        fun rememberCartoBasemapKey() =
            rememberPreference(key = CARTO_BASEMAP_KEY, defaultValue = "")

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

        /**
         * Effective metadata isolation mode. When Android Advanced Protection Mode
         * (AAPM) is enabled, the "shared" mode is raised to "hybrid" automatically;
         * the user's stored preference is left untouched and is restored once AAPM
         * is turned off.
         */
        fun getMetadataIsolationMode(context: Context) =
            context.activeDataStore.data
                .map { it[METADATA_ISOLATION_MODE] ?: METADATA_ISOLATION_SHARED }
                .combine(AdvancedProtectionMonitor.enabled) { mode, aapm ->
                    if (aapm && mode == METADATA_ISOLATION_SHARED) METADATA_ISOLATION_HYBRID else mode
                }

        private val SANDBOXED_DECODE = booleanPreferencesKey("sandboxed_decode")

        @Composable
        fun rememberSandboxedDecode() =
            rememberPreference(key = SANDBOXED_DECODE, defaultValue = false)

        /**
         * Effective sandboxed-decode state. Forced on whenever Android Advanced
         * Protection Mode (AAPM) is enabled, regardless of the stored preference.
         */
        fun getSandboxedDecode(context: Context) =
            context.activeDataStore.data
                .map { it[SANDBOXED_DECODE] ?: false }
                .combine(AdvancedProtectionMonitor.enabled) { pref, aapm -> pref || aapm }

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
