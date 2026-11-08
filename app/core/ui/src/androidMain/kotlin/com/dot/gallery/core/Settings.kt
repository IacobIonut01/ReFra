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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.SearchModel
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.domain.date.SystemDateFormatField
import com.dot.gallery.core.presentation.util.systemDateTimePattern
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.core.security.AdvancedProtectionMonitor
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.domain.model.SearchHistory
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.settings.resolveLongPressAction
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.feature.viewer.SlideshowConfig
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.core.location.MapAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.ui.theme.seed
import com.dot.gallery.core.presentation.components.util.permissionGranted
import com.dot.gallery.core.navigation.Routes
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Constants.cellsList
import com.dot.gallery.core.Constants.mosaicColumnsList
import com.dot.gallery.core.Settings.Misc.CURRENT_SETUP_VERSION
import com.dot.gallery.core.Settings.Misc.rememberSetupCompletedVersion
import com.dot.gallery.domain.model.StoryCardsConfig
import com.dot.gallery.domain.uri.query
import com.dot.gallery.ui.theme.THEME_SEED_NEUTRAL
import com.dot.gallery.ui.theme.THEME_SEED_SYSTEM
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.feature.library.ui.libraryShortcutsLayoutKey
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.logging.printDebug
import com.dot.gallery.core.logging.printError


object Settings {

    object Album {
        private val ALBUM_MEDIA_SORT = stringPreferencesKey("album_media_sort_obj")

        /** Moved to `:core:domain` for KMP — alias keeps `Settings.Album.LastSort` references working. */
        typealias LastSort = com.dot.gallery.domain.model.LastSort

        /** Moved to `:core:domain` for KMP — alias keeps `Settings.Album.ViewType` references working. */
        typealias ViewType = com.dot.gallery.domain.model.AlbumViewType

        fun getAlbumMediaSortFlow(context: Context): Flow<LastSort> =
            context.activeDataStore.data.map { prefs ->
                prefs[ALBUM_MEDIA_SORT]?.let {
                    runCatching { Json.decodeFromString<LastSort>(it) }.getOrNull()
                } ?: LastSort(OrderType.Descending, FilterKind.DATE)
            }

        @Composable
        fun rememberLastSort() =
            rememberPreference(SettingsKeys.albumLastSort)

        @Composable
        fun rememberAlbumMediaSort() =
            rememberPreference(SettingsKeys.albumMediaSort)

        @Composable
        fun rememberLastViewType() =
            rememberPreference(SettingsKeys.albumViewType)

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
            rememberPreference(SettingsKeys.hideTimelineOnAlbum)

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
            val state = rememberPreference(SettingsKeys.albumGroupByDate)
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
            rememberPreference(SettingsKeys.mergeAlbumsByName)

        val ALBUM_SECTIONS_ENABLED = booleanPreferencesKey("album_sections_enabled")

        @Composable
        fun rememberAlbumSectionsEnabled() =
            rememberPreference(SettingsKeys.albumSectionsEnabled)

        @Composable
        fun rememberPinnedAlbumsAsGrid() =
            rememberPreference(SettingsKeys.pinnedAlbumsAsGrid)

        @Composable
        fun rememberShowMediaTypeAlbums() =
            rememberPreference(SettingsKeys.showMediaTypeAlbums)

        private val UPDATE_MODIFIED_DATE = booleanPreferencesKey("update_modified_date")

        fun resolveUpdateModifiedDate(storedValue: Boolean?): Boolean = storedValue ?: false

        fun updateModifiedDate(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { resolveUpdateModifiedDate(it[UPDATE_MODIFIED_DATE]) }

        @Composable
        fun rememberUpdateModifiedDate() =
            rememberPreference(SettingsKeys.updateModifiedDate)
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
            rememberPreference(SettingsKeys.slideshowIntervalSeconds)

        @Composable
        fun rememberRandomOrder() =
            rememberPreference(SettingsKeys.slideshowRandomOrder)

        @Composable
        fun rememberReverseOrder() =
            rememberPreference(SettingsKeys.slideshowReverseOrder)

        @Composable
        fun rememberIncludeGifs() =
            rememberPreference(SettingsKeys.slideshowIncludeGifs)

        @Composable
        fun rememberIncludeVideos() =
            rememberPreference(SettingsKeys.slideshowIncludeVideos)

        @Composable
        fun rememberLoop() =
            rememberPreference(SettingsKeys.slideshowLoop)

        @Composable
        fun rememberTransition() =
            rememberPreference(SettingsKeys.slideshowTransition)

        @Composable
        fun rememberKenBurns() =
            rememberPreference(SettingsKeys.slideshowKenBurns)

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
        /**
         * Persisted, user-editable layout for the Library shortcut tiles
         * (order, per-tile span and visibility). Empty by default — the screen
         * derives a sensible default order for any shortcut not yet stored.
         */
        @Composable
        fun rememberShortcutsLayout() =
            rememberPreference(libraryShortcutsLayoutKey)

        /**
         * Whether the Library screen shows the Locations carousel section (#1181).
         * Also read by `LibraryContentInputs` as the data-collection gate — it is a
         * data switch, not just a display toggle.
         */
        @Composable
        fun rememberShowLocationCategories() =
            rememberPreference(SettingsKeys.showLocationCategories)

        /** Section ids that can be toggled in the Library edit mode (#1262). */
        const val SECTION_PEOPLE = "people"
        const val SECTION_CATEGORIES = "categories"

        /**
         * Sections the user hid via the Library edit mode. Locations is not in this
         * set — it keeps [rememberShowLocationCategories], which also gates collection.
         */
        @Composable
        fun rememberHiddenSections() =
            rememberPreference(SettingsKeys.hiddenSections)
    }

