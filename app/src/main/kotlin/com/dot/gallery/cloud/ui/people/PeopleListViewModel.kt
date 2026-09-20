/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.data.data_source.SmartScanDao
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A labelled group of people belonging to one non-local provider account. */
data class ProviderPeopleSection(
    val key: String,
    val title: String,
    val people: List<PersonInfo>
)

/** Live status of the on-device people scan, or idle when no PERSONS run is active. */
data class PeopleScanUiState(
    val running: Boolean = false,
    val phase: SmartScanPhase? = null,
    val processed: Int = 0,
    val total: Int = 0,
    val runId: String? = null
)

data class PeopleListUiState(
    /** Named on-device people, most photos first. */
    val namedPeople: List<PersonInfo> = emptyList(),
    /** Unnamed on-device people ("New people"), most photos first. */
    val newPeople: List<PersonInfo> = emptyList(),
    /** Hidden on-device people — only populated while [showHidden] is on. */
    val hiddenPeople: List<PersonInfo> = emptyList(),
    /** Non-local provider sections (Immich & friends), shown below the local groups. */
    val providerSections: List<ProviderPeopleSection> = emptyList(),
    /** Local pairs worth a manual "same person?" review. */
    val mergeSuggestions: List<PersonMergeSuggestion> = emptyList(),
    /** Distinct photos containing at least one assigned face. */
    val scannedPhotoCount: Int = 0,
    val showHidden: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleListViewModel @Inject constructor(
    private val repository: CloudRepository,
    private val registry: ProviderRegistry,
    private val modelManager: ModelManager,
    private val smartScanScheduler: SmartScanScheduler,
    smartScanDao: SmartScanDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleListUiState())
    val uiState: StateFlow<PeopleListUiState> = _uiState.asStateFlow()

    /** Whether an on-device face scan can be started (face detector model installed). */
    val localScanAvailable: Boolean
        get() = modelManager.isReady(ModelGroup.FACE_DETECT) &&
            modelManager.isReady(ModelGroup.FACE_RECOGNITION) &&
            registry.getPeopleProviders().any {
                it.providerType == ProviderType.LOCAL_PEOPLE && it.isAvailable
            }

    private fun localProvider(): LocalPeopleProvider? =
        registry.getPeopleProviders()
            .firstOrNull { it.providerType == ProviderType.LOCAL_PEOPLE } as? LocalPeopleProvider

    private val activeRun = smartScanDao.observeActiveRun()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scanState: StateFlow<PeopleScanUiState> = activeRun
        .flatMapLatest { run ->
            if (run == null ||
                !SmartScanPlan.runRequestsFeature(run, SmartScanFeature.PERSONS) ||
                !SmartScanPlan.shouldShowRun(run.userVisible, run.totalMedia)
            ) {
                flowOf(PeopleScanUiState())
            } else {
                smartScanDao.observePhases(run.runId).map { phases ->
                    val current = phases.firstOrNull { it.phase == run.currentPhase }
                    PeopleScanUiState(
                        running = true,
                        phase = run.currentPhase,
                        processed = current?.processedMedia ?: run.processedMedia,
                        total = current?.totalMedia ?: run.totalMedia,
                        runId = run.runId
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleScanUiState())

    fun scanForPeople() {
        viewModelScope.launch { smartScanScheduler.manual(SmartScanFeature.PERSONS.bit) }
    }

    /** Re-run only the grouping phase over stored embeddings — no media re-scan. */
    fun regroupFaces() {
        viewModelScope.launch { smartScanScheduler.regroupFaces() }
    }

    fun cancelScan() {
        val runId = scanState.value.runId ?: return
        viewModelScope.launch { smartScanScheduler.cancel(runId) }
    }

    /** Inline "Add name" on an unnamed local person — no navigation required. */
    fun renamePerson(person: PersonInfo, name: String) {
        viewModelScope.launch {
            repository.updatePersonName(
                type = person.providerType,
                configId = person.serverConfigId,
                personId = person.id,
                name = name
            )
        }
    }

    fun hidePerson(person: PersonInfo) {
        viewModelScope.launch { localProvider()?.setHidden(person.id, true) }
    }

    fun unhidePerson(person: PersonInfo) {
        viewModelScope.launch { localProvider()?.setHidden(person.id, false) }
    }

    /** "Same person" in the merge review — the better-curated side keeps the identity. */
    fun confirmMerge(suggestion: PersonMergeSuggestion) {
        val (target, source) = orderedMergePair(suggestion)
        viewModelScope.launch { localProvider()?.mergePeople(source.id, target.id) }
    }

    /** "Different people" — records cannot-link assertions so the pair stops resurfacing. */
    fun rejectMerge(suggestion: PersonMergeSuggestion) {
        viewModelScope.launch {
            localProvider()?.rejectPersonPair(suggestion.first.id, suggestion.second.id)
        }
    }

    private fun orderedMergePair(suggestion: PersonMergeSuggestion): Pair<PersonInfo, PersonInfo> {
        val a = suggestion.first
        val b = suggestion.second
        val aNamed = a.name.isNotBlank()
        val bNamed = b.name.isNotBlank()
        return when {
            aNamed && !bNamed -> a to b
            bNamed && !aNamed -> b to a
            a.assetCount >= b.assetCount -> a to b
            else -> b to a
        }
    }

    init {
        loadPeople()
        viewModelScope.launch {
            repository.peopleInvalidation.collect {
                loadPeople()
            }
        }
        viewModelScope.launch {
            localProvider()?.observeIndexedPhotoCount()?.collect { count ->
                _uiState.value = _uiState.value.copy(scannedPhotoCount = count)
            }
        }
        viewModelScope.launch {
            localProvider()?.observeMergeSuggestions()?.collect { suggestions ->
                _uiState.value = _uiState.value.copy(mergeSuggestions = suggestions)
            }
        }
        viewModelScope.launch {
            _uiState
                .map { it.showHidden }
                .flatMapLatest { show ->
                    if (show) localProvider()?.observeHiddenPeople() ?: flowOf(emptyList())
                    else flowOf(emptyList())
                }
                .collect { hidden -> _uiState.value = _uiState.value.copy(hiddenPeople = hidden) }
        }
    }

    fun setShowHidden(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHidden = show)
    }

    fun loadPeople() {
        viewModelScope.launch {
            repository.getAllPeople().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val all = resource.data ?: emptyList()
                        val local = all.filter { it.providerType == ProviderType.LOCAL_PEOPLE }
                        val others = all - local.toSet()
                        _uiState.value = _uiState.value.copy(
                            namedPeople = local.filter { it.name.isNotBlank() },
                            newPeople = local.filter { it.name.isBlank() },
                            providerSections = others
                                .groupBy { it.providerType to it.serverConfigId }
                                .map { (account, people) ->
                                    ProviderPeopleSection(
                                        key = "${account.first.name}_${account.second}",
                                        title = account.first.displayName,
                                        people = people
                                    )
                                },
                            isLoading = false,
                            error = null
                        )
                    }
                    is Resource.Error -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = resource.message
                    )
                    else -> Unit
                }
            }
        }
    }
}
