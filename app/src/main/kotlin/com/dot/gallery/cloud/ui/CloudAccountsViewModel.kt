/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.CredentialEncryptor
import com.dot.gallery.cloud.core.Disconnectable
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.auth.CloudInteractiveAuthHandler
import com.dot.gallery.cloud.core.auth.InteractiveAuthErrorKind
import com.dot.gallery.cloud.core.auth.InteractiveAuthException
import com.dot.gallery.cloud.core.auth.InteractiveAuthPollResult
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.cloud.data.dao.CloudAlbumSyncDao
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudOfflinePinDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.cloud.data.dao.SyncStateDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.data.entity.CloudUploadPrefEntity
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.di.CloudProviderInitializer
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.cloud.offline.CacheAssetRef
import com.dot.gallery.cloud.offline.CloudMediaCache
import com.dot.gallery.cloud.sync.CloudOfflineDownloadWorker
import com.dot.gallery.cloud.sync.CloudSyncScheduler
import com.dot.gallery.cloud.sync.cloudSyncScheduleChanged
import com.dot.gallery.cloud.sync.fetchAllCloudIndexPages
import com.dot.gallery.core.backup.PendingCloudFavoriteStore
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CloudAccountUiState(
    val configs: List<CloudServerConfigEntity> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val assetCounts: Map<Long, Int> = emptyMap()
)

sealed interface CloudAuthenticationState {
    data object Idle : CloudAuthenticationState
    data object Starting : CloudAuthenticationState
    data object WaitingForBrowser : CloudAuthenticationState
    data object Polling : CloudAuthenticationState
    data object Verifying : CloudAuthenticationState
    data class Verified(val serverUrl: String, val username: String) : CloudAuthenticationState
    data class Failed(
        val message: String,
        val kind: InteractiveAuthErrorKind? = null,
        val unsupported: Boolean = false
    ) : CloudAuthenticationState
    data object Cancelled : CloudAuthenticationState
    data object Expired : CloudAuthenticationState
}

data class BrowserLaunchEvent(val id: Long, val url: String)

data class AddServerUiState(
    val providerType: ProviderType = ProviderType.IMMICH,
    val serverUrl: String = "",
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val syncEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    // Networking (auto local/external URL switching)
    val autoUrlSwitch: Boolean = false,
    val localWifiSsid: String = "",
    val localServerUrl: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val savedConfigId: Long? = null,
    val error: String? = null,
    // Final sync stage: local folders selected as upload sources for this account.
    val selectedLocalAlbumIds: Set<Long> = emptySet(),
    val authenticationState: CloudAuthenticationState = CloudAuthenticationState.Idle,
    val browserLaunchEvent: BrowserLaunchEvent? = null
)

