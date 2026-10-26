/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.docs.DocsGalleryData
import com.dot.gallery.docs.LocalDocsGallerySession
import com.dot.gallery.docs.docsDrawableFor
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.uri.parseUri
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.core.metadata.MediaContainerFormat
import com.dot.gallery.core.metadata.MetadataDirectory
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataPolicy
import com.dot.gallery.core.metadata.MetadataTag
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.docs.DocsGalleryHost
import com.dot.gallery.docs.DocsGalleryPage
import com.dot.gallery.feature.exif.ui.CaptureDateCopyKind
import com.dot.gallery.feature.exif.ui.CaptureDateEditSheetContent
import com.dot.gallery.feature.exif.ui.MetadataEditSheetContent
import com.dot.gallery.feature.exif.ui.MetadataViewContent
import com.dot.gallery.feature.exif.ui.MetadataViewState
import com.dot.gallery.feature.exif.ui.TransferCompletionPanel
import com.dot.gallery.feature.exif.ui.TransferJourneyPreview
import com.dot.gallery.feature.exif.ui.TransferSheetMode
import com.dot.gallery.feature.viewer.MediaViewSheetActions
import com.dot.gallery.feature.viewer.MetadataRemovalSheet
import com.dot.gallery.feature.viewer.SlideshowControls
import com.dot.gallery.feature.ignored.ui.IgnoredContent
import com.dot.gallery.feature.ignored.ui.IgnoredState
import com.dot.gallery.feature.picker.ui.PickerActions
import com.dot.gallery.feature.picker.ui.PickerContent
import com.dot.gallery.feature.picker.ui.PickerState
import com.dot.gallery.feature.viewer.GroupMemberSelectionBar
import com.dot.gallery.feature.viewer.GroupMemberStrip
import com.dot.gallery.feature.viewer.MediaViewQuickBottomBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource

/** The real ignored-albums list over the session distributor — no ViewModel. */
@Composable
fun DocsIgnoredScene(state: String?) {
    val session = LocalDocsGallerySession.current
        ?: error("DocsIgnoredScene requires LocalDocsGallerySession")
    val albumsState = session.distributor.albumsFlow.collectAsState()
    val ignored = session.distributor.blacklistedAlbumsFlow.collectAsState()
    IgnoredContent(
        state = IgnoredState(albums = ignored.value),
        albumsState = albumsState,
        onAddClick = {},
        onAlbumClick = {},
    )
}

/** Real system-style photo picker sheet over the demo library. */
@Composable
fun DocsPickerScene(state: String?) {
    val session = LocalDocsGallerySession.current!!
    val selector = LocalMediaSelector.current
    if (state == "selected") {
        remember { selector.rawUpdateSelection(setOf(1L, 3L, 10L)) }
    }
    val albumsState = session.distributor.albumsFlow.collectAsState()
    val metadataState = session.distributor.metadataFlow.collectAsState(
        initial = MediaMetadataState()
    )
    val vaultState = session.distributor.vaultsMediaFlow.collectAsState()
    val mediaFlow = remember {
        MutableStateFlow<MediaState<Media>>(
            DocsGalleryData.mediaStateFor(DocsGalleryData.mediaItems())
        )
    }
    PickerContent(
        state = PickerState(
            title = "Select photos",
            allowSelection = true,
            albumsState = albumsState,
            metadataState = metadataState,
            privateFolderMediaState = MutableStateFlow(MediaState(isLoading = false)),
            cloudMediaState = MutableStateFlow(MediaState(isLoading = false)),
            vaultState = vaultState,
            cloudAvailable = false,
            mediaStateFor = { mediaFlow },
            vaultMediaStateFor = { MutableStateFlow(MediaState(isLoading = false)) },
        ),
        actions = PickerActions(
            onClose = {},
            onLockedAlbum = { _, _, _ -> },
            onPrivateFolder = { open -> open() },
            onVaultGate = { reveal -> reveal() },
            onVault = { _, open -> open() },
            onPickConfirmed = { _, _ -> true },
        ),
        albumImage = { album, onClick ->
            // Real album tiles — the same AlbumImage the app passes in.
            com.dot.gallery.feature.albums.ui.components.AlbumImage(
                album = album,
                isEnabled = true,
                onItemClick = onClick,
                onItemLongClick = null,
            )
        },
        previewContent = { _, _, _, _, _ -> },
    )
}

