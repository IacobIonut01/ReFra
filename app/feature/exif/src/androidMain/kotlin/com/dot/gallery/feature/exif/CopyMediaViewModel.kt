package com.dot.gallery.feature.exif

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.CloudAccountRuntimeSettings
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudMediaId
import com.dot.gallery.cloud.core.isUnsupportedSameAccountCloudMove
import com.dot.gallery.cloud.core.capabilities.RemoteAlbumWriteProvider
import com.dot.gallery.cloud.core.capabilities.RemoteMediaProvider
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.sync.CloudAlbumTransferMode
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.exif.ui.CloudCopyDestinationStatus
import com.dot.gallery.feature.exif.ui.CloudDestinationAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.util.or
import com.dot.gallery.cloud.core.matches
import com.dot.gallery.core.presentation.util.remove
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.offline.OfflineModeManager
import com.dot.gallery.cloud.sync.CloudAlbumCopyRequestStore
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.uri.host
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.feature.exif.ui.cloudCopyDestinationStatus
import com.dot.gallery.core.logging.printWarn

class CopyMediaViewModel @Inject constructor(
    private val copyRuntime: CopyMediaRuntime,
    private val requestStore: CloudAlbumCopyRequestStore,
    private val registry: ProviderRegistry,
    private val configDao: CloudServerConfigDao,
    private val offlineModeManager: OfflineModeManager,
    @me.tatarka.inject.annotations.Assisted private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _cloudCopyState = MutableStateFlow(CloudCopyUiState())
    val cloudCopyState: StateFlow<CloudCopyUiState> = _cloudCopyState.asStateFlow()
    private val _localCopyState = MutableStateFlow(LocalCopyUiState())
    val localCopyState: StateFlow<LocalCopyUiState> = _localCopyState.asStateFlow()
    private var cloudWorkJob: Job? = null
    private var localWorkJob: Job? = null

    val cloudEnvironment: StateFlow<CloudCopyEnvironment> = combine(
        registry.connectionStates,
        CloudRuntimeSettings.settingsByConfigId,
        configDao.getActive(),
        offlineModeManager.effectiveOffline
    ) { connectionStates, settings, configs, offline ->
        val accounts = configs.associate { config ->
            val host = runCatching { Uri.parse(config.serverUrl).host.orEmpty() }.getOrDefault("")
            val title = config.displayName.ifBlank {
                host.ifBlank { config.providerType.displayName }
            }
            val subtitle = listOf(
                config.providerType.displayName,
                config.username.orEmpty(),
                host.takeUnless { it == title }.orEmpty()
            ).filter(String::isNotBlank).distinct().joinToString(" · ")
            config.id to CloudDestinationAccount(
                providerType = config.providerType,
                title = title,
                subtitle = subtitle
            )
        }
        CloudCopyEnvironment(connectionStates, settings, accounts, offline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudCopyEnvironment())

    init {
        savedStateHandle.get<String>(KEY_CLOUD_WORK_ID)?.let(::restoreCloudWork)
    }

    /**
     * Picks a cloud transfer back up after the process was recreated, but only while it is still
     * running. A transfer that already finished (or failed) is cleared rather than re-shown, so
     * the copy sheet doesn't pop up with a stale result on a later launch.
     */
    private fun restoreCloudWork(workId: String) {
        viewModelScope.launch {
            val snapshot = runCatching { copyRuntime.observeCloudAlbumCopy(workId).first() }
                .onFailure {
                    printWarn(
                        tag = "media-ops.copy",
                        message = "Could not restore cloud copy state",
                        ctx = mapOf("error" to (it.message ?: it::class.simpleName.orEmpty())),
                    )
                }
                .getOrNull()
            if (snapshot == null || snapshot.state.isTerminalCopyState()) {
                clearCloudCopyResult()
            } else {
                observeCloudWork(workId)
            }
        }
    }

    fun cloudDestinationStatus(
        album: Album,
        media: List<Media>,
        environment: CloudCopyEnvironment
    ): CloudCopyDestinationStatus? {
        val identity = album.cloudIdentity ?: return null
        val provider = registry.getByConfigId(identity.serverConfigId)
            ?.takeIf { it.providerType == identity.providerType }
        val sourcesSupported = media.isNotEmpty() && media.all { it.isCloudTransferSource() }
        return cloudCopyDestinationStatus(
            albumId = album.id,
            identity = identity,
            sourcesSupported = sourcesSupported,
            accountAvailable = provider?.isAvailable == true &&
                environment.connectionStates[identity.serverConfigId].let {
                    it == ConnectionState.CONNECTED || it == ConnectionState.SYNCING
                },
            supportsAlbumWrite = provider is RemoteAlbumWriteProvider,
            readOnly = environment.settingsByConfigId[identity.serverConfigId]?.readOnlyMode == true,
            offline = environment.offline
        )
    }

    fun cloudMoveSourceStatus(
        media: List<Media>,
        environment: CloudCopyEnvironment
    ): CloudCopyDestinationStatus {
        if (media.isEmpty() || !media.all { it.isCloudTransferSource() }) {
            return CloudCopyDestinationStatus.SOURCE_UNSUPPORTED
        }
        val cloudSources = media.filter { it.isCloud }
        if (cloudSources.isNotEmpty() && cloudSources.size != media.size) {
            return CloudCopyDestinationStatus.MOVE_UNSUPPORTED
        }
        for (sourceMedia in cloudSources) {
            val source = CloudUri.parse(sourceMedia.getUri().toString())
                ?: return CloudCopyDestinationStatus.SOURCE_UNSUPPORTED
            val sourceProvider = registry.getByConfigId(source.configId)
                ?.takeIf { it.providerType == source.providerType } as? RemoteMediaProvider
                ?: return CloudCopyDestinationStatus.ACCOUNT_UNAVAILABLE
            if (!sourceProvider.isAvailable) return CloudCopyDestinationStatus.ACCOUNT_UNAVAILABLE
            if (environment.settingsByConfigId[source.configId]?.readOnlyMode == true) {
                return CloudCopyDestinationStatus.READ_ONLY
            }
        }
        return CloudCopyDestinationStatus.READY
    }

    fun cloudMoveDestinationStatus(
        album: Album,
        media: List<Media>,
        environment: CloudCopyEnvironment
    ): CloudCopyDestinationStatus? {
        val base = cloudDestinationStatus(album, media, environment) ?: return null
        if (base != CloudCopyDestinationStatus.READY) return base
        val sourceStatus = cloudMoveSourceStatus(media, environment)
        if (sourceStatus != CloudCopyDestinationStatus.READY) return sourceStatus
        for (sourceMedia in media.filter { it.isCloud }) {
            val source = CloudUri.parse(sourceMedia.getUri().toString())
                ?: return CloudCopyDestinationStatus.SOURCE_UNSUPPORTED
            if (isUnsupportedSameAccountCloudMove(
                    source,
                    requireNotNull(album.cloudIdentity),
                    sourceMedia.label
                )
            ) return CloudCopyDestinationStatus.MOVE_UNSUPPORTED
        }
        return CloudCopyDestinationStatus.READY
    }

    fun enqueueCloudCopy(
        media: List<Media>,
        album: Album,
        mode: CloudAlbumTransferMode = CloudAlbumTransferMode.COPY
    ) {
        val identity = album.cloudIdentity ?: return
        if (!identity.matches(album.id) || media.isEmpty() || !media.all { it.isCloudTransferSource() }) return
        if (mode == CloudAlbumTransferMode.MOVE &&
            cloudMoveDestinationStatus(album, media, cloudEnvironment.value) != CloudCopyDestinationStatus.READY
        ) return
        _cloudCopyState.value = CloudCopyUiState(
            mode = mode,
            destination = identity,
            destinationAlbumId = album.id,
            destinationLabel = album.label,
            total = media.size,
            active = true
        )
        viewModelScope.launch {
            runCatching {
                requestStore.create(album.id, identity, album.label, media, mode)
            }.onSuccess { request ->
                val workId = copyRuntime.enqueueCloudAlbumCopy(request.id)
                savedStateHandle[KEY_CLOUD_REQUEST_ID] = request.id
                savedStateHandle[KEY_CLOUD_WORK_ID] = workId
                _cloudCopyState.value = _cloudCopyState.value.copy(
                    requestId = request.id,
                    workId = workId
                )
                observeCloudWork(workId)
            }.onFailure { error ->
                _cloudCopyState.value = _cloudCopyState.value.copy(
                    active = false,
                    finished = true,
                    message = error.message ?: "Could not start cloud copy"
                )
            }
        }
    }

    fun retryCloudCopy() {
        val requestId = _cloudCopyState.value.requestId
            ?: savedStateHandle.get<String>(KEY_CLOUD_REQUEST_ID)
            ?: return
        viewModelScope.launch {
            val workId = copyRuntime.enqueueCloudAlbumCopy(requestId)
            savedStateHandle[KEY_CLOUD_WORK_ID] = workId
            _cloudCopyState.value = _cloudCopyState.value.copy(
                workId = workId,
                active = true,
                finished = false,
                succeeded = false,
                retryable = false,
                message = ""
            )
            observeCloudWork(workId)
        }
    }

    fun clearCloudCopyResult() {
        val requestId = _cloudCopyState.value.requestId
            ?: savedStateHandle.get<String>(KEY_CLOUD_REQUEST_ID)
        if (requestId != null) viewModelScope.launch { requestStore.delete(requestId) }
        cloudWorkJob?.cancel()
        cloudWorkJob = null
        savedStateHandle.remove<String>(KEY_CLOUD_REQUEST_ID)
        savedStateHandle.remove<String>(KEY_CLOUD_WORK_ID)
        _cloudCopyState.value = CloudCopyUiState()
    }

    private fun observeCloudWork(workId: String) {
        cloudWorkJob?.cancel()
        cloudWorkJob = viewModelScope.launch {
            copyRuntime.observeCloudAlbumCopy(workId).collect { snap ->
                val current = _cloudCopyState.value
                val active = snap.state == CopyWorkState.RUNNING ||
                    snap.state == CopyWorkState.ENQUEUED ||
                    snap.state == CopyWorkState.BLOCKED
                val succeeded = snap.state == CopyWorkState.SUCCEEDED
                val finished = snap.state == CopyWorkState.SUCCEEDED ||
                    snap.state == CopyWorkState.FAILED ||
                    snap.state == CopyWorkState.CANCELLED
                val providerType = snap.destinationProvider
                    ?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
                val configId = snap.destinationConfigId
                    ?: current.destination?.serverConfigId ?: 0L
                val remoteAlbumId = snap.destinationRemoteAlbumId
                    ?: current.destination?.remoteId.orEmpty()
                val destination = if (providerType != null && configId > 0L && remoteAlbumId.isNotBlank()) {
                    CloudAlbumIdentity(providerType, configId, remoteAlbumId)
                } else current.destination
                _cloudCopyState.value = current.copy(
                    requestId = snap.requestId ?: current.requestId
                        ?: savedStateHandle.get<String>(KEY_CLOUD_REQUEST_ID),
                    workId = workId,
                    mode = snap.mode
                        ?.let { runCatching { CloudAlbumTransferMode.valueOf(it) }.getOrNull() }
                        ?: current.mode,
                    destination = destination,
                    destinationAlbumId = (snap.destinationAlbumId ?: current.destinationAlbumId)
                        .takeIf { it != 0L },
                    destinationLabel = snap.destinationLabel ?: current.destinationLabel,
                    targetRemoteId = snap.targetRemoteId
                        ?.takeIf(String::isNotBlank) ?: current.targetRemoteId,
                    total = snap.total ?: current.total,
                    completed = snap.completed ?: current.completed,
                    alreadyPresent = snap.alreadyPresent ?: current.alreadyPresent,
                    failed = snap.failed ?: current.failed,
                    current = snap.current ?: current.current,
                    active = active,
                    finished = finished,
                    succeeded = succeeded,
                    retryable = snap.retryable,
                    message = snap.message.orEmpty()
                )
            }
        }
    }

    fun <T : Media> enqueueLocalCopy(
        media: List<T>,
        destination: Album,
        onStarted: () -> Unit = {}
    ) {
        if (media.isEmpty()) return
        viewModelScope.launch {
            val workIds = copyRuntime.enqueueMediaCopy(
                media.map { it to destination.absolutePath }
            )
            _localCopyState.value = LocalCopyUiState(
                workIds = workIds,
                destinationAlbumId = destination.id,
                destinationLabel = destination.label,
                total = media.size,
                active = true
            )
            observeLocalCopy(workIds)
            onStarted()
        }
    }

    fun clearLocalCopyResult() {
        localWorkJob?.cancel()
        localWorkJob = null
        _localCopyState.value = LocalCopyUiState()
    }

    private fun observeLocalCopy(workIds: List<String>) {
        localWorkJob?.cancel()
        if (workIds.isEmpty()) return
        localWorkJob = viewModelScope.launch {
            copyRuntime.observeMediaCopy(workIds).map { infos ->
                val active = infos.any {
                    it.state == CopyWorkState.RUNNING ||
                        it.state == CopyWorkState.ENQUEUED ||
                        it.state == CopyWorkState.BLOCKED
                }
                val finished = infos.isNotEmpty() && infos.all {
                    it.state == CopyWorkState.SUCCEEDED ||
                        it.state == CopyWorkState.FAILED ||
                        it.state == CopyWorkState.CANCELLED
                }
                val succeeded = finished && infos.all { it.state == CopyWorkState.SUCCEEDED }
                val completed = infos.count { it.state == CopyWorkState.SUCCEEDED }
                val progress = when {
                    succeeded -> 1f
                    infos.isEmpty() -> 0f
                    else -> infos.map { it.progressPercent ?: 0 }
                        .average().toFloat().div(100f).coerceIn(0f, 1f)
                }
                val targetMediaId = if (_localCopyState.value.total == 1) {
                    infos.singleOrNull()?.targetUri
                        ?.let(Uri::parse)?.lastPathSegment?.toLongOrNull()
                } else null
                _localCopyState.value.copy(
                    completed = completed,
                    progress = progress,
                    active = active,
                    finished = finished,
                    succeeded = succeeded,
                    targetMediaId = targetMediaId,
                    message = ""
                )
            }.collect { _localCopyState.value = it }
        }
    }

    /**
     * Copies into a folder path (the new-album flow). Tracked exactly like [enqueueLocalCopy], so
     * the sheet follows only the copies it started, never older work still queued in the
     * background.
     */
    fun <T: Media> enqueueCopy(vararg sets: Pair<T, String>, onStarted: () -> Unit = {}) {
        if (sets.isEmpty()) return
        viewModelScope.launch {
            val workIds = copyRuntime.enqueueMediaCopy(sets.toList())
            _localCopyState.value = LocalCopyUiState(
                workIds = workIds,
                destinationLabel = sets.first().second.trimEnd('/').substringAfterLast('/'),
                total = sets.size,
                active = true
            )
            observeLocalCopy(workIds)
            onStarted()
        }
    }

    private fun Media.isCloudTransferSource(): Boolean {
        val uri = runCatching { getUri() }.getOrNull() ?: return false
        if (!isCloud) return uri.scheme == "content" || uri.scheme == "file"
        val source = CloudUri.parse(uri.toString()) ?: return false
        if (source.configId <= 0L) return false
        return (registry.getByConfigId(source.configId) as? RemoteMediaProvider)
            ?.takeIf { it.providerType == source.providerType }
            ?.isAvailable == true
    }

    companion object {
        private const val KEY_CLOUD_REQUEST_ID = "cloud_copy_request_id"
        private const val KEY_CLOUD_WORK_ID = "cloud_copy_work_id"
    }
}
