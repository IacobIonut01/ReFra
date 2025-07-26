package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isLocalContent
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.CopyButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.EditButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.HideButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.MoveButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.OpenAsButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.RestoreButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton

@Composable
fun <T : Media> MediaViewSheetActions(
    media: T,
    albumsState: State<AlbumState>,
    vaults: State<VaultState>,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)?,
    currentVault: Vault?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Share Component
        ShareButton(media, followTheme = true, enabled = true)
        // Hide
        if (media.isLocalContent) {
            HideButton(
                media,
                vaults = vaults.value,
                followTheme = true,
                enabled = true
            )
        }
        // Restore
        if (media.isEncrypted && restoreMedia != null && currentVault != null) {
            RestoreButton(
                media,
                currentVault = currentVault,
                restoreMedia = restoreMedia,
                followTheme = true
            )
        }
        // Use as or Open With
        OpenAsButton(media, followTheme = true, enabled = true)
        if (albumsState.value.albums.isNotEmpty() && media.canMakeActions) {
            // Copy
            CopyButton(media, albumsState, followTheme = true, enabled = true)
            // Move
            MoveButton(media, albumsState, followTheme = true, enabled = true)
        }
        // Edit
        if (!media.isEncrypted) {
            EditButton(media, followTheme = true, enabled = true)
        }
    }
}