    object SmartFeatures {
        private val INCLUDE_IGNORED_ALBUMS = booleanPreferencesKey("smart_features_include_ignored_albums")
        private val INDEX_ON_DEVICE_PROVIDERS = stringSetPreferencesKey("smart_features_index_on_device_providers")
        private val PROVIDER_SMART_SEARCH = booleanPreferencesKey("smart_features_provider_smart_search")

        fun includeIgnoredAlbums(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[INCLUDE_IGNORED_ALBUMS] ?: false }

        suspend fun setIncludeIgnoredAlbums(context: Context, include: Boolean) {
            context.activeDataStore.edit { it[INCLUDE_IGNORED_ALBUMS] = include }
        }

        /**
         * Provider type names (ProviderType.name) whose cached cloud media is included in
         * the on-device Smart Scan (search embeddings + face detection). Empty by default:
         * providers that declare SMART_SEARCH are delegated to the server instead, and
         * indexing remote media on-device costs a thumbnail download per asset.
         */
        fun indexOnDeviceProviders(context: Context): Flow<Set<String>> =
            context.activeDataStore.data.map { it[INDEX_ON_DEVICE_PROVIDERS] ?: emptySet() }

        suspend fun setIndexOnDeviceProvider(
            context: Context,
            provider: ProviderType,
            enabled: Boolean
        ) {
            context.activeDataStore.edit { prefs ->
                val current = prefs[INDEX_ON_DEVICE_PROVIDERS] ?: emptySet()
                prefs[INDEX_ON_DEVICE_PROVIDERS] =
                    if (enabled) current + provider.name else current - provider.name
            }
        }

        /**
         * Whether text/image queries are delegated to connected servers that declare
         * SMART_SEARCH (Immich CLIP search). Default on.
         */
        fun providerSmartSearch(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[PROVIDER_SMART_SEARCH] ?: true }

        suspend fun setProviderSmartSearch(context: Context, enabled: Boolean) {
            context.activeDataStore.edit { it[PROVIDER_SMART_SEARCH] = enabled }
        }

        private val SEARCH_MODEL = stringPreferencesKey("smart_features_search_model")

        /**
         * The search engine powering Smart Search + Categories. Defaults to CLIP (the
         * historical engine); switching re-indexes the library on the next Smart Scan.
         */
        fun searchModel(context: Context): Flow<SearchModel> =
            context.activeDataStore.data.map { prefs ->
                prefs[SEARCH_MODEL]
                    ?.let { runCatching { SearchModel.valueOf(it) }.getOrNull() }
                    ?: SearchModel.DEFAULT
            }

        suspend fun setSearchModel(context: Context, model: SearchModel) {
            context.activeDataStore.edit { it[SEARCH_MODEL] = model.name }
        }

        private val REMOVED_MODEL_GROUPS = stringSetPreferencesKey("smart_features_removed_model_groups")

        /**
         * Model groups the user explicitly deleted. Persisted so a bundled (withML) build does
         * not silently re-install them on the next app launch (issue #1229).
         */
        fun removedModelGroups(context: Context): Flow<Set<ModelGroup>> =
            context.activeDataStore.data.map { prefs ->
                prefs[REMOVED_MODEL_GROUPS].orEmpty()
                    .flatMapTo(LinkedHashSet()) { name ->
                        // "SEARCH" was the single search-model group before CLIP and
                        // EmbeddingGemma 2 became selectable engines — honor the removal
                        // intent for every search engine, not just its enum successor.
                        if (name == "SEARCH") {
                            listOf(ModelGroup.SEARCH_CLIP, ModelGroup.SEARCH_EG2)
                        } else {
                            listOfNotNull(runCatching { ModelGroup.valueOf(name) }.getOrNull())
                        }
                    }
            }

        suspend fun setModelGroupRemoved(context: Context, group: ModelGroup, removed: Boolean) {
            context.activeDataStore.edit { prefs ->
                val current = prefs[REMOVED_MODEL_GROUPS] ?: emptySet()
                prefs[REMOVED_MODEL_GROUPS] =
                    if (removed) current + group.name else current - group.name
            }
        }
    }

