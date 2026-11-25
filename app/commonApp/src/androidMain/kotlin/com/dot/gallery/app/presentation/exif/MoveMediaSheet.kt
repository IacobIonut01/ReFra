/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.exif

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.sync.CloudAlbumTransferMode
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.exif.ui.AlbumDestinationSource
import com.dot.gallery.feature.exif.ui.CloudCopyDestinationStatus
import com.dot.gallery.feature.exif.ui.MoveMediaSheetContent
import com.dot.gallery.feature.exif.ui.SecurityInfoSheet
import com.dot.gallery.feature.exif.ui.TransferOpenTargetType
import com.dot.gallery.data.repository.MediaMutationResult
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.ui.util.launchWriteRequest
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.app.presentation.util.toastError
import com.dot.gallery.core.ui.util.writeRequest
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource as resStringResource
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.feature.exif.ui.restrictedMoveSources
import com.dot.gallery.feature.exif.ui.resolveNewAlbumMovePath
import com.dot.gallery.domain.util.MediaStoreWriteCollection
import com.dot.gallery.domain.util.mediaStoreWriteCollection
import com.dot.gallery.domain.util.resolveMediaStoreVolume
import com.dot.gallery.feature.exif.ui.normalizeMediaStoreRelativePath
import com.dot.gallery.domain.util.mediaStoreVolumeName
import com.dot.gallery.core.rememberMediaViewerNavigate
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.exif.ui.resolveTransferOpenTarget
import com.dot.gallery.feature.exif.ui.isAlbumMoveDestinationEnabled
import com.dot.gallery.feature.exif.generated.resources.Res as ResFeatureExif
import com.dot.gallery.feature.exif.generated.resources.cloud_copy_source_unsupported
import com.dot.gallery.feature.exif.CopyMediaViewModel
import com.dot.gallery.feature.settings.ui.label
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.unlock_album_biometric_subtitle