/** Real transfer preview cards — in-flight copy and the completion panel. */
@Composable
fun DocsTransferProgressScene(state: String?) {
    val media = remember {
        DocsGalleryData.mediaItems().filter { it.id in DocsGalleryData.tripMediaIds }
    }
    val destination = remember { DocsGalleryData.albums().first { it.id == DocsGalleryData.ALBUM_SCREENSHOTS } }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (state == "complete") {
            TransferCompletionPanel(
                media = media,
                destination = destination,
                destinationLabel = destination.label,
                mode = TransferSheetMode.COPY,
                sourceRetained = true,
                openLabel = "Open Screenshots",
                onOpen = null,
                onDone = {},
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TransferJourneyPreview(
                media = media,
                destination = destination,
                destinationLabel = destination.label,
                mode = TransferSheetMode.COPY,
                progress = 0.6f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Burst-group strip over two burst shots + the real selection bar. */
@Composable
fun DocsGroupMembersScene(state: String?) {
    val members = remember {
        DocsGalleryData.mediaItems().filter { it.id == 6L || it.id == 7L }
    }
    var selectedId by rememberSaveable { mutableStateOf(6L) }
    var multiSelect by rememberSaveable { mutableStateOf(state == "multi") }
    var multiIds by rememberSaveable { mutableStateOf(setOf<Long>(6L, 7L)) }
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (multiSelect) {
            GroupMemberSelectionBar(
                selectedCount = multiIds.size,
                totalCount = members.size,
                onClose = { multiSelect = false },
                onSelectAll = { multiIds = members.mapTo(HashSet()) { it.id } },
                onShare = {},
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
        GroupMemberStrip(
            members = members,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            modifier = Modifier.fillMaxWidth(),
            multiSelectMode = multiSelect,
            multiSelectedIds = multiIds,
            onEnterMultiSelect = { id ->
                multiSelect = true
                multiIds = setOf(id)
            },
            onToggleMultiSelect = { id ->
                multiIds = if (id in multiIds) multiIds - id else multiIds + id
            },
        )
    }
}

/**
 * The real viewer quick-action bar over media 1 — heart toggles in memory.
 * The copy swaps only the uri to a `content://media/…` docs path so the real
 * `canMakeActions` capability policy shows the full action row.
 */
@Composable
fun DocsQuickActionsScene(state: String?) {
    val session = LocalDocsGallerySession.current!!
    var media by remember {
        mutableStateOf(
            DocsGalleryData.mediaItems().first { it.id == 1L }
                .copy(uri = parseUri("content://media/docs-preview/images/media/1"))
        )
    }
    var favorite by rememberSaveable { mutableStateOf(media.favorite == 1) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaViewQuickBottomBar(
                currentMedia = media,
                enabled = true,
                showDeleteButton = true,
                platformSupportsFavorite = true,
                trashSupported = true,
                isFavorite = favorite,
                onToggleFavorite = { m, fav ->
                    favorite = fav
                    session.distributor.setFavoriteOverride(m.id, fav)
                },
                onRename = { _, name -> media = media.copy(label = name) },
                onShare = {},
                onEdit = {},
            )
        }
    }
}

/**
 * The real [MediaViewSheetActions] grid from the photo information panel over
 * a docs `content://media/…` item — the same capability rewrite
 * [DocsQuickActionsScene] uses — so every row a local photo offers renders:
 * share, clipboard, hide, Use as (its real album-cover/chooser sub-sheet),
 * copy/move, edit and Add to collection. Sheet slots keep their shared
 * defaults; nothing launches an intent or mutates the library.
 */
@Composable
fun DocsMoreActionsScene(state: String?) {
    val media = remember {
        DocsGalleryData.mediaItems().first { it.id == 1L }
            .copy(uri = parseUri("content://media/docs-preview/images/media/1"))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaViewSheetActions(
            media = media,
            hasAlbums = true,
            hasVaults = true,
            restoreMedia = null,
            currentVault = null,
            moveSheetState = rememberAppBottomSheetState(),
            platformSupportsFavorite = true,
        )
    }
}

// ── Metadata scenes ───────────────────────────────────────────────────────

private val docsExifDirectories = listOf(
    MetadataDirectory(
        name = "EXIF",
        tags = listOf(
            MetadataTag("Date/Time Original", "2026:01:15 15:42:30"),
            MetadataTag("Make", "Google"),
            MetadataTag("Model", "Pixel 9 Pro"),
            MetadataTag("F-Number", "f/1.7"),
            MetadataTag("ISO Speed Ratings", "125"),
            MetadataTag("Focal Length", "6.81 mm"),
            MetadataTag("Exposure Time", "1/120 sec"),
            MetadataTag("GPS Latitude", "51.3217"),
            MetadataTag("GPS Longitude", "-116.1860"),
            MetadataTag("Image Description", "Moraine Lake at sunrise"),
        ),
    ),
    MetadataDirectory(
        name = "File",
        tags = listOf(
            MetadataTag("File Name", "Lake_01.jpg"),
            MetadataTag("File Size", "2.4 MB"),
            MetadataTag("Image Width", "1280 pixels"),
            MetadataTag("Image Height", "853 pixels"),
        ),
    ),
)

private val docsMediaMetadata = MediaMetadata(
    mediaId = 1L,
    imageDescription = "Moraine Lake at sunrise",
    dateTimeOriginal = "2026:01:15 15:42:30",
    manufacturerName = "Google",
    modelName = "Pixel 9 Pro",
    aperture = "f/1.7",
    exposureTime = "1/120",
    iso = "125",
    focalLength = 6.81,
    gpsLatitude = 51.3217,
    gpsLongitude = -116.1860,
    gpsLocationName = "Moraine Lake",
    gpsLocationNameCountry = "Canada",
    gpsLocationNameCity = null,
    imageWidth = 1280,
    imageHeight = 853,
    imageResolutionX = 72.0,
    imageResolutionY = 72.0,
    resolutionUnit = 2,
    durationMs = null,
    videoWidth = null,
    videoHeight = null,
    frameRate = null,
    bitRate = null,
    isNightMode = false,
    isPanorama = false,
    isPhotosphere = false,
    isLongExposure = false,
    isMotionPhoto = false,
)

/** The real all-metadata screen over typed docs EXIF directories. */
@Composable
fun DocsMetadataFieldsScene(state: String?) {
    MetadataViewContent(
        state = MetadataViewState(
            isLoading = false,
            directories = docsExifDirectories,
        )
    )
}

/**
 * The real label/description edit sheet over the timeline surface. Saving
 * only mutates the in-memory fixture — nothing reaches MediaStore.
 */
@Composable
fun DocsMetadataEditScene(state: String?) {
    var media by remember {
        mutableStateOf(DocsGalleryData.mediaItems().first { it.id == 1L })
    }
    DocsGalleryHost(DocsGalleryPage.TIMELINE, null)
    val sheetState = rememberAppBottomSheetState()
    LaunchedEffect(Unit) { sheetState.show() }
    MetadataEditSheetContent(
        state = sheetState,
        media = media,
        metadata = docsMediaMetadata,
        onSave = { newLabel, _ -> media = media.copy(label = newLabel) },
        onCancel = {},
    )
}

/**
 * The real capture-date sheet. `copy-required` poses the dated-copy dialog a
 * PNG save would raise (no embedded capture date to rewrite in place).
 */
@Composable
fun DocsCaptureDateScene(state: String?) {
    val media = remember {
        DocsGalleryData.mediaItems().first {
            if (state == "copy-required") it.id == 8L else it.id == 1L
        }
    }
    DocsGalleryHost(DocsGalleryPage.TIMELINE, null)
    val sheetState = rememberAppBottomSheetState()
    LaunchedEffect(Unit) { sheetState.show() }
    var copyKind by remember {
        mutableStateOf(if (state == "copy-required") CaptureDateCopyKind.SAFE_COPY else null)
    }
    CaptureDateEditSheetContent(
        state = sheetState,
        media = media,
        busy = false,
        is24Hour = true,
        copyKind = copyKind,
        copyCreated = false,
        canTrashOriginal = true,
        onDismiss = {},
        onSave = {},
        onCreateCopy = {},
        onDismissCopy = { copyKind = null },
        onTrashOriginal = {},
        onKeepBoth = {},
    )
}

/**
 * The real metadata-removal sheet. `location-supported` shows a JPEG with
 * location, dates, device and camera metadata found; `unsupported-format`
 * shows a container that cannot be cleaned.
 */
@Composable
fun DocsMetadataRemovalScene(state: String?) {
    DocsGalleryHost(DocsGalleryPage.TIMELINE, null)
    val sheetState = rememberAppBottomSheetState()
    LaunchedEffect(Unit) { sheetState.show() }
    MetadataRemovalSheet(
        state = sheetState,
        capability = if (state == "unsupported-format") {
            SanitizationCapability(
                format = MediaContainerFormat.MP4,
                removableCategories = emptySet(),
                limitation = "This video container is not supported.",
            )
        } else {
            SanitizationCapability(
                format = MediaContainerFormat.JPEG,
                removableCategories = MetadataPolicy.ALL,
                presentCategories = setOf(
                    MetadataCategory.LOCATION,
                    MetadataCategory.TIMESTAMPS,
                    MetadataCategory.IDENTITY_DEVICE,
                    MetadataCategory.CAPTURE_SETTINGS,
                ),
                previews = mapOf(
                    MetadataCategory.LOCATION to "44.4268, 26.1025",
                    MetadataCategory.TIMESTAMPS to "2026:05:14 18:32:07",
                    MetadataCategory.IDENTITY_DEVICE to "Google Pixel 6",
                    MetadataCategory.CAPTURE_SETTINGS to "f/1.9 · 1/120 s · ISO 64",
                ),
            )
        },
        isBusy = false,
        canReplaceOriginal = true,
        onConfirm = { _, _ -> },
    )
}

/** The real slideshow control pill — play/pause flips in memory. */
@Composable
fun DocsSlideshowControlsScene(state: String?) {
    var paused by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableStateOf(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SlideshowControls(
            isPaused = paused,
            onPlayPause = { paused = !paused },
            onPrevious = { position -= 1 },
            onNext = { position += 1 },
            onExit = {},
        )
    }
}
