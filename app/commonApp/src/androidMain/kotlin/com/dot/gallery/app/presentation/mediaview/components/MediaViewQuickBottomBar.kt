/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import android.media.MediaScannerConnection
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.viewer.MediaViewQuickBottomBar as FeatureMediaViewQuickBottomBar
import com.dot.gallery.domain.trash.TrashDialogAction
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.data.repository.MediaMutationResult
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderMoveViewModel
import com.dot.gallery.feature.vault.privatefolder.PrivateFolderViewModel
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.app.presentation.util.toastError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.app.presentation.util.launchEditIntent
import com.dot.gallery.feature.viewer.MediaViewButton
import com.dot.gallery.core.presentation.util.copyEncryptedMediaToClipboard
import com.dot.gallery.core.ui.util.launchWriteRequest
import com.dot.gallery.core.ui.util.writeRequest
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.presentation.util.shareMedia
import com.dot.gallery.app.presentation.util.launchEditImageIntent
import com.dot.gallery.core.presentation.util.copyMediaToClipboard
import com.dot.gallery.core.presentation.util.shareEncryptedMedia
import com.dot.gallery.app.presentation.util.launchOpenWithIntent
import com.dot.gallery.core.presentation.components.TrashDialog
import com.dot.gallery.app.presentation.util.launchUseAsIntent
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.viewer.isReadOnlyCloudMedia
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_delete_permanently
import com.dot.gallery.generated.resources.cloud_media_delete_failed
import com.dot.gallery.generated.resources.download_complete
import com.dot.gallery.generated.resources.download_failed
import com.dot.gallery.generated.resources.downloading
import com.dot.gallery.generated.resources.error_toast
import com.dot.gallery.generated.resources.trash_restore

/**
 * App-side host for the viewer quick-action pill: keeps the legacy call signature and
 * wires every platform/app seam (share/copy/edit/rename intents, trash + restore
 * dialogs, vault flows, private-folder SAF delete, optimistic favorite toggling)
 * into the shared `feature.viewer` implementation.
 */
