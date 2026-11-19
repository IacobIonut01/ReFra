package com.dot.gallery.feature.viewer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenRotationAlt
import androidx.compose.runtime.Composable
import com.dot.gallery.domain.model.Media
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.rotate

/**
 * Rotate-the-current-page button; the rotation itself runs in the page that is shown, so
 * [onRotate] only asks for it.
 */
@Composable
fun <T : Media> RotateButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false,
    onRotate: () -> Unit
) {
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.Outlined.ScreenRotationAlt,
        followTheme = followTheme,
        title = stringResource(Res.string.rotate),
        enabled = enabled,
        onItemClick = { onRotate() }
    )
}
