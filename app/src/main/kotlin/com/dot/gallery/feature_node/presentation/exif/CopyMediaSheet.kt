package com.dot.gallery.feature_node.presentation.exif

import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dot.gallery.R
import com.dot.gallery.cloud.sync.CloudAlbumTransferMode
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.SecurityInfoSheet
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.albums.components.AlbumComponent
import com.dot.gallery.feature_node.presentation.mediaview.rememberMediaViewerNavigate
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.Screen
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
    val navigateFromViewer = rememberMediaViewerNavigate()
    val hasFullMediaAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Environment.isExternalStorageManager() || MediaStore.canManageMedia(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true
    val viewModel: CopyMediaViewModel = hiltViewModel()
    val progress by viewModel.progress.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val localCopyState by viewModel.localCopyState.collectAsState()
    val cloudCopyState by viewModel.cloudCopyState.collectAsState()
    val cloudEnvironment by viewModel.cloudEnvironment.collectAsState()
    val operationActive = localCopyState.active || cloudCopyState.active ||
        (isActive && localCopyState.workIds.isEmpty())
    val operationProgress = when {
        cloudCopyState.active -> cloudCopyState.progress
        localCopyState.active -> localCopyState.progress
        else -> progress
    }

    val newAlbumSheetState = rememberAppBottomSheetState()
    val securitySheetState = rememberAppBottomSheetState()
    var pendingLockedAlbum by remember { mutableStateOf<Album?>(null) }
    var operationDestination by remember { mutableStateOf<Album?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val mutex = Mutex()
    val cloudCopySucceeded = cloudCopyState.finished && cloudCopyState.succeeded
    val localCopySucceeded = localCopyState.finished && localCopyState.succeeded
    val copySucceeded = cloudCopySucceeded || localCopySucceeded
    val copyFailed = cloudCopyState.finished && !cloudCopyState.succeeded ||
        localCopyState.finished && !localCopyState.succeeded
    val completedDestinationId = cloudCopyState.destinationAlbumId
        ?: localCopyState.destinationAlbumId
    val completedDestination = completedDestinationId?.let { id ->
        albumsState.value.albums.firstOrNull { it.id == id }
    } ?: operationDestination
    val completionTotal = if (cloudCopyState.finished) cloudCopyState.total else localCopyState.total
    val completionTargetMediaId = if (cloudCopyState.finished) {
        cloudCopyState.targetMediaId
    } else {
        localCopyState.targetMediaId
    }
    val completionDestinationLabel = cloudCopyState.destinationLabel
        .ifBlank { localCopyState.destinationLabel }
    val openTarget = resolveTransferOpenTarget(
        itemCount = completionTotal,
        targetMediaId = completionTargetMediaId,
        destinationAlbumId = completedDestinationId,
        destinationLabel = completionDestinationLabel
    )

    fun Album.isCopyDestinationEnabled(): Boolean {
        val cloudStatus = viewModel.cloudDestinationStatus(this, mediaList, cloudEnvironment)
        if (cloudStatus != null) return cloudStatus == CloudCopyDestinationStatus.READY
        return absolutePath.isNotBlank() && isAlbumCopyDestinationEnabled(
            hasFullMediaAccess = hasFullMediaAccess,
            albumRelativePath = relativePath,
            isCloudAlbum = uri.scheme == "cloud" || relativePath.startsWith("cloud/"),
        )
    }

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

    fun copyToAlbum(album: Album) {
        operationDestination = album
        if (album.cloudIdentity != null) {
            viewModel.enqueueCloudCopy(mediaList, album)
        } else {
            viewModel.enqueueLocalCopy(mediaList, album) {
                scope.launch { sheetState.show() }
            }
        }
    }

    fun finishCopy(route: String? = null) {
        scope.launch {
            sheetState.hide()
            viewModel.clearCloudCopyResult()
            viewModel.clearLocalCopyResult()
            operationDestination = null
            onFinish()
            if (route != null) navigateFromViewer(route)
        }
    }

    fun openCompletion() {
        val target = openTarget ?: return
        val route = when (target.type) {
            TransferOpenTargetType.MEDIA -> Screen.MediaViewScreen.idAndAlbum(
                requireNotNull(target.mediaId),
                target.albumId
            )
            TransferOpenTargetType.ALBUM -> Screen.AlbumViewScreen.album(
                target.albumId,
                target.albumLabel
            )
        }
        finishCopy(route)
    }

    val biometricState = rememberBiometricState(
        title = stringResource(R.string.biometric_authentication),
        subtitle = stringResource(R.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingLockedAlbum?.let(::copyToAlbum)
            pendingLockedAlbum = null
        },
        onFailed = {
            pendingLockedAlbum = null
        }
    )

    LaunchedEffect(
        isActive,
        localCopyState.active,
        localCopyState.finished,
        cloudCopyState.active,
        cloudCopyState.finished
    ) {
        when {
            operationActive || localCopyState.finished || cloudCopyState.finished -> sheetState.show()
            else -> sheetState.hide()
        }
    }

    AnimatedVisibility(
        visible = sheetState.isVisible,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        val shouldDismiss by rememberedDerivedState(operationActive) {
            !operationActive
        }
        val prop = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = shouldDismiss
        )
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                when {
                    operationActive -> scope.launch { sheetState.show() }
                    cloudCopyState.finished || localCopyState.finished -> finishCopy()
                    else -> scope.launch { sheetState.hide() }
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
                AnimatedVisibility(
                    visible = !copySucceeded,
                    enter = enterAnimation,
                    exit = exitAnimation
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
                }

                AnimatedVisibility(
                    visible = !operationActive && !cloudCopyState.finished && !localCopyState.finished,
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
                    visible = operationActive,
                    modifier = Modifier
                        .padding(32.dp)
                        .align(Alignment.CenterHorizontally),
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    if (cloudCopyState.active || localCopyState.active) {
                        TransferJourneyPreview(
                            media = mediaList,
                            destination = completedDestination,
                            destinationLabel = completionDestinationLabel,
                            mode = CloudAlbumTransferMode.COPY,
                            progress = operationProgress
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { operationProgress },
                                strokeWidth = 4.dp,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(128.dp),
                            )
                            Text(text = "${(operationProgress * 100).roundToInt()}%")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = copySucceeded,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    TransferCompletionPanel(
                        media = mediaList,
                        destination = completedDestination,
                        destinationLabel = completionDestinationLabel,
                        mode = CloudAlbumTransferMode.COPY,
                        sourceRetained = false,
                        openLabel = stringResource(
                            if (openTarget?.type == TransferOpenTargetType.MEDIA) {
                                R.string.open_media
                            } else {
                                R.string.open_album
                            }
                        ),
                        onOpen = openTarget?.let { { openCompletion() } },
                        onDone = { finishCopy() }
                    )
                }

                AnimatedVisibility(
                    visible = copyFailed,
                    enter = enterAnimation,
                    exit = exitAnimation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_copy_incomplete),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (cloudCopyState.finished) {
                                cloudCopyState.message.ifBlank {
                                    pluralStringResource(
                                        R.plurals.cloud_copy_failed_count,
                                        cloudCopyState.failed,
                                        cloudCopyState.failed,
                                        cloudCopyState.total
                                    )
                                }
                            } else {
                                stringResource(R.string.transfer_copy_failed)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cloudCopyState.finished && cloudCopyState.canRetry) {
                                Button(onClick = viewModel::retryCloudCopy) {
                                    Text(stringResource(R.string.retry_failed_items))
                                }
                            }
                            TextButton(
                                onClick = {
                                    viewModel.clearCloudCopyResult()
                                    viewModel.clearLocalCopyResult()
                                    scope.launch { sheetState.hide() }
                                }
                            ) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }

                val albumSize by rememberAlbumGridSize()
                AnimatedVisibility(
                    visible = !operationActive && !cloudCopyState.finished && !localCopyState.finished,
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
                    val localSectionTitle = stringResource(R.string.transfer_on_device)
                    val unavailableSectionTitle = stringResource(R.string.transfer_unavailable_cloud)
                    val destinationSections = remember(
                        filteredUngroupedAlbums,
                        cloudEnvironment.accountsByConfigId,
                        localSectionTitle,
                        unavailableSectionTitle
                    ) {
                        buildTransferDestinationSections(
                            albums = filteredUngroupedAlbums,
                            accounts = cloudEnvironment.accountsByConfigId,
                            localTitle = localSectionTitle,
                            unavailableTitle = unavailableSectionTitle
                        )
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
                                val cloudStatus = viewModel.cloudDestinationStatus(
                                    item,
                                    mediaList,
                                    cloudEnvironment
                                )
                                val isEnabled = item.isCopyDestinationEnabled()
                                val disabledDescription = cloudStatus
                                    ?.takeUnless { it == CloudCopyDestinationStatus.READY }
                                    ?.let { stringResource(it.cloudDestinationMessageRes()) }
                                AlbumComponent(
                                    modifier = Modifier
                                        .animateItem()
                                        .semantics {
                                            if (!isEnabled && disabledDescription != null) {
                                                disabled()
                                                stateDescription = disabledDescription
                                            }
                                        },
                                    album = item,
                                    isEnabled = isEnabled,
                                    onItemClick = { album ->
                                        if (album.isLocked) {
                                            if (!biometricState.isSupported) {
                                                scope.launch { securitySheetState.show() }
                                            } else {
                                                pendingLockedAlbum = album
                                                biometricState.authenticate()
                                            }
                                        } else {
                                            copyToAlbum(album)
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

                            destinationSections.forEach { section ->
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "destination_header_${section.key}"
                                ) {
                                    TransferDestinationSectionHeader(section)
                                }
                                items(
                                    items = section.albums,
                                    key = { item -> "${section.key}_${item.id}" }
                                ) { item ->
                                    val cloudStatus = viewModel.cloudDestinationStatus(
                                        item,
                                        mediaList,
                                        cloudEnvironment
                                    )
                                    val isEnabled = item.isCopyDestinationEnabled()
                                    val disabledDescription = cloudStatus
                                        ?.takeUnless { it == CloudCopyDestinationStatus.READY }
                                        ?.let { stringResource(it.cloudDestinationMessageRes()) }
                                    AlbumComponent(
                                        modifier = Modifier.semantics {
                                            if (!isEnabled && disabledDescription != null) {
                                                disabled()
                                                stateDescription = disabledDescription
                                            }
                                        },
                                        album = item,
                                        isEnabled = isEnabled,
                                        onItemClick = { album ->
                                            if (album.isLocked) {
                                                if (!biometricState.isSupported) {
                                                    scope.launch { securitySheetState.show() }
                                                } else {
                                                    pendingLockedAlbum = album
                                                    biometricState.authenticate()
                                                }
                                            } else {
                                                copyToAlbum(album)
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

internal fun CloudCopyDestinationStatus.cloudDestinationMessageRes(): Int = when (this) {
    CloudCopyDestinationStatus.SOURCE_UNSUPPORTED -> R.string.cloud_copy_source_unsupported
    CloudCopyDestinationStatus.MOVE_UNSUPPORTED -> R.string.cloud_move_unsupported
    CloudCopyDestinationStatus.PROVIDER_UNSUPPORTED -> R.string.cloud_copy_provider_unsupported
    CloudCopyDestinationStatus.READ_ONLY -> R.string.cloud_copy_read_only
    CloudCopyDestinationStatus.OFFLINE -> R.string.cloud_copy_offline
    CloudCopyDestinationStatus.READY -> R.string.copy
    CloudCopyDestinationStatus.MISSING_IDENTITY,
    CloudCopyDestinationStatus.ACCOUNT_UNAVAILABLE -> R.string.cloud_copy_account_unavailable
}