@Composable
fun <T : Media> MediaViewQuickBottomBar(
    currentMedia: T?,
    showDeleteButton: Boolean,
    enabled: Boolean,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)?,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    isImageDark: Boolean = false,
    autoContrast: Boolean = false,
    // Visual-search slot: non-null target when the feature is enabled, resolved, and placed here.
    visualSearchTarget: VisualSearchTarget? = null,
    visualSearchTitle: String = "",
    onVisualSearch: (T) -> Unit = {},
    onTrashConfirmed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handler = LocalMediaHandler.current
    val distributor = LocalMediaDistributor.current
    val cloudSelectionViewModel = injectViewModel<CloudSelectionViewModel> {cloudSelectionViewModel}
    val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
    val isPrivateFolderItem =
        currentMedia?.albumID == PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID
    val providerSupportsTrash = currentMedia?.isCloud == true &&
        cloudSelectionViewModel.supportsTrash(listOf(currentMedia))
    val providerSupportsFavorite = currentMedia?.isCloud == true &&
        cloudSelectionViewModel.supportsFavorite(listOf(currentMedia))

    // Lazily create a single KeychainHolder for encrypted operations
    val keychainHolder = remember(currentVault) {
        if (currentVault != null) lazy { KeychainHolder(context) } else null
    }
    val defaultEditor by Settings.Misc.rememberDefaultImageEditor()

    val downloadingText = stringResource(Res.string.downloading)
    val downloadCompleteText = stringResource(Res.string.download_complete)
    val downloadFailedText = stringResource(Res.string.download_failed)
    val deletionError = toastError(
        stringResource(
            if (currentMedia?.isCloud == true) {
                Res.string.cloud_media_delete_failed
            } else {
                Res.string.error_toast
            }
        )
    )
    val renameErrorToast = toastError()

    // The heart flips immediately via the distributor's optimistic override; the
    // underlying source catches up asynchronously.
    val favoriteOverrides by distributor.favoriteOverrides.collectAsStateWithLifecycle()
    val isFavorite = currentMedia?.let { favoriteOverrides[it.id] ?: it.isFavorite } == true
    val favoriteRequest = rememberActivityResult(onResultCanceled = {
        currentMedia?.let { distributor.clearFavoriteOverride(it.id) }
    })

    // Rename passes through a MediaStore write request first; the label travels in
    // pendingRename because the permission result callback carries no arguments.
    var pendingRename by remember { mutableStateOf<Pair<T, String>?>(null) }
    fun performRename() {
        val (target, newLabel) = pendingRename ?: return
        pendingRename = null
        scope.launch(Dispatchers.IO) {
            val done = newLabel != target.label &&
                newLabel.isNotBlank() &&
                handler.renameMedia(target, newLabel)
            if (done) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.path),
                    arrayOf(target.mimeType),
                    null
                )
            } else {
                renameErrorToast.show()
            }
        }
    }
    val renameRequest = rememberActivityResult(onResultOk = { performRename() })

    FeatureMediaViewQuickBottomBar(
        currentMedia = currentMedia,
        enabled = enabled,
        cloudReadOnly = currentMedia?.isReadOnlyCloudMedia(cloudSettingsByConfigId) == true,
        providerSupportsFavorite = providerSupportsFavorite,
        providerSupportsTrash = providerSupportsTrash,
        platformSupportsFavorite = SdkCompat.supportsFavorites,
        showDeleteButton = showDeleteButton,
        isPrivateFolderItem = isPrivateFolderItem,
        restoreMedia = restoreMedia,
        deleteMedia = deleteMedia,
        currentVault = currentVault,
        isImageDark = isImageDark,
        autoContrast = autoContrast,
        visualSearchTarget = visualSearchTarget,
        visualSearchTitle = visualSearchTitle,
        onVisualSearch = onVisualSearch,
        isFavorite = isFavorite,
        onToggleFavorite = { media, target ->
            scope.launch {
                distributor.setFavoriteOverride(media.id, target)
                handler.toggleFavorite(
                    result = favoriteRequest,
                    mediaList = arrayListOf(media),
                    favorite = target
                )
            }
        },
        onShare = { media ->
            scope.launch {
                if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                    context.shareEncryptedMedia(media, currentVault, keychainHolder.value)
                } else {
                    context.shareMedia(media)
                }
            }
        },
        onCopyToClipboard = { media ->
            scope.launch {
                if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                    context.copyEncryptedMediaToClipboard(media, keychainHolder.value)
                } else {
                    context.copyMediaToClipboard(media)
                }
            }
        },
        onDownload = { media ->
            scope.launch {
                Toast.makeText(context, downloadingText, Toast.LENGTH_SHORT).show()
                val result = handler.downloadCloudMedia(listOf(media))
                val count = result.getOrDefault(0)
                if (count > 0) {
                    Toast.makeText(context, downloadCompleteText, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, downloadFailedText, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onEdit = { media ->
            if (media.isImage && defaultEditor != Settings.Misc.EDITOR_BUILTIN &&
                defaultEditor != Settings.Misc.EDITOR_STUDIO
            ) {
                try {
                    context.launchEditImageIntent(defaultEditor, media.getUri())
                } catch (_: Exception) {
                    scope.launch { context.launchEditIntent(media) }
                }
            } else {
                scope.launch { context.launchEditIntent(media) }
            }
        },
        onRename = { media, newLabel ->
            pendingRename = media to newLabel
            renameRequest.launchWriteRequest(media.writeRequest(context.contentResolver)) {
                performRename()
            }
        },
        onOpenAs = { media ->
            scope.launch {
                if (media.isVideo) context.launchOpenWithIntent(media)
                else context.launchUseAsIntent(media)
            }
        },
        trashSupported = SdkCompat.supportsTrash,
        cloudSupportsTrash = providerSupportsTrash,
        trashDialog = { state, action ->
            currentMedia?.let { media ->
                // A trashed item is arriving in the trash view (so the mark must not
                // hide it there); every other confirmation removes it from all views.
                val pendingScope = if (action == TrashDialogAction.TRASH) {
                    PendingRemovalScope.NON_TRASH
                } else {
                    PendingRemovalScope.EVERYWHERE
                }
                // Cloud copies backing up the viewed item, for the device/cloud/both picker.
                val timelineState by distributor.timelineMediaFlow.collectAsStateWithLifecycle(
                    initialValue = MediaState<UriMedia>()
                )
                // Ids resolved by the delete-scope picker (can include cloud backup copies),
                // stashed while a MediaStore request is in flight.
                var pendingRemovalIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                val markPendingRemoval = {
                    val ids = pendingRemovalIds.ifEmpty { setOf(media.id) }
                    distributor.markPendingRemoval(ids, pendingScope)
                    pendingRemovalIds = emptySet()
                    if (media.id in ids) onTrashConfirmed()
                }
                val result = rememberActivityResult(
                    onResultCanceled = {
                        pendingRemovalIds = emptySet()
                        scope.launch { state.hide() }
                    },
                    onResultOk = markPendingRemoval
                )
                TrashDialog(
                    appBottomSheetState = state,
                    data = listOf(media),
                    action = if (deleteMedia != null && currentVault != null) {
                        TrashDialogAction.DELETE
                    } else {
                        action
                    },
                    cloudBackups = timelineState.cloudBackups,
        supportsCloudTrash = { cloudSelectionViewModel.supportsTrash(it) },
    ) { items ->
                    if (deleteMedia != null && currentVault != null) {
                        if (items.isNotEmpty()) {
                            deleteMedia(currentVault, media) {}
                        }
                        distributor.markPendingRemoval(
                            items.map { m -> m.id }.toSet(),
                            PendingRemovalScope.EVERYWHERE
                        )
                        onTrashConfirmed()
                    } else {
                        val mutationResult = if (action == TrashDialogAction.TRASH) {
                            handler.trashMedia(result, items, true)
                        } else {
                            handler.deleteMedia(result, items)
                        }
                        when (mutationResult) {
                            MediaMutationResult.COMPLETED -> {
                                val removedIds = items.mapTo(HashSet()) { m -> m.id }
                                distributor.markPendingRemoval(removedIds, pendingScope)
                                // Only leave the viewer when the viewed item itself was
                                // removed — a cloud-only scope keeps the local copy.
                                if (media.id in removedIds) onTrashConfirmed()
                            }
                            MediaMutationResult.FAILED -> deletionError.show()
                            MediaMutationResult.REQUEST_LAUNCHED ->
                                pendingRemovalIds = items.mapTo(HashSet()) { m -> m.id }
                        }
                    }
                }
            }
        },
        privateFolderDeleteDialog = { state ->
            val privateFolderViewModel = injectViewModel<PrivateFolderMoveViewModel> {privateFolderMoveViewModel}
            TrashDialog(
                appBottomSheetState = state,
                data = listOfNotNull(currentMedia),
                action = TrashDialogAction.DELETE,
        supportsCloudTrash = { cloudSelectionViewModel.supportsTrash(it) },
    ) { items ->
                privateFolderViewModel.deleteFromPrivateFolder(items)
                onTrashConfirmed()
            }
        },
        trashedActions = { media, followTheme ->
            val restoreSheetState = rememberAppBottomSheetState()
            val deleteSheetState = rememberAppBottomSheetState()
            // Scope of the in-flight restore/delete request — restore only leaves the trash
            // view while a permanent delete leaves every view.
            var pendingRemovalScope by remember {
                mutableStateOf(PendingRemovalScope.EVERYWHERE)
            }
            val result = rememberActivityResult(onResultOk = {
                distributor.markPendingRemoval(setOf(media.id), pendingRemovalScope)
                onTrashConfirmed()
            })
            MediaViewButton(
                currentMedia = media,
                imageVector = Icons.Outlined.RestoreFromTrash,
                title = stringResource(Res.string.trash_restore),
                followTheme = followTheme,
                enabled = enabled
            ) {
                scope.launch { restoreSheetState.show() }
            }
            MediaViewButton(
                currentMedia = media,
                imageVector = Icons.Outlined.DeleteOutline,
                title = stringResource(Res.string.action_delete_permanently),
                enabled = enabled
            ) {
                scope.launch { deleteSheetState.show() }
            }
            TrashDialog(
                appBottomSheetState = restoreSheetState,
                data = listOf(media),
                action = TrashDialogAction.RESTORE,
        supportsCloudTrash = { cloudSelectionViewModel.supportsTrash(it) },
    ) {
                val mutationResult = handler.trashMedia(
                    result = result,
                    mediaList = it,
                    trash = false
                )
                when (mutationResult) {
                    MediaMutationResult.COMPLETED -> {
                        distributor.markPendingRemoval(
                            it.map { m -> m.id }.toSet(),
                            PendingRemovalScope.TRASH_ONLY
                        )
                        onTrashConfirmed()
                    }
                    MediaMutationResult.REQUEST_LAUNCHED ->
                        pendingRemovalScope = PendingRemovalScope.TRASH_ONLY
                    MediaMutationResult.FAILED -> Unit
                }
            }
            TrashDialog(
                appBottomSheetState = deleteSheetState,
                data = listOf(media),
                action = TrashDialogAction.DELETE,
        supportsCloudTrash = { cloudSelectionViewModel.supportsTrash(it) },
    ) {
                val mutationResult = handler.deleteMedia(result = result, mediaList = it)
                when (mutationResult) {
                    MediaMutationResult.COMPLETED -> {
                        distributor.markPendingRemoval(
                            it.map { m -> m.id }.toSet(),
                            PendingRemovalScope.EVERYWHERE
                        )
                        onTrashConfirmed()
                    }
                    MediaMutationResult.REQUEST_LAUNCHED ->
                        pendingRemovalScope = PendingRemovalScope.EVERYWHERE
                    MediaMutationResult.FAILED -> Unit
                }
            }
        },
    )
}
