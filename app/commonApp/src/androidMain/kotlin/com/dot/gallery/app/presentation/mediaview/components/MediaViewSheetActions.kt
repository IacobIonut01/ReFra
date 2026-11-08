/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.viewer.MediaViewSheetActions as FeatureMediaViewSheetActions
import com.dot.gallery.data.data_source.KeychainHolder
import com.dot.gallery.domain.util.MotionPhotoHelper
import com.dot.gallery.feature.albums.ui.collections.CollectionViewModel
import com.dot.gallery.feature.albums.ui.collections.AddToCollectionSheet
import com.dot.gallery.core.presentation.util.copyEncryptedMediaToClipboard
import com.dot.gallery.core.presentation.util.copyMediaToClipboard
import com.dot.gallery.app.presentation.util.launchEditImageIntent
import com.dot.gallery.app.presentation.util.launchEditIntent
import com.dot.gallery.app.presentation.util.launchOpenWithIntent
import com.dot.gallery.app.presentation.util.launchUseAsIntent
import com.dot.gallery.core.ui.util.rememberActivityResult
import com.dot.gallery.core.presentation.util.shareEncryptedMedia
import com.dot.gallery.core.presentation.util.shareMedia
import com.dot.gallery.feature.vault.VaultViewModel
import com.dot.gallery.feature.vault.ui.components.AddToVaultSheet
import com.dot.gallery.feature.vault.ui.components.SelectVaultSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isImage
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.app.presentation.exif.CopyMediaSheet
import com.dot.gallery.core.logging.logEvent
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.feature.viewer.isReadOnlyCloudMedia
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.album_cover_updated
import com.dot.gallery.generated.resources.download_complete
import com.dot.gallery.generated.resources.download_failed
import com.dot.gallery.generated.resources.downloading
import com.dot.gallery.generated.resources.motion_photo_export_failed
import com.dot.gallery.generated.resources.motion_photo_export_success
import com.dot.gallery.generated.resources.motion_photo_exporting
import com.dot.gallery.generated.resources.vault_create_first
import com.dot.gallery.generated.resources.vault_hide_in_progress

/**
 * App-side host for the viewer's action grid: keeps the legacy call signature and
 * wires every platform/app seam (intents, MediaHandler, vault + collection sheets,
 * ViewModels, toasts) into the shared `feature.viewer` implementation.
 */
