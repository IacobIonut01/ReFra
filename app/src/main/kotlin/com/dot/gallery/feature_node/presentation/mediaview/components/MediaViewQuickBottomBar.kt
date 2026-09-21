package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.cloud.ui.CloudSelectionViewModel
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.PendingRemovalScope
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.VisualSearchTarget
import com.dot.gallery.feature_node.presentation.mediaview.icon
import com.dot.gallery.feature_node.presentation.mediaview.tintIcon
import com.dot.gallery.feature_node.presentation.mediaview.viewerActionCapabilities
import com.dot.gallery.core.Settings.Misc.rememberShowFavoriteButton
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaMutationResult
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.CopyToClipboardButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.DownloadButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.EditButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.FavoriteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.MediaViewButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.OpenAsButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.PrivateFolderDeleteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.RestoreButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.TrashButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.VisualSearchButton
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialog
import com.dot.gallery.feature_node.presentation.trashed.components.TrashDialogAction
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.privatefolder.PrivateFolderViewModel
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import kotlinx.coroutines.launch

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
    val handler = LocalMediaHandler.current
    val cloudSelectionViewModel = hiltViewModel<CloudSelectionViewModel>()
    val cloudSettingsByConfigId by CloudRuntimeSettings.settingsByConfigId.collectAsStateWithLifecycle()
    val allowBlur = LocalMediaViewerVisualPolicy.current.allowBlur
    val isVideo by rememberedDerivedState(currentMedia) {
        currentMedia?.isVideo ?: false
    }
    val isDarkTheme = com.dot.gallery.ui.theme.isDarkTheme()
    val followTheme = remember(allowBlur, isVideo, isDarkTheme, autoContrast, isImageDark) {
        if (autoContrast) !isImageDark
        else !allowBlur && !isVideo
    }
    val contentColor by animateColorAsState(
        targetValue = when {
            autoContrast -> if (isImageDark) Color.White else Color.Black
            followTheme -> MaterialTheme.colorScheme.onSurface
            else -> Color.White
        },
        label = "BottomBarContentColor"
    )
    val eventHandler = LocalEventHandler.current
    LaunchedEffect(followTheme) {
        eventHandler.setFollowTheme(followTheme)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
    if (currentMedia != null) {
        val isPrivateFolder = currentMedia.albumID == PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID
        val providerSupportsFavorite = currentMedia.isCloud &&
            cloudSelectionViewModel.supportsFavorite(listOf(currentMedia))
        val providerSupportsTrash = currentMedia.isCloud &&
            cloudSelectionViewModel.supportsTrash(listOf(currentMedia))
        val capabilities = currentMedia.viewerActionCapabilities(
            settingsByConfigId = cloudSettingsByConfigId,
            providerSupportsFavorite = providerSupportsFavorite,
            providerSupportsTrash = providerSupportsTrash,
            platformSupportsFavorite = SdkCompat.supportsFavorites,
            sourceAllowsDelete = showDeleteButton &&
                (currentMedia.canMakeActions || currentMedia.isCloud || isPrivateFolder ||
                    currentMedia.isEncrypted),
            vaultRestoreAvailable = currentVault != null && restoreMedia != null,
            vaultDeleteAvailable = currentVault != null && deleteMedia != null,
        )
        if (currentMedia.isTrashed) {
            val scope = rememberCoroutineScope()
            val distributor = LocalMediaDistributor.current
            val restoreSheetState = rememberAppBottomSheetState()
            val deleteSheetState = rememberAppBottomSheetState()
            // Scope of the in-flight restore/delete request — restore only leaves the trash
            // view while a permanent delete leaves every view.
            var pendingRemovalScope by remember {
                mutableStateOf(PendingRemovalScope.EVERYWHERE)
            }
            val result = rememberActivityResult(onResultOk = {
                distributor.markPendingRemoval(setOf(currentMedia.id), pendingRemovalScope)
                onTrashConfirmed()
            })
            if (capabilities.trash) {
                MediaViewButton(
                    currentMedia = currentMedia,
                    imageVector = Icons.Outlined.RestoreFromTrash,
                    title = stringResource(id = R.string.trash_restore),
                    followTheme = followTheme,
                    enabled = enabled
                ) {
                    scope.launch { restoreSheetState.show() }
                }
                MediaViewButton(
                    currentMedia = currentMedia,
                    imageVector = Icons.Outlined.DeleteOutline,
                    title = stringResource(id = R.string.action_delete_permanently),
                    enabled = enabled
                ) {
                    scope.launch { deleteSheetState.show() }
                }
                TrashDialog(
                    appBottomSheetState = restoreSheetState,
                    data = listOf(currentMedia),
                    action = TrashDialogAction.RESTORE
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
                    data = listOf(currentMedia),
                    action = TrashDialogAction.DELETE
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
            }
        } else {
            if (capabilities.share) {
                ShareButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme,
                    currentVault = currentVault
                )
            }
            if (capabilities.visualSearch && visualSearchTarget != null) {
                VisualSearchButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme,
                    icon = visualSearchTarget.icon,
                    tintIcon = visualSearchTarget.tintIcon,
                    title = visualSearchTitle,
                    onItemClick = onVisualSearch,
                )
            }
            if (capabilities.copyToClipboard) {
                CopyToClipboardButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme,
                    currentVault = currentVault
                )
            }
            // Favorite Component
            val showFavoriteButton by rememberShowFavoriteButton()
            if (showFavoriteButton && capabilities.favorite) {
                FavoriteButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            if (currentMedia.readUriOnly && capabilities.openExternally) {
                OpenAsButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            // Restore
            if (capabilities.restoreFromVault && restoreMedia != null && currentVault != null) {
                RestoreButton(
                    media = currentMedia,
                    currentVault = currentVault,
                    restoreMedia = restoreMedia,
                    followTheme = followTheme
                )
            }
            // Download (cloud only)
            if (capabilities.download) {
                DownloadButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            // Edit
            if (capabilities.edit) {
                EditButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            // Trash Component
            if (capabilities.trash) {
                // Private-folder items are SAF documents; deleting them via a
                // MediaStore request crashes (#1015). Route them through a
                // SAF-only delete instead of the regular TrashButton.
                if (currentMedia.albumID == PrivateFolderViewModel.PRIVATE_FOLDER_ALBUM_ID) {
                    PrivateFolderDeleteButton(
                        media = currentMedia,
                        enabled = enabled,
                        followTheme = followTheme,
                        onDeleted = onTrashConfirmed
                    )
                } else {
                    TrashButton(
                        media = currentMedia,
                        enabled = enabled,
                        deleteMedia = deleteMedia,
                        currentVault = currentVault,
                        followTheme = followTheme,
                        cloudSupportsTrash = currentMedia.isCloud &&
                            cloudSelectionViewModel.supportsTrash(listOf(currentMedia)),
                        onTrashConfirmed = onTrashConfirmed
                    )
                }
            }
        }
    }
    }
}