package com.dot.gallery.feature_node.presentation.vault

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.core.workers.VaultOperationWorker
import com.dot.gallery.core.workers.enqueueVaultOperation
import com.dot.gallery.core.workers.enqueueVaultOperationWithId
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Media.UriMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.repository.MediaRepository
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
    distributor: MediaDistributor,
    private val workManager: WorkManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

    private val defaultDateFormat =
        repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat =
        repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, Constants.EXTENDED_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat =
        repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, Constants.WEEKLY_DATE_FORMAT)
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.WEEKLY_DATE_FORMAT)

    var currentVault = mutableStateOf<Vault?>(null)

    // Emits lists of original URIs that should be deleted (user permission required) after a
    // vault operation (encrypt/hide) succeeds with deleteOriginals=true.
    private val _pendingDeletions = kotlinx.coroutines.flow.MutableSharedFlow<List<Uri>>(extraBufferCapacity = 1)
    val pendingDeletions: kotlinx.coroutines.flow.SharedFlow<List<Uri>> = _pendingDeletions

    val vaultState = distributor.vaultsMediaFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultState())

    val isRunning = workManager.getWorkInfosByTagFlow("VaultOp")
        .map { it.lastOrNull()?.state == WorkInfo.State.RUNNING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val progress = workManager.getWorkInfosByTagFlow("VaultOp")
        .map {
            it.lastOrNull()?.progress?.getFloat(VaultOperationWorker.KEY_PROGRESS, 0f) ?: 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    val metadataState = distributor.metadataFlow.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        MediaMetadataState()
    )
    val albumsState = distributor.albumsFlow.stateIn(
        viewModelScope,
        started = SharingStarted.Eagerly,
        AlbumState()
    )

    fun createMediaState(vault: Vault?) = repository.getEncryptedMedia(vault)
        .mapMedia(
            albumId = -1,
            updateDatabase = {},
            defaultDateFormat = defaultDateFormat.value,
            extendedDateFormat = extendedDateFormat.value,
            weeklyDateFormat = weeklyDateFormat.value
        )
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaState())

    fun setVault(vault: Vault?, transferable: Boolean = false, onFailed: (reason: String) -> Unit = {}, onSuccess: () -> Unit) {
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
                    transferable = transferable,
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
        workManager.enqueueVaultOperation(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = list,
            vault = vault,
            uniqueKey = vault.uuid.toString()
        )
    }

    /** Start an encrypt operation that will request original deletion on success. */
    fun encryptAndRequestDeletion(vault: Vault, uris: List<Uri>) {
        val id = workManager.enqueueVaultOperationWithId(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = uris,
            vault = vault,
            uniqueKey = "encrypt_${vault.uuid}_${System.currentTimeMillis()}",
            deleteOriginals = true
        )
        observeDeletionOutput(id)
    }

    /** Start a hide operation (single media) which after success requests deletion of original. */
    fun hideAndRequestDeletion(vault: Vault, uri: Uri) {
        val id = workManager.enqueueVaultOperationWithId(
            operation = VaultOperationWorker.OP_HIDE,
            media = listOf(uri),
            vault = vault,
            uniqueKey = "hide_${vault.uuid}_${System.currentTimeMillis()}",
            deleteOriginals = true
        )
        observeDeletionOutput(id)
    }

    private fun observeDeletionOutput(id: java.util.UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            // getWorkInfoByIdFlow is provided by WorkManager KTX
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                if (info?.state == WorkInfo.State.SUCCEEDED) {
                    val leftoversJson = info.outputData.getString(VaultOperationWorker.KEY_LEFTOVER_URIS)
                    val leftovers = leftoversJson?.let {
                        kotlinx.serialization.json.Json.decodeFromString<List<String>>(it).map { s -> s.toUri() }
                    }.orEmpty()
                    if (leftovers.isNotEmpty()) {
                        _pendingDeletions.emit(leftovers)
                    }
                }
            }
        }
    }

    fun restoreMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        // Use worker for consistency (single item list)
        workManager.enqueueVaultOperation(
            operation = VaultOperationWorker.OP_DECRYPT,
            media = listOf(media.uri),
            vault = vault,
            uniqueKey = "restore_${vault.uuid}_${media.id}"
        )
        onSuccess()
    }

    fun deleteMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        // Hide/delete encrypted media directly (could also be a worker if long running)
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEncryptedMedia(vault, media)
            withContext(Dispatchers.Main) { onSuccess() }
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
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val mediaList = uris.mapNotNull { Media.createFromUri(appContext, it) }
            if (mediaList.isNotEmpty()) {
                repository.deleteMedia(result, mediaList)
            }
        }
    }

    fun importPortableVault(vault: Vault, base64Key: String, force: Boolean = false, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.importPortableVault(vault, base64Key, force)
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    fun migrateVaultToPortable(vault: Vault, onProgress: (Int, Int) -> Unit = { _, _ -> }, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.migrateVaultToPortable(vault, onProgress)
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    private fun Resource<List<Vault>>?.mapToVaultState(): VaultState {
        return VaultState(
            isLoading = false,
            vaults = (this?.data) ?: emptyList()
        )
    }

}