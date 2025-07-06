package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import kotlinx.coroutines.launch

@Composable
fun <T : Media> RestoreButton(
    media: T,
    currentVault: Vault,
    restoreMedia: (Vault, T, () -> Unit) -> Unit,
    followTheme: Boolean = false
) {
    val scope = rememberCoroutineScope()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Image,
        followTheme = followTheme,
        title = stringResource(R.string.restore)
    ) {
        scope.launch {
            restoreMedia(currentVault, it) {

            }
        }
    }
}