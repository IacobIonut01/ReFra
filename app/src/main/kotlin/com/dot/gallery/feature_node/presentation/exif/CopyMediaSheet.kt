package com.dot.gallery.feature_node.presentation.exif

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
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
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.toastError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Media> CopyMediaSheet(
    sheetState: AppBottomSheetState,
    albumsState: State<AlbumState>,
    mediaList: List<T>,
    onFinish: () -> Unit,
) {
    val handler = LocalMediaHandler.current
    val toastError = toastError()
    val scope = rememberCoroutineScope()
    var progress by remember(mediaList) { mutableFloatStateOf(0f) }
    var newPath by remember(mediaList) { mutableStateOf("") }

    val newAlbumSheetState = rememberAppBottomSheetState()
    val mutex = Mutex()

    fun copyMedia(path: String) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                newPath = path
                async {
                    mediaList.forEachIndexed { i, media ->
                        if (handler.copyMedia(media, newPath)) {
                            progress = (i + 1f) / mediaList.size
                        }
                    }
                }.await()
                newPath = ""
                if (progress == 1f) {
                    sheetState.hide()
                    progress = 0f
                    onFinish()
                } else {
                    toastError.show()
                    delay(1000)
                    sheetState.hide()
                    progress = 0f
                }
            }
        }
    }

    if (sheetState.isVisible) {
        val prop = remember(progress) {
            val shouldDismiss = progress == 0f
            ModalBottomSheetProperties(
                securePolicy = SecureFlagPolicy.Inherit,
                shouldDismissOnBackPress = shouldDismiss
            )
        }
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch {
                    if (progress == 0f) {
                        sheetState.hide()
                    } else {
                        sheetState.show()
                    }
                }
            },
            properties = prop,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {

            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.copy),
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
                        .align(Alignment.CenterHorizontally),
                    enter = Constants.Animation.enterAnimation,
                    exit = Constants.Animation.exitAnimation
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = {
                                progress
                            },
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.size(128.dp),
                        )
                        Text(text = "${(progress * 100).roundToInt()}%")
                    }
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
                            items = albumsState.value.albums,
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
                            val isStorageManager = Environment.isExternalStorageManager()
                            AlbumComponent(
                                album = item,
                                isEnabled = isStorageManager || (item.volume == mediaVolume
                                        && albumOwnership == "allow"
                                        && mediaOwnership == "allow"
                                        && (item.relativePath.contains("Pictures")
                                        || item.relativePath.contains("DCIM"))),
                                onItemClick = { album ->
                                    copyMedia(album.relativePath)
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
            if (Environment.isExternalStorageManager()) {
                copyMedia(newAlbum)
            } else {
                copyMedia("Pictures/$newAlbum")
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