    /**
     * ReFra Handover — self-hosted remote inference / search-provider server.
     * See HANDOVER_MEGAPLAN.md; wire protocol is HPKG v1 (docs/protocol.md).
     */
    object Handover {
        private val ENABLED = booleanPreferencesKey("handover_enabled")
        private val REMOTE_URL = stringPreferencesKey("handover_remote_url")
        private val LOCAL_URL = stringPreferencesKey("handover_local_url")
        private val LOCAL_WIFI_SSID = stringPreferencesKey("handover_local_wifi_ssid")
        private val AUTO_URL_SWITCH = booleanPreferencesKey("handover_auto_url_switch")
        private val ENGINE = stringPreferencesKey("handover_engine")
        private val SINK = stringPreferencesKey("handover_sink")
        private val ENCRYPTED_SECRET = stringPreferencesKey("handover_secret")
        private val AUTH_MODE = stringPreferencesKey("handover_auth_mode")
        private val PREFER_REMOTE_QUERIES = booleanPreferencesKey("handover_prefer_remote_queries")
        private val PENDING_INDEX_DELETES = stringSetPreferencesKey("handover_pending_index_deletes")

        fun enabled(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[ENABLED] ?: false }

        suspend fun setEnabled(context: Context, enabled: Boolean) {
            context.activeDataStore.edit { it[ENABLED] = enabled }
        }

        /** Externally reachable base URL of the handover server. */
        fun remoteUrl(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[REMOTE_URL] ?: "" }

        suspend fun setRemoteUrl(context: Context, url: String) {
            context.activeDataStore.edit { it[REMOTE_URL] = url.trimEnd('/') }
        }

        /** LAN base URL used while on [localWifiSsid] when [autoUrlSwitch] is on. */
        fun localUrl(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[LOCAL_URL] ?: "" }

        suspend fun setLocalUrl(context: Context, url: String) {
            context.activeDataStore.edit { it[LOCAL_URL] = url.trimEnd('/') }
        }

        fun localWifiSsid(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[LOCAL_WIFI_SSID] ?: "" }

        suspend fun setLocalWifiSsid(context: Context, ssid: String) {
            context.activeDataStore.edit { it[LOCAL_WIFI_SSID] = ssid }
        }

        fun autoUrlSwitch(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[AUTO_URL_SWITCH] ?: true }

        suspend fun setAutoUrlSwitch(context: Context, enabled: Boolean) {
            context.activeDataStore.edit { it[AUTO_URL_SWITCH] = enabled }
        }

        /** Engine id override; empty = server default. */
        fun engine(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[ENGINE] ?: "" }

        suspend fun setEngine(context: Context, engine: String) {
            context.activeDataStore.edit { it[ENGINE] = engine }
        }

        /** Where computed embeddings land: "client" | "server" | "both". */
        fun sink(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[SINK] ?: "both" }

        suspend fun setSink(context: Context, sink: String) {
            context.activeDataStore.edit { it[SINK] = sink }
        }

        /** Bearer secret, AES-256-GCM encrypted via CredentialEncryptor. */
        fun encryptedSecret(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[ENCRYPTED_SECRET] ?: "" }

        suspend fun setEncryptedSecret(context: Context, encrypted: String) {
            context.activeDataStore.edit { it[ENCRYPTED_SECRET] = encrypted }
        }

        /** "bearer" (default) | "hmac" — must match the server's HANDOVER_AUTH_MODE. */
        fun authMode(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[AUTH_MODE] ?: "bearer" }

        suspend fun setAuthMode(context: Context, mode: String) {
            context.activeDataStore.edit { it[AUTH_MODE] = mode }
        }

        /** Route text search through the server-side index (POST /api/v1/query). */
        fun preferRemoteQueries(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[PREFER_REMOTE_QUERIES] ?: true }

        suspend fun setPreferRemoteQueries(context: Context, enabled: Boolean) {
            context.activeDataStore.edit { it[PREFER_REMOTE_QUERIES] = enabled }
        }

        /**
         * Media ids queued for POST /api/v1/index/delete — persisted so deletes
         * made while offline/unconfigured flush on the next successful call.
         */
        fun pendingIndexDeletes(context: Context): Flow<Set<String>> =
            context.activeDataStore.data.map { it[PENDING_INDEX_DELETES] ?: emptySet() }

        suspend fun addPendingIndexDeletes(context: Context, ids: Collection<String>) {
            if (ids.isEmpty()) return
            context.activeDataStore.edit { prefs ->
                prefs[PENDING_INDEX_DELETES] =
                    (prefs[PENDING_INDEX_DELETES] ?: emptySet()) + ids.toSet()
            }
        }

        suspend fun removePendingIndexDeletes(context: Context, ids: Collection<String>) {
            if (ids.isEmpty()) return
            context.activeDataStore.edit { prefs ->
                prefs[PENDING_INDEX_DELETES] =
                    (prefs[PENDING_INDEX_DELETES] ?: emptySet()) - ids.toSet()
            }
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
        fun rememberSearchHistory() = rememberPreference(SettingsKeys.searchHistory)

    }

