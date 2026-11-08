/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.redesign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import com.dot.gallery.app.presentation.favorites.components.FavoritesSelectionActions
import androidx.compose.foundation.layout.PaddingValues
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.memories.MemoriesGrid
import com.dot.gallery.cloud.ui.memories.toEntry
import com.dot.gallery.cloud.ui.sharing.EditSharedLinkSheetContent
import com.dot.gallery.cloud.ui.sharing.SharedLinksFilter
import com.dot.gallery.cloud.ui.sharing.SharedLinksFilterRow
import com.dot.gallery.core.presentation.components.TrashDialogActions
import com.dot.gallery.core.presentation.components.TrashDialogHeader
import com.dot.gallery.domain.trash.TrashDialogAction
import com.dot.gallery.app.presentation.location.MapGridItem
import com.dot.gallery.app.presentation.location.MediaGridPanel
import com.dot.gallery.app.presentation.location.PlaceDetailHeader
import com.dot.gallery.app.presentation.location.mapPanelPlace
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.MockedMediaImageRenderer
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.UriMedia
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ConfirmationSheetContent
import com.dot.gallery.core.presentation.components.MoveToSectionOption
import com.dot.gallery.core.presentation.components.MoveToSectionSheetContent
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.OptionLayout
import com.dot.gallery.core.presentation.components.OptionLayoutStyle
import com.dot.gallery.core.presentation.components.OverwriteFallbackSheetContent
import com.dot.gallery.core.presentation.components.SheetNameInputContent
import com.dot.gallery.core.presentation.components.SheetSummaryTile
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.uri.parseUri
import com.dot.gallery.feature.albums.ui.collections.AddToCollectionOption
import com.dot.gallery.feature.albums.ui.collections.AddToCollectionSheetContent
import com.dot.gallery.feature.albums.ui.components.AddToGroupSheetContent
import com.dot.gallery.feature.albums.ui.components.AlbumGroupOption
import com.dot.gallery.feature.albums.ui.components.AlbumOptionSheetHeader
import com.dot.gallery.feature.albums.ui.components.AlbumOptionThumbnail
import com.dot.gallery.feature.exif.ui.SecurityInfoSheetContent
import com.dot.gallery.app.presentation.albumtimeline.components.SlideshowOptions
import com.dot.gallery.app.presentation.albumtimeline.components.SlideshowOptionsContent
import com.dot.gallery.core.presentation.components.FilterKind
import com.dot.gallery.domain.model.LastSort
import com.dot.gallery.domain.model.SlideshowTransition
import com.dot.gallery.domain.model.TimelineFilter
import com.dot.gallery.domain.util.OrderType
import com.dot.gallery.feature.exif.ui.CaptureDateEditorBody
import com.dot.gallery.feature.timeline.TimelineFilterSheetContent
import com.dot.gallery.feature.viewer.LocationDetailSheetContent
import com.dot.gallery.feature.viewer.SubtitleOption
import com.dot.gallery.feature.viewer.SubtitleSheetContent
import com.dot.gallery.app.presentation.cast.FCastDevice
import com.dot.gallery.app.presentation.cast.components.CastPermissionRowState
import com.dot.gallery.app.presentation.cast.components.CastPermissionsContent
import com.dot.gallery.app.presentation.cast.components.FCastDevicePickerContent
import com.dot.gallery.app.presentation.frameextract.FrameExportFormat
import com.dot.gallery.app.presentation.frameextract.components.FrameExportFormatContent
import com.dot.gallery.core.presentation.components.CutoutSaveChoiceContent
import com.dot.gallery.feature.editor.components.develop.RawExportSheetContent
import com.dot.gallery.feature.help.ui.SupportSheetContent
import com.dot.gallery.app.presentation.trashed.components.TrashFooterContent

/** Mocked entries for [RedesignGalleryTest], grouped like the FigCraft page. */
object RedesignGalleryEntries {

