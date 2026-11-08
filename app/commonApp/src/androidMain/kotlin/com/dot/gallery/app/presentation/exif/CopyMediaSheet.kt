/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.exif

import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.exif.ui.CloudCopyDestinationStatus
import com.dot.gallery.feature.exif.ui.CopyMediaSheetContent
import com.dot.gallery.feature.exif.ui.SecurityInfoSheet
import com.dot.gallery.feature.exif.ui.TransferOpenTargetType
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.pluralStringResource as resPluralStringResource
import org.jetbrains.compose.resources.stringResource as resStringResource
import com.dot.gallery.domain.uri.scheme
import com.dot.gallery.feature.exif.ui.resolveNewAlbumMovePath
import com.dot.gallery.feature.exif.ui.resolveTransferOpenTarget
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.feature.exif.ui.isAlbumCopyDestinationEnabled
import com.dot.gallery.core.rememberMediaViewerNavigate
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.exif.generated.resources.Res as ResFeatureExif
import com.dot.gallery.feature.exif.generated.resources.cloud_copy_failed_count
import com.dot.gallery.feature.exif.generated.resources.transfer_copy_failed
import com.dot.gallery.feature.exif.CopyMediaViewModel
import com.dot.gallery.feature.exif.copySheetOperationVisible
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.unlock_album_biometric_subtitle


