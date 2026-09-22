package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.presentation.util.launchEditImageIntent
import com.dot.gallery.feature_node.presentation.util.launchEditIntent
import kotlinx.coroutines.launch

@Composable
fun <T : Media> EditButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultEditor by Settings.Misc.rememberDefaultImageEditor()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Tune,
        followTheme = followTheme,
        title = stringResource(R.string.edit),
        enabled = enabled
    ) {
        if (it.isImage && defaultEditor != Settings.Misc.EDITOR_BUILTIN) {
            try {
                context.launchEditImageIntent(defaultEditor, it.getUri())
            } catch (_: Exception) {
                scope.launch { context.launchEditIntent(it) }
            }
        } else {
            scope.launch { context.launchEditIntent(it) }
        }
    }
}