    private val album = Album(
        id = 1,
        label = "Banff 2026",
        uri = parseUri("content://media/external/images/media/1"),
        pathToThumbnail = "",
        relativePath = "Pictures/Banff 2026/",
        timestamp = 0,
        count = 42,
        size = 312L * 1024 * 1024,
        isLocked = true,
    )

    val sharedSheets: List<GalleryEntry> = listOf(
        GalleryEntry.Sheet("confirmation-move-album-to-trash") {
            ConfirmationSheetContent(
                title = "Move album to trash?",
                summary = "The album and everything in it moves to Trash. You can restore it for 30 days.",
                confirmText = "Move to trash",
                dismissText = "Cancel",
                icon = Icons.Outlined.Delete,
                tone = SheetTone.Error,
                confirmIcon = Icons.Outlined.Delete,
                onDismiss = {},
                onConfirm = {},
                details = {
                    SheetSummaryTile(
                        title = album.label,
                        summary = "42 items · 312 MB",
                        leading = { AlbumOptionThumbnail(album = album, size = 48.dp) },
                    )
                },
            )
        },
        GalleryEntry.Sheet("confirmation-plain") {
            ConfirmationSheetContent(
                title = "Restore 3 items?",
                summary = "They go back to the albums they came from.",
                confirmText = "Restore",
                dismissText = "Cancel",
                onDismiss = {},
                onConfirm = {},
            )
        },
        GalleryEntry.Sheet("overwrite-fallback") {
            OverwriteFallbackSheetContent(onCreateCopy = {}, onDismiss = {})
        },
        GalleryEntry.Sheet("security-info") {
            SecurityInfoSheetContent(onDismiss = {})
        },
        GalleryEntry.Sheet("album-options") {
            AlbumOptionSheetHeader(
                album = album,
                summary = "42 items · 312 MB",
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            )
            val errorContainer = MaterialTheme.colorScheme.errorContainer
            val onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
            val options = remember {
                mutableStateListOf(
                    OptionItem(icon = Icons.Outlined.Delete, text = "Move to trash", containerColor = errorContainer, contentColor = onErrorContainer, onClick = {}),
                    OptionItem(icon = Icons.Outlined.PushPin, text = "Pin", onClick = {}),
                    OptionItem(icon = Icons.Outlined.Wallpaper, text = "Change thumbnail", onClick = {}),
                    OptionItem(icon = Icons.Outlined.Lock, text = "Lock album", onClick = {}),
                    OptionItem(icon = Icons.Outlined.VisibilityOff, text = "Add to ignored", onClick = {}),
                    OptionItem(icon = Icons.Outlined.CreateNewFolder, text = "Add to group", onClick = {}),
                    OptionItem(icon = Icons.Outlined.Folder, text = "Move to section", onClick = {}),
                    OptionItem(icon = Icons.Outlined.AccountTree, text = "Merge subfolders", onClick = {}),
                    OptionItem(icon = Icons.Outlined.CollectionsBookmark, text = "Add to collection", enabled = false, onClick = {}),
                )
            }
            OptionLayout(optionList = options, style = OptionLayoutStyle.Grid)
        },
        GalleryEntry.Sheet("album-group-add") {
            AddToGroupSheetContent(
                groups = listOf(
                    AlbumGroupOption(1, "Travel", 6),
                    AlbumGroupOption(2, "Family", 4),
                    AlbumGroupOption(3, "Screenshots and downloads", 1),
                ),
                onGroup = {},
                onNewGroup = {},
            )
        },
        GalleryEntry.Sheet("move-to-section") {
            MoveToSectionSheetContent(
                sections = listOf(MoveToSectionOption(1, "Trips", 9), MoveToSectionOption(2, "Work", 2)),
                currentSectionId = null,
                onAutomatic = {},
                onSection = {},
            )
        },
        GalleryEntry.Sheet("section-name") {
            SheetNameInputContent(
                title = "Create section",
                body = "Sections add a heading to All albums. Move albums into it from each album's options.",
                value = "Trips",
                onValueChange = {},
                label = "Section name",
                confirmText = "Create section",
                dismissText = "Cancel",
                onDismiss = {},
                onConfirm = {},
                requestFocus = false,
            )
        },
        GalleryEntry.Sheet("add-to-collection") {
            AddToCollectionSheetContent(
                collections = listOf(
                    AddToCollectionOption(1, "Best of 2026", 48, null),
                    AddToCollectionOption(2, "Wallpapers", 21, null),
                    AddToCollectionOption(3, "Print later", 7, null),
                ),
                showCreateField = true,
                newCollectionName = "Road trip",
                onNewCollectionNameChange = {},
                onToggleCreate = {},
                onCreate = {},
                onCollection = {},
            )
        },
    )

