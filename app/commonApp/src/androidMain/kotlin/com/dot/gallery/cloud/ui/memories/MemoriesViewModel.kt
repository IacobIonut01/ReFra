/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.feature.timeline.storycards.storyCardStableId
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_memories_years_ago_count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dot.gallery.core.presentation.util.clear
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.cloud.core.ProviderRegistry
import org.jetbrains.compose.resources.getPluralString
import java.time.LocalDate


data class MemoriesUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

private data class CloudMemoriesState(
    val memories: List<MemoryInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class MemoriesViewModel @Inject constructor(
    private val repository: CloudRepository,
    private val registry: ProviderRegistry,
    private val serverConfigDao: CloudServerConfigDao,
    distributor: MediaDistributor,
) : ViewModel() {

    private val cloudState = MutableStateFlow(CloudMemoriesState())

    private val localMemories = distributor.timelineMediaFlow
        .filter { !it.isLoading }
        .map { state ->
            val today = LocalDate.now()
            groupLocalMemories(state.media, today).map { it.toEntry(today) }
        }
        .flowOn(Dispatchers.Default)
        .onStart<List<MemoryEntry>?> { emit(null) }

    val uiState: StateFlow<MemoriesUiState> = combine(cloudState, localMemories) { cloud, local ->
        val today = LocalDate.now()
        val memories = (cloud.memories.map { it.toEntry(today) } + local.orEmpty())
            .sortedByDescending { it.year }
        MemoriesUiState(
            memories = memories,
            isLoading = local == null || cloud.isLoading && memories.isEmpty(),
            error = cloud.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoriesUiState())

    private val memoriesByAccount = mutableMapOf<Long, List<MemoryInfo>>()
    private var loadJob: Job? = null

    init {
        loadMemories()
    }

    fun loadMemories() {
        loadJob?.cancel()
        cloudState.value = cloudState.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            val accounts = serverConfigDao.getActive().first().filter { config ->
                val provider = registry.getByConfigId(config.id)
                provider?.isAvailable == true && ProviderCapability.MEMORIES in provider.capabilities
            }
            // Without a memories server the page still has the device's own memories.
            if (accounts.isEmpty()) {
                cloudState.value = CloudMemoriesState(isLoading = false)
                return@launch
            }

            memoriesByAccount.clear()
            val pendingAccounts = accounts.mapTo(mutableSetOf()) { it.id }
            accounts.forEach { config ->
                launch {
                    repository.getMemories(config.providerType, config.id).collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                memoriesByAccount[config.id] = resource.data.orEmpty()
                                pendingAccounts.remove(config.id)
                                publishMemories(pendingAccounts.isNotEmpty(), null)
                            }
                            is Resource.Error -> {
                                pendingAccounts.remove(config.id)
                                publishMemories(pendingAccounts.isNotEmpty(), resource.message)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun publishMemories(isLoading: Boolean, error: String?) {
        cloudState.value = CloudMemoriesState(
            memories = memoriesByAccount.values.flatten().sortedByDescending { it.createdAt },
            isLoading = isLoading,
            error = error
        )
    }

    /** [entries] as story-viewer cards, in page order; memories without photos are left out. */
    suspend fun storyCards(entries: List<MemoryEntry>): List<StoryCard> {
        val currentYear = LocalDate.now().year
        return entries.filter { it.media.isNotEmpty() }.map { entry ->
            val type = if (entry.isLocal) StoryCardType.MEMORIES else StoryCardType.CLOUD_MEMORIES
            val yearsAgo = (currentYear - entry.year).coerceAtLeast(0)
            val ago = getPluralString(Res.plurals.cloud_memories_years_ago_count, yearsAgo, yearsAgo)
            StoryCard(
                id = storyCardId(entry),
                type = type,
                title = when {
                    entry.year <= 0 -> entry.typeLabel
                    entry.onThisDay -> ago
                    else -> entry.year.toString()
                },
                subtitle = when {
                    entry.year <= 0 -> null
                    entry.onThisDay -> entry.year.toString()
                    else -> ago
                },
                thumbnailMedia = entry.media.first(),
                mediaList = entry.media.take(MEMORY_HIGHLIGHTS),
                year = entry.year.takeIf { it > 0 },
            )
        }
    }

    fun storyCardId(entry: MemoryEntry): Long = storyCardStableId(
        if (entry.isLocal) StoryCardType.MEMORIES else StoryCardType.CLOUD_MEMORIES,
        "memories-page/${entry.key}",
    )
}
