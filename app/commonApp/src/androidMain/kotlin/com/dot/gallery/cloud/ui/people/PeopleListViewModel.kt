/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import android.content.Context
import androidx.lifecycle.ViewModel
import com.dot.gallery.core.Settings
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import com.dot.gallery.core.Resource
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.entity.SmartScanPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dot.gallery.core.util.or
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.local.LocalPeopleProvider
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.smart.SmartScanScheduler
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


private val FAILED_SCAN_STATUSES = setOf(SmartScanStatus.FAILED, SmartScanStatus.BLOCKED)

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

/** One-shot events the screen surfaces as toasts. */
sealed interface PeopleScanEvent {
    /** Scheduling threw — the scan never started. */
    data object ScanStartFailed : PeopleScanEvent

    /** A PERSONS run finished FAILED or BLOCKED while this screen was alive. */
    data object ScanRunFailed : PeopleScanEvent
}

data class PeopleListUiState(
    /** Named on-device people, most photos first. */
    val namedPeople: List<PersonInfo> = emptyList(),
    /** Unnamed on-device people ("New people"), most photos first. */
    val newPeople: List<PersonInfo> = emptyList(),
    /** Hidden on-device people — populated while [showHidden] is on, or when hidden
     * people are the only content so they never become unreachable. */
    val hiddenPeople: List<PersonInfo> = emptyList(),
    /** Hidden person count — tracked unconditionally for the empty-state gate. */
    val hiddenPeopleCount: Int = 0,
    /** Non-local provider sections (Immich & friends), shown below the local groups. */
    val providerSections: List<ProviderPeopleSection> = emptyList(),
    /** Local pairs worth a manual "same person?" review. */
    val mergeSuggestions: List<PersonMergeSuggestion> = emptyList(),
    /** Distinct photos containing at least one assigned face. */
    val scannedPhotoCount: Int = 0,
    val showHidden: Boolean = false,
    /** Every hidden on-device person, for the People settings sheet (independent of [showHidden]). */
    val allHiddenPeople: List<PersonInfo> = emptyList(),
    /** Source keys the user turned off (see [LOCAL_PEOPLE_SOURCE]). */
    val hiddenSources: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleListViewModel @Inject constructor(
    private val repository: CloudRepository,
    private val registry: ProviderRegistry,
    private val modelManager: ModelManager,
    private val smartScanScheduler: SmartScanScheduler,
    smartScanDao: SmartScanDao,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleListUiState())
    val uiState: StateFlow<PeopleListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PeopleScanEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<PeopleScanEvent> = _events.asSharedFlow()

    /** Terminal failures only notify for runs finished while this screen lives — an old
     * failed run in the table must not toast on every page open. */
    private val createdAt = System.currentTimeMillis()

    /** Whether an on-device face scan can be started (the active face tier is installed). */
    val localScanAvailable: Boolean
        get() = modelManager.isReady(modelManager.activeFaceModel) &&
            registry.getPeopleProviders().any {
                it.providerType == ProviderType.LOCAL_PEOPLE && it.isAvailable
            }

    /** Face models are missing but can be installed, so the empty state offers the setup preview. */
    val faceSetupAvailable: Boolean
        get() = !modelManager.isReady(modelManager.activeFaceModel) && modelManager.canInstallModels

    // Registered unconditionally under the local config id — merges and corrections are
    // pure database writes that must keep working while the face model is unavailable.
    private fun localProvider(): LocalPeopleProvider? =
        registry.getByConfigId(LOCAL_PEOPLE_CONFIG_ID) as? LocalPeopleProvider

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
        viewModelScope.launch {
            runCatching { smartScanScheduler.manual(SmartScanFeature.PERSONS.bit) }
                .onSuccess { scheduled ->
                    printInfo(
                        "ml.people",
                        "people scan scheduled (run=${scheduled.runId.take(8)} " +
                            "created=${scheduled.created} work=${scheduled.workId?.take(8)})"
                    )
                }
                .onFailure { error ->
                    printError("ml.people", "people scan scheduling failed", throwable = error)
                    _events.emit(PeopleScanEvent.ScanStartFailed)
                }
        }
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
            localProvider()?.observeHiddenPeopleCount()?.collect { count ->
                _uiState.value = _uiState.value.copy(hiddenPeopleCount = count)
            }
        }
        viewModelScope.launch {
            smartScanDao.observeLatestRun()
                .map { run -> run?.takeIf { SmartScanPlan.runRequestsFeature(it, SmartScanFeature.PERSONS) } }
                .distinctUntilChangedBy { it?.runId to it?.status }
                .collect { run ->
                    if (run != null && run.status in FAILED_SCAN_STATUSES &&
                        (run.finishedAt ?: 0L) >= createdAt
                    ) {
                        printWarn(
                            "ml.people",
                            "people scan finished ${run.status.name.lowercase()} " +
                                "(run=${run.runId.take(8)} code=${run.lastErrorCode ?: "none"})"
                        )
                        _events.emit(PeopleScanEvent.ScanRunFailed)
                    }
                }
        }
        viewModelScope.launch {
            Settings.PeoplePage.hiddenSources(context).collect { sources ->
                _uiState.value = _uiState.value.copy(hiddenSources = sources)
            }
        }
        // The settings sheet manages every hidden person, whatever the Hidden chip shows.
        viewModelScope.launch {
            _uiState
                .map { it.hiddenPeopleCount > 0 }
                .distinctUntilChanged()
                .flatMapLatest { any ->
                    if (any) localProvider()?.observeHiddenPeople() ?: flowOf(emptyList())
                    else flowOf(emptyList())
                }
                .collect { hidden -> _uiState.value = _uiState.value.copy(allHiddenPeople = hidden) }
        }
        viewModelScope.launch {
            _uiState
                .map {
                    // Load hidden people when the toggle is on — and also when they
                    // are the only content, so hiding the last visible person cannot
                    // strand them behind an empty page.
                    it.showHidden || (
                        it.namedPeople.isEmpty() && it.newPeople.isEmpty() &&
                            it.providerSections.isEmpty() && it.hiddenPeopleCount > 0
                        )
                }
                .distinctUntilChanged()
                .flatMapLatest { shouldLoad ->
                    if (shouldLoad) localProvider()?.observeHiddenPeople() ?: flowOf(emptyList())
                    else flowOf(emptyList())
                }
                .collect { hidden -> _uiState.value = _uiState.value.copy(hiddenPeople = hidden) }
        }
    }

    /** Turns a People source on or off. The last visible source cannot be turned off. */
    fun setSourceVisible(key: String, visible: Boolean) {
        val state = _uiState.value
        val visibleCount = peopleSources(state).count { it.key !in state.hiddenSources }
        if (!visible && visibleCount <= 1 && key !in state.hiddenSources) return
        viewModelScope.launch { Settings.PeoplePage.setSourceVisible(context, key, visible) }
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
                }
            }
        }
    }
}
