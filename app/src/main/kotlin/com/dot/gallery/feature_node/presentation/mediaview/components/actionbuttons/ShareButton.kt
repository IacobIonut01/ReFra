package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.shareEncryptedMedia
import com.dot.gallery.feature_node.presentation.util.shareMedia
import kotlinx.coroutines.launch

@Composable
fun <T : Media> ShareButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false,
    currentVault: Vault? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Share,
        followTheme = followTheme,
        title = stringResource(R.string.share),
        enabled = enabled
    ) {
        scope.launch {
            if (media.isEncrypted && currentVault != null) {
                // Share encrypted media by decrypting it first
                val keychainHolder = KeychainHolder(context)
                context.shareEncryptedMedia(
                    media = it, 
                    vault = currentVault, 
                    keychainHolder = keychainHolder
                )
            } else {
                // Share regular media normally
                context.shareMedia(media = it)
            }
        }
    }
}