package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.presentation.util.launchEditImageIntent
import com.dot.gallery.feature_node.presentation.util.launchEditIntent

@Composable
fun <T : Media> EditButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val context = LocalContext.current
    val defaultEditor by Settings.Misc.rememberDefaultImageEditor()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.Edit,
        followTheme = followTheme,
        title = stringResource(R.string.edit),
        enabled = enabled
    ) {
        if (it.isImage && defaultEditor != Settings.Misc.EDITOR_BUILTIN) {
            try {
                context.launchEditImageIntent(defaultEditor, it.getUri())
            } catch (_: Exception) {
                context.launchEditIntent(it)
            }
        } else {
            context.launchEditIntent(it)
        }
    }
}