@Composable
fun <T : Media> MoveMediaSheet(
    sheetState: AppBottomSheetState,
    mediaList: List<T>,
    albumState: State<AlbumState>,
    onFinish: () -> Unit,
) {
    // The flows feeding mediaList re-emit once a move lands — the moved item leaves
    // the source album's list, or re-emits with an updated path on the timeline.
    // Every operation state below is keyed on mediaList, so that re-emit would reset
    // localMoveCompleted/pendingCopyUris/restrictedMoveJob mid-flight and recompute
    // moveSourceStatus against an empty list, bouncing the sheet back to a disabled
    // picker with a bogus "source cannot be transferred" error after a successful
    // move. Freeze the selection for the sheet's visible session instead; the
    // passthrough while hidden preserves the per-selection reset between opens.
    val mediaList = remember(sheetState.isVisible) {
        if (sheetState.isVisible) mediaList.toList() else mediaList
    }
    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    val navigateFromViewer = rememberMediaViewerNavigate()
    val context = LocalContext.current
    val transferViewModel = injectViewModel<CopyMediaViewModel>(key = "MoveMediaSheet") {copyMediaViewModel(it)}
    val cloudTransferState by transferViewModel.cloudCopyState.collectAsState()
    val cloudEnvironment by transferViewModel.cloudEnvironment.collectAsState()
    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
    val hasFullMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasAllFilesAccess || MediaStore.canManageMedia(context)
    } else hasAllFilesAccess
    val toastError = toastError()

    val scope = rememberCoroutineScope()
    var progress by remember(mediaList) { mutableFloatStateOf(0f) }
    var newPath by remember(mediaList) { mutableStateOf("") }
    var awaitingCloudOriginalDelete by rememberSaveable(mediaList) { mutableStateOf(false) }
    var cloudCleanupStarted by rememberSaveable(mediaList) { mutableStateOf(false) }
    var cloudCleanupComplete by rememberSaveable(mediaList) { mutableStateOf(false) }
    var cloudMoveSourceRetained by rememberSaveable(mediaList) { mutableStateOf(false) }
    var localMoveCompleted by rememberSaveable(mediaList) { mutableStateOf(false) }
    var localMoveTargetMediaId by rememberSaveable(mediaList) { mutableStateOf<Long?>(null) }
    var operationDestination by remember { mutableStateOf<Album?>(null) }
    val hasMixedSources = mediaList.any { it.isCloud } && mediaList.any { !it.isCloud }
    val moveSourceStatus = transferViewModel.cloudMoveSourceStatus(mediaList, cloudEnvironment)
    val operationActive = progress > 0f || cloudTransferState.active || awaitingCloudOriginalDelete
    val cloudTransferFailed = cloudTransferState.finished && !cloudTransferState.succeeded
    val cloudMoveSucceeded = cloudTransferState.finished && cloudTransferState.succeeded &&
        (mediaList.none { !it.isCloud } || cloudCleanupComplete)
    val moveSucceeded = cloudMoveSucceeded || localMoveCompleted
    val completedDestination = cloudTransferState.destinationAlbumId?.let { id ->
        albumState.value.albums.firstOrNull { it.id == id }
    } ?: operationDestination
    val completionDestinationLabel = cloudTransferState.destinationLabel
        .ifBlank { completedDestination?.label.orEmpty() }
    val openTarget = resolveTransferOpenTarget(
        itemCount = if (cloudTransferState.finished) cloudTransferState.total else mediaList.size,
        targetMediaId = if (cloudTransferState.finished) {
            cloudTransferState.targetMediaId
        } else {
            localMoveTargetMediaId
        },
        destinationAlbumId = cloudTransferState.destinationAlbumId ?: completedDestination?.id,
        destinationLabel = completionDestinationLabel
    )
    val operationProgress = when {
        cloudTransferState.active -> cloudTransferState.progress
        awaitingCloudOriginalDelete -> 1f
        else -> progress
    }

    val securitySheetState = rememberAppBottomSheetState()
    var pendingLockedAlbum by remember { mutableStateOf<Album?>(null) }
    val sources = mediaList.filter { !it.isCloud }.map {
        AlbumDestinationSource(it.mediaStoreVolumeName, it.relativePath, it.mimeType)
    }

    fun Album.isMoveDestinationEnabled(): Boolean {
        if (moveSourceStatus != CloudCopyDestinationStatus.READY) return false
        val cloudStatus = transferViewModel.cloudMoveDestinationStatus(
            this,
            mediaList,
            cloudEnvironment
        )
        if (cloudStatus != null) return cloudStatus == CloudCopyDestinationStatus.READY
        return absolutePath.isNotBlank() && isAlbumMoveDestinationEnabled(
            hasFullMediaAccess = hasFullMediaAccess,
            albumVolume = volume,
            albumRelativePath = relativePath,
            sources = sources,
            isCloudAlbum = false,
            hasAllFilesAccess = SdkCompat.hasFullFileAccess,
        )
    }

    fun localMediaForDestination(path: String): List<T> {
        val (destinationVolume, destinationRelativePath) = resolveMediaStoreVolume(path)
        return mediaList.filter {
            !it.isCloud && (it.mediaStoreVolumeName != destinationVolume ||
                normalizeMediaStoreRelativePath(it.relativePath) !=
                normalizeMediaStoreRelativePath(destinationRelativePath))
        }
    }

    val doMove: () -> Unit = {
        scope.launch {
            val localMedia = localMediaForDestination(newPath)
            val done = async {
                localMedia.forEachIndexed { index, it ->
                    if (handler.moveMedia(media = it, newPath = newPath)) {
                        val movedFilePath = newPath.trimEnd('/') + "/" + it.label
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(movedFilePath),
                            arrayOf(it.mimeType),
                            null
                        )
                        progress = (index + 1).toFloat() / mediaList.size
                    } else {
                        return@async false
                    }
                }
                return@async true
            }
            if (done.await()) {
                context.contentResolver.notifyChange(
                    MediaStore.Files.getContentUri("external"), null
                )
                progress = 0f
                localMoveTargetMediaId = mediaList.singleOrNull()?.id
                if (operationDestination != null) {
                    localMoveCompleted = true
                } else {
                    sheetState.hide()
                    onFinish()
                }
            } else {
                toastError.show()
                delay(1000)
                sheetState.hide()
            }
        }
    }

    val request = rememberActivityResult { doMove() }

    var pendingCopyUris by rememberSaveable(mediaList) { mutableStateOf<List<String>>(emptyList()) }
    var restrictedMoveJob by remember(mediaList) { mutableStateOf<Job?>(null) }

    val finishMove: () -> Unit = {
        scope.launch {
            context.contentResolver.notifyChange(
                MediaStore.Files.getContentUri("external"), null
            )
            localMoveTargetMediaId = pendingCopyUris.singleOrNull()
                ?.let(Uri::parse)?.lastPathSegment?.toLongOrNull()
                ?: mediaList.singleOrNull()?.id
            pendingCopyUris = emptyList()
            progress = 0f
            if (operationDestination != null) {
                localMoveCompleted = true
            } else {
                sheetState.hide()
                onFinish()
            }
        }
    }

    val deleteRequest = rememberActivityResult(
        onResultCanceled = {
            // The originals stay where they are, so drop the copies: a cancelled move must
            // not leave a duplicate behind.
            scope.launch {
                handler.discardMediaCopies(pendingCopyUris.map(Uri::parse))
                pendingCopyUris = emptyList()
                progress = 0f
                sheetState.hide()
            }
        },
        onResultOk = finishMove
    )

    val cloudOriginalDeleteRequest = rememberActivityResult(
        onResultCanceled = {
            awaitingCloudOriginalDelete = false
            cloudMoveSourceRetained = true
            cloudCleanupComplete = true
        },
        onResultOk = {
            awaitingCloudOriginalDelete = false
            cloudCleanupComplete = true
        }
    )

    /**
     * Media stored in another app's `Android/media/<package>` folder cannot be renamed into an
     * album. Copy the selection to the destination first, then let the system ask the user to
     * confirm the removal of the originals - a single dialog for the whole batch.
     */
    fun startRestrictedMove(albumPath: String, localMedia: List<T>) {
        if (restrictedMoveJob?.isActive == true) return
        progress = 0.001f
        restrictedMoveJob = scope.launch {
            var copies = emptyList<Uri>()
            var copyOwnershipTransferred = false
            try {
                copies = handler.copyMediaForMove(localMedia, albumPath) { copyProgress ->
                    withContext(Dispatchers.Main) { progress = copyProgress }
                }
                if (copies.isEmpty()) {
                    progress = 0f
                    toastError.show()
                    delay(1000)
                    sheetState.hide()
                    return@launch
                }
                pendingCopyUris = copies.map(Uri::toString)
                when (handler.deleteMedia(deleteRequest, localMedia)) {
                    // The dialog drives the rest through deleteRequest.
                    MediaMutationResult.REQUEST_LAUNCHED -> copyOwnershipTransferred = true
                    MediaMutationResult.COMPLETED -> {
                        copyOwnershipTransferred = true
                        finishMove()
                    }
                    MediaMutationResult.FAILED -> {
                        withContext(NonCancellable) { handler.discardMediaCopies(copies) }
                        copies = emptyList()
                        pendingCopyUris = emptyList()
                        progress = 0f
                        toastError.show()
                        delay(1000)
                        sheetState.hide()
                    }
                }
            } catch (e: CancellationException) {
                if (!copyOwnershipTransferred) {
                    if (copies.isNotEmpty()) {
                        withContext(NonCancellable) { handler.discardMediaCopies(copies) }
                    }
                    pendingCopyUris = emptyList()
                }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) { handler.discardMediaCopies(copies) }
                pendingCopyUris = emptyList()
                progress = 0f
                toastError.show()
                delay(1000)
                sheetState.hide()
            } finally {
                restrictedMoveJob = null
            }
        }
    }

    fun startCloudToLocalMove(albumPath: String, cloudMedia: List<T>) {
        if (restrictedMoveJob?.isActive == true) return
        progress = 0.001f
        restrictedMoveJob = scope.launch {
            var copies = emptyList<Uri>()
            var sourceMutationStarted = false
            try {
                copies = handler.copyMediaForMove(cloudMedia, albumPath) { copyProgress ->
                    withContext(Dispatchers.Main) { progress = copyProgress }
                }
                if (copies.isEmpty()) {
                    progress = 0f
                    toastError.show()
                    delay(1000)
                    sheetState.hide()
                    return@launch
                }
                pendingCopyUris = copies.map(Uri::toString)
                sourceMutationStarted = true
                when (handler.deleteMedia(deleteRequest, cloudMedia)) {
                    MediaMutationResult.COMPLETED -> {
                        scope.launch { distributor.invalidate() }
                        finishMove()
                    }
                    MediaMutationResult.REQUEST_LAUNCHED -> {
                        pendingCopyUris = copies.map(Uri::toString)
                    }
                    MediaMutationResult.FAILED -> {
                        scope.launch { distributor.invalidate() }
                        progress = 0f
                        cloudMoveSourceRetained = true
                        localMoveTargetMediaId = copies.singleOrNull()
                            ?.lastPathSegment?.toLongOrNull()
                        localMoveCompleted = true
                    }
                }
            } catch (e: CancellationException) {
                if (!sourceMutationStarted && copies.isNotEmpty()) {
                    withContext(NonCancellable) { handler.discardMediaCopies(copies) }
                }
                throw e
            } catch (e: Exception) {
                progress = 0f
                if (sourceMutationStarted) {
                    cloudMoveSourceRetained = true
                    localMoveTargetMediaId = copies.singleOrNull()
                        ?.lastPathSegment?.toLongOrNull()
                    localMoveCompleted = true
                } else {
                    withContext(NonCancellable) { handler.discardMediaCopies(copies) }
                    toastError.show()
                    delay(1000)
                    sheetState.hide()
                }
            } finally {
                restrictedMoveJob = null
            }
        }
    }

    fun startLocalMove(albumPath: String) {
        val cloudMedia = mediaList.filter { it.isCloud }
        if (cloudMedia.isNotEmpty()) {
            startCloudToLocalMove(albumPath, cloudMedia)
            return
        }
        val localMedia = localMediaForDestination(albumPath)
        val destinationRelativePath = resolveMediaStoreVolume(albumPath).second
        val isRestrictedMove = restrictedMoveSources(hasFullMediaAccess, localMedia).isNotEmpty() ||
            (!hasFullMediaAccess && localMedia.any {
                mediaStoreWriteCollection(it.mimeType, destinationRelativePath, false) ==
                    MediaStoreWriteCollection.FILES
            })
        if (isRestrictedMove && restrictedMoveJob?.isActive == true) return
        if (localMedia.isNotEmpty()) {
            if (isRestrictedMove) {
                startRestrictedMove(albumPath, localMedia)
            } else {
                scope.launch(Dispatchers.Main) {
                    newPath = albumPath
                    request.launchWriteRequest(
                        localMedia.writeRequest(context.contentResolver),
                        doMove
                    )
                }
            }
        } else {
            scope.launch {
                sheetState.hide()
                onFinish()
            }
        }
    }

    fun startMove(album: Album) {
        operationDestination = album
        if (album.cloudIdentity != null) {
            transferViewModel.enqueueCloudCopy(mediaList, album, CloudAlbumTransferMode.MOVE)
        } else {
            startLocalMove(album.absolutePath)
        }
    }

    fun finishCloudMove(route: String? = null) {
        scope.launch {
            sheetState.hide()
            transferViewModel.clearCloudCopyResult()
            localMoveCompleted = false
            cloudCleanupStarted = false
            cloudCleanupComplete = false
            cloudMoveSourceRetained = false
            operationDestination = null
            onFinish()
            if (route != null) navigateFromViewer(route)
        }
    }

    fun openCloudMoveCompletion() {
        val target = openTarget ?: return
        val route = when (target.type) {
            TransferOpenTargetType.MEDIA -> Screen.MediaViewScreen.idAndAlbum(
                requireNotNull(target.mediaId),
                target.albumId
            )
            TransferOpenTargetType.ALBUM -> Screen.AlbumViewScreen.album(
                target.albumId,
                target.albumLabel
            )
        }
        finishCloudMove(route)
    }

    LaunchedEffect(
        cloudTransferState.workId,
        cloudTransferState.finished,
        cloudTransferState.succeeded,
        cloudCleanupStarted
    ) {
        if (!cloudTransferState.finished || !cloudTransferState.succeeded || cloudCleanupStarted) {
            return@LaunchedEffect
        }
        val localSources = mediaList.filter { !it.isCloud }
        if (localSources.isEmpty()) return@LaunchedEffect
        cloudCleanupStarted = true
        awaitingCloudOriginalDelete = true
        when (handler.deleteMedia(cloudOriginalDeleteRequest, localSources)) {
            MediaMutationResult.REQUEST_LAUNCHED -> Unit
            MediaMutationResult.COMPLETED -> {
                awaitingCloudOriginalDelete = false
                cloudCleanupComplete = true
            }
            MediaMutationResult.FAILED -> {
                awaitingCloudOriginalDelete = false
                cloudMoveSourceRetained = true
                cloudCleanupComplete = true
            }
        }
    }

    val biometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingLockedAlbum?.let(::startMove)
            pendingLockedAlbum = null
        },
        onFailed = {
            pendingLockedAlbum = null
        }
    )

    val failureMessage = cloudTransferState.message.ifBlank {
        resStringResource(ResFeatureExif.string.cloud_copy_source_unsupported)
    }

    MoveMediaSheetContent(
        sheetState = sheetState,
        media = mediaList,
        albums = albumState.value.albums,
        albumGroups = albumState.value.albumGroups,
        cloudAccounts = cloudEnvironment.accountsByConfigId,
        gridSizeIndex = rememberAlbumGridSize().value,
        moveSourceStatus = moveSourceStatus,
        hasMixedSources = hasMixedSources,
        operationActive = operationActive,
        operationProgress = operationProgress,
        awaitingSourceCleanup = awaitingCloudOriginalDelete,
        succeeded = moveSucceeded,
        sourceRetained = cloudMoveSourceRetained,
        failed = cloudTransferFailed,
        failureMessage = failureMessage,
        canRetry = cloudTransferState.canRetry,
        destination = completedDestination,
        destinationLabel = completionDestinationLabel,
        openTarget = openTarget,
        hasFullStorageAccess = hasAllFilesAccess,
        isDestinationEnabled = { it.isMoveDestinationEnabled() },
        destinationStatus = {
            transferViewModel.cloudMoveDestinationStatus(it, mediaList, cloudEnvironment)
        },
        onDestinationSelected = { album ->
            if (album.isLocked) {
                if (!biometricState.isSupported) {
                    scope.launch { securitySheetState.show() }
                } else {
                    pendingLockedAlbum = album
                    biometricState.authenticate()
                }
            } else {
                startMove(album)
            }
        },
        onNewAlbum = { newAlbum ->
            // Same routing as an existing album so restricted sources reach the copy +
            // delete-request path here too.
            startLocalMove(resolveNewAlbumMovePath(newAlbum, hasAllFilesAccess))
        },
        onDismissRequest = {
            if (!operationActive) {
                if (moveSucceeded) {
                    finishCloudMove()
                } else {
                    restrictedMoveJob?.cancel()
                    transferViewModel.clearCloudCopyResult()
                    scope.launch { sheetState.hide() }
                }
            }
        },
        onRetry = transferViewModel::retryCloudCopy,
        onCloseResult = {
            transferViewModel.clearCloudCopyResult()
            scope.launch { sheetState.hide() }
        },
        onDone = { finishCloudMove() },
        onOpen = { openCloudMoveCompletion() },
        providerIcon = { providerType, tint, modifier ->
            ProviderBrandIcon(providerType = providerType, modifier = modifier, tint = tint)
        }
    )

    SecurityInfoSheet(sheetState = securitySheetState)
}