    object Misc {
        @RequiresApi(Build.VERSION_CODES.S)
        @Composable
        fun rememberIsMediaManager() =
            rememberPreference(
                SettingsKeys.useMediaManager.withDefault(
                    MediaStore.canManageMedia(LocalContext.current)
                )
            )

        private val ENABLE_TRASH = booleanPreferencesKey("enable_trashcan")

        @Composable
        fun rememberTrashEnabled() =
            rememberPreference(SettingsKeys.trashEnabled.withDefault(SdkCompat.supportsTrash))

        fun getTrashEnabled(context: Context) =
            context.activeDataStore.data.map { it[ENABLE_TRASH] ?: SdkCompat.supportsTrash }

        @Composable
        fun rememberTrashConfirmationEnabled() =
            rememberPreference(SettingsKeys.trashConfirmationEnabled)

        const val DELETE_SCOPE_ASK = "ask"
        const val DELETE_SCOPE_DEVICE = "device"
        const val DELETE_SCOPE_CLOUD = "cloud"
        const val DELETE_SCOPE_BOTH = "both"
        /**
         * Where media that exists on the device AND in a cloud account is deleted.
         * [DELETE_SCOPE_ASK] keeps the per-delete picker ("delete from device /
         * cloud / both"); any other value is applied silently as the saved default.
         */
        @Composable
        fun rememberCloudDeleteScope() =
            rememberPreference(SettingsKeys.cloudDeleteScope)

        private val SYNC_ON_APP_OPEN = booleanPreferencesKey("cloud_sync_on_app_open")

        @Composable
        fun rememberSyncOnAppOpen() =
            rememberPreference(SettingsKeys.syncOnAppOpen)

        fun getSyncOnAppOpen(context: Context) =
            context.activeDataStore.data.map { it[SYNC_ON_APP_OPEN] ?: true }

        private val LAST_SCREEN = stringPreferencesKey("last_screen")

        @Composable
        fun rememberLastScreen() =
            rememberPreference(SettingsKeys.lastScreen)

        @Composable
        fun rememberLastSeenVersion() =
            rememberPreference(SettingsKeys.lastSeenVersion)

        @Composable
        fun rememberForcedLastScreen() =
            rememberPreference(SettingsKeys.forcedLastScreen)

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

        @Composable
        fun rememberForceTheme() =
            rememberPreference(SettingsKeys.forceTheme)

        @Composable
        fun rememberIsDarkMode() =
            rememberPreference(SettingsKeys.darkMode)

        @Composable
        fun rememberIsAmoledMode() =
            rememberPreference(SettingsKeys.amoledMode)

        val THEME_SEED_SYSTEM = com.dot.gallery.ui.theme.THEME_SEED_SYSTEM
        val THEME_SEED_NEUTRAL = com.dot.gallery.ui.theme.THEME_SEED_NEUTRAL

        @Composable
        fun rememberThemeColorSeed() =
            rememberPreference(SettingsKeys.themeColorSeed)

        private val SECURE_MODE = booleanPreferencesKey("secure_mode")

        @Composable
        fun rememberSecureMode() =
            rememberPreference(SettingsKeys.secureMode)

        fun getSecureMode(context: Context) =
            context.activeDataStore.data.map { it[SECURE_MODE] ?: false }

        private val TIMELINE_SORT = stringPreferencesKey("timeline_sort_obj")

        fun getTimelineSortFlow(context: Context): Flow<Album.LastSort> =
            context.activeDataStore.data.map { prefs ->
                prefs[TIMELINE_SORT]?.let {
                    runCatching { Json.decodeFromString<Album.LastSort>(it) }.getOrNull()
                } ?: Album.LastSort(OrderType.Descending, FilterKind.DATE)
            }

        @Composable
        fun rememberTimelineSort() =
            rememberPreference(SettingsKeys.timelineSort)

        @Composable
        fun rememberTimelineGroupByMonth() =
            rememberPreference(SettingsKeys.timelineGroupByMonth)

        @Composable
        fun rememberTimelineGroupByYear() =
            rememberPreference(SettingsKeys.timelineGroupByYear)

        val GROUP_SIMILAR_MEDIA = booleanPreferencesKey("group_similar_media")

        @Composable
        fun rememberGroupSimilarMedia() =
            rememberPreference(SettingsKeys.groupSimilarMedia)

        val GROUP_RAW_JPG = booleanPreferencesKey("group_raw_jpg")

        @Composable
        fun rememberGroupRawJpg() =
            rememberPreference(SettingsKeys.groupRawJpg)

        val GROUP_EDITED_COPIES = booleanPreferencesKey("group_edited_copies")

        @Composable
        fun rememberGroupEditedCopies() =
            rememberPreference(SettingsKeys.groupEditedCopies)

