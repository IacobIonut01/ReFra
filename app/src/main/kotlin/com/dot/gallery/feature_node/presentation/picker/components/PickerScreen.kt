/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.presentation.picker.components

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.albums.components.AlbumImage
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewScreen
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.picker.AllowedMedia
import com.dot.gallery.feature_node.presentation.picker.PickerViewModel
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import com.dot.gallery.feature_node.presentation.util.selectedMedia
import com.dot.gallery.feature_node.presentation.util.toggleOrientation
import com.dot.gallery.ui.theme.Dimens
import kotlinx.coroutines.launch

private sealed class PickerNavState {
    data class Tabs(val tabIndex: Int) : PickerNavState()
    data class AlbumDetail(val album: Album) : PickerNavState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PickerScreen(
    title: String,
    allowedMedia: AllowedMedia,
    allowSelection: Boolean,
    onClose: () -> Unit,
    sendMediaAsResult: (List<Uri>) -> Unit,
    sendMediaAsMediaResult: (List<Media>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val mediaVM = hiltViewModel<PickerViewModel>().apply {
        this.allowedMedia = allowedMedia
    }
    val albumsState by mediaVM.albumsState.collectAsStateWithLifecycle()
    val metadataState = mediaVM.metadataState.collectAsStateWithLifecycle()
    val selector = LocalMediaSelector.current
    val selectedMedia = selector.selectedMedia.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var pendingLockedAlbum by remember { mutableStateOf<Album?>(null) }
    val securitySheetState = rememberAppBottomSheetState()
    val biometricState = rememberBiometricState(
        title = stringResource(R.string.biometric_authentication),
        subtitle = stringResource(R.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingLockedAlbum?.let { album ->
                selectedAlbum = album
            }
            pendingLockedAlbum = null
        },
        onFailed = {
            pendingLockedAlbum = null
        }
    )
    val onAlbumClickWithLock: (Album) -> Unit = remember(biometricState) {
        { album ->
            if (album.isLocked) {
                if (!biometricState.isSupported) {
                    scope.launch { securitySheetState.show() }
                } else {
                    pendingLockedAlbum = album
                    biometricState.authenticate()
                }
            } else {
                selectedAlbum = album
            }
        }
    }

    PickerSecurityInfoSheet(sheetState = securitySheetState)

    val navState: PickerNavState = if (selectedAlbum != null) {
        PickerNavState.AlbumDetail(selectedAlbum!!)
    } else {
        PickerNavState.Tabs(selectedTabIndex)
    }

    LaunchedEffect(selectedAlbum) {
        mediaVM.albumId = selectedAlbum?.id ?: -1L
    }

    val eventHandler = LocalEventHandler.current
    val activity = LocalActivity.current as? ComponentActivity
    val windowInsetsController = rememberWindowInsetsController()

    SharedTransitionLayout {
        AnimatedContent(
            targetState = showPreview,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 })
                    .togetherWith(fadeOut() + slideOutVertically { -it / 3 })
            },
            label = "previewTransition"
        ) { isPreview ->
            val previewScope = this@AnimatedContent
            if (!isPreview) {
                Scaffold(
                    topBar = {
                        AnimatedContent(
                            targetState = selectedAlbum != null,
                            transitionSpec = {
                                if (targetState) {
                                    (slideInVertically { -it } + fadeIn())
                                        .togetherWith(slideOutVertically { -it } + fadeOut())
                                } else {
                                    (slideInVertically { -it } + fadeIn())
                                        .togetherWith(slideOutVertically { -it } + fadeOut())
                                }.using(SizeTransform(clip = false))
                            },
                            label = "topBarAnimation"
                        ) { isAlbumDetail ->
                            if (isAlbumDetail && selectedAlbum != null) {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = selectedAlbum!!.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { selectedAlbum = null }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.navigate_up)
                                            )
                                        }
                                    },
                                    actions = {
                                        if (selectedMedia.value.isNotEmpty()) {
                                            TextButton(onClick = { showPreview = true }) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Visibility,
                                                    contentDescription = null
                                                )
                                                Text(
                                                    text = stringResource(R.string.preview),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            } else {
                                Column {
                                    TopAppBar(
                                        title = { Text(text = title) },
                                        navigationIcon = {
                                            IconButton(onClick = onClose) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Close,
                                                    contentDescription = stringResource(R.string.close)
                                                )
                                            }
                                        },
                                        actions = {
                                            if (selectedMedia.value.isNotEmpty()) {
                                                TextButton(onClick = { showPreview = true }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Visibility,
                                                        contentDescription = null
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.preview),
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 8.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            PillTab(
                                                selected = selectedTabIndex == 0,
                                                text = stringResource(R.string.timeline),
                                                onClick = { selectedTabIndex = 0 }
                                            )
                                            PillTab(
                                                selected = selectedTabIndex == 1,
                                                text = stringResource(R.string.albums),
                                                onClick = { selectedTabIndex = 1 }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = paddingValues.calculateTopPadding())
                    ) {
                        AnimatedContent(
                            targetState = navState,
                            transitionSpec = {
                                when (targetState) {
                                    is PickerNavState.AlbumDetail if initialState is PickerNavState.Tabs -> {
                                        (slideInHorizontally { it } + fadeIn())
                                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                                    }

                                    is PickerNavState.Tabs if initialState is PickerNavState.AlbumDetail -> {
                                        (slideInHorizontally { -it / 3 } + fadeIn())
                                            .togetherWith(slideOutHorizontally { it } + fadeOut())
                                    }

                                    is PickerNavState.Tabs if initialState is PickerNavState.Tabs -> {
                                        val targetTab = (targetState as PickerNavState.Tabs).tabIndex
                                        val initialTab = (initialState as PickerNavState.Tabs).tabIndex
                                        val direction = if (targetTab > initialTab) 1 else -1
                                        (slideInHorizontally { direction * it } + fadeIn())
                                            .togetherWith(slideOutHorizontally { -direction * it } + fadeOut())
                                    }

                                    else -> fadeIn() togetherWith fadeOut()
                                }.using(SizeTransform(clip = false))
                            },
                            label = "contentAnimation"
                        ) { state ->
                            val contentScope = this@AnimatedContent
                            when (state) {
                                is PickerNavState.AlbumDetail -> {
                                    PickerMediaScreen(
                                        mediaState = mediaVM.mediaState.value,
                                        metadataState = metadataState,
                                        allowSelection = allowSelection,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = previewScope,
                                    )
                                }
                                is PickerNavState.Tabs -> {
                                    when (state.tabIndex) {
                                        0 -> {
                                            PickerMediaScreen(
                                                mediaState = mediaVM.mediaState.value,
                                                metadataState = metadataState,
                                                allowSelection = allowSelection,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedVisibilityScope = previewScope,
                                            )
                                        }
                                        1 -> {
                                            PickerAlbumsGrid(
                                                albums = albumsState.albums.filter { it.id != -1L },
                                                onAlbumClick = onAlbumClickWithLock,
                                                sharedTransitionScope = this@SharedTransitionLayout,
                                                animatedContentScope = contentScope
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(32.dp),
                            visible = allowSelection || selectedMedia.value.isNotEmpty(),
                            enter = slideInVertically { it * 2 },
                            exit = slideOutVertically { it * 2 }
                        ) {
                            val enabled by rememberedDerivedState {
                                selectedMedia.value.isNotEmpty()
                            }
                            val containerColor by animateColorAsState(
                                targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                label = "containerColor"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "contentColor"
                            )
                            val mediaState by mediaVM.mediaState.value.collectAsStateWithLifecycle()
                            val selectedMediaList = mediaState.media.selectedMedia(selectedMedia)
                            ExtendedFloatingActionButton(
                                text = {
                                    if (allowSelection)
                                        Text(text = "Add (${selectedMedia.value.size})")
                                    else
                                        Text(text = "Add")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null
                                    )
                                },
                                containerColor = containerColor,
                                contentColor = contentColor,
                                expanded = true,
                                onClick = {
                                    if (enabled) {
                                        scope.launch {
                                            sendMediaAsResult(selectedMediaList.map { it.getUri() })
                                            sendMediaAsMediaResult(selectedMediaList)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .semantics {
                                        contentDescription = "Add media"
                                    }
                            )
                            BackHandler(selectedMedia.value.isNotEmpty()) {
                                selector.clearSelection()
                            }
                        }
                    }
                }
            } else {
                val collectedMediaState by mediaVM.mediaState.value.collectAsStateWithLifecycle()
                val previewMediaList = collectedMediaState.media.selectedMedia(selectedMedia)
                if (previewMediaList.isNotEmpty()) {
                    val previewMediaState = remember(previewMediaList) {
                        mutableStateOf(
                            MediaState(
                                media = previewMediaList,
                                isLoading = false
                            )
                        )
                    }
                    val emptyAlbumState = remember { mutableStateOf(AlbumState()) }
                    val emptyVaultState = remember { mutableStateOf(VaultState(isLoading = false)) }

                    val previousNavigateUp = remember { eventHandler.navigateUpAction }
                    val previousLightStatusBars = remember { windowInsetsController.isAppearanceLightStatusBars }
                    LaunchedEffect(Unit) {
                        windowInsetsController.isAppearanceLightStatusBars = false
                        eventHandler.navigateUpAction = {
                            windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                            showPreview = false
                        }
                    }

                    Scaffold { paddingValues ->
                        MediaViewScreen(
                            toggleRotate = { activity?.toggleOrientation() },
                            paddingValues = paddingValues,
                            isStandalone = true,
                            mediaId = previewMediaList.first().id,
                            mediaState = previewMediaState,
                            metadataState = metadataState,
                            albumsState = emptyAlbumState,
                            vaultState = emptyVaultState,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@AnimatedContent
                        )
                    }

                    BackHandler {
                        windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                        eventHandler.navigateUpAction = previousNavigateUp
                        showPreview = false
                    }
                } else {
                    LaunchedEffect(Unit) { showPreview = false }
                }
            }
        }
    }

    BackHandler(selectedAlbum != null) {
        selectedAlbum = null
    }
}

@Composable
private fun RowScope.PillTab(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "pillTabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillTabText"
    )
    Surface(
        modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSecurityInfoSheet(
    sheetState: AppBottomSheetState
) {
    val scope = rememberCoroutineScope()
    if (sheetState.isVisible) {
        ModalBottomSheet(
            sheetState = sheetState.sheetState,
            onDismissRequest = {
                scope.launch { sheetState.hide() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.locked),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    text = stringResource(R.string.locked_album_security_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PickerAlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope
) {
    with(sharedTransitionScope) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.Album()),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = albums,
                key = { it.toString() }
            ) { album ->
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "album_${album.id}"),
                                animatedVisibilityScope = animatedContentScope,
                                enter = fadeIn(),
                                exit = fadeOut()
                            )
                    ) {
                        AlbumImage(
                            album = album,
                            isEnabled = true,
                            onItemClick = onAlbumClick,
                            onItemLongClick = null
                        )
                    }
                    Text(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .padding(horizontal = 16.dp),
                        text = album.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
