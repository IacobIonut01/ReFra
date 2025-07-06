package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.exif.MoveMediaSheet
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch

@Composable
fun <T : Media> MoveButton(
    media: T,
    albumsState: State<AlbumState>,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val moveSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    MediaViewButton(
        currentMedia = media,
        imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
        followTheme = followTheme,
        title = stringResource(R.string.move),
        enabled = enabled
    ) {
        scope.launch {
            moveSheetState.show()
        }
    }

    MoveMediaSheet(
        sheetState = moveSheetState,
        mediaList = listOf(media),
        albumState = albumsState,
        onFinish = { }
    )
}