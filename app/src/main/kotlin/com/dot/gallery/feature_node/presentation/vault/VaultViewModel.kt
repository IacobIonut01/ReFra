package com.dot.gallery.feature_node.presentation.vault

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.R
import com.dot.gallery.core.Resource
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import android.os.Environment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
open class VaultViewModel @Inject constructor(
    private val repository: MediaRepository,
    distributor: MediaDistributor,
    private val workManager: WorkManager,
    database: InternalDatabase,
    @param:ApplicationContext private val appContext: Context
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

    val currentVault = MutableStateFlow<Vault?>(null)

    // User-facing message flow for snackbar / toast feedback (multicast, no replay)
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val userMessage: SharedFlow<String> = _userMessage

    // Emits lists of original URIs that should be deleted (user permission required) after a
    // vault operation (encrypt/hide) succeeds with deleteOriginals=true.
    private val _pendingDeletions = MutableSharedFlow<List<Uri>>(extraBufferCapacity = 10)
    val pendingDeletions: SharedFlow<List<Uri>> = _pendingDeletions

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

    val vaultItemCounts = database.getVaultDao().getMediaCountPerVault()
        .map { counts -> counts.associate { it.uuid to it.count } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
        .debounce(300)
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
                    currentVault.value = null
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

    /** Encrypt into vault without deleting originals, and show feedback on completion. */
    fun addMediaKeepOriginals(vault: Vault, uris: List<Uri>) {
        val id = workManager.enqueueVaultOperationWithId(
            operation = VaultOperationWorker.OP_ENCRYPT,
            media = uris,
            vault = vault,
            uniqueKey = "encrypt_keep_${vault.uuid}_${System.currentTimeMillis()}",
            deleteOriginals = false
        )
        viewModelScope.launch(Dispatchers.IO) {
            val info = workManager.getWorkInfoByIdFlow(id)
                .filter { it?.state?.isFinished == true }
                .first()
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    _userMessage.emit(
                        appContext.getString(R.string.vault_items_encrypted, uris.size)
                    )
                }
                WorkInfo.State.FAILED -> {
                    _userMessage.emit(
                        appContext.getString(R.string.vault_encrypt_failed, uris.size)
                    )
                }
                else -> { /* ignore */ }
            }
        }
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
        observeDeletionOutput(id, uris.size)
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

    private fun observeDeletionOutput(id: UUID, itemCount: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = workManager.getWorkInfoByIdFlow(id)
                .filter { it?.state?.isFinished == true }
                .first()
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val leftoversJson = info.outputData.getString(VaultOperationWorker.KEY_LEFTOVER_URIS)
                    val leftovers = leftoversJson?.let {
                        Json.decodeFromString<List<String>>(it).map { s -> s.toUri() }
                    }.orEmpty()
                    if (leftovers.isNotEmpty()) {
                        _pendingDeletions.emit(leftovers)
                    }
                    _userMessage.emit(
                        appContext.getString(R.string.vault_items_encrypted, itemCount.coerceAtLeast(leftovers.size))
                    )
                }
                WorkInfo.State.FAILED -> {
                    _userMessage.emit(
                        appContext.getString(R.string.vault_encrypt_failed, itemCount)
                    )
                }
                else -> { /* CANCELLED — ignore */ }
            }
        }
    }

    fun restoreMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        val id = workManager.enqueueVaultOperationWithId(
            operation = VaultOperationWorker.OP_DECRYPT,
            media = listOf(media.uri),
            vault = vault,
            uniqueKey = "restore_${vault.uuid}_${media.id}_${System.currentTimeMillis()}"
        )
        viewModelScope.launch(Dispatchers.IO) {
            val info = workManager.getWorkInfoByIdFlow(id)
                .filter { it?.state?.isFinished == true }
                .first()
            if (info?.state == WorkInfo.State.SUCCEEDED) {
                val restoredPath = if (media.mimeType.startsWith("video"))
                    Environment.DIRECTORY_MOVIES + "/Restored"
                else
                    Environment.DIRECTORY_PICTURES + "/Restored"
                _userMessage.emit(
                    appContext.getString(R.string.vault_restored_to, 1, restoredPath)
                )
                withContext(Dispatchers.Main) { onSuccess() }
            }
        }
    }

    suspend fun transferMedia(sourceVault: Vault, targetVault: Vault, media: UriMedia, copy: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            repository.transferMedia(sourceVault, targetVault, media, copy)
        }
    }

    fun deleteMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEncryptedMedia(vault, media)
            _userMessage.emit(appContext.getString(R.string.vault_item_deleted))
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