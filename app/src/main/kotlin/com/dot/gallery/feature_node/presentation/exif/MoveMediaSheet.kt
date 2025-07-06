package com.dot.gallery.feature_node.presentation.exif

import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Constants
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.volume
import com.dot.gallery.feature_node.presentation.albums.components.AlbumComponent
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.toastError
import com.dot.gallery.feature_node.presentation.util.writeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Media> MoveMediaSheet(
    sheetState: AppBottomSheetState,
    mediaList: List<T>,
    albumState: State<AlbumState>,
    onFinish: () -> Unit,
) {
    val handler = LocalMediaHandler.current
    val context = LocalContext.current
    val toastError = toastError()

    val scope = rememberCoroutineScope()
    var progress by remember(mediaList) { mutableFloatStateOf(0f) }
    var newPath by remember(mediaList) { mutableStateOf("") }

    val newAlbumSheetState = rememberAppBottomSheetState()

    val request = rememberActivityResult {
        scope.launch {
            val done = async {
                mediaList.forEachIndexed { index, it ->
                    if (handler.moveMedia(media = it, newPath = newPath)) {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(newPath),
                            arrayOf(it.mimeType),
                            null
                        )
                        progress = index.toFloat() / mediaList.size
                    } else {
                        return@async false
                    }
                }
                return@async true
            }
            if (done.await()) {
                sheetState.hide()
                onFinish()
            } else {
                toastError.show()
                delay(1000)
                sheetState.hide()
            }
        }
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch {
                    sheetState.hide()
                }
            },
            dragHandle = { DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.move_to_another_album),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                )

                AnimatedVisibility(
                    visible = progress > 0f,
                    modifier = Modifier
                        .padding(32.dp)
                        .padding(bottom = 64.dp)
                        .navigationBarsPadding()
                        .size(128.dp)
                        .align(Alignment.CenterHorizontally),
                    enter = Constants.Animation.enterAnimation,
                    exit = Constants.Animation.exitAnimation
                ) {
                    CircularProgressIndicator(
                        progress = {
                            progress
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val albumSize by rememberAlbumGridSize()
                AnimatedVisibility(
                    visible = progress == 0f,
                    enter = Constants.Animation.enterAnimation,
                    exit = Constants.Animation.exitAnimation
                ) {
                    LazyVerticalGrid(
                        state = rememberLazyGridState(),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        columns = albumCellsList[albumSize],
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            bottom = WindowInsets.navigationBars.getBottom(
                                LocalDensity.current
                            ).dp
                        )
                    ) {
                        item {
                            AlbumComponent(
                                album = Album.NewAlbum,
                                isEnabled = true,
                                onItemClick = {
                                    scope.launch(Dispatchers.Main) {
                                        newAlbumSheetState.show()
                                    }
                                }
                            )
                        }
                        items(
                            items = albumState.value.albums,
                            key = { item -> item.toString() }
                        ) { item ->
                            val mediaVolume = (mediaList.firstOrNull()?.volume ?: item.volume)
                            val albumOwnership =
                                item.relativePath.substringBeforeLast("Android/media/", "allow")
                            val mediaOwnership =
                                mediaList.firstOrNull()?.relativePath?.substringBeforeLast(
                                    "Android/media/",
                                    "allow"
                                ) ?: albumOwnership
                            val mediaAlbum = mediaList.firstOrNull()?.albumLabel ?: item.label
                            val isStorageManager = Environment.isExternalStorageManager()
                            AlbumComponent(
                                album = item,
                                isEnabled = isStorageManager || (item.volume == mediaVolume
                                        && albumOwnership == "allow"
                                        && mediaOwnership == "allow"
                                        && item.label != mediaAlbum
                                        && (item.relativePath.contains("Pictures")
                                        || item.relativePath.contains("DCIM"))),
                                onItemClick = { album ->
                                    scope.launch(Dispatchers.Main) {
                                        newPath = album.relativePath
                                        request.launch(mediaList.writeRequest(context.contentResolver))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    AddAlbumSheet(
        sheetState = newAlbumSheetState,
        onFinish = { newAlbum ->
            scope.launch(Dispatchers.Main) {
                newPath = if (Environment.isExternalStorageManager()) newAlbum else "Pictures/$newAlbum"
                request.launch(mediaList.writeRequest(context.contentResolver))
            }
        },
        onCancel = {
            if (newAlbumSheetState.isVisible) {
                scope.launch(Dispatchers.Main) {
                    newAlbumSheetState.hide()
                }
            }
        }
    )
}