@Composable
fun <T : Media> CopyMediaSheet(
    sheetState: AppBottomSheetState,
    albumsState: State<AlbumState>,
    mediaList: List<T>,
    onFinish: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navigateFromViewer = rememberMediaViewerNavigate()
    val hasFullMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Environment.isExternalStorageManager() || MediaStore.canManageMedia(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
    val viewModel: CopyMediaViewModel = injectViewModel { copyMediaViewModel(it) }
    val localCopyState by viewModel.localCopyState.collectAsState()
    val cloudCopyState by viewModel.cloudCopyState.collectAsState()
    val cloudEnvironment by viewModel.cloudEnvironment.collectAsState()
    val operationActive = localCopyState.active || cloudCopyState.active
    val operationFinished = cloudCopyState.finished || localCopyState.finished
    val operationProgress = when {
        cloudCopyState.active -> cloudCopyState.progress
        else -> localCopyState.progress
    }

    val securitySheetState = rememberAppBottomSheetState()
    var pendingLockedAlbum by remember { mutableStateOf<Album?>(null) }
    var operationDestination by remember { mutableStateOf<Album?>(null) }
    val mutex = Mutex()
    val cloudCopySucceeded = cloudCopyState.finished && cloudCopyState.succeeded
    val localCopySucceeded = localCopyState.finished && localCopyState.succeeded
    val copySucceeded = cloudCopySucceeded || localCopySucceeded
    val copyFailed = cloudCopyState.finished && !cloudCopyState.succeeded ||
        localCopyState.finished && !localCopyState.succeeded
    val completedDestinationId = cloudCopyState.destinationAlbumId
        ?: localCopyState.destinationAlbumId
    val completedDestination = completedDestinationId?.let { id ->
        albumsState.value.albums.firstOrNull { it.id == id }
    } ?: operationDestination
    val completionTotal = if (cloudCopyState.finished) cloudCopyState.total else localCopyState.total
    val completionTargetMediaId = if (cloudCopyState.finished) {
        cloudCopyState.targetMediaId
    } else {
        localCopyState.targetMediaId
    }
    val completionDestinationLabel = cloudCopyState.destinationLabel
        .ifBlank { localCopyState.destinationLabel }
    val openTarget = resolveTransferOpenTarget(
        itemCount = completionTotal,
        targetMediaId = completionTargetMediaId,
        destinationAlbumId = completedDestinationId,
        destinationLabel = completionDestinationLabel
    )

    fun Album.isCopyDestinationEnabled(): Boolean {
        val cloudStatus = viewModel.cloudDestinationStatus(this, mediaList, cloudEnvironment)
        if (cloudStatus != null) return cloudStatus == CloudCopyDestinationStatus.READY
        return absolutePath.isNotBlank() && isAlbumCopyDestinationEnabled(
            hasFullMediaAccess = hasFullMediaAccess,
            albumRelativePath = relativePath,
            isCloudAlbum = uri.scheme == "cloud" || relativePath.startsWith("cloud/"),
        )
    }

    fun copyMedia(path: String) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                viewModel.enqueueCopy(*mediaList.map { it to path }.toTypedArray()) {
                    scope.launch {
                        sheetState.show()
                    }
                }
            }
        }
    }

    fun copyToAlbum(album: Album) {
        operationDestination = album
        if (album.cloudIdentity != null) {
            viewModel.enqueueCloudCopy(mediaList, album)
        } else {
            viewModel.enqueueLocalCopy(mediaList, album) {
                scope.launch { sheetState.show() }
            }
        }
    }

    fun finishCopy(route: String? = null) {
        scope.launch {
            sheetState.hide()
            viewModel.clearCloudCopyResult()
            viewModel.clearLocalCopyResult()
            operationDestination = null
            onFinish()
            if (route != null) navigateFromViewer(route)
        }
    }

    fun openCompletion() {
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
        finishCopy(route)
    }

    val biometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingLockedAlbum?.let(::copyToAlbum)
            pendingLockedAlbum = null
        },
        onFailed = {
            pendingLockedAlbum = null
        }
    )

    // Only transfers started from this sheet open it; leftover background copy work doesn't.
    val operationVisible = copySheetOperationVisible(localCopyState, cloudCopyState)
    LaunchedEffect(operationVisible) {
        if (operationVisible) sheetState.show() else sheetState.hide()
    }

    val failureMessage = if (cloudCopyState.finished) {
        cloudCopyState.message.ifBlank {
            resPluralStringResource(
                ResFeatureExif.plurals.cloud_copy_failed_count,
                cloudCopyState.failed,
                cloudCopyState.failed,
                cloudCopyState.total
            )
        }
    } else {
        resStringResource(ResFeatureExif.string.transfer_copy_failed)
    }

    CopyMediaSheetContent(
        sheetState = sheetState,
        media = mediaList,
        albums = albumsState.value.albums,
        albumGroups = albumsState.value.albumGroups,
        cloudAccounts = cloudEnvironment.accountsByConfigId,
        gridSizeIndex = rememberAlbumGridSize().value,
        operationActive = operationActive,
        operationProgress = operationProgress,
        operationFinished = operationFinished,
        journeyVisible = cloudCopyState.active || localCopyState.active,
        succeeded = copySucceeded,
        failed = copyFailed,
        failureMessage = failureMessage,
        canRetry = cloudCopyState.finished && cloudCopyState.canRetry,
        destination = completedDestination,
        destinationLabel = completionDestinationLabel,
        openTarget = openTarget,
        hasFullStorageAccess = hasFullMediaAccess,
        isDestinationEnabled = { it.isCopyDestinationEnabled() },
        destinationStatus = {
            viewModel.cloudDestinationStatus(it, mediaList, cloudEnvironment)
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
                copyToAlbum(album)
            }
        },
        onNewAlbum = { newAlbum ->
            copyMedia(resolveNewAlbumMovePath(newAlbum, hasFullMediaAccess))
        },
        onRetry = viewModel::retryCloudCopy,
        onCloseResult = {
            viewModel.clearCloudCopyResult()
            viewModel.clearLocalCopyResult()
            scope.launch { sheetState.hide() }
        },
        onDone = { finishCopy() },
        onOpen = { openCompletion() },
        providerIcon = { providerType, tint, modifier ->
            ProviderBrandIcon(providerType = providerType, modifier = modifier, tint = tint)
        }
    )

    SecurityInfoSheet(sheetState = securitySheetState)
}