@HiltViewModel
class CloudAccountsViewModel @Inject constructor(
    private val configDao: CloudServerConfigDao,
    private val cloudMediaDao: CloudMediaDao,
    private val offlinePinDao: CloudOfflinePinDao,
    private val cloudMediaCache: CloudMediaCache,
    private val workManager: WorkManager,
    private val syncStateDao: SyncStateDao,
    private val pendingCloudFavoriteStore: PendingCloudFavoriteStore,
    private val registry: ProviderRegistry,
    private val providerInitializer: CloudProviderInitializer,
    private val credentialEncryptor: CredentialEncryptor,
    private val urlResolver: ServerUrlResolver,
    private val uploadPrefDao: CloudUploadPrefDao,
    private val albumSyncDao: CloudAlbumSyncDao,
    private val mediaRepository: MediaRepository,
    private val cloudRepository: CloudRepository,
    private val syncScheduler: CloudSyncScheduler,
    interactiveAuthHandlers: Set<@JvmSuppressWildcards CloudInteractiveAuthHandler>
) : ViewModel() {

    val accountState: StateFlow<List<CloudServerConfigEntity>> = configDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** On-device albums (folders) offered as local backup sources, excluding cloud albums. */
    private val _localAlbums = MutableStateFlow<List<Album>>(emptyList())
    val localAlbums: StateFlow<List<Album>> = _localAlbums.asStateFlow()

    private fun loadLocalAlbums() {
        viewModelScope.launch {
            mediaRepository.getAlbums(MediaOrder.Label(OrderType.Ascending)).collect { resource ->
                _localAlbums.value = (resource.data ?: emptyList()).filter { it.uri.scheme != "cloud" }
            }
        }
    }

    private val _addServerState = MutableStateFlow(AddServerUiState())
    val addServerState: StateFlow<AddServerUiState> = _addServerState.asStateFlow()
    val connectionStates: StateFlow<Map<Long, ConnectionState>> = registry.connectionStates

    private val interactiveAuthByType = interactiveAuthHandlers.associateBy { it.providerType }
    private var interactiveAuthJob: Job? = null
    private var verifiedFingerprint: Int? = null
    private var browserEventId = 0L

    /** Capabilities advertised by the registered provider for [providerType], if any. */
    fun capabilitiesOf(providerType: ProviderType): Set<ProviderCapability> =
        registry.get(providerType)?.capabilities ?: emptySet()

    fun initAddServer(providerType: ProviderType) {
        interactiveAuthJob?.cancel()
        verifiedFingerprint = null
        _syncCompleted.value = false
        _addServerState.value = AddServerUiState(providerType = providerType)
        loadLocalAlbums()
    }

    fun supportsInteractiveAuth(providerType: ProviderType): Boolean =
        interactiveAuthByType.containsKey(providerType)

    fun startInteractiveAuth() {
        val state = _addServerState.value
        val handler = interactiveAuthByType[state.providerType] ?: return
        if (state.serverUrl.isBlank()) return
        interactiveAuthJob?.cancel()
        verifiedFingerprint = null
        _addServerState.value = state.copy(
            authenticationState = CloudAuthenticationState.Starting,
            browserLaunchEvent = null,
            testResult = null,
            testSuccess = false
        )
        interactiveAuthJob = viewModelScope.launch {
            try {
                val session = handler.begin(state.serverUrl)
                val event = BrowserLaunchEvent(++browserEventId, session.browserUrl)
                _addServerState.value = _addServerState.value.copy(
                    authenticationState = CloudAuthenticationState.WaitingForBrowser,
                    browserLaunchEvent = event
                )
                while (true) {
                    when (val result = handler.poll(session)) {
                        InteractiveAuthPollResult.Pending -> {
                            _addServerState.value = _addServerState.value.copy(
                                authenticationState = CloudAuthenticationState.Polling
                            )
                            delay(1_000)
                        }
                        is InteractiveAuthPollResult.Complete -> {
                            val credentials = result.credentials
                            val authenticatedState = _addServerState.value.copy(
                                serverUrl = credentials.serverUrl,
                                username = credentials.username,
                                password = credentials.password,
                                authenticationState = CloudAuthenticationState.Verifying,
                                browserLaunchEvent = null
                            )
                            _addServerState.value = authenticatedState
                            verifyCandidate(authenticatedState)
                            return@launch
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: InteractiveAuthException) {
                verifiedFingerprint = null
                _addServerState.value = _addServerState.value.copy(
                    authenticationState = if (error.kind == InteractiveAuthErrorKind.EXPIRED) {
                        CloudAuthenticationState.Expired
                    } else {
                        CloudAuthenticationState.Failed(
                            message = error.message ?: "Nextcloud sign-in failed",
                            kind = error.kind,
                            unsupported = error.kind == InteractiveAuthErrorKind.UNSUPPORTED
                        )
                    },
                    browserLaunchEvent = null,
                    testSuccess = false
                )
            } catch (error: Exception) {
                verifiedFingerprint = null
                _addServerState.value = _addServerState.value.copy(
                    authenticationState = CloudAuthenticationState.Failed(
                        error.message ?: "Nextcloud sign-in failed"
                    ),
                    browserLaunchEvent = null,
                    testSuccess = false
                )
            }
        }
    }

    fun consumeBrowserLaunchEvent(eventId: Long) {
        if (_addServerState.value.browserLaunchEvent?.id == eventId) {
            _addServerState.value = _addServerState.value.copy(browserLaunchEvent = null)
        }
    }

    fun browserLaunchFailed() {
        interactiveAuthJob?.cancel()
        interactiveAuthJob = null
        verifiedFingerprint = null
        _addServerState.value = _addServerState.value.copy(
            browserLaunchEvent = null,
            authenticationState = CloudAuthenticationState.Failed("No browser is available")
        )
    }

    fun cancelInteractiveAuth() {
        interactiveAuthJob?.cancel()
        interactiveAuthJob = null
        verifiedFingerprint = null
        _addServerState.value = _addServerState.value.copy(
            browserLaunchEvent = null,
            authenticationState = CloudAuthenticationState.Cancelled,
            testSuccess = false
        )
    }

    private fun invalidateVerification() {
        interactiveAuthJob?.cancel()
        interactiveAuthJob = null
        verifiedFingerprint = null
        _addServerState.value = _addServerState.value.copy(
            authenticationState = CloudAuthenticationState.Idle,
            browserLaunchEvent = null,
            testResult = null,
            testSuccess = false
        )
    }

    fun toggleLocalAlbum(albumId: Long) {
        val current = _addServerState.value.selectedLocalAlbumIds
        _addServerState.value = _addServerState.value.copy(
            selectedLocalAlbumIds = if (albumId in current) current - albumId else current + albumId
        )
    }

    fun initEditServer(configId: Long) {
        viewModelScope.launch {
            configDao.getById(configId)?.let { entity ->
                _addServerState.value = AddServerUiState(
                    providerType = entity.providerType,
                    serverUrl = entity.serverUrl,
                    apiKey = entity.apiKey?.let { credentialEncryptor.decrypt(it) } ?: "",
                    username = entity.username ?: "",
                    password = entity.encryptedPassword?.let { credentialEncryptor.decrypt(it) } ?: "",
                    displayName = entity.displayName,
                    syncEnabled = entity.syncEnabled,
                    wifiOnly = entity.wifiOnly,
                    autoUrlSwitch = entity.autoUrlSwitch,
                    localWifiSsid = entity.localWifiSsid,
                    localServerUrl = entity.localServerUrl,
                    savedConfigId = entity.id
                )
            }
        }
    }

    fun updateServerUrl(url: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(serverUrl = url)
    }

    fun updateApiKey(key: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(apiKey = key)
    }

    fun updateUsername(username: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(password = password)
    }

    fun updateDisplayName(name: String) {
        _addServerState.value = _addServerState.value.copy(displayName = name)
    }

    fun updateSyncEnabled(enabled: Boolean) {
        _addServerState.value = _addServerState.value.copy(syncEnabled = enabled)
    }

    fun updateWifiOnly(wifiOnly: Boolean) {
        _addServerState.value = _addServerState.value.copy(wifiOnly = wifiOnly)
    }

    fun updateAutoUrlSwitch(enabled: Boolean) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(autoUrlSwitch = enabled)
    }

    fun updateLocalWifiSsid(ssid: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(localWifiSsid = ssid)
    }

    fun updateLocalServerUrl(url: String) {
        invalidateVerification()
        _addServerState.value = _addServerState.value.copy(localServerUrl = url)
    }

    fun testConnection() {
        val state = _addServerState.value
        if (state.serverUrl.isBlank()) {
            _addServerState.value = state.copy(testResult = "Server URL is required", testSuccess = false)
            return
        }
        interactiveAuthJob?.cancel()
        _addServerState.value = state.copy(
            isTesting = true,
            testResult = null,
            authenticationState = CloudAuthenticationState.Verifying
        )
        viewModelScope.launch {
            try {
                verifyCandidate(state)
            } catch (error: Exception) {
                verifiedFingerprint = null
                _addServerState.value = _addServerState.value.copy(
                    isTesting = false,
                    testResult = error.message ?: "Connection failed",
                    testSuccess = false,
                    authenticationState = CloudAuthenticationState.Failed(
                        error.message ?: "Connection failed"
                    )
                )
            }
        }
    }

    private suspend fun verifyCandidate(state: AddServerUiState) {
        val config = urlResolver.resolve(buildConfig(state))
        val provider = providerInitializer.createTransientProvider(state.providerType) as? RemoteMediaProvider
            ?: throw IllegalStateException("Provider not available. Is it enabled in this build?")
        provider.configure(config)
        provider.authenticate(config).getOrThrow()
        verifiedFingerprint = stateFingerprint(state)
        _addServerState.value = _addServerState.value.copy(
            isTesting = false,
            testResult = provider.displayName,
            testSuccess = true,
            authenticationState = CloudAuthenticationState.Verified(
                serverUrl = state.serverUrl,
                username = state.username
            )
        )
    }

    private fun stateFingerprint(state: AddServerUiState): Int = listOf(
        state.providerType.name,
        state.serverUrl.trim().trimEnd('/'),
        state.apiKey,
        state.username,
        state.password,
        state.autoUrlSwitch.toString(),
        state.localWifiSsid.trim(),
        state.localServerUrl.trim().trimEnd('/')
    ).hashCode()

    private fun isCurrentCandidateVerified(state: AddServerUiState): Boolean =
        verifiedFingerprint == stateFingerprint(state)

    private val _syncCompleted = MutableStateFlow(false)
    val syncCompleted: StateFlow<Boolean> = _syncCompleted.asStateFlow()

    fun saveServer() {
        val state = _addServerState.value
        _addServerState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val oldEntity = state.savedConfigId?.let { configDao.getById(it) }
            var savedId: Long? = null
            try {
                if (!isCurrentCandidateVerified(state)) verifyCandidate(state)
                val entity = mergeCloudServerConfig(
                    oldEntity = oldEntity,
                    state = state,
                    encrypt = credentialEncryptor::encrypt
                )
                val id = configDao.insert(entity)
                savedId = id
                providerInitializer.registerAccount(id).getOrThrow()
                persistSyncSelections(state, id)
                if (oldEntity != null && credentialsChanged(oldEntity, state)) {
                    revokeBestEffort(oldEntity)
                }
                syncScheduler.reconcile()
                _addServerState.value = _addServerState.value.copy(
                    isSaving = false,
                    savedConfigId = id
                )
                triggerSync(id)
                _syncCompleted.value = true
            } catch (e: Exception) {
                savedId?.let { id ->
                    (registry.getByConfigId(id) as? Disconnectable)?.disconnect()
                    registry.unregister(id)
                    if (oldEntity == null) {
                        cloudMediaDao.deleteByServerConfig(id)
                        uploadPrefDao.deleteByConfig(id)
                        albumSyncDao.deleteByServer(id)
                        configDao.deleteById(id)
                        CloudRuntimeSettings.remove(id)
                    } else {
                        configDao.insert(oldEntity)
                        providerInitializer.registerAccount(oldEntity.id)
                    }
                }
                verifiedFingerprint = null
                _addServerState.value = _addServerState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Save failed",
                    testSuccess = false,
                    authenticationState = CloudAuthenticationState.Failed(e.message ?: "Save failed")
                )
            }
        }
    }

    fun deleteServer(configId: Long) {
        viewModelScope.launch {
            val entity = configDao.getById(configId) ?: return@launch
            val cachedAssets = cloudMediaDao.getByServerConfig(configId).first()
            runCatching { revokeBestEffort(entity) }
            withContext(Dispatchers.IO) {
                workManager.cancelUniqueWork(CloudOfflineDownloadWorker.WORK_NAME).result.get()
            }
            offlinePinDao.deleteByConfig(configId)
            cloudMediaCache.clearForAssets(
                cachedAssets.map {
                    CacheAssetRef(it.providerType, it.serverConfigId, it.remoteId)
                }
            )
            syncStateDao.deleteByConfig(configId)
            pendingCloudFavoriteStore.removeForAccount(configId)
            cloudMediaDao.deleteByServerConfig(configId)
            uploadPrefDao.deleteByConfig(configId)
            albumSyncDao.deleteByServer(configId)
            configDao.deleteById(configId)
            CloudRuntimeSettings.remove(configId)
            val provider = registry.getByConfigId(configId)
            if (provider is Disconnectable) {
                provider.disconnect()
            }
            registry.unregister(configId)
            // If this was the last account of its type, mark the type disconnected so the
            // media distributor drops its cloud albums. (Cached media rows were already
            // removed above via cloudMediaDao.deleteByServerConfig, which the timeline
            // observes reactively.)
            if (registry.getAllForType(entity.providerType).isEmpty()) {
                cloudRepository.disconnect(entity.providerType)
            }
            syncScheduler.reconcile()
        }
    }

    fun getConnectionState(configId: Long): ConnectionState =
        registry.connectionStates.value[configId] ?: ConnectionState.DISCONNECTED

    private fun credentialsChanged(old: CloudServerConfigEntity, state: AddServerUiState): Boolean {
        val oldPassword = old.encryptedPassword?.let(credentialEncryptor::decrypt).orEmpty()
        return old.serverUrl.trimEnd('/') != state.serverUrl.trimEnd('/') ||
            old.username.orEmpty() != state.username || oldPassword != state.password
    }

    private suspend fun revokeBestEffort(entity: CloudServerConfigEntity) {
        val handler = interactiveAuthByType[entity.providerType] ?: return
        val config = entity.toCloudServerConfig().copy(
            apiKey = entity.apiKey?.let(credentialEncryptor::decrypt),
            password = entity.encryptedPassword?.let(credentialEncryptor::decrypt)
        )
        handler.revoke(config)
    }

    fun updateConfigById(configId: Long, transform: CloudServerConfigEntity.() -> CloudServerConfigEntity) {
        viewModelScope.launch {
            val entity = configDao.getById(configId) ?: return@launch
            val updated = entity.transform()
            configDao.update(updated)
            if (cloudSyncScheduleChanged(entity, updated)) syncScheduler.reconcile()
        }
    }

    suspend fun getAssetCount(providerType: ProviderType): Int {
        return cloudMediaDao.countByProvider(providerType)
    }

    // All keyed by account id (configId) so several accounts of the same provider type
    // each show their own storage, version, count and sync progress.
    private val _storageInfo = MutableStateFlow<Map<Long, CloudStorageInfo>>(emptyMap())
    val storageInfo: StateFlow<Map<Long, CloudStorageInfo>> = _storageInfo.asStateFlow()

    private val _assetCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val assetCounts: StateFlow<Map<Long, Int>> = _assetCounts.asStateFlow()

    private val _serverVersions = MutableStateFlow<Map<Long, String>>(emptyMap())
    val serverVersions: StateFlow<Map<Long, String>> = _serverVersions.asStateFlow()

    fun loadStorageInfo() {
        viewModelScope.launch {
            configDao.getAll().first().forEach { config ->
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: return@forEach
                try {
                    provider.getStorageInfo().onSuccess { info ->
                        _storageInfo.value = _storageInfo.value + (config.id to info)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun loadAssetCounts() {
        viewModelScope.launch {
            configDao.getAll().first().forEach { config ->
                _assetCounts.value = _assetCounts.value + (config.id to cloudMediaDao.countByConfig(config.id))
            }
        }
    }

    fun loadServerVersions() {
        viewModelScope.launch {
            configDao.getAll().first().forEach { config ->
                val provider = registry.getByConfigId(config.id) as? RemoteMediaProvider ?: return@forEach
                try {
                    provider.getServerVersion().onSuccess { version ->
                        _serverVersions.value = _serverVersions.value + (config.id to version)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private val _syncProgress = MutableStateFlow<Map<Long, SyncProgress>>(emptyMap())
    val syncProgress: StateFlow<Map<Long, SyncProgress>> = _syncProgress.asStateFlow()

    data class SyncProgress(
        val isSyncing: Boolean = false,
        val mediaCount: Int = 0,
        val albumCount: Int = 0,
        val message: String = ""
    )

    fun triggerSync(configId: Long) {
        viewModelScope.launch {
            val provider = registry.getByConfigId(configId) as? RemoteMediaProvider ?: return@launch
            _syncProgress.value = _syncProgress.value + (configId to SyncProgress(
                isSyncing = true, message = "Fetching media..."
            ))
            try {
                val mediaCount = fetchAllCloudIndexPages(
                    pageSize = 500,
                    maxPages = 500,
                    fetchPage = { page, pageSize -> provider.getRemoteAssets(page, pageSize).first() },
                    onPage = { _, total ->
                        _syncProgress.value = _syncProgress.value + (configId to SyncProgress(
                            isSyncing = true, mediaCount = total, message = "Synced $total media items..."
                        ))
                    }
                ).getOrThrow()
                _syncProgress.value = _syncProgress.value + (configId to SyncProgress(
                    isSyncing = true, mediaCount = mediaCount, message = "Fetching albums..."
                ))
                var albumCount = 0
                provider.getRemoteAlbums().collect { resource ->
                    if (resource is com.dot.gallery.core.Resource.Success) {
                        albumCount = resource.data?.size ?: 0
                    }
                }
                _syncProgress.value = _syncProgress.value + (configId to SyncProgress(
                    isSyncing = false, mediaCount = mediaCount, albumCount = albumCount,
                    message = "Done: $mediaCount media, $albumCount albums"
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncProgress.value = _syncProgress.value + (configId to SyncProgress(
                    isSyncing = false, message = "Sync failed: ${e.message}"
                ))
            }
        }
    }

    /** Writes the chosen local-folder upload prefs and remote-album sync flags for [configId]. */
    private suspend fun persistSyncSelections(state: AddServerUiState, configId: Long) {
        val albums = _localAlbums.value
        state.selectedLocalAlbumIds.forEach { albumId ->
            val label = albums.firstOrNull { it.id == albumId }?.label ?: ""
            uploadPrefDao.upsert(
                CloudUploadPrefEntity(
                    serverConfigId = configId,
                    albumId = albumId,
                    providerType = state.providerType,
                    albumLabel = label,
                    uploadEnabled = true
                )
            )
        }
    }

    private fun buildConfig(state: AddServerUiState) = CloudServerConfig(
        id = state.savedConfigId ?: 0L,
        providerType = state.providerType,
        serverUrl = state.serverUrl.trimEnd('/'),
        apiKey = state.apiKey.ifBlank { null },
        username = state.username.ifBlank { null },
        password = state.password.ifBlank { null },
        displayName = state.displayName,
        syncEnabled = state.syncEnabled,
        wifiOnly = state.wifiOnly,
        autoUrlSwitch = state.autoUrlSwitch,
        localWifiSsid = state.localWifiSsid.trim(),
        localServerUrl = state.localServerUrl.trim().trimEnd('/')
    )
}

internal fun mergeCloudServerConfig(
    oldEntity: CloudServerConfigEntity?,
    state: AddServerUiState,
    encrypt: (String) -> String
): CloudServerConfigEntity {
    val apiKey = state.apiKey.ifBlank { null }?.let(encrypt)
    val password = state.password.ifBlank { null }?.let(encrypt)
    val syncEnabled = state.syncEnabled || state.selectedLocalAlbumIds.isNotEmpty()
    val displayName = state.displayName.ifBlank { "${state.providerType.displayName} Server" }
    val serverUrl = state.serverUrl.trimEnd('/')
    val localServerUrl = state.localServerUrl.trim().trimEnd('/')
    return oldEntity?.copy(
        providerType = state.providerType,
        serverUrl = serverUrl,
        apiKey = apiKey,
        username = state.username.ifBlank { null },
        encryptedPassword = password,
        displayName = displayName,
        isActive = true,
        syncEnabled = syncEnabled,
        wifiOnly = state.wifiOnly,
        autoUrlSwitch = state.autoUrlSwitch,
        localWifiSsid = state.localWifiSsid.trim(),
        localServerUrl = localServerUrl
    ) ?: CloudServerConfigEntity(
        id = state.savedConfigId ?: 0L,
        providerType = state.providerType,
        serverUrl = serverUrl,
        apiKey = apiKey,
        username = state.username.ifBlank { null },
        encryptedPassword = password,
        displayName = displayName,
        isActive = true,
        syncEnabled = syncEnabled,
        wifiOnly = state.wifiOnly,
        autoUrlSwitch = state.autoUrlSwitch,
        localWifiSsid = state.localWifiSsid.trim(),
        localServerUrl = localServerUrl
    )
}
