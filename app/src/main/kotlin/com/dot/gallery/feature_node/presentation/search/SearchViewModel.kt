package com.dot.gallery.feature_node.presentation.search

import android.content.Context
import androidx.compose.runtime.Stable
import com.bumptech.glide.Glide
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.R
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.feature_node.data.data_source.SmartScanDao
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaGroupType
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.classifyGroupType
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.groupKey
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchIndex
import com.dot.gallery.feature_node.presentation.help.data.HelpSearchItem
import com.dot.gallery.feature_node.presentation.help.search.HelpFuzzyMatcher
import com.dot.gallery.feature_node.presentation.library.CategoryMedia
import com.dot.gallery.feature_node.presentation.location.MapGeoMediaSource
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch
import com.frosch2010.fuzzywuzzy_kotlin.ToStringFunction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val VISUAL_SEARCH_INPUT_SIZE = 224

@Stable
data class SearchResultsState(
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val isRelevanceSearch: Boolean = false,
    val progress: Float = 0f,
    val results: MediaState<Media.UriMedia> = MediaState(isLoading = false),
    val dateResults: MediaState<Media.UriMedia>? = null
)

internal fun <T, K> reconcileSearchResults(
    previousResults: List<T>,
    currentMedia: List<T>,
    identity: (T) -> K,
): List<T> {
    val currentById = currentMedia.associateBy(identity)
    return previousResults.mapNotNull { currentById[identity(it)] }
}

internal fun <T> dateOrderedSearchResults(
    results: List<T>,
    timestamp: (T) -> Long,
): List<T> = results.sortedByDescending(timestamp)

