package com.dot.gallery.feature_node.presentation.exif

import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dot.gallery.feature_node.domain.model.AlbumGroupWithAlbums
import com.dot.gallery.feature_node.presentation.albums.components.AlbumGroupComponent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.SecurityInfoSheet
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.volume
import com.dot.gallery.feature_node.presentation.albums.components.AlbumComponent
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import kotlinx.coroutines.Dispatchers
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasFullMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Environment.isExternalStorageManager() || MediaStore.canManageMedia(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
    val viewModel: CopyMediaViewModel = hiltViewModel()
    val progress by viewModel.progress.collectAsState()
    val isActive by viewModel.isActive.collectAsState()

    val newAlbumSheetState = rememberAppBottomSheetState()
    val securitySheetState = rememberAppBottomSheetState()
    var pendingLockedAlbumPath by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val mutex = Mutex()

    fun copyMedia(path: String) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                viewModel.enqueueCopy(*mediaList.map { it to path }.toTypedArray()) {
                    scope.launch {
                        sheetState.show()
                    }
                }
            }
        }
    }

    val biometricState = rememberBiometricState(
        title = stringResource(R.string.biometric_authentication),
        subtitle = stringResource(R.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingLockedAlbumPath?.let { path ->
                copyMedia(path)
            }
            pendingLockedAlbumPath = null
        },
        onFailed = {
            pendingLockedAlbumPath = null
        }
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    AnimatedVisibility(
        visible = sheetState.isVisible,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        val shouldDismiss by rememberedDerivedState(progress) {
            progress == 0f
        }
        val prop = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = shouldDismiss
        )
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
                    .imePadding()
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
                    visible = progress == 0f,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        placeholder = { Text(stringResource(R.string.search_albums)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = progress > 0f,
                    modifier = Modifier
                        .padding(32.dp)
                        .align(Alignment.CenterHorizontally),
                    enter = enterAnimation,
                    exit = exitAnimation
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
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    val allGroups = albumsState.value.albumGroups
                    val groupedAlbumIds = remember(allGroups) {
                        allGroups.flatMap { g -> g.albums.map { it.id } }.toSet()
                    }
                    val allUngroupedAlbums = remember(albumsState.value.albums, groupedAlbumIds) {
                        albumsState.value.albums.filter { it.id !in groupedAlbumIds }
                    }
                    var selectedGroup by remember { mutableStateOf<AlbumGroupWithAlbums?>(null) }
                    // Keep selectedGroup in sync with latest data
                    val liveSelectedGroup = selectedGroup?.let { sel ->
                        allGroups.find { it.group.id == sel.group.id }
                    }

                    val query = searchQuery.trim()
                    val filteredGroups = remember(allGroups, query) {
                        if (query.isEmpty()) allGroups
                        else allGroups.mapNotNull { g ->
                            val matched = g.albums.filter { it.label.contains(query, ignoreCase = true) }
                            if (matched.isNotEmpty()) g.copy(albums = matched)
                            else if (g.group.label.contains(query, ignoreCase = true)) g
                            else null
                        }
                    }
                    val filteredUngroupedAlbums = remember(allUngroupedAlbums, query) {
                        if (query.isEmpty()) allUngroupedAlbums
                        else allUngroupedAlbums.filter { it.label.contains(query, ignoreCase = true) }
                    }
                    val filteredGroupAlbums = remember(liveSelectedGroup, query) {
                        val albums = liveSelectedGroup?.albums ?: emptyList()
                        if (query.isEmpty()) albums
                        else albums.filter { it.label.contains(query, ignoreCase = true) }
                    }

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
                        if (liveSelectedGroup != null) {
                            // Group detail view
                            item(
                                span = { GridItemSpan(maxLineSpan) },
                                key = "group_back_header"
                            ) {
                                PickerGroupBackHeader(
                                    group = liveSelectedGroup,
                                    onBack = {
                                        selectedGroup = null
                                        searchQuery = ""
                                    }
                                )
                            }
                            items(
                                items = filteredGroupAlbums,
                                key = { item -> "group_album_${item.id}" }
                            ) { item ->
                                val mediaVolume = (mediaList.firstOrNull()?.volume ?: item.volume)
                                val albumOwnership =
                                    item.relativePath.substringBeforeLast("Android/media/", "allow")
                                val mediaOwnership =
                                    mediaList.firstOrNull()?.relativePath?.substringBeforeLast(
                                        "Android/media/",
                                        "allow"
                                    ) ?: albumOwnership
                                AlbumComponent(
                                    modifier = Modifier.animateItem(),
                                    album = item,
                                    isEnabled = hasFullMediaAccess || (item.volume == mediaVolume
                                            && albumOwnership == "allow"
                                            && mediaOwnership == "allow"),
                                    onItemClick = { album ->
                                        if (album.isLocked) {
                                            if (!biometricState.isSupported) {
                                                scope.launch { securitySheetState.show() }
                                            } else {
                                                pendingLockedAlbumPath = album.absolutePath
                                                biometricState.authenticate()
                                            }
                                        } else {
                                            copyMedia(album.absolutePath)
                                        }
                                    }
                                )
                            }
                        } else {
                            // Main view: New Album + groups + ungrouped albums
                            if (query.isEmpty()) {
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
                            }

                            items(
                                items = filteredGroups,
                                key = { group -> "group_${group.group.id}" }
                            ) { group ->
                                AlbumGroupComponent(
                                    modifier = Modifier.animateItem(),
                                    groupWithAlbums = group,
                                    onGroupClick = { selectedGroup = it }
                                )
                            }

                            items(
                                items = filteredUngroupedAlbums,
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
                                AlbumComponent(
                                    album = item,
                                    isEnabled = hasFullMediaAccess || (item.volume == mediaVolume
                                            && albumOwnership == "allow"
                                            && mediaOwnership == "allow"),
                                    onItemClick = { album ->
                                        if (album.isLocked) {
                                            if (!biometricState.isSupported) {
                                                scope.launch { securitySheetState.show() }
                                            } else {
                                                pendingLockedAlbumPath = album.absolutePath
                                                biometricState.authenticate()
                                            }
                                        } else {
                                            copyMedia(album.absolutePath)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SecurityInfoSheet(sheetState = securitySheetState)

    AddAlbumSheet(
        sheetState = newAlbumSheetState,
        onFinish = { newAlbum ->
            if (hasFullMediaAccess) {
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