        val GROUP_BURST_SEQUENCES = booleanPreferencesKey("group_burst_sequences")

        @Composable
        fun rememberGroupBurstSequences() =
            rememberPreference(SettingsKeys.groupBurstSequences)

        val GROUP_CLOUD_LOCAL = booleanPreferencesKey("group_cloud_local")

        @Composable
        fun rememberGroupCloudLocal() =
            rememberPreference(SettingsKeys.groupCloudLocal)

        @Composable
        fun rememberUseSystemFont() =
            rememberPreference(SettingsKeys.useSystemFont)

        @Composable
        fun rememberAllowBlur() =
            rememberPreference(SettingsKeys.allowBlur.withDefault(SdkCompat.supportsBlur))

        @Composable
        fun rememberAllowGlass() = rememberPreference(SettingsKeys.allowGlass)

        // Default true: a black viewer background is the standard gallery UX and works
        // in both themes; the toggle exists for the light-theme users who prefer white.
        @Composable
        fun rememberDarkMediaViewer() =
            rememberPreference(SettingsKeys.darkMediaViewer)

        @Composable
        fun rememberAutoContrast() = rememberPreference(SettingsKeys.autoContrast)

        @Composable
        fun rememberDisableSmoothing() =
            rememberPreference(SettingsKeys.disableSmoothing)

        // One of LONG_PRESS_ACTION_ROTATE (default), _CUTOUT or _NONE. Falls back to the legacy
        // long_press_cutout switch until the user picks an action.
        @Composable
        fun rememberLongPressAction(): String {
            val legacyCutout by rememberPreference(SettingsKeys.longPressCutout)
            val storedAction by rememberPreference(SettingsKeys.longPressAction)
            return resolveLongPressAction(storedAction, legacyCutout)
        }

        // ==== Re-encode / save quality (format-preserving overwrite) ====
        // Mode: "auto" (JXL lossless where supported, else high quality) or "manual" (use sliders).
        private val REENCODE_QUALITY_MODE = stringPreferencesKey("reencode_quality_mode")
        const val REENCODE_MODE_AUTO = "auto"
        const val REENCODE_MODE_MANUAL = "manual"

        @Composable
        fun rememberReencodeQualityMode() =
            rememberPreference(SettingsKeys.reencodeQualityMode)

        // Shared lossy quality (1..100) for JPEG/WebP/AVIF/HEIC when re-encoding.
        private val REENCODE_LOSSY_QUALITY = intPreferencesKey("reencode_lossy_quality")

        @Composable
        fun rememberReencodeLossyQuality() =
            rememberPreference(SettingsKeys.reencodeLossyQuality)

        // JXL encoder effort (1 = fastest .. 9 = slowest/best).
        private val REENCODE_JXL_EFFORT = intPreferencesKey("reencode_jxl_effort")

        @Composable
        fun rememberReencodeJxlEffort() =
            rememberPreference(SettingsKeys.reencodeJxlEffort)

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

        @Composable
        fun rememberOldNavbar() = rememberPreference(SettingsKeys.oldNavbar)

        private val ALLOW_VIBRATIONS = booleanPreferencesKey("allow_vibrations")

        fun allowVibrations(context: Context) =
            context.activeDataStore.data.map { it[ALLOW_VIBRATIONS] ?: true }

        @Composable
        fun rememberAllowVibrations() =
            rememberPreference(SettingsKeys.allowVibrations)

        @Composable
        fun rememberAutoHideSearchBar() =
            rememberPreference(SettingsKeys.autoHideSearchBar)

        @Composable
        fun rememberAutoOpenSearchKeyboard() =
            rememberPreference(SettingsKeys.autoOpenSearchKeyboard)

        @Composable
        fun rememberAutoHideNavBar() =
            rememberPreference(SettingsKeys.autoHideNavBar)

        @Composable
        fun rememberFullBrightnessView() =
            rememberPreference(SettingsKeys.fullBrightnessView)

        private val TAP_SIDES_TO_NAVIGATE = booleanPreferencesKey("tap_sides_to_navigate")
        private val TAP_SIDES_TO_NAVIGATE_PROMPT_SHOWN =
            booleanPreferencesKey("tap_sides_to_navigate_prompt_shown")

        @Composable
        fun rememberTapSidesToNavigate() =
            rememberPreference(SettingsKeys.tapSidesToNavigate)

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

        @Composable
        fun rememberAutoHideOnVideoPlay() =
            rememberPreference(SettingsKeys.autoHideOnVideoPlay)

        val NO_CLASSIFICATION = booleanPreferencesKey("no_classification")

        @Composable
        fun rememberNoClassification() =
            rememberPreference(SettingsKeys.noClassification)

        val CATEGORIES_EXCLUDE_VIDEOS = booleanPreferencesKey("categories_exclude_videos")

