package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isTrashed
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.EditButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.FavoriteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.MediaViewButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.OpenAsButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.RestoreButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.TrashButton
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import kotlinx.coroutines.launch

@Composable
fun <T : Media> MediaViewQuickBottomBar(
    currentMedia: T?,
    showDeleteButton: Boolean,
    enabled: Boolean,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)?,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?
) {
    val handler = LocalMediaHandler.current
    val allowBlur by rememberAllowBlur()
    val isVideo by rememberedDerivedState(currentMedia) {
        currentMedia?.isVideo ?: false
    }
    val followTheme = remember(allowBlur, isVideo) { !allowBlur && !isVideo }
    val eventHandler = LocalEventHandler.current
    LaunchedEffect(followTheme) {
        eventHandler.setFollowTheme(followTheme)
    }
    if (currentMedia != null) {
        if (currentMedia.isTrashed) {
            val scope = rememberCoroutineScope()
            val result = rememberActivityResult()
            // Restore Component
            MediaViewButton(
                currentMedia = currentMedia,
                imageVector = Icons.Outlined.RestoreFromTrash,
                title = stringResource(id = R.string.trash_restore),
                followTheme = followTheme,
                enabled = enabled
            ) {
                scope.launch {
                    handler.trashMedia(result = result, arrayListOf(it), trash = false)
                }
            }
            // Delete Component
            MediaViewButton(
                currentMedia = currentMedia,
                imageVector = Icons.Outlined.DeleteOutline,
                title = stringResource(id = R.string.trash_delete),
                enabled = enabled
            ) {
                scope.launch {
                    handler.deleteMedia(result = result, arrayListOf(it))
                }
            }
        } else {
            // Share Component
            ShareButton(
                media = currentMedia,
                enabled = enabled,
                followTheme = followTheme
            )
            // Favorite Component
            if (currentMedia.canMakeActions) {
                FavoriteButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            if (currentMedia.readUriOnly) {
                OpenAsButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            // Restore
            if (currentMedia.isEncrypted && restoreMedia != null && currentVault != null) {
                RestoreButton(
                    media = currentMedia,
                    currentVault = currentVault,
                    restoreMedia = restoreMedia,
                    followTheme = followTheme
                )
            }
            // Edit
            if (!currentMedia.isEncrypted) {
                EditButton(
                    media = currentMedia,
                    enabled = enabled,
                    followTheme = followTheme
                )
            }
            // Trash Component
            if (showDeleteButton) {
                TrashButton(
                    media = currentMedia,
                    enabled = enabled,
                    deleteMedia = deleteMedia,
                    currentVault = currentVault,
                    followTheme = followTheme
                )
            }
        }
    }
}