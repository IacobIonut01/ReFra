package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.presentation.util.launchOpenWithIntent
import com.dot.gallery.feature_node.presentation.util.launchUseAsIntent
import kotlinx.coroutines.launch

@Composable
fun <T : Media> OpenAsButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (media.isVideo) {
        MediaViewButton(
            currentMedia = media,
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            followTheme = followTheme,
            title = stringResource(R.string.open_with),
            enabled = enabled
        ) {
            scope.launch { context.launchOpenWithIntent(it) }
        }
    } else {
        MediaViewButton(
            currentMedia = media,
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            followTheme = followTheme,
            title = stringResource(R.string.use_as),
            enabled = enabled
        ) {
            scope.launch { context.launchUseAsIntent(it) }
        }
    }
}