@Composable
fun <T : Media> MediaViewSheetActions(
    media: T,
    albumsState: State<AlbumState>,
    vaults: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?,
    moveSheetState: AppBottomSheetState,
    isMotionPhoto: Boolean = false,
    onOpenFramePicker: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handler = LocalMediaHandler.current

    val defaultEditor by Settings.Misc.rememberDefaultImageEditor()
    val cloudSelectionViewModel = injectViewModel<CloudSelectionViewModel> {cloudSelectionViewModel}
    val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
    val providerSupportsFavorite = media.isCloud &&
        cloudSelectionViewModel.supportsFavorite(listOf(media))
    val providerSupportsTrash = media.isCloud &&
        cloudSelectionViewModel.supportsTrash(listOf(media))
    // Lazily create a single KeychainHolder for encrypted operations
    val keychainHolder = remember(currentVault) {
        if (currentVault != null) lazy { KeychainHolder(context) } else null
    }
    val mediaCtx = remember(media) {
        mapOf(
            "count" to "1",
            "bytes" to media.size.toString(),
            "type" to media.mimeType.substringBefore('/'),
            "cloud" to media.isCloud.toString(),
        )
    }
    val albumCoverUpdatedText = stringResource(Res.string.album_cover_updated)
    val downloadingText = stringResource(Res.string.downloading)
    val downloadCompleteText = stringResource(Res.string.download_complete)
    val downloadFailedText = stringResource(Res.string.download_failed)
    val exportingText = stringResource(Res.string.motion_photo_exporting)
    val exportSuccessText = stringResource(Res.string.motion_photo_export_success)
    val exportFailedText = stringResource(Res.string.motion_photo_export_failed)
    val createFirstText = stringResource(Res.string.vault_create_first)
    val hidingText = stringResource(Res.string.vault_hide_in_progress)

    FeatureMediaViewSheetActions(
        media = media,
        hasAlbums = albumsState.value.albums.isNotEmpty(),
        hasVaults = vaults.value.vaults.isNotEmpty(),
        restoreMedia = restoreMedia,
        currentVault = currentVault,
        moveSheetState = moveSheetState,
        isMotionPhoto = isMotionPhoto,
        onOpenFramePicker = onOpenFramePicker,
        cloudReadOnly = media.isReadOnlyCloudMedia(cloudSettingsByConfigId),
        providerSupportsFavorite = providerSupportsFavorite,
        providerSupportsTrash = providerSupportsTrash,
        platformSupportsFavorite = SdkCompat.supportsFavorites,
        onShare = {
            scope.launch {
                if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                    context.shareEncryptedMedia(media, currentVault, keychainHolder.value)
                } else {
                    context.shareMedia(media)
                }
            }
        },
        onCopyToClipboard = {
            scope.launch {
                if (media.isEncrypted && currentVault != null && keychainHolder != null) {
                    context.copyEncryptedMediaToClipboard(media, keychainHolder.value)
                } else {
                    context.copyMediaToClipboard(media)
                }
            }
        },
        onHideNoVaults = {
            Toast.makeText(context, createFirstText, Toast.LENGTH_SHORT).show()
        },
        onOpenAs = {
            scope.launch {
                if (media.isVideo) context.launchOpenWithIntent(media)
                else context.launchUseAsIntent(media)
            }
        },
        onSetAlbumCover = {
            scope.launch {
                handler.updateAlbumThumbnail(media.albumID, media.getUri())
                Toast.makeText(context, albumCoverUpdatedText, Toast.LENGTH_SHORT).show()
            }
        },
        onExportMotionVideo = {
            scope.launch {
                Toast.makeText(context, exportingText, Toast.LENGTH_SHORT).show()
                val saved = MotionPhotoHelper.saveVideoToGallery(
                    context = context,
                    uri = media.getUri(),
                    sourceLabel = media.label
                )
                Toast.makeText(
                    context,
                    if (saved != null) exportSuccessText else exportFailedText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onEdit = {
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
        onDownload = {
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
        logViewAction = { name -> logEvent("viewer.$name", mediaCtx, scope = "media-ops") },
        vaultHideSheets = { hideSheetState ->
            val vaultViewModel = injectViewModel<VaultViewModel> {vaultViewModel}
            var vaultEncryptBehavior by Settings.Vault.rememberVaultEncryptBehavior()
            val addToVaultSheetState = rememberAppBottomSheetState()
            var selectedVault by remember { mutableStateOf<Vault?>(null) }
            val hideResult = rememberActivityResult(onResultOk = {
                scope.launch { hideSheetState.hide() }
            })
            fun startHide(vault: Vault, deleteOriginals: Boolean) {
                Toast.makeText(context, hidingText, Toast.LENGTH_SHORT).show()
                if (deleteOriginals) {
                    vaultViewModel.hideAndRequestDeletion(vault, media.getUri())
                } else {
                    vaultViewModel.addMediaKeepOriginals(vault, listOf(media.getUri()))
                }
            }
            SelectVaultSheet(
                state = hideSheetState,
                vaultState = vaults.value,
                onVaultSelected = { vault ->
                    scope.launch {
                        when (vaultEncryptBehavior) {
                            Settings.Vault.ENCRYPT_DELETE -> startHide(vault, deleteOriginals = true)
                            Settings.Vault.ENCRYPT_KEEP -> startHide(vault, deleteOriginals = false)
                            else -> {
                                selectedVault = vault
                                addToVaultSheetState.show()
                            }
                        }
                    }
                }
            )
            AddToVaultSheet(
                state = addToVaultSheetState,
                onEncryptAndDelete = {
                    val vault = selectedVault ?: return@AddToVaultSheet
                    startHide(vault, deleteOriginals = true)
                },
                onEncryptAndKeep = {
                    val vault = selectedVault ?: return@AddToVaultSheet
                    startHide(vault, deleteOriginals = false)
                },
                onBehaviorChanged = { vaultEncryptBehavior = it }
            )
            LaunchedEffect(Unit) {
                vaultViewModel.userMessage.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            LaunchedEffect(Unit) {
                vaultViewModel.pendingDeletions.collect { leftovers ->
                    if (leftovers.isNotEmpty()) {
                        if (SdkCompat.supportsMediaStoreRequests) {
                            val intentSender = MediaStore.createDeleteRequest(
                                context.contentResolver,
                                leftovers
                            ).intentSender
                            val senderRequest = IntentSenderRequest.Builder(intentSender)
                                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                                .build()
                            hideResult.launch(senderRequest)
                        } else {
                            withContext(Dispatchers.IO) {
                                leftovers.forEach { uri ->
                                    runCatching {
                                        context.contentResolver.delete(uri, null, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        copySheet = { copySheetState ->
            CopyMediaSheet(
                sheetState = copySheetState,
                mediaList = listOf(media),
                albumsState = albumsState,
                onFinish = { }
            )
        },
        collectionSheet = { visible, onDismiss ->
            val collectionViewModel = injectViewModel<CollectionViewModel> {collectionViewModel}
            AddToCollectionSheet(
                visible = visible,
                collections = albumsState.value.collections,
                onDismiss = onDismiss,
                onCollectionSelected = { collectionId ->
                    collectionViewModel.addMediaToCollection(collectionId, media.id)
                },
                onCreateAndAdd = { name ->
                    collectionViewModel.createCollectionAndAddMedia(name, listOf(media.id))
                },
                collectionThumbnailModel = { thumbnailMediaId ->
                    ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        thumbnailMediaId
                    )
                }
            )
        },
    )
}
