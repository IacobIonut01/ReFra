package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.util.copyMediaToClipboard

@Composable
fun <T : Media> CopyToClipboardButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val context = LocalContext.current
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.ContentCopy,
        followTheme = followTheme,
        title = stringResource(R.string.copy_to_clipboard),
        enabled = enabled
    ) {
        context.copyMediaToClipboard(it)
    }
}
