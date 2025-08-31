package com.dot.gallery.feature_node.presentation.search

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.presentation.util.mapMediaToItem
import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch
import com.frosch2010.fuzzywuzzy_kotlin.ToStringFunction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    @ApplicationContext
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

    val locations = mediaDistributor.metadataFlow.map { state ->
        state.metadata
            .filter { it.gpsLocationNameCity != null && it.gpsLocationNameCountry != null }
            .groupBy { "${it.gpsLocationNameCity}, ${it.gpsLocationNameCountry}" }
            .mapNotNull { (location, items) ->
                val media = allMedia.value.media.find { it.id == items.first().mediaId }
                if (media != null) media to location else null
            }
            .sortedBy { it.second }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchIndexerState = combine(
        workManager.getWorkInfosForUniqueWorkFlow("SearchIndexerUpdater")
            .map { it.lastOrNull()?.state == WorkInfo.State.RUNNING },
        workManager.getWorkInfosForUniqueWorkFlow("SearchIndexerUpdater")
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