    val timelineAndViewerSheets: List<GalleryEntry> = listOf(
        GalleryEntry.Sheet("timeline-filter") {
            TimelineFilterSheetContent(
                filter = TimelineFilter(),
                sort = LastSort(OrderType.Descending, FilterKind.DATE),
                availableYears = listOf(2026, 2025, 2024),
                availableAlbums = listOf(1L to "Camera", 2L to "Screenshots"),
                indexProgress = 42,
                onFilterChange = {},
                onSortChange = {},
                onReset = {},
                onApply = {},
            )
        },
        GalleryEntry.Sheet("slideshow-options") {
            SlideshowOptionsContent(
                options = SlideshowOptions(
                    intervalSeconds = 5,
                    transition = SlideshowTransition.FADE,
                    kenBurns = false,
                    randomOrder = false,
                    reverseOrder = false,
                    includeGifs = false,
                    includeVideos = true,
                    loop = true,
                ),
                canStart = true,
                onChange = {},
                onStart = {},
                onMoreSettings = {},
            )
        },
        GalleryEntry.Sheet("capture-date") {
            CaptureDateEditorBody(
                title = "Edit capture date",
                date = "Sep 18, 2026",
                time = "15:42",
                offset = "UTC−06:00",
                canSave = true,
                onDate = {},
                onTime = {},
                onDismiss = {},
                onSave = {},
            )
        },
        GalleryEntry.Sheet("location-detail") {
            LocationDetailSheetContent(
                address = "Moraine Lake Road, Banff, Alberta, Canada",
                mediaUri = null,
                mapPreview = null,
                onOpenMap = {},
                onShowInApp = {},
            )
        },
        GalleryEntry.Sheet("subtitles") {
            SubtitleSheetContent(
                tracks = listOf(
                    SubtitleOption("0:0", "English", "en", selected = true, external = false),
                    SubtitleOption("0:1", "Español", "es", selected = false, external = false),
                    SubtitleOption("1:0", "trip_notes.srt", null, selected = false, external = true),
                ),
                onSelect = {},
                onDisable = {},
                onAdd = {},
                onRemove = {},
            )
        },
    )