internal fun <T, K> smartSearchMediaPool(
    timelineMedia: List<T>,
    completeLocalMedia: List<T>,
    includeIgnoredAlbums: Boolean,
    identity: (T) -> K,
    isSearchableIgnoredMedia: (T) -> Boolean,
    sort: (List<T>) -> List<T>
): List<T> {
    if (!includeIgnoredAlbums) return timelineMedia
    val includedIds = timelineMedia.mapTo(hashSetOf(), identity)
    val ignoredMedia = completeLocalMedia.filter { item ->
        identity(item) !in includedIds && isSearchableIgnoredMedia(item)
    }
    return sort(timelineMedia + ignoredMedia)
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    mediaDistributor: MediaDistributor,
    mapGeoMediaSource: MapGeoMediaSource,
    smartScanDao: SmartScanDao,
    private val searchHelper: SearchHelper,
    repository: MediaRepository,
    modelManager: ModelManager,
    @param:ApplicationContext
    private val context: Context
) : ViewModel() {

    val isModelAvailable: StateFlow<Boolean> = modelManager.status(ModelGroup.SEARCH)
        .map { it == ModelStatus.READY }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = modelManager.isReady(ModelGroup.SEARCH)
        )

    private val imageRecords = mediaDistributor.imageEmbeddingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private var _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /** In-memory help/tips index backing the inline "Help & Tips" search section. */
    private val helpSearchItems: List<HelpSearchItem> by lazy { HelpSearchIndex.buildTips(context) }

    /**
     * Tips matching the current query, resolved instantly and independently of
     * the media search pipeline ([searchJob]). Cancelling the media search never
     * clears these, so both result kinds coexist over one query string.
     */
    val tipResults: StateFlow<List<HelpSearchItem>> = _query
        .map { q -> if (q.isBlank()) emptyList() else HelpFuzzyMatcher.search(helpSearchItems, q, limit = 8) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedImageMedia = MutableStateFlow<Media.UriMedia?>(null)
    val selectedImageMedia = _selectedImageMedia.asStateFlow()

    private val _searchResultsState = MutableStateFlow(SearchResultsState())

    private val dateFormats = mediaDistributor.dateFormatsFlow

    // Top categories for the search screen carousel (matching LibraryScreen style)
    val topCategories: StateFlow<ImmutableList<CategoryMedia>> = combine(
        repository.getTopCategories(8),
        mediaDistributor.timelineMediaFlow
    ) { categories, mediaState ->
        val mediaMap = mediaState.media.associateBy { it.id }
        categories.map { category ->
            CategoryMedia(
                category = category,
                thumbnailMedia = category.thumbnailMediaId?.let { mediaMap[it] }
            )
        }.toImmutableList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = persistentListOf()
    )

    // Top locations for the search screen carousel (matching LibraryScreen style)
    private val geoMedia = mapGeoMediaSource.mergedGeoMedia(
        localGeoMedia = mediaDistributor.geoMediaFlow,
        timelineMedia = mediaDistributor.timelineMediaFlow,
    )
    val topLocations: StateFlow<ImmutableList<LocationMedia>> = mapGeoMediaSource.mergedLocations(
        localLocations = mediaDistributor.locationsMediaFlow,
        geoMedia = geoMedia,
        timelineMedia = mediaDistributor.timelineMediaFlow,
    ).map { it.take(10).toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    // Top MIME types carousel – grouped by mimeType, readable labels
    val topMimeTypes: StateFlow<ImmutableList<SearchMediaItem>> = mediaDistributor.timelineMediaFlow
        .map { mediaState ->
            mediaState.media
                .groupBy { it.mimeType }
                .map { (mimeType, mediaList) ->
                    SearchMediaItem(
                        key = mimeType,
                        label = mimeType.toReadableMimeType(),
                        media = mediaList.firstOrNull(),
                        count = mediaList.size
                    )
                }
                .sortedByDescending { it.count }
                .take(10)
                .toImmutableList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    // Top camera/lens models – grouped by manufacturer + model
    val topLensModels: StateFlow<ImmutableList<SearchMediaItem>> = combine(
        mediaDistributor.metadataFlow,
        mediaDistributor.timelineMediaFlow
    ) { metadataState, mediaState ->
        val mediaMap = mediaState.media.associateBy { it.id }
        metadataState.metadata
            .asSequence()
            .filter { !it.modelName.isNullOrEmpty() }
            .groupBy { "${it.manufacturerName.orEmpty()} ${it.modelName.orEmpty()}".trim() }
            .map { (lensModel, metadataList) ->
                SearchMediaItem(
                    key = lensModel,
                    label = lensModel,
                    media = metadataList.firstNotNullOfOrNull { mediaMap[it.mediaId] },
                    count = metadataList.size
                )
            }
            .sortedByDescending { it.count }
            .take(10)
            .toImmutableList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = persistentListOf()
    )

    // Media mode carousels – night mode, panorama, photosphere, long exposure, motion photo
    private data class MediaModeSpec(
        val key: String,
        val labelResId: Int,
        val predicate: (MediaMetadata) -> Boolean
    )

    private val mediaModeSpecs = listOf(
        MediaModeSpec("night_mode", R.string.night_mode) { it.isNightMode },
        MediaModeSpec("panorama", R.string.panoramas) { it.isPanorama },
        MediaModeSpec("photosphere", R.string.photospheres) { it.isPhotosphere },
        MediaModeSpec("long_exposure", R.string.long_exposures) { it.isLongExposure },
        MediaModeSpec("motion_photo", R.string.motion_photos) { it.isMotionPhoto },
    )

    val topMediaModes: StateFlow<ImmutableList<SearchMediaItem>> = combine(
        mediaDistributor.metadataFlow,
        mediaDistributor.timelineMediaFlow
    ) { metadataState, mediaState ->
        val mediaMap = mediaState.media.associateBy { it.id }
        mediaModeSpecs.mapNotNull { spec ->
            val matching = metadataState.metadata.filter(spec.predicate)
            if (matching.isEmpty()) null
            else SearchMediaItem(
                key = spec.key,
                label = context.getString(spec.labelResId),
                media = matching.firstNotNullOfOrNull { mediaMap[it.mediaId] },
                count = matching.size
            )
        }.toImmutableList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = persistentListOf()
    )

    // Media groupings carousel – bursts, RAW+JPG pairs, edits
    private data class GroupTypeSpec(
        val type: MediaGroupType,
        val key: String,
        val labelResId: Int
    )

    private val groupTypeSpecs = listOf(
        GroupTypeSpec(MediaGroupType.BURST, "bursts", R.string.group_type_bursts),
        GroupTypeSpec(MediaGroupType.RAW_JPG, "raw_jpg", R.string.group_type_raw_jpg),
        GroupTypeSpec(MediaGroupType.EDITS, "edits", R.string.group_type_edits),
    )

    val topGroupTypes: StateFlow<ImmutableList<SearchMediaItem>> = mediaDistributor.timelineMediaFlow
        .map { mediaState ->
            val groups = mediaState.media
                .groupBy { it.groupKey }
                .values
                .filter { it.size > 1 }

            groupTypeSpecs.mapNotNull { spec ->
                val matching = groups.filter { it.classifyGroupType() == spec.type }
                if (matching.isEmpty()) null
                else {
                    val allMedia = matching.flatten()
                    SearchMediaItem(
                        key = spec.key,
                        label = context.getString(spec.labelResId),
                        media = allMedia.firstOrNull(),
                        count = allMedia.size
                    )
                }
            }.toImmutableList()
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ignoredMediaSource = Settings.SmartFeatures.includeIgnoredAlbums(context)
        .flatMapLatest { includeIgnoredAlbums ->
            if (includeIgnoredAlbums) {
                repository.getCompleteMedia().map { includeIgnoredAlbums to it.data.orEmpty() }
            } else {
                flowOf(includeIgnoredAlbums to emptyList())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allMedia = combine(
        mediaDistributor.timelineMediaFlow,
        ignoredMediaSource,
        mediaDistributor.blacklistedAlbumsFlow,
        repository.getLockedAlbums()
    ) { timelineState, (includeIgnoredAlbums, completeLocalMedia), ignoredAlbums, lockedAlbums ->
        val lockedAlbumIds = lockedAlbums.mapTo(hashSetOf()) { it.id }
        val unlockedTimelineMedia = timelineState.media.filterNot { it.albumID in lockedAlbumIds }
        timelineState.copy(
            media = smartSearchMediaPool(
                timelineMedia = unlockedTimelineMedia,
                completeLocalMedia = completeLocalMedia,
                includeIgnoredAlbums = includeIgnoredAlbums,
                identity = { it.id },
                isSearchableIgnoredMedia = { item ->
                    item.albumID !in lockedAlbumIds && ignoredAlbums.any { it.matchesMedia(item) }
                },
                sort = MediaOrder.Default::sortMedia
            )
        )
    }.mapLatest { state ->
        updateQueriedMedia(state)
        state
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MediaState()
        )

    val searchableMediaIds: StateFlow<Set<Long>> = allMedia
        .map { state -> state.media.mapTo(hashSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val searchResultsState: StateFlow<SearchResultsState> = combine(
        _searchResultsState,
        searchableMediaIds,
        dateFormats
    ) { state, searchableIds, formats ->
        val filteredMedia = state.results.media.filter { it.id in searchableIds }
        if (filteredMedia.size == state.results.media.size) state else if (state.isRelevanceSearch) {
            val filteredIds = filteredMedia.mapTo(hashSetOf()) { it.id }
            val (filteredResults, filteredDateResults) = mapRelevanceResults(
                media = filteredMedia,
                error = state.results.error,
                groupSimilarMedia = state.results.mediaGroups.isNotEmpty(),
                cloudBackups = state.results.cloudBackups.filterKeys { it in filteredIds },
                formats = formats
            )
            state.copy(
                results = filteredResults.copy(isLoading = state.results.isLoading),
                dateResults = filteredDateResults.copy(isLoading = state.results.isLoading)
            )
        } else {
            state.copy(
                results = mapMediaToItem(
                    data = filteredMedia,
                    error = state.results.error,
                    albumId = -1L,
                    groupSimilarMedia = state.results.mediaGroups.isNotEmpty(),
                    defaultDateFormat = formats.first,
                    extendedDateFormat = formats.second,
                    weeklyDateFormat = formats.third
                ).copy(isLoading = state.results.isLoading)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchResultsState())

    private val metadata = mediaDistributor.metadataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MediaMetadataState()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchIndexerState = smartScanDao.observeActiveRun()
        .flatMapLatest { run ->
            if (run == null) flowOf(SearchIndexerState())
            else smartScanDao.observePhases(run.runId).map { phases -> deriveSearchIndexerState(run, phases) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchIndexerState())

    private var searchJob: Job? = null

    fun addHistory(query: String) {
        viewModelScope.launch {
            Settings.Search.addHistory(context, query)
        }
    }

    private var removingHistoryJob: Job? = null
    fun removeHistory(query: String) {
        if (removingHistoryJob == null || removingHistoryJob?.isCompleted == true) {
            removingHistoryJob = viewModelScope.launch {
                Settings.Search.removeHistory(context, query)
            }
        }
    }

    fun addImageHistory(media: Media.UriMedia) {
        viewModelScope.launch {
            Settings.Search.addImageHistory(context, media.id, media.label, media.getUri().toString())
        }
    }

    fun removeImageHistory(mediaId: Long) {
        if (removingHistoryJob == null || removingHistoryJob?.isCompleted == true) {
            removingHistoryJob = viewModelScope.launch {
                Settings.Search.removeImageHistory(context, mediaId)
            }
        }
    }

    fun retrySearch() {
        val selectedMedia = _selectedImageMedia.value
        when {
            selectedMedia != null -> searchByImage(selectedMedia)
            _query.value.isNotEmpty() -> setQuery(_query.value)
        }
    }

    fun clearQuery() {
        viewModelScope.launch {
            searchJob?.cancel()
            _query.tryEmit("")
            _selectedImageMedia.tryEmit(null)
            _searchResultsState.tryEmit(SearchResultsState())
        }
    }

    fun setSelectedMedia(media: Media.UriMedia) {
        _selectedImageMedia.value = media
        addImageHistory(media)
        searchByImage(media)
    }

    fun restoreImageSearch(mediaId: Long) {
        val media = allMedia.value.media.find { it.id == mediaId } ?: return
        _selectedImageMedia.value = media
        searchByImage(media)
    }

    fun clearSelectedMedia() {
        _selectedImageMedia.value = null
        _searchResultsState.tryEmit(SearchResultsState())
    }

    fun searchByImage(media: Media.UriMedia) {
        searchJob?.cancel()
        _query.value = ""
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = true,
                    progress = 0f,
                    results = MediaState(isLoading = true)
                )
            )
            try {
                val uri = media.getUri()
                val bitmap = Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(uri)
                    .centerCrop()
                    .override(VISUAL_SEARCH_INPUT_SIZE, VISUAL_SEARCH_INPUT_SIZE)
                    .submit()
                    .get()

                if (!searchHelper.isAvailable) {
                    _searchResultsState.tryEmit(
                        SearchResultsState(
                            hasSearched = true,
                            isSearching = false,
                            progress = 1f,
                            results = MediaState(error = context.getString(R.string.ai_models_not_installed), isLoading = false)
                        )
                    )
                    return@launch
                }
                searchHelper.setupVisionSession().use { session ->
                    val imageEmbedding = searchHelper.getImageEmbedding(session, bitmap)
                    val searchResultsPair = searchHelper.sortByCosineDistance(
                        searchEmbedding = imageEmbedding,
                        imageEmbeddingsList = imageRecords.value.map { it.embedding },
                        imageIdxList = imageRecords.value.map { it.id }
                    )
                    val allMediaList = allMedia.value.media
                    val results = searchResultsPair.mapNotNull { (id, score) ->
                        if (id == media.id) return@mapNotNull null
                        val m = allMediaList.find { it.id == id }
                        if (m != null) score to m else null
                    }
                    val (mediaState, dateMediaState) = mapRelevanceResults(
                        currentlySearchable(results.map { it.second })
                    )
                    _searchResultsState.tryEmit(
                        SearchResultsState(
                            hasSearched = true,
                            isSearching = false,
                            isRelevanceSearch = true,
                            progress = 1f,
                            results = mediaState,
                            dateResults = dateMediaState
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _searchResultsState.tryEmit(
                    SearchResultsState(
                        hasSearched = true,
                        isSearching = false,
                        progress = 1f,
                        results = MediaState(
                            error = context.getString(R.string.visual_search_failed),
                            isLoading = false,
                        )
                    )
                )
            }
        }
    }

    private fun currentlySearchable(media: List<Media.UriMedia>): List<Media.UriMedia> {
        val currentMedia = allMedia.value.media.associateBy { it.id }
        return media.mapNotNull { currentMedia[it.id] }
    }

    private suspend fun mapRelevanceResults(
        media: List<Media.UriMedia>,
        error: String = "",
        groupSimilarMedia: Boolean = false,
        cloudBackups: Map<Long, List<Media.UriMedia>> = emptyMap(),
        formats: Triple<String, String, String> = dateFormats.value,
    ): Pair<MediaState<Media.UriMedia>, MediaState<Media.UriMedia>> {
        suspend fun map(data: List<Media.UriMedia>) = mapMediaToItem(
            data = data,
            error = error,
            albumId = -1L,
            groupSimilarMedia = groupSimilarMedia,
            cloudBackups = cloudBackups,
            defaultDateFormat = formats.first,
            extendedDateFormat = formats.second,
            weeklyDateFormat = formats.third
        )
        return map(media) to map(dateOrderedSearchResults(media, Media.UriMedia::definedTimestamp))
    }

    private suspend fun updateQueriedMedia(newMediaState: MediaState<Media.UriMedia>) {
        val resultsState = _searchResultsState.value
        if (newMediaState.error.isNotEmpty()) {
            if (resultsState.hasSearched) {
                _searchResultsState.emit(
                    resultsState.copy(
                        isSearching = false,
                        results = resultsState.results.copy(
                            error = newMediaState.error,
                            isLoading = false,
                        )
                    )
                )
            }
            return
        }

        val selectedMedia = _selectedImageMedia.value
        if (selectedMedia != null && newMediaState.media.none { it.id == selectedMedia.id }) {
            searchJob?.cancel()
            _selectedImageMedia.value = null
            _searchResultsState.emit(SearchResultsState())
            return
        }
        if (_query.value.isEmpty() && selectedMedia == null) return
        if (!resultsState.hasSearched || resultsState.isSearching) return

        val updatedResults = reconcileSearchResults(
            previousResults = resultsState.results.media,
            currentMedia = newMediaState.media,
            identity = { it.id },
        )
        val updatedIds = updatedResults.mapTo(hashSetOf()) { it.id }
        val formats = dateFormats.value
        val cloudBackups = resultsState.results.cloudBackups.filterKeys { it in updatedIds }
        if (resultsState.isRelevanceSearch) {
            val (rebuiltResults, rebuiltDateResults) = mapRelevanceResults(
                media = updatedResults,
                error = resultsState.results.error,
                groupSimilarMedia = resultsState.results.mediaGroups.isNotEmpty(),
                cloudBackups = cloudBackups,
                formats = formats
            )
            _searchResultsState.emit(
                resultsState.copy(
                    results = rebuiltResults.copy(isLoading = false),
                    dateResults = rebuiltDateResults.copy(isLoading = false)
                )
            )
        } else {
            val rebuiltResults = mapMediaToItem(
                data = updatedResults,
                error = resultsState.results.error,
                albumId = -1L,
                groupSimilarMedia = resultsState.results.mediaGroups.isNotEmpty(),
                cloudBackups = cloudBackups,
                defaultDateFormat = formats.first,
                extendedDateFormat = formats.second,
                weeklyDateFormat = formats.third,
            ).copy(isLoading = false)
            _searchResultsState.emit(resultsState.copy(results = rebuiltResults))
        }
    }

    fun setMimeTypeQuery(mimeType: String, hideExplicitQuery: Boolean = false) {
        if (hideExplicitQuery) {
            _query.value = if (mimeType.startsWith("image")) "Images" else "Videos"
        } else {
            _query.value = mimeType
        }
        val searchQuery = if (mimeType.contains("/*")) {
            mimeType.substringBefore("/*")
        } else {
            mimeType
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val allMedia = allMedia.value.media
            val filteredMedia = allMedia.filter { it.mimeType.startsWith(searchQuery) }
            val mediaState = mapMediaToItem(
                data = filteredMedia,
                error = "",
                albumId = -1L,
                defaultDateFormat = dateFormats.value.first,
                extendedDateFormat = dateFormats.value.second,
                weeklyDateFormat = dateFormats.value.third
            )
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    progress = 1f,
                    results = mediaState
                )
            )
        }
    }

    /**
     * Search by metadata flag (night mode, panorama, photosphere, long exposure, motion photo).
     * @param modeKey one of "night_mode", "panorama", "photosphere", "long_exposure", "motion_photo"
     */
    fun setMediaModeQuery(modeKey: String) {
        val spec = mediaModeSpecs.find { it.key == modeKey } ?: return
        _query.value = context.getString(spec.labelResId)
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val allMediaMap = allMedia.value.media.associateBy { it.id }
            val matchingIds = metadata.value.metadata
                .filter(spec.predicate)
                .map { it.mediaId }
                .toSet()
            val filteredMedia = matchingIds.mapNotNull { allMediaMap[it] }
            val mediaState = mapMediaToItem(
                data = filteredMedia,
                error = "",
                albumId = -1L,
                defaultDateFormat = dateFormats.value.first,
                extendedDateFormat = dateFormats.value.second,
                weeklyDateFormat = dateFormats.value.third
            )
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    progress = 1f,
                    results = mediaState
                )
            )
        }
    }

    /**
     * Search by camera/lens model name.
     * Filters metadata by manufacturer + model match and returns associated media.
     */
    fun setLensModelQuery(lensModel: String) {
        _query.value = lensModel
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val allMediaMap = allMedia.value.media.associateBy { it.id }
            val matchingIds = metadata.value.metadata
                .filter {
                    val full = "${it.manufacturerName.orEmpty()} ${it.modelName.orEmpty()}".trim()
                    full.equals(lensModel, ignoreCase = true)
                }
                .map { it.mediaId }
                .toSet()
            val filteredMedia = matchingIds.mapNotNull { allMediaMap[it] }
            val mediaState = mapMediaToItem(
                data = filteredMedia,
                error = "",
                albumId = -1L,
                defaultDateFormat = dateFormats.value.first,
                extendedDateFormat = dateFormats.value.second,
                weeklyDateFormat = dateFormats.value.third
            )
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    progress = 1f,
                    results = mediaState
                )
            )
        }
    }

    /**
     * Search by media group type (bursts, RAW+JPG pairs, edits).
     * Groups all media by groupKey, classifies each group, and returns
     * all media items that belong to groups of the specified type.
     */
    fun setGroupTypeQuery(groupTypeKey: String) {
        val spec = groupTypeSpecs.find { it.key == groupTypeKey } ?: return
        _query.value = context.getString(spec.labelResId)
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val groups = allMedia.value.media
                .groupBy { it.groupKey }
                .values
                .filter { it.size > 1 }
            val filteredMedia = groups
                .filter { it.classifyGroupType() == spec.type }
                .flatten()
            val mediaState = mapMediaToItem(
                data = filteredMedia,
                error = "",
                albumId = -1L,
                groupSimilarMedia = true,
                defaultDateFormat = dateFormats.value.first,
                extendedDateFormat = dateFormats.value.second,
                weeklyDateFormat = dateFormats.value.third
            )
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    progress = 1f,
                    results = mediaState
                )
            )
        }
    }

    fun setQuery(query: String, apply: Boolean = true) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            _query.tryEmit(query)
            if (query.isEmpty() || !apply) {
                _searchResultsState.tryEmit(SearchResultsState())
                return@launch
            }
            val results = mutableListOf<Pair<Float, Media.UriMedia>>()
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = true,
                    progress = 0f,
                    results = MediaState(isLoading = true)
                )
            )
            val allMedia = allMedia.value.media

            if (query.matches(Regex("^[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&-^_.+*]*$"))) {
                setMimeTypeQuery(query)
                return@launch
            }

            if (allMedia.find { it.albumLabel == query } != null) {
                // If the query matches an album label, filter by that album
                val filteredMedia = allMedia.filter { it.albumLabel == query }
                results.mergeWithHighestScore(
                    filteredMedia.map { 1f to it }
                )
                val mediaState = mapMediaToItem(
                    data = currentlySearchable(results.map { it.second }),
                    error = "",
                    albumId = -1L,
                    defaultDateFormat = dateFormats.value.first,
                    extendedDateFormat = dateFormats.value.second,
                    weeklyDateFormat = dateFormats.value.third
                )
                _searchResultsState.tryEmit(
                    SearchResultsState(
                        hasSearched = true,
                        isSearching = false,
                        progress = 1f,
                        results = mediaState
                    )
                )
                return@launch
            }
            val metadataMatchIds = metadata.value.metadata
                .filter { it.searchableText.contains(query, ignoreCase = true) }
                .mapTo(HashSet()) { it.mediaId }
            if (metadataMatchIds.isNotEmpty()) {
                val filteredMedia = allMedia.filter { it.id in metadataMatchIds }
                results.mergeWithHighestScore(
                    filteredMedia.map { 1f to it }
                )
            }
            if (searchHelper.isAvailable) {
                searchHelper.setupTextSession().use { session ->
                    val textEmbedding = searchHelper.getTextEmbedding(session, query)
                    val searchResultsPair = searchHelper.sortByCosineDistance(
                        searchEmbedding = textEmbedding,
                        imageEmbeddingsList = imageRecords.value.map { it.embedding },
                        imageIdxList = imageRecords.value.map { it.id }
                    )
                    val searchResultsMedia = searchResultsPair.mapNotNull { (id, score) ->
                        val media = allMedia.find { it.id == id }
                        if (media != null) score to media else null
                    }

                    results.mergeWithHighestScore(searchResultsMedia)
                    val (mediaState, dateMediaState) = mapRelevanceResults(
                        currentlySearchable(results.map { it.second })
                    )
                    _searchResultsState.tryEmit(
                        SearchResultsState(
                            hasSearched = true,
                            isSearching = false,
                            isRelevanceSearch = true,
                            progress = 0.5f,
                            results = mediaState,
                            dateResults = dateMediaState
                        )
                    )
                }
            }
            val fuzzySearchResults = allMedia.parseFuzzySearch(query)
            results.mergeWithHighestScore(fuzzySearchResults)
            val (mediaState, dateMediaState) = mapRelevanceResults(
                currentlySearchable(results.map { it.second })
            )
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    isRelevanceSearch = true,
                    progress = 1f,
                    results = mediaState,
                    dateResults = dateMediaState
                )
            )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _searchResultsState.tryEmit(
                    SearchResultsState(
                        hasSearched = true,
                        isSearching = false,
                        progress = 1f,
                        results = MediaState(
                            error = context.getString(R.string.search_failed),
                            isLoading = false,
                        )
                    )
                )
            }
        }

    }

    private fun MutableList<Pair<Float, Media.UriMedia>>.mergeWithHighestScore(newList: List<Pair<Float, Media.UriMedia>>) {
        val merged = (this + newList)
            .groupBy { it.second.id }
            .map { (_, pairs) -> pairs.maxBy { it.first } }
            .sortedByDescending { it.first }
        clear()
        addAll(merged)
    }

    private suspend fun <T> List<T>.parseFuzzySearch(query: String): List<Pair<Float, T>> {
        return withContext(Dispatchers.IO) {
            if (query.isEmpty())
                return@withContext emptyList()

            val matches = FuzzySearch.extractSorted(
                query = query,
                choices = this@parseFuzzySearch,
                toStringFunction = object : ToStringFunction<T> {
                    override fun apply(item: T): String {
                        return item.toString()
                    }
                },
                cutoff = 60
            )
            return@withContext matches.map { (it.score.toFloat() / 100f) to it.referent }
                .ifEmpty { emptyList() }
        }
    }


}