        @Composable
        fun rememberCategoriesExcludeVideos() =
            rememberPreference(SettingsKeys.categoriesExcludeVideos)

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
            key: PrefKey<String>,
            field: SystemDateFormatField
        ): State<String> {
            val raw by rememberPreference(key)
            val context = LocalContext.current
            // Subscribe to configuration so locale / 24h changes trigger recomposition.
            LocalConfiguration.current
            val resolved = if (raw.isBlank()) systemDateTimePattern(context, field) else raw
            return rememberUpdatedState(resolved)
        }

        @Composable
        fun rememberRawDateHeaderFormat() =
            rememberPreference(SettingsKeys.rawDateHeaderFormat)

        @Composable
        fun rememberDateHeaderFormat() =
            rememberResolvedDateFormat(SettingsKeys.rawDateHeaderFormat, SystemDateFormatField.HEADER)

        @Composable
        fun rememberRawExtendedDateHeaderFormat() =
            rememberPreference(SettingsKeys.rawExtendedDateHeaderFormat)

        @Composable
        fun rememberExtendedDateHeaderFormat() =
            rememberResolvedDateFormat(
                SettingsKeys.rawExtendedDateHeaderFormat,
                SystemDateFormatField.EXTENDED_HEADER
            )

        @Composable
        fun rememberRawExifDateFormat() =
            rememberPreference(SettingsKeys.rawExifDateFormat)

        @Composable
        fun rememberExifDateFormat() =
            rememberResolvedDateFormat(SettingsKeys.rawExifDateFormat, SystemDateFormatField.EXIF)

        val EXTENDED_DATE_FORMAT = stringPreferencesKey("extended_date_format")

        @Composable
        fun rememberRawExtendedDateFormat() =
            rememberPreference(SettingsKeys.rawExtendedDateFormat)

        @Composable
        fun rememberExtendedDateFormat() =
            rememberResolvedDateFormat(SettingsKeys.rawExtendedDateFormat, SystemDateFormatField.EXTENDED)

        val DEFAULT_DATE_FORMAT = stringPreferencesKey("default_date_format")

        @Composable
        fun rememberRawDefaultDateFormat() =
            rememberPreference(SettingsKeys.rawDefaultDateFormat)

        @Composable
        fun rememberDefaultDateFormat() =
            rememberResolvedDateFormat(SettingsKeys.rawDefaultDateFormat, SystemDateFormatField.DEFAULT)

        val WEEKLY_DATE_FORMAT = stringPreferencesKey("weekly_date_format")

        @Composable
        fun rememberRawWeeklyDateFormat() =
            rememberPreference(SettingsKeys.rawWeeklyDateFormat)

        @Composable
        fun rememberWeeklyDateFormat() =
            rememberResolvedDateFormat(SettingsKeys.rawWeeklyDateFormat, SystemDateFormatField.WEEKLY)

        fun <T> getSetting(context: Context, key: Preferences.Key<T>, defaultValue: T) =
            context.activeDataStore.data.map { it[key] ?: defaultValue }

        @Composable
        fun rememberVideoAutoplay() =
            rememberPreference(SettingsKeys.videoAutoplay)

        /**
         * Workaround for video [android.view.SurfaceView] blacking out when the
         * system bars are toggled while playing. When enabled, the player
         * surface is re-bound on every system-bar visibility change. Defaults to on
         * for Samsung devices, where the issue is observed, and off elsewhere.
         */
        @Composable
        fun rememberVideoSurfaceRebind() =
            rememberPreference(SettingsKeys.videoSurfaceRebind.withDefault(SdkCompat.isSamsung))

        /**
         * Lets the media viewer switch the window into
         * [android.content.pm.ActivityInfo.COLOR_MODE_HDR] while a Dolby Vision / HDR10 / HLG
         * video is playing. Only takes effect on HDR-capable displays — the window
         * toggle itself is gated on `HdrCapabilities.isHdrDisplay`, so this silently no-ops on
         * SDR-only hardware.
         */
        @Composable
        fun rememberVideoHdrPlayback() =
            rememberPreference(SettingsKeys.videoHdrPlayback)

        @Composable
        fun rememberSharedElements() =
            rememberPreference(SettingsKeys.sharedElements)

        @Composable
        fun rememberShowMediaViewDateHeader() =
            rememberPreference(SettingsKeys.mediaViewDateHeader)

        const val ALIAS_REFRA = "ReFra"
        const val ALIAS_GALLERY = "Gallery"
        const val ALIAS_MONO = "Monochrome"
        const val ALIAS_GALLERY_MONO = "Gallery Mono"

        @Composable
        fun rememberAppNameAlias() =
            rememberPreference(SettingsKeys.appNameAlias)

        @Composable
        fun rememberAppLogoAlias() =
            rememberPreference(SettingsKeys.appLogoAlias)

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
            rememberPreference(SettingsKeys.setupCompletedVersion)