    val castAndExportSheets: List<GalleryEntry> = listOf(
        GalleryEntry.Sheet("cast-picker") {
            FCastDevicePickerContent(
                connectedDeviceName = null,
                isCasting = false,
                isDiscovering = true,
                isConnecting = false,
                devices = listOf(
                    FCastDevice(name = "Living room TV", host = "192.168.1.40", port = 46899),
                    FCastDevice(name = "Bedroom projector", host = "192.168.1.57", port = 46899),
                ),
                connectionError = null,
                onDeviceSelected = {},
                onCastMedia = {},
                onStopCasting = {},
                onDisconnect = {},
                onDismiss = {},
            )
        },
        GalleryEntry.Sheet("cast-connected") {
            FCastDevicePickerContent(
                connectedDeviceName = "Living room TV",
                isCasting = true,
                isDiscovering = false,
                isConnecting = false,
                devices = emptyList(),
                connectionError = null,
                onDeviceSelected = {},
                onCastMedia = {},
                onStopCasting = {},
                onDisconnect = {},
                onDismiss = {},
            )
        },
        GalleryEntry.Sheet("cast-permissions") {
            CastPermissionsContent(
                permissions = listOf(
                    CastPermissionRowState("Nearby devices", "Find receivers on your network", granted = false),
                    CastPermissionRowState("Local network", "Talk to TVs on this Wi-Fi", granted = true),
                ),
                confirmText = "Open settings",
                onDismiss = {},
                onConfirm = {},
            )
        },
        GalleryEntry.Sheet("frame-export") {
            FrameExportFormatContent(selected = FrameExportFormat.JPEG, onSelect = {}, onSave = {}, onDismiss = {})
        },
        GalleryEntry.Sheet("raw-export") {
            RawExportSheetContent(allowTiff = false, onFormatSelected = {})
        },
        GalleryEntry.Sheet("cutout-save") {
            CutoutSaveChoiceContent(onChoice = {}, onDismiss = {})
        },
        GalleryEntry.Sheet("support") {
            SupportSheetContent(
                options = listOf(
                    OptionItem(text = "PayPal", onClick = {}),
                    OptionItem(text = "Revolut", onClick = {}),
                    OptionItem(text = "Crypto", onClick = {}),
                ),
                showingAddresses = false,
            )
        },
    )

    val utilityScreens: List<GalleryEntry> = listOf(
        GalleryEntry.Screen("trash-header") {
            TrashFooterContent(hasMedia = true, onRestoreAll = {}, onDeleteAll = {})
        },
        GalleryEntry.Screen("trash-empty") { com.dot.gallery.core.presentation.components.EmptyTrash() },
        GalleryEntry.Screen("favorites-empty") { com.dot.gallery.core.presentation.components.EmptyFavorites() },
        GalleryEntry.Screen("free-up-space-summary") {
            com.dot.gallery.cloud.ui.space.FreeUpSpaceSummaryCard(
                description = "Backed-up photos and videos can leave this phone. They stay on your server.",
                itemCount = 1284,
                isScanning = true,
            )
        },
    )

    private fun media(id: Long, daysAgo: Int) = UriMedia(
        id = id,
        label = "IMG_$id.jpg",
        uri = parseUri("content://media/external/images/media/$id"),
        path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
        relativePath = "DCIM/Camera/",
        albumID = 1,
        albumLabel = "Camera",
        timestamp = 1_789_000_000L - daysAgo * 86_400L,
        fullDate = "",
        mimeType = "image/jpeg",
        favorite = 0,
        trashed = 0,
        size = 1_000_000,
    )

    private val banffGeo = (1L..7L).map { id ->
        GeoMedia(id, 51.42, -116.18, "Banff", "Alberta, Canada", media(id, daysAgo = if (id < 4) 2 else 140))
    }

