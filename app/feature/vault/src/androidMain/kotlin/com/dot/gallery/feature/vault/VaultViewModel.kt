package com.dot.gallery.feature.vault

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.core.Constants
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.Resource
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.core.Settings
import com.dot.gallery.core.decryption.VaultDecryptStore
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.data.repository.MediaRepository
import com.dot.gallery.domain.date.SystemDateFormatField
import com.dot.gallery.core.presentation.util.mapMedia
import com.dot.gallery.core.presentation.util.resolvedDateFormat
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
import android.os.Environment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import javax.inject.Inject
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.vault.VaultOp
import com.dot.gallery.core.vault.VaultOpOutcome
import com.dot.gallery.core.vault.VaultRuntime
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.vault_encrypt_failed
import com.dot.gallery.feature.vault.generated.resources.vault_item_deleted
import com.dot.gallery.feature.vault.generated.resources.vault_items_encrypted
import com.dot.gallery.feature.vault.generated.resources.vault_restored_to
import org.jetbrains.compose.resources.getString
import com.dot.gallery.domain.model.createFromUri



@OptIn(FlowPreview::class)
open class VaultViewModel @Inject constructor(
    private val repository: MediaRepository,
    distributor: MediaDistributor,
    private val vaultRuntime: VaultRuntime,
    database: InternalDatabase,
    private val appContext: Context
) : ViewModel() {

    private val defaultDateFormat =
        repository.getSetting(Settings.Misc.DEFAULT_DATE_FORMAT, "")
            .map { resolvedDateFormat(appContext, it, SystemDateFormatField.DEFAULT) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.DEFAULT_DATE_FORMAT)

    private val extendedDateFormat =
        repository.getSetting(Settings.Misc.EXTENDED_DATE_FORMAT, "")
            .map { resolvedDateFormat(appContext, it, SystemDateFormatField.EXTENDED) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.EXTENDED_DATE_FORMAT)

    private val weeklyDateFormat =
        repository.getSetting(Settings.Misc.WEEKLY_DATE_FORMAT, "")
            .map { resolvedDateFormat(appContext, it, SystemDateFormatField.WEEKLY) }
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

    val isRunning = vaultRuntime.vaultOpRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val progress = vaultRuntime.vaultOpProgress
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

    override fun onCleared() {
        // Free decrypted vault temps when leaving the vault — the periodic worker
        // covers stragglers, but there's no reason to keep plaintext alive while the user
        // browses the plain library.
        Thread { VaultDecryptStore.sweep(appContext) }.start()
        super.onCleared()
    }

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
            var deleted = false
            repository.deleteVault(
                vault = vault,
                onSuccess = {
                    deleted = true
                    currentVault.value = null
                },
                onFailed = {
                    printError("Failed to delete vault: $it")
                }
            )
            // Deleting a vault must also remove its credential and lockout state; otherwise a
            // later restore of a backup with the same UUID silently inherits the old password.
            if (deleted) {
                runCatching {
                    com.dot.gallery.feature.vault.utils.VaultPasswordManager
                        .clearVaultSecrets(appContext, vault.uuid)
                }.onFailure {
                    printError("Failed to clear vault credentials: ${it.message}")
                }
            }
        }
    }

    fun addMedia(vault: Vault, list: List<Uri>) {
        viewModelScope.launch {
            vaultRuntime.enqueueVaultOp(
                operation = VaultOp.ENCRYPT,
                media = list,
                vault = vault,
                uniqueKey = vault.uuid.toString()
            )
        }
    }

    /** Encrypt into vault without deleting originals, and show feedback on completion. */
    fun addMediaKeepOriginals(vault: Vault, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = vaultRuntime.runVaultOp(
                operation = VaultOp.ENCRYPT,
                media = uris,
                vault = vault,
                uniqueKey = "encrypt_keep_${vault.uuid}_${System.currentTimeMillis()}",
                deleteOriginals = false
            )
            when (outcome) {
                is VaultOpOutcome.Succeeded -> {
                    val succeeded = outcome.succeededCount ?: uris.size
                    _userMessage.emit(
                        getString(
                            if (succeeded > 0) Res.string.vault_items_encrypted else Res.string.vault_encrypt_failed,
                            if (succeeded > 0) succeeded else uris.size
                        )
                    )
                }
                VaultOpOutcome.Failed -> {
                    _userMessage.emit(getString(Res.string.vault_encrypt_failed, uris.size))
                }
                else -> { /* ignore */ }
            }
        }
    }

    /** Start an encrypt operation that will request original deletion on success. */
    fun encryptAndRequestDeletion(vault: Vault, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            observeDeletionOutput(
                vaultRuntime.runVaultOp(
                    operation = VaultOp.ENCRYPT,
                    media = uris,
                    vault = vault,
                    uniqueKey = "encrypt_${vault.uuid}_${System.currentTimeMillis()}",
                    deleteOriginals = true
                ),
                uris.size
            )
        }
    }

    /** Start a hide operation (single media) which after success requests deletion of original. */
    fun hideAndRequestDeletion(vault: Vault, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            observeDeletionOutput(
                vaultRuntime.runVaultOp(
                    operation = VaultOp.HIDE,
                    media = listOf(uri),
                    vault = vault,
                    uniqueKey = "hide_${vault.uuid}_${System.currentTimeMillis()}",
                    deleteOriginals = true
                )
            )
        }
    }

    private suspend fun observeDeletionOutput(outcome: VaultOpOutcome, itemCount: Int = 0) {
        when (outcome) {
            is VaultOpOutcome.Succeeded -> {
                if (outcome.leftoverUris.isNotEmpty()) {
                    _pendingDeletions.emit(outcome.leftoverUris)
                }
                // Report the count that actually landed in the vault — the worker
                // records per-item results; fall back to the requested count for older data.
                val succeeded = outcome.succeededCount
                    ?: itemCount.coerceAtLeast(outcome.leftoverUris.size)
                _userMessage.emit(
                    getString(
                        if (succeeded > 0) Res.string.vault_items_encrypted else Res.string.vault_encrypt_failed,
                        if (succeeded > 0) succeeded else itemCount
                    )
                )
            }
            VaultOpOutcome.Failed -> {
                _userMessage.emit(getString(Res.string.vault_encrypt_failed, itemCount))
            }
            else -> { /* CANCELLED — ignore */ }
        }
    }

    fun restoreMedia(vault: Vault, media: UriMedia, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = vaultRuntime.runVaultOp(
                operation = VaultOp.DECRYPT,
                media = listOf(media.uri),
                vault = vault,
                uniqueKey = "restore_${vault.uuid}_${media.id}_${System.currentTimeMillis()}"
            )
            if (outcome is VaultOpOutcome.Succeeded) {
                val restoredPath = if (media.mimeType.startsWith("video"))
                    Environment.DIRECTORY_MOVIES + "/Restored"
                else
                    Environment.DIRECTORY_PICTURES + "/Restored"
                _userMessage.emit(
                    getString(Res.string.vault_restored_to, 1, restoredPath)
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
            _userMessage.emit(getString(Res.string.vault_item_deleted))
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