        /**
         * Synchronously determine whether the setup wizard should be shown at startup.
         * Reading the value directly (rather than via [rememberSetupCompletedVersion], which
         * emits its default first and the stored value asynchronously) avoids briefly showing
         * the wizard before navigating to the real start screen.
         */
        fun isSetupNeeded(context: Context): Boolean {
            val preferences = runBlocking {
                context.activeDataStore.data.first()
            }
            return isSetupNeeded(preferences)
        }

        fun isSetupNeeded(preferences: Preferences): Boolean =
            FORCE_SETUP_WIZARD || (preferences[SETUP_COMPLETED_VERSION] ?: 0) < CURRENT_SETUP_VERSION

        fun startupDestination(preferences: Preferences, permissionGranted: Boolean): String =
            if (!permissionGranted || isSetupNeeded(preferences)) {
                Routes.SETUP
            } else {
                preferences[LAST_SCREEN].takeIf {
                    it == Routes.TIMELINE ||
                        it == Routes.ALBUMS ||
                        it == Routes.LIBRARY
                } ?: Routes.TIMELINE
            }

        fun secureMode(preferences: Preferences): Boolean = preferences[SECURE_MODE] ?: false

        const val FAV_ICON_DISABLED = "disabled"
        const val FAV_ICON_BOTTOM_END = "bottom_end"
        const val FAV_ICON_BOTTOM_START = "bottom_start"
        const val FAV_ICON_TOP_END = "top_end"
        const val FAV_ICON_TOP_START = "top_start"
        @Composable
        fun rememberFavoriteIconPosition() =
            rememberPreference(SettingsKeys.favoriteIconPosition)

        @Composable
        fun rememberShowFavoriteButton() =
            rememberPreference(SettingsKeys.showFavoriteButton)

        @Composable
        fun rememberVisualSearchEnabled() =
            rememberPreference(SettingsKeys.visualSearchEnabled)

        const val VISUAL_SEARCH_PROVIDER_AUTO = ""

        @Composable
        fun rememberVisualSearchProvider() =
            rememberPreference(SettingsKeys.visualSearchProvider)

        const val VISUAL_SEARCH_POSITION_TOP = "top"
        const val VISUAL_SEARCH_POSITION_BOTTOM = "bottom"

        @Composable
        fun rememberVisualSearchPosition() =
            rememberPreference(SettingsKeys.visualSearchPosition)

        @Composable
        fun rememberVisualSearchAllowVault() =
            rememberPreference(SettingsKeys.visualSearchAllowVault)

        const val VISUAL_SEARCH_CONVERT_ASK = "ask"
        const val VISUAL_SEARCH_CONVERT_ALWAYS = "always"
        const val VISUAL_SEARCH_CONVERT_NEVER = "never"

        @Composable
        fun rememberVisualSearchConvertMode() =
            rememberPreference(SettingsKeys.visualSearchConvertMode)

        const val VISUAL_SEARCH_FORMAT_JPEG = "jpeg"
        const val VISUAL_SEARCH_FORMAT_PNG = "png"
        const val VISUAL_SEARCH_FORMAT_WEBP = "webp"

        @Composable
        fun rememberVisualSearchConvertFormat() =
            rememberPreference(SettingsKeys.visualSearchConvertFormat)

        @Composable
        fun rememberShowSearchBarFavoriteButton() =
            rememberPreference(SettingsKeys.showSearchBarFavButton)

        @Composable
        fun rememberShowFilterButton() =
            rememberPreference(SettingsKeys.showFilterButton)

        @Composable
        fun rememberFavoritesGroupByDate() =
            rememberPreference(SettingsKeys.favoritesGroupByDate)

        @Composable
        fun rememberTimelineGroupByDate() =
            rememberPreference(SettingsKeys.timelineGroupByDate)

        @Composable
        fun rememberVaultGroupByDate() =
            rememberPreference(SettingsKeys.vaultGroupByDate)

        @Composable
        fun rememberCloudArchiveGroupByDate() =
            rememberPreference(SettingsKeys.cloudArchiveGroupByDate)

        @Composable
        fun rememberLocationGroupByDate() =
            rememberPreference(SettingsKeys.locationGroupByDate)

        const val GROUP_NORMAL = "normal"
        const val GROUP_MONTHLY = "monthly"
        const val GROUP_YEARLY = "yearly"

        @Composable
        fun rememberTimelineGroupMethod() =
            rememberPreference(SettingsKeys.timelineGroupMethod)

        @Composable
        fun rememberAlbumsGroupMethod() =
            rememberPreference(SettingsKeys.albumsGroupMethod)

        @Composable
        fun rememberFavoritesGroupMethod() =
            rememberPreference(SettingsKeys.favoritesGroupMethod)

        @Composable
        fun rememberVaultGroupMethod() =
            rememberPreference(SettingsKeys.vaultGroupMethod)

        @Composable
        fun rememberCloudArchiveGroupMethod() =
            rememberPreference(SettingsKeys.cloudArchiveGroupMethod)

        @Composable
        fun rememberLocationGroupMethod() =
            rememberPreference(SettingsKeys.locationGroupMethod)