    val placeScreens: List<GalleryEntry> = listOf(
        GalleryEntry.Screen("place-detail-header") {
            CompositionLocalProvider(LocalMediaImageRenderer provides MockedMediaImageRenderer) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    PlaceDetailHeader(
                        title = "Banff",
                        stats = "1,204 items · Alberta, Canada · 2019 – 2026",
                        coverModel = "content://media/external/images/media/1",
                        onMap = {},
                        onSlideshow = {},
                        onShare = {},
                    )
                }
            }
        },
        GalleryEntry.Screen("map-panel") {
            val items = remember {
                listOf(MapGridItem.Header("September 2026")) + banffGeo.take(3).map { MapGridItem.MediaCell(it) } +
                    MapGridItem.Header("April 2026") + banffGeo.drop(3).map { MapGridItem.MediaCell(it) }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = ExpressiveSheetShape) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DragHandle(modifier = Modifier.padding(top = 5.dp, bottom = 1.dp))
                    MediaGridPanel(
                        modifier = Modifier.heightIn(max = 520.dp),
                        gridState = rememberLazyGridState(),
                        gridItems = items,
                        place = mapPanelPlace(banffGeo.first(), banffGeo),
                        onOpenPlace = {},
                        selectedMediaId = 1,
                        onMediaClick = {},
                        onLocationClick = {},
                    )
                }
            }
        },
    )

    val selectionScreens: List<GalleryEntry> = listOf(
        GalleryEntry.Screen("favorites-selection-actions") {
            Surface(color = MaterialTheme.colorScheme.surface) {
                FavoritesSelectionActions(onShare = {}, onUnfavorite = {}, modifier = Modifier.padding(16.dp))
            }
        },
    )

    val linksAndTrash: List<GalleryEntry> = listOf(
        GalleryEntry.Screen("shared-links-filters") {
            Surface(color = MaterialTheme.colorScheme.surface) {
                SharedLinksFilterRow(
                    currentFilter = SharedLinksFilter.ALL,
                    onFilterSelected = {},
                    modifier = Modifier.padding(16.dp),
                )
            }
        },
        GalleryEntry.Sheet("shared-link-edit") {
            EditSharedLinkSheetContent(
                subtitle = "Public album · Banff 2026",
                description = "Trip photos for the family",
                onDescriptionChange = {},
                expiryDays = 30,
                onExpiryDays = {},
                passwordEnabled = true,
                onPasswordEnabled = {},
                password = "",
                onPasswordChange = {},
                showMetadata = false,
                onShowMetadata = {},
                allowDownload = true,
                onAllowDownload = {},
                allowUpload = false,
                onAllowUpload = {},
                canSave = true,
                onDismiss = {},
                onSave = {},
            )
        },
        GalleryEntry.Sheet("trash-dialog-delete") {
            TrashDialogHeader(action = TrashDialogAction.DELETE, confirmed = false, itemCount = 86)
            TrashDialogActions(action = TrashDialogAction.DELETE, confirmed = false, onCancel = {}, onConfirm = {})
        },
        GalleryEntry.Sheet("trash-dialog-restore") {
            TrashDialogHeader(action = TrashDialogAction.RESTORE, confirmed = false, itemCount = 3)
            TrashDialogActions(action = TrashDialogAction.RESTORE, confirmed = false, onCancel = {}, onConfirm = {})
        },
    )

    private val memoriesToday = java.time.LocalDate.of(2026, 10, 5)
    private val memoriesTodayMillis = memoriesToday.atTime(12, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun memory(id: Int, year: Int, onThisDay: Boolean, count: Int) = MemoryInfo(
        id = "memory-$id",
        type = "on_this_day",
        year = year,
        assetCount = count,
        providerType = ProviderType.IMMICH,
        serverConfigId = 1L,
        createdAt = if (onThisDay) memoriesTodayMillis else memoriesTodayMillis - id * 86_400_000L * 30,
        media = listOf(media(id.toLong(), daysAgo = id)),
    )

    val memoryScreens: List<GalleryEntry> = listOf(
        GalleryEntry.Screen("memories") {
            CompositionLocalProvider(LocalMediaImageRenderer provides MockedMediaImageRenderer) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.heightIn(max = 760.dp)) {
                    MemoriesGrid(
                        memories = listOf(
                            memory(1, 2023, onThisDay = true, count = 42),
                            memory(2, 2021, onThisDay = true, count = 18),
                            memory(3, 2019, onThisDay = true, count = 7),
                            memory(4, 2025, onThisDay = false, count = 112),
                            memory(5, 2024, onThisDay = false, count = 86),
                            memory(6, 2023, onThisDay = false, count = 64),
                            memory(7, 2022, onThisDay = false, count = 51),
                        ).map { it.toEntry(memoriesToday) },
                        today = memoriesToday,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    )
                }
            }
        },
    )

    val all: List<GalleryEntry>
        get() = sharedSheets + timelineAndViewerSheets + castAndExportSheets + utilityScreens + placeScreens +
            selectionScreens + linksAndTrash + memoryScreens
}
