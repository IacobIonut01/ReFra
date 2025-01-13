package com.dot.gallery.feature_node.presentation.vault

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.collectMedia
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
open class VaultViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val workManager: WorkManager
) : ViewModel() {

    var currentVault = mutableStateOf<Vault?>(null)

    private val _mediaState = MutableStateFlow(MediaState<UriMedia>())
    val mediaState = _mediaState.asStateFlow()

    private val _vaultState = MutableStateFlow(VaultState())
    val vaultState = _vaultState.asStateFlow()

    private val defaultDateFormat = repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    @SuppressLint("ComposableNaming")
    @Composable
    fun attachToLifecycle() {
        LaunchedEffect(Unit) {
            fetchVaultsAndMedia()
        }
    }

    fun setVault(vault: Vault?, onFailed: (reason: String) -> Unit = {}, onSuccess: () -> Unit) {
        if (vault != currentVault.value) {
            _mediaState.value = MediaState()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val newVaultState = repository.getVaults().singleOrNull().mapToVaultState()
            _vaultState.emit(newVaultState)
        }
        viewModelScope.launch(Dispatchers.IO) {
            currentVault.value = vault
            if (vault == null) {
                fetchVaultsAndMedia()
                withContext(Dispatchers.Main.immediate) { onSuccess() }
                return@launch
            }
            val hasVault = _vaultState.value.vaults.find { it.uuid == vault.uuid } != null
            if (hasVault) {
                fetchVaultsAndMedia(vault)
                withContext(Dispatchers.Main.immediate) { onSuccess() }
            } else {
                if (_vaultState.value.vaults.firstOrNull { it.name == vault.name } != null) {
                    onFailed("Already exists")
                    return@launch
                }
                repository.createVault(
                    vault = vault,
                    onSuccess = {
                        fetchVaultsAndMedia(vault)
                        currentVault.value = vault
                        viewModelScope.launch(Dispatchers.Main.immediate) { onSuccess() }
                    },
                    onFailed = onFailed
                )
            }
        }
    }

    fun deleteVault(vault: Vault) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVault(
                vault = vault,
                onSuccess = {
                    fetchVaultsAndMedia()
                },
                onFailed = {
                    printError("Failed to delete vault: $it")
                    fetchVaultsAndMedia(vault)
                }
            )
        }
    }

    fun addMedia(vault: Vault, mediaList: List<UriMedia>) {
        workManager.scheduleEncryptingMedia(vault, mediaList)
    }

    fun addMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit, onFailed: (reason: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (repository.addMedia(vault, media)) {
                    fetchVaultsAndMedia(vault)
                    onSuccess()
                } else {
                    onFailed("Failed to add media")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                onFailed("Failed to add media")
            }
        }
    }

    fun restoreMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreMedia(vault, media)
            onSuccess()
            fetchVaultsAndMedia(vault)
        }
    }

    fun deleteMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEncryptedMedia(vault, media)
            onSuccess()
            fetchVaultsAndMedia(vault)
        }
    }

    fun deleteAllMedia(vault: Vault) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllEncryptedMedia(
                vault = vault,
                onSuccess = {
                    fetchVaultsAndMedia(vault)
                },
                onFailed = { failedFiles ->
                    printError("Failed to delete files: $failedFiles")
                    // TODO: Handle failed files
                }
            )
        }
    }

    private fun fetchVaultsAndMedia(vault: Vault? = null) {
        currentVault.value = vault
        viewModelScope.launch(Dispatchers.IO) {
            repository.getVaults().collectLatest { resource ->
                _vaultState.emit(resource.mapToVaultState())
            }
        }
        vault?.let {
            viewModelScope.launch(Dispatchers.IO) {
                repository.getEncryptedMedia(vault).collectLatest {
                    _mediaState.collectMedia(
                        data = it.data ?: emptyList(),
                        error = it.message ?: "",
                        albumId = -1,
                        groupByMonth = false,
                        withMonthHeader = true,
                        defaultDateFormat = defaultDateFormat.value,
                        extendedDateFormat = extendedDateFormat.value,
                        weeklyDateFormat = weeklyDateFormat.value
                    )
                }
            }
        }
    }

    private fun Resource<List<Vault>>?.mapToVaultState(): VaultState {
        return VaultState(
            isLoading = false,
            vaults = (this?.data) ?: emptyList()
        )
    }

    override fun onCleared() {
        _mediaState.value = MediaState()
        super.onCleared()
    }
}