        @Composable
        fun rememberMapAppearance(): MutableState<MapAppearance> {
            val stored = rememberPreference(SettingsKeys.mapAppearance)
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

        @Composable
        fun rememberCartoBasemapKey() =
            rememberPreference(SettingsKeys.cartoBasemapKey)

        @Composable
        fun rememberAllowGifAnimation() =
            rememberPreference(SettingsKeys.allowGifAnimation)

        const val LAYOUT_GRID = "grid"
        const val LAYOUT_MOSAIC = "mosaic"

        @Composable
        fun rememberTimelineLayoutType() =
            rememberPreference(SettingsKeys.timelineLayoutType)

        const val EDITOR_BUILTIN = "builtin"
        const val EDITOR_STUDIO = "studio"

        @Composable
        fun rememberDefaultImageEditor() =
            rememberPreference(SettingsKeys.defaultImageEditor)

        @Composable
        fun rememberHeaderBannerDismissed() =
            rememberPreference(SettingsKeys.headerBannerDismissed)

        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        @Composable
        fun rememberDeveloperMode() =
            rememberPreference(SettingsKeys.developerMode)

        fun getDeveloperModeFlow(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[DEVELOPER_MODE] ?: false }

        private val DEV_LOG_MIN_LEVEL = stringPreferencesKey("dev_log_min_level")

        @Composable
        fun rememberDevLogMinLevel() =
            rememberPreference(SettingsKeys.devLogMinLevel)

        fun getDevLogMinLevelFlow(context: Context): Flow<String> =
            context.activeDataStore.data.map { it[DEV_LOG_MIN_LEVEL] ?: "DEBUG" }

        private val DEV_LOG_BREADCRUMBS = booleanPreferencesKey("dev_log_breadcrumbs")

        @Composable
        fun rememberDevLogBreadcrumbs() =
            rememberPreference(SettingsKeys.devLogBreadcrumbs)

        fun getDevLogBreadcrumbsFlow(context: Context): Flow<Boolean> =
            context.activeDataStore.data.map { it[DEV_LOG_BREADCRUMBS] ?: true }

        private val STORY_CARDS_CONFIG = stringPreferencesKey("story_cards_config")

        @Composable
        fun rememberStoryCardsConfig() =
            rememberPreference(SettingsKeys.storyCardsConfig)

        fun getStoryCardsConfig(context: Context) =
            context.activeDataStore.data.map {
                it[STORY_CARDS_CONFIG]?.let { json ->
                    runCatching { Json.decodeFromString<com.dot.gallery.domain.model.StoryCardsConfig>(json) }.getOrNull()
                } ?: com.dot.gallery.domain.model.StoryCardsConfig()
            }

        @Composable
        fun rememberStoryViewerAutoAdvance() =
            rememberPreference(SettingsKeys.storyViewerAutoAdvance)

        @Composable
        fun rememberStoryViewerDuration() =
            rememberPreference(SettingsKeys.storyViewerDurationSeconds)

    }

    object Security {
        const val METADATA_ISOLATION_SHARED = com.dot.gallery.domain.settings.METADATA_ISOLATION_SHARED
        const val METADATA_ISOLATION_HYBRID = com.dot.gallery.domain.settings.METADATA_ISOLATION_HYBRID
        const val METADATA_ISOLATION_PER_FILE = com.dot.gallery.domain.settings.METADATA_ISOLATION_PER_FILE

        private val METADATA_ISOLATION_MODE = stringPreferencesKey("metadata_isolation_mode")

        @Composable
        fun rememberMetadataIsolationMode() =
            rememberPreference(SettingsKeys.metadataIsolationMode)

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
            rememberPreference(SettingsKeys.sandboxedDecode)

        /**
         * Effective sandboxed-decode state. Forced on whenever Android Advanced
         * Protection Mode (AAPM) is enabled, regardless of the stored preference.
         */
        fun getSandboxedDecode(context: Context) =
            context.activeDataStore.data
                .map { it[SANDBOXED_DECODE] ?: false }
                .combine(AdvancedProtectionMonitor.enabled) { pref, aapm -> pref || aapm }

        @Composable
        fun rememberPrivateFolderEnabled() =
            rememberPreference(SettingsKeys.privateFolderEnabled)

        private val PRIVATE_FOLDER_URI = stringPreferencesKey("private_folder_uri")

        @Composable
        fun rememberPrivateFolderUri() =
            rememberPreference(SettingsKeys.privateFolderUri)

        fun getPrivateFolderUri(context: Context) =
            context.activeDataStore.data.map { it[PRIVATE_FOLDER_URI] ?: "" }
    }

    object Vault {
        const val ENCRYPT_ASK = "ask"
        const val ENCRYPT_DELETE = "delete"
        const val ENCRYPT_KEEP = "keep"

        @Composable
        fun rememberVaultEncryptBehavior() =
            rememberPreference(SettingsKeys.vaultEncryptBehavior)
    }
}
