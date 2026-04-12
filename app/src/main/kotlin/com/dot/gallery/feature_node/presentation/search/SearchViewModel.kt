package com.dot.gallery.feature_node.presentation.search

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.R
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.library.CategoryMedia
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch
import com.frosch2010.fuzzywuzzy_kotlin.ToStringFunction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Stable
data class SearchResultsState(
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val progress: Float = 0f,
    val results: MediaState<Media.UriMedia> = MediaState(isLoading = false)
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    mediaDistributor: MediaDistributor,
    workManager: WorkManager,
    private val searchHelper: SearchHelper,
    repository: MediaRepository,
    @param:ApplicationContext
    private val context: Context
) : ViewModel() {

    private val imageRecords = mediaDistributor.imageEmbeddingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private var _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _searchResultsState = MutableStateFlow(SearchResultsState())
    val searchResultsState = _searchResultsState.asStateFlow()

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
    val topLocations: StateFlow<ImmutableList<LocationMedia>> = mediaDistributor.locationsMediaFlow
        .map { it.take(10).toImmutableList() }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allMedia = mediaDistributor.timelineMediaFlow
        .mapLatest { state ->
            updateQueriedMedia(state)
            state
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MediaState()
        )

    private val metadata = mediaDistributor.metadataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MediaMetadataState()
        )

    val searchIndexerState = combine(
        workManager.getWorkInfosByTagFlow("SearchIndexerUpdater")
            .map { it.lastOrNull()?.state == WorkInfo.State.RUNNING },
        workManager.getWorkInfosByTagFlow("SearchIndexerUpdater")
            .map { it.lastOrNull()?.progress?.getFloat("progress", 0f) ?: 0f }
    ) { isRunning, progress ->
        SearchIndexerState(
            isIndexing = isRunning,
            progress = progress
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchIndexerState())

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

    fun clearQuery() {
        viewModelScope.launch {
            searchJob?.cancel()
            _query.tryEmit("")
            _searchResultsState.tryEmit(SearchResultsState())
        }
    }

    private fun updateQueriedMedia(newMediaState: MediaState<Media.UriMedia>) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = _query.value
            if (query.isEmpty()) return@launch
            val resultsState = _searchResultsState.value
            if (resultsState.hasSearched && !resultsState.isSearching) {
                // Check resultsState and update any media that has changed based on the new MediaState
                // If is deleted, remove it from results
                // If is updated, update it in results
                val updatedResults = resultsState.results.media.mapNotNull { mediaItem ->
                    newMediaState.media.find { it.id == mediaItem.id }
                }
                if (updatedResults.isNotEmpty()) {
                    _searchResultsState.tryEmit(
                        resultsState.copy(
                            results = MediaState(
                                media = updatedResults,
                                isLoading = false,
                                error = resultsState.results.error
                            )
                        )
                    )
                }
            }
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

    fun setQuery(query: String, apply: Boolean = true) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
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
                    data = results.map { it.second },
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
            val metadataMatches = metadata.value.metadata.filter { mtd ->
                mtd.toString().contains(query, ignoreCase = true)
            }
            if (metadataMatches.isNotEmpty()) {
                // If the query matches metadata, filter by that metadata
                val filteredMedia = allMedia.filter { media ->
                    metadataMatches.any { it.mediaId == media.id }
                }
                results.mergeWithHighestScore(
                    filteredMedia.map { 1f to it }
                )
                val mediaState = mapMediaToItem(
                    data = results.map { it.second },
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
                _searchResultsState.tryEmit(
                    SearchResultsState(
                        hasSearched = true,
                        isSearching = false,
                        progress = 0.5f,
                        results = mapMediaToItem(
                            data = results.map { it.second },
                            error = "",
                            albumId = -1L,
                            defaultDateFormat = dateFormats.value.first,
                            extendedDateFormat = dateFormats.value.second,
                            weeklyDateFormat = dateFormats.value.third
                        )
                    )
                )
            }
            val fuzzySearchResults = allMedia.parseFuzzySearch(query)
            results.mergeWithHighestScore(fuzzySearchResults)
            _searchResultsState.tryEmit(
                SearchResultsState(
                    hasSearched = true,
                    isSearching = false,
                    progress = 1f,
                    results = mapMediaToItem(
                        data = results.map { it.second },
                        error = "",
                        albumId = -1L,
                        defaultDateFormat = dateFormats.value.first,
                        extendedDateFormat = dateFormats.value.second,
                        weeklyDateFormat = dateFormats.value.third
                    )
                )
            )
            if (results.isEmpty()) {
                _searchResultsState.tryEmit(
                    SearchResultsState(
                        hasSearched = true,
                        isSearching = false,
                        progress = 1f,
                        results = MediaState(error = "No results found", isLoading = false)
                    )
                )
            }
        }

    }

    private fun MutableList<Pair<Float, Media.UriMedia>>.mergeWithHighestScore(newList: List<Pair<Float, Media.UriMedia>>) {
        val merged = (this + newList)
            .groupBy { it.second.id }
            .map { (_, pairs) -> pairs.maxBy { it.first } }
        clear()
        addAll(merged)
        sortedByDescending { it.first }
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