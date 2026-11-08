package com.dot.gallery.feature.viewer
import com.dot.gallery.domain.trash.TrashDialogAction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.core.ui.mediaview.ViewerQuickAction
import com.dot.gallery.core.ui.mediaview.VisualSearchTarget
import com.dot.gallery.core.ui.mediaview.icon
import com.dot.gallery.core.ui.mediaview.rememberViewerActionBarConfig
import com.dot.gallery.core.ui.mediaview.tintIcon
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.canMakeActions
import com.dot.gallery.domain.util.isCloud
import com.dot.gallery.domain.util.isEncrypted
import com.dot.gallery.domain.util.isTrashed
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.domain.util.readUriOnly
import com.dot.gallery.ui.theme.isDarkTheme


/**
 * Pill row of quick actions under the viewer. Every app-side seam (trash/restore
 * dialogs, vault flows, share/edit/download intents) is a callback or slot —
 * capability inputs are resolved by the caller.
 */
@Composable
fun <T : Media> MediaViewQuickBottomBar(
    currentMedia: T?,
    enabled: Boolean,
    // Capability inputs resolved app-side (provider account settings, SDK gates).
    cloudReadOnly: Boolean = false,
    providerSupportsFavorite: Boolean = false,
    providerSupportsTrash: Boolean = false,
    platformSupportsFavorite: Boolean = false,
    // showDeleteButton keeps its call-site meaning: the source allows deletes at all.
    showDeleteButton: Boolean,
    isPrivateFolderItem: Boolean = false,
    // Vault flows; the buttons render only when these are non-null.
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    currentVault: Vault? = null,
    isImageDark: Boolean = false,
    autoContrast: Boolean = false,
    // Visual-search slot: non-null target when the feature is enabled, resolved, and placed here.
    visualSearchTarget: VisualSearchTarget? = null,
    visualSearchTitle: String = "",
    onVisualSearch: (T) -> Unit = {},
    // Action seams — fired on the app side where handlers/intents live.
    isFavorite: Boolean = false,
    onToggleFavorite: (T, Boolean) -> Unit = { _, _ -> },
    onShare: (T) -> Unit = {},
    onCopyToClipboard: (T) -> Unit = {},
    onDownload: (T) -> Unit = {},
    onEdit: (T) -> Unit = {},
    onRename: (T, String) -> Unit = { _, _ -> },
    onOpenAs: (T) -> Unit = {},
    // Platform trash support (SdkCompat on Android) and cloud provider trash support.
    trashSupported: Boolean = false,
    cloudSupportsTrash: Boolean = false,
    // Confirmation dialogs rendered by the app (TrashDialog / SAF private-folder delete).
    trashDialog: @Composable (state: AppBottomSheetState, action: TrashDialogAction) -> Unit =
        { _, _ -> },
    privateFolderDeleteDialog: @Composable (state: AppBottomSheetState) -> Unit = {},
    // The whole trashed-state action cluster (restore + delete-forever buttons and their
    // dialogs) is app-side: it needs the media handler, distributor and activity results.
    trashedActions: @Composable (media: T, followTheme: Boolean) -> Unit = { _, _ -> },
) {
    val visualPolicy = LocalMediaViewerVisualPolicy.current
    val isVideo by rememberedDerivedState(currentMedia) {
        currentMedia?.isVideo ?: false
    }
    val isDarkTheme = isDarkTheme()
    val followTheme = remember(visualPolicy, isVideo, isDarkTheme, autoContrast, isImageDark) {
        if (autoContrast) !isImageDark
        else !visualPolicy.usesDarkBackground(isDarkTheme) && !isVideo
    }
    val contentColor by animateColorAsState(
        targetValue = when {
            autoContrast -> if (isImageDark) Color.White else Color.Black
            followTheme -> MaterialTheme.colorScheme.onSurface
            else -> Color.White
        },
        // "Animate media items" off — chrome colour shifts snap.
        animationSpec = if (visualPolicy.animationsEnabled) spring() else snap(),
        label = "BottomBarContentColor"
    )
    val eventHandler = LocalEventHandler.current
    LaunchedEffect(followTheme) {
        eventHandler.setFollowTheme(followTheme)
    }
    // User-arranged quick actions; each one is still gated by the item's capabilities.
    val actionBarConfig by rememberViewerActionBarConfig()
    val quickActions = remember(actionBarConfig) { actionBarConfig.sanitized().actions }
    val showFavoriteButton by rememberPreference(SettingsKeys.showFavoriteButton)
    val trashEnabled by rememberPreference(SettingsKeys.trashEnabled)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
    if (currentMedia != null) {
        val capabilities = currentMedia.viewerActionCapabilities(
            cloudReadOnly = cloudReadOnly,
            providerSupportsFavorite = providerSupportsFavorite,
            providerSupportsTrash = providerSupportsTrash,
            platformSupportsFavorite = platformSupportsFavorite,
            sourceAllowsDelete = showDeleteButton &&
                (currentMedia.canMakeActions || currentMedia.isCloud || isPrivateFolderItem ||
                    currentMedia.isEncrypted),
            vaultRestoreAvailable = currentVault != null && restoreMedia != null,
            vaultDeleteAvailable = currentVault != null && deleteMedia != null,
        )
        if (currentMedia.isTrashed) {
            if (capabilities.trash) {
                trashedActions(currentMedia, followTheme)
            }
        } else {
            quickActions.forEach { action ->
                key(action) {
                    when (action) {
                        ViewerQuickAction.SHARE -> if (capabilities.share) {
                            ShareButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onShare = onShare
                            )
                        }
                        ViewerQuickAction.VISUAL_SEARCH -> if (
                            capabilities.visualSearch && visualSearchTarget != null
                        ) {
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
                        ViewerQuickAction.COPY_TO_CLIPBOARD -> if (capabilities.copyToClipboard) {
                            CopyToClipboardButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onCopyToClipboard = onCopyToClipboard
                            )
                        }
                        ViewerQuickAction.FAVORITE -> if (
                            showFavoriteButton && capabilities.favorite
                        ) {
                            FavoriteButton(
                                media = currentMedia,
                                isFavorite = isFavorite,
                                enabled = enabled,
                                followTheme = followTheme,
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                        ViewerQuickAction.OPEN_AS -> if (
                            currentMedia.readUriOnly && capabilities.openExternally
                        ) {
                            OpenAsButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onOpenAs = onOpenAs
                            )
                        }
                        ViewerQuickAction.RESTORE -> if (
                            capabilities.restoreFromVault && restoreMedia != null &&
                            currentVault != null
                        ) {
                            RestoreButton(
                                media = currentMedia,
                                currentVault = currentVault,
                                restoreMedia = restoreMedia,
                                followTheme = followTheme
                            )
                        }
                        ViewerQuickAction.DOWNLOAD -> if (capabilities.download) {
                            DownloadButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onDownload = onDownload
                            )
                        }
                        ViewerQuickAction.RENAME -> if (capabilities.rename) {
                            RenameButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onRename = onRename
                            )
                        }
                        ViewerQuickAction.EDIT -> if (capabilities.edit) {
                            EditButton(
                                media = currentMedia,
                                enabled = enabled,
                                followTheme = followTheme,
                                onEdit = onEdit
                            )
                        }
                        ViewerQuickAction.TRASH -> if (capabilities.trash) {
                            // Private-folder items are SAF documents; deleting them via a
                            // MediaStore request crashes. Route them through a
                            // SAF-only delete instead of the regular TrashButton.
                            if (isPrivateFolderItem) {
                                PrivateFolderDeleteButton(
                                    media = currentMedia,
                                    enabled = enabled,
                                    followTheme = followTheme,
                                    dialog = privateFolderDeleteDialog
                                )
                            } else {
                                TrashButton(
                                    media = currentMedia,
                                    enabled = enabled,
                                    followTheme = followTheme,
                                    trashEnabled = trashEnabled,
                                    trashSupported = trashSupported,
                                    cloudSupportsTrash = cloudSupportsTrash,
                                    dialog = trashDialog
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
