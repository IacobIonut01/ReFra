package com.dot.gallery.feature_node.presentation.vault

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.presentation.util.RepeatOnResume
import com.dot.gallery.feature_node.presentation.util.mapMedia
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
open class VaultViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val defaultDateFormat = repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat = repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat = repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    var currentVault = mutableStateOf<Vault?>(null)

    val vaultState = repository
        .getVaults()
        .map { it.mapToVaultState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultState())

    val isRunning = workManager.getWorkInfosByTagFlow("VaultWorker")
        .map { it.lastOrNull()?.state == WorkInfo.State.RUNNING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val progress = workManager.getWorkInfosByTagFlow("VaultWorker")
        .map {
            it.lastOrNull()?.progress?.getFloat("progress", 0f) ?: 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    @SuppressLint("ComposableNaming")
    @Composable
    fun attachToLifecycle() {
        RepeatOnResume {
        }
    }

    fun createMediaState(vault: Vault?) = repository.getEncryptedMedia(vault)
        .mapMedia(
            albumId = -1,
            updateDatabase = {},
            defaultDateFormat = defaultDateFormat.value,
            extendedDateFormat = extendedDateFormat.value,
            weeklyDateFormat = weeklyDateFormat.value
        )
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaState())

    fun setVault(vault: Vault?, onFailed: (reason: String) -> Unit = {}, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            currentVault.value = vault
            if (vault == null) {
                withContext(Dispatchers.Main.immediate) { onSuccess() }
                return@launch
            }
            val hasVault = vaultState.value.vaults.find { it.uuid == vault.uuid } != null
            if (hasVault) {
                withContext(Dispatchers.Main.immediate) { onSuccess() }
            } else {
                if (vaultState.value.vaults.firstOrNull { it.name == vault.name } != null) {
                    onFailed("Already exists")
                    return@launch
                }
                repository.createVault(
                    vault = vault,
                    onSuccess = {
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
                    setVault(vault = vaultState.value.vaults.firstOrNull(), onSuccess = {})
                },
                onFailed = {
                    printError("Failed to delete vault: $it")
                }
            )
        }
    }

    fun addMedia(vault: Vault, list: List<Uri>) {
        workManager.scheduleEncryptingMedia(vault, list)
    }

    fun restoreMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreMedia(vault, media)
            onSuccess()
        }
    }

    fun deleteMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEncryptedMedia(vault, media)
            onSuccess()
        }
    }

    fun deleteAllMedia(vault: Vault) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllEncryptedMedia(
                vault = vault,
                onSuccess = {
                },
                onFailed = { failedFiles ->
                    printError("Failed to delete files: $failedFiles")
                    // TODO: Handle failed files
                }
            )
        }
    }

    fun restoreVault(vault: Vault) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreVault(vault)
        }
    }

    fun deleteLeftovers(result: ActivityResultLauncher<IntentSenderRequest>, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaList = uris.map { Media.createFromUri(null, it) }.requireNoNulls()
            repository.deleteMedia(result, mediaList)
        }
    }

    private fun Resource<List<Vault>>?.mapToVaultState(): VaultState {
        return VaultState(
            isLoading = false,
            vaults = (this?.data) ?: emptyList()
        )
    }

}