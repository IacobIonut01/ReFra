/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaItem
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.isHeaderKey
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.albums.components.AlbumImage
import com.dot.gallery.feature_node.presentation.common.components.MediaImage
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers

/**
 * Thumbnail chip shown inside the search input when a media is selected for visual search.
 * Displays the media thumbnail with rounded corners and an X button overlay.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageSearchChip(
    media: Media.UriMedia,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlideImage(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
                model = media.getUri(),
                contentDescription = stringResource(R.string.image_search_preview),
                contentScale = ContentScale.Crop,
                requestBuilderTransform = {
                    it.centerCrop().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                }
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(4.dp),
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.remove_image),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Quick preview dialog for the selected visual search media.
 * Shows a larger preview and offers "Remove" and "Pick another" actions.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageSearchPreviewDialog(
    media: Media.UriMedia,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onPickAnother: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.visual_search))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlideImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    model = media.getUri(),
                    contentDescription = stringResource(R.string.image_search_preview),
                    contentScale = ContentScale.Crop,
                    requestBuilderTransform = {
                        it.centerCrop().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    }
                )
                Text(
                    text = media.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onPickAnother) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.pick_another))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.remove_image))
                }
            }
        }
    )
}

private sealed class ImagePickerNavState {
    data class Tabs(val tabIndex: Int) : ImagePickerNavState()
    data class AlbumDetail(val album: Album) : ImagePickerNavState()
}

/**
 * Bottom sheet media picker for selecting an image/video to use for visual search.
 * Modelled after PickerScreen with pill tabs (Timeline / Albums) and album drill-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchPickerSheet(
    onMediaSelected: (Media.UriMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val distributor = LocalMediaDistributor.current

    val timelineState by distributor.timelineMediaFlow.collectAsStateWithLifecycle(
        context = Dispatchers.IO,
        initialValue = MediaState()
    )
    val albumsState by distributor.albumsFlow.collectAsStateWithLifecycle()
    val metadataState = distributor.metadataFlow.collectAsStateWithLifecycle(
        context = Dispatchers.IO,
        initialValue = MediaMetadataState()
    )

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }

    // Per-album media: observe the distributor's album timeline flow when an album is selected
    val albumMediaState by remember(selectedAlbum) {
        selectedAlbum?.let { distributor.albumTimelineMediaFlow(it.id) }
            ?: kotlinx.coroutines.flow.MutableStateFlow(MediaState<Media.UriMedia>())
    }.collectAsStateWithLifecycle(initialValue = MediaState())

    val navState: ImagePickerNavState = if (selectedAlbum != null) {
        ImagePickerNavState.AlbumDetail(selectedAlbum!!)
    } else {
        ImagePickerNavState.Tabs(selectedTabIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        dragHandle = { DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header: title + optional back arrow when inside album
            AnimatedContent(
                targetState = selectedAlbum != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pickerHeaderAnimation"
            ) { isAlbumDetail ->
                if (isAlbumDetail && selectedAlbum != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedAlbum = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd)
                            )
                        }
                        Text(
                            text = selectedAlbum!!.label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ImageSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.search_by_image),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        }
                        // Pill tabs
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
                                PickerPillTab(
                                    selected = selectedTabIndex == 0,
                                    text = stringResource(R.string.timeline),
                                    onClick = { selectedTabIndex = 0 }
                                )
                                PickerPillTab(
                                    selected = selectedTabIndex == 1,
                                    text = stringResource(R.string.albums),
                                    onClick = { selectedTabIndex = 1 }
                                )
                            }
                        }
                    }
                }
            }

            // Content: animated tab/album switching
            AnimatedContent(
                targetState = navState,
                transitionSpec = {
                    when (targetState) {
                        is ImagePickerNavState.AlbumDetail if initialState is ImagePickerNavState.Tabs -> {
                            (slideInHorizontally { it } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                        }
                        is ImagePickerNavState.Tabs if initialState is ImagePickerNavState.AlbumDetail -> {
                            (slideInHorizontally { -it / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                        is ImagePickerNavState.Tabs if initialState is ImagePickerNavState.Tabs -> {
                            val targetTab = (targetState as ImagePickerNavState.Tabs).tabIndex
                            val initialTab = (initialState as ImagePickerNavState.Tabs).tabIndex
                            val direction = if (targetTab > initialTab) 1 else -1
                            (slideInHorizontally { direction * it } + fadeIn())
                                .togetherWith(slideOutHorizontally { -direction * it } + fadeOut())
                        }
                        else -> fadeIn() togetherWith fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "pickerContentAnimation"
            ) { state ->
                when (state) {
                    is ImagePickerNavState.AlbumDetail -> {
                        ImagePickerMediaGrid(
                            mediaState = albumMediaState,
                            metadataState = metadataState,
                            onMediaClick = { onMediaSelected(it) }
                        )
                    }
                    is ImagePickerNavState.Tabs -> {
                        when (state.tabIndex) {
                            0 -> {
                                ImagePickerMediaGrid(
                                    mediaState = timelineState,
                                    metadataState = metadataState,
                                    onMediaClick = { onMediaSelected(it) }
                                )
                            }
                            1 -> {
                                ImagePickerAlbumsGrid(
                                    albums = albumsState.albums.filter { it.id != -1L },
                                    onAlbumClick = { album -> selectedAlbum = album }
                                )
                            }
                        }
                    }
                }
            }
        }

        BackHandler(selectedAlbum != null) {
            selectedAlbum = null
        }
    }
}

/**
 * Media grid for the image search picker.
 * Shows media items with date headers; single tap selects the media.
 */
