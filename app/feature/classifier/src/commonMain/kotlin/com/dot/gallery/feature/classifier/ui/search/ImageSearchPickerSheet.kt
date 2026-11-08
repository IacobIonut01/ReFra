/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.image.thumbnail.LocalThumbnailMotion
import com.dot.gallery.core.image.thumbnail.rememberThumbnailMotionState
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MediaImage
import com.dot.gallery.core.presentation.components.MediaItemHeader
import com.dot.gallery.ui.theme.Dimens
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaItem
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.isHeaderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.albums
import com.dot.gallery.feature.classifier.generated.resources.back_cd
import com.dot.gallery.feature.classifier.generated.resources.close
import com.dot.gallery.feature.classifier.generated.resources.header_today
import com.dot.gallery.feature.classifier.generated.resources.header_yesterday
import com.dot.gallery.feature.classifier.generated.resources.no_media_found
import com.dot.gallery.feature.classifier.generated.resources.search_by_image
import com.dot.gallery.feature.classifier.generated.resources.search_by_image_subtitle
import com.dot.gallery.feature.classifier.generated.resources.timeline


/**
 * Bottom sheet media picker for selecting an image/video to use for visual search.
 * Modelled after the media picker with pill tabs (Timeline / Albums) and album drill-in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ImageSearchPickerSheet(
    onMediaSelected: (UriMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val distributor = LocalMediaDistributor.current

    val timelineState by distributor.timelineMediaFlow.collectAsStateWithLifecycle(
        context = Dispatchers.Default,
        initialValue = MediaState()
    )
    val albumsState by distributor.albumsFlow.collectAsStateWithLifecycle()
    val metadataState = distributor.metadataFlow.collectAsStateWithLifecycle(
        context = Dispatchers.Default,
        initialValue = MediaMetadataState()
    )

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }

    // Per-album media: observe the distributor's album timeline flow when an album is selected
    val albumMediaState by remember(selectedAlbum) {
        selectedAlbum?.let { distributor.albumTimelineMediaFlow(it.id) }
            ?: MutableStateFlow(MediaState<UriMedia>())
    }.collectAsStateWithLifecycle(initialValue = MediaState())

    val navState: ImagePickerNavState = if (selectedAlbum != null) {
        ImagePickerNavState.AlbumDetail(selectedAlbum!!)
    } else {
        ImagePickerNavState.Tabs(selectedTabIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                contentDescription = stringResource(Res.string.back_cd)
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
                                .padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.search_by_image),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.search_by_image_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(Res.string.close)
                                )
                            }
                        }
                        // Source chips
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PickerPillTab(
                                selected = selectedTabIndex == 0,
                                text = stringResource(Res.string.timeline),
                                onClick = { selectedTabIndex = 0 }
                            )
                            PickerPillTab(
                                selected = selectedTabIndex == 1,
                                text = stringResource(Res.string.albums),
                                onClick = { selectedTabIndex = 1 }
                            )
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

private sealed interface ImagePickerNavState {
    data class Tabs(val tabIndex: Int) : ImagePickerNavState
    data class AlbumDetail(val album: Album) : ImagePickerNavState
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
    val stringToday = stringResource(Res.string.header_today)
    val stringYesterday = stringResource(Res.string.header_yesterday)
    val gridState = rememberLazyGridState()

    if (mediaState.media.isEmpty() && !mediaState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.no_media_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // Cheap MOTION tier while the search results grid is flinging.
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
    // Album covers (RenderImage) load the cheap MOTION tier while scrolling.
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
                ImagePickerAlbumCell(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier.aspectRatio(1f)
                )
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

/**
 * Minimal album cover cell for the picker — a single-tap thumbnail rendered
 * through [LocalMediaImageRenderer] (the shared renderer handles signatures).
 */
@Composable
private fun ImagePickerAlbumCell(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRenderer = LocalMediaImageRenderer.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        imageRenderer.RenderImage(
            modifier = Modifier.fillMaxSize(),
            model = album.uri,
            contentScale = ContentScale.Crop,
            contentDescription = album.label,
            signature = album.uri
        )
    }
}

/** Source chip: primary container with 12 dp corners when selected, a pill otherwise. */
@Composable
private fun PickerPillTab(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "pillTabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillTabText"
    )
    val shape = if (selected) RoundedCornerShape(12.dp) else CircleShape
    Surface(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        shape = shape,
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 40.dp)) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