@Composable
private fun <T : Media> ImagePickerMediaGrid(
    mediaState: MediaState<T>,
    metadataState: State<MediaMetadataState>,
    onMediaClick: (T) -> Unit,
) {
    val stringToday = stringResource(id = R.string.header_today)
    val stringYesterday = stringResource(id = R.string.header_yesterday)
    val gridState = rememberLazyGridState()

    if (mediaState.media.isEmpty() && !mediaState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_media_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // Phase 3 (#1076): cheap MOTION tier while the search results grid is flinging.
        val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })
        CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(Dimens.Photo()),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = mediaState.mappedMedia,
                key = { it.key },
                contentType = { it.key.startsWith("media_") },
                span = { item ->
                    GridItemSpan(if (item.key.isHeaderKey) maxLineSpan else 1)
                }
            ) { item ->
                when (item) {
                    is MediaItem.Header -> {
                        val title = item.text
                            .replace("Today", stringToday)
                            .replace("Yesterday", stringYesterday)
                        MediaItemHeader(
                            date = title,
                            showAsBig = item.key.contains("big"),
                            isChecked = remember { mutableStateOf(false) }
                        ) { }
                    }
                    is MediaItem.MediaViewItem -> {
                        MediaImage(
                            modifier = Modifier.animateItem(),
                            media = item.media,
                            metadataState = metadataState,
                            canClick = { true },
                            onMediaClick = { onMediaClick(it) },
                            onItemSelect = { onMediaClick(it) }
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Albums grid for the image search picker.
 */
@Composable
private fun ImagePickerAlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
) {
    // Phase 3 (#1076): album covers (RenderImage) load the cheap MOTION tier while scrolling.
    val gridState = rememberLazyGridState()
    val thumbnailMotion = rememberThumbnailMotionState({ gridState.isScrollInProgress })
    CompositionLocalProvider(LocalThumbnailMotion provides thumbnailMotion) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(Dimens.Album()),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = albums,
            key = { it.id }
        ) { album ->
            Column(
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(modifier = Modifier.aspectRatio(1f)) {
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

@Composable
private fun RowScope.PickerPillTab(
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
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
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
