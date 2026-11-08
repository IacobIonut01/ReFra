/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.location.MapLocationsContent
import com.dot.gallery.app.presentation.settings.subsettings.EditBackupsViewerContent
import com.dot.gallery.cloud.core.CloudAlbumIdentity
import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.cloudAlbumId
import com.dot.gallery.cloud.ui.archive.CloudArchiveScreen
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.people.PeopleListActions
import com.dot.gallery.cloud.ui.people.PeopleListContent
import com.dot.gallery.cloud.ui.people.PeopleListUiState
import com.dot.gallery.cloud.ui.people.PeopleScanUiState
import com.dot.gallery.cloud.ui.people.PersonDetailUiState
import com.dot.gallery.cloud.ui.people.PersonHeaderContent
import com.dot.gallery.core.Constants
import com.dot.gallery.core.EditBackupManager
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.ml.CutoutHelper
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.core.logging.printError
import com.dot.gallery.core.ml.installBundledModel
import com.dot.gallery.core.presentation.components.SelectionAddon
import com.dot.gallery.core.presentation.components.SelectAllAddon
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.docs.scenes.DocsSharedScopes
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.domain.model.MediaState
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.editor.adjustments.varfilter.Saturation
import com.dot.gallery.feature.exif.ui.CloudCopyDestinationStatus
import com.dot.gallery.feature.exif.ui.CloudDestinationAccount
import com.dot.gallery.feature.exif.ui.CopyMediaSheetContent
import com.dot.gallery.feature.exif.ui.TransferOpenTarget
import com.dot.gallery.feature.exif.ui.TransferOpenTargetType
import com.dot.gallery.feature.settings.subsettings.EditBackupsViewModel
import com.dot.gallery.feature.viewer.components.media.CutoutController
import com.dot.gallery.feature.viewer.components.media.CutoutControlsBar
import com.dot.gallery.feature.viewer.components.media.CutoutState
import com.dot.gallery.feature.viewer.components.media.ZoomablePagerImage
import dev.chrisbanes.haze.hazeSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explore-area scenes: the real on-device people list + person controls, the
 * edit-backups comparison viewer, the MapLibre locations map, the cloud album
 * browser + archive, the copy destination sheet, the focused selection
 * controls, and a real MobileSAM subject cutout. All run the production
 * composables over bundled docs fixtures — the people rows are illustrative
 * [PersonInfo] records (no face model ever runs), the backup pair is a genuine
 * original + a real `Saturation(0f)`-edited JPEG, the map renders live
 * OpenFreeMap tiles gated on MapLibre's fully-rendered callback, and the
 * cutout runs the two bundled SAM models for real.
 */
val exploreScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "people-browser",
        title = "People",
        pathTrace = listOf("Library", "People"),
        states = listOf("named", "new", "hidden", "scanning"),
        // The face thumbnail decodes through Sketch off the compose clock.
        settleMillis = 1000L,
    ) { state -> DocsPeopleBrowserScene(state) },
    DocsScene(
        id = "person-controls",
        title = "Person controls",
        pathTrace = listOf("Library", "People", "Person"),
        presentation = "component",
        widthDp = 412,
        heightDp = 320,
        settleMillis = 1000L,
    ) { DocsPersonControlsScene() },
    DocsScene(
        id = "edit-backups-viewer",
        title = "Edit backups",
        pathTrace = listOf("Settings", "Smart Features", "Edit Backups"),
        states = listOf("edited", "original", "options"),
        // The actions OptionSheet is a real modal layer — window shot.
        captureWindow = true,
        settleMillis = 1500L,
    ) { state -> DocsEditBackupsScene(state) },
    DocsScene(
        id = "map-locations",
        title = "Map locations",
        pathTrace = listOf("Library", "Locations"),
        // MapLibre draws on a GL surface — window shot. A fixed delay cannot
        // prove tiles arrived, so capture waits for the tagged node the scene
        // emits on the map's first fully-rendered frame.
        captureWindow = true,
        settleMillis = 1000L,
        requiredReadyTag = "docs-map-rendered",
    ) { DocsMapLocationsScene() },
    DocsScene(
        id = "cloud-albums-browser",
        title = "Cloud albums",
        pathTrace = listOf("Albums", "Cloud"),
        // MediaStore thumbs decode off the compose clock.
        settleMillis = 1000L,
    ) { DocsCloudAlbumsScene() },
    DocsScene(
        id = "cloud-archive",
        title = "Cloud archive",
        pathTrace = listOf("Library", "Archive"),
        states = listOf("filled", "scrolled"),
        settleMillis = 1000L,
    ) { state -> DocsCloudArchiveScene(state) },
    DocsScene(
        id = "album-transfer",
        title = "Copy destination",
        pathTrace = listOf("Select photos", "Copy to…"),
        states = listOf("destinations", "copying", "complete"),
        // The destination sheet is a real ModalBottomSheet — window shot.
        captureWindow = true,
        settleMillis = 1000L,
    ) { state -> DocsAlbumTransferScene(state) },
    DocsScene(
        id = "selection-controls",
        title = "Selection controls",
        pathTrace = listOf("Timeline", "Select"),
        states = listOf("selected", "all"),
        presentation = "component",
        widthDp = 412,
        heightDp = 160,
    ) { state -> DocsSelectionControlsScene(state) },
    DocsScene(
        id = "subject-cutout",
        title = "Subject cutout",
        pathTrace = listOf("Photo", "Long press", "Cutout"),
        presentation = "component",
        widthDp = 412,
        heightDp = 680,
        // GL-free surface, but the mask overlay draws against the same zoom
        // canvas — window shot keeps the composite intact. The ready tag paces
        // capture on the real encoder+decoder run, not a guessed delay.
        captureWindow = true,
        settleMillis = 1000L,
        requiredReadyTag = "docs-cutout-ready",
    ) { DocsSubjectCutoutScene() },
)

// ── People ──────────────────────────────────────────────────────────────────

/**
 * The face crop inside the bundled `docs_astronaut.png` (NASA portrait of
 * Eileen Collins, public domain — see showcase ASSETS.md). Rect measured with
 * the platform face detector on the bundled asset; cropped at runtime so the
 * thumbnail is a real crop of the real asset.
 */
private const val DOCS_FACE_CROP_X = 148
private const val DOCS_FACE_CROP_Y = 48
private const val DOCS_FACE_CROP_SIZE = 150

/** Materialises the astronaut asset and writes the face crop to private cache. */
@Composable
private fun rememberDocsFaceUrl(): String? {
    val context = LocalContext.current
    val url by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val source = DocsMediaStoreFixture.materialise(context, "files/docs_astronaut.png")
            val bitmap = BitmapFactory.decodeFile(source.absolutePath)
                ?: error("docs astronaut fixture failed to decode: ${source.absolutePath}")
            val crop = Bitmap.createBitmap(
                bitmap, DOCS_FACE_CROP_X, DOCS_FACE_CROP_Y,
                DOCS_FACE_CROP_SIZE, DOCS_FACE_CROP_SIZE,
            )
            val file = File(context.cacheDir, "docs_astronaut_face.png")
            FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.toURI().toString()
        }
    }
    return url
}

/** Local-account people rows for the docs library — illustrative only. */
private fun docsPerson(
    id: String,
    name: String,
    assetCount: Int,
    thumbnailUrl: String?,
    hidden: Boolean = false,
) = PersonInfo(
    id = id,
    name = name,
    providerType = ProviderType.LOCAL_PEOPLE,
    serverConfigId = LOCAL_PEOPLE_CONFIG_ID,
    thumbnailUrl = thumbnailUrl,
    assetCount = assetCount,
    hidden = hidden,
)

@Composable
private fun DocsPeopleBrowserScene(state: String?) {
    val faceUrl = rememberDocsFaceUrl()
    // One sample person at different stages: named, awaiting a name, hidden,
    // and part-way through a scan — no unrelated second identity.
    val eileen = remember(faceUrl) { docsPerson("eileen", "Eileen", 3, faceUrl) }
    val eileenUnnamed = remember(faceUrl) { docsPerson("eileen", "", 3, faceUrl) }
    val eileenHidden = remember(faceUrl) { docsPerson("eileen", "Eileen", 3, faceUrl, hidden = true) }
    val eileenScanning = remember(faceUrl) { docsPerson("eileen", "Eileen", 1, faceUrl) }

    val uiState = when (state) {
        "new" -> PeopleListUiState(
            newPeople = listOf(eileenUnnamed),
            scannedPhotoCount = 3,
            isLoading = false,
        )
        "hidden" -> PeopleListUiState(
            hiddenPeople = listOf(eileenHidden),
            hiddenPeopleCount = 1,
            showHidden = true,
            scannedPhotoCount = 3,
            isLoading = false,
        )
        "scanning" -> PeopleListUiState(
            namedPeople = listOf(eileenScanning),
            scannedPhotoCount = 3,
            isLoading = false,
        )
        else -> PeopleListUiState(
            namedPeople = listOf(eileen),
            scannedPhotoCount = 3,
            isLoading = false,
        )
    }
    val scanState = if (state == "scanning") {
        PeopleScanUiState(
            running = true,
            phase = SmartScanPhase.FACE_INDEX,
            processed = 1,
            total = 3,
        )
    } else {
        PeopleScanUiState()
    }

    // In-memory actions: the hidden-toggle and rename update the list so the
    // captured controls are real; scans stay inert (no model work runs).
    // Keyed on faceUrl: the crop lands after first composition, and the people
    // must be rebuilt with it or the thumbnails stay placeholder icons.
    var liveState by remember(state, faceUrl) { mutableStateOf(uiState) }
    DocsViewerProviders(emptyList()) {
        PeopleListContent(
            state = liveState,
            scanState = scanState,
            localScanAvailable = true,
            actions = PeopleListActions(
                setShowHidden = { liveState = liveState.copy(showHidden = it) },
                renamePerson = { person, name ->
                    liveState = liveState.copy(
                        namedPeople = liveState.namedPeople.map {
                            if (it.accountKey == person.accountKey) it.copy(name = name) else it
                        },
                        newPeople = liveState.newPeople.map {
                            if (it.accountKey == person.accountKey) it.copy(name = name) else it
                        },
                    )
                },
            ),
        )
    }
}

@Composable
private fun DocsPersonControlsScene() {
    val faceUrl = rememberDocsFaceUrl()
    val eileen = remember(faceUrl) { docsPerson("eileen", "Eileen", 3, faceUrl) }
    DocsViewerProviders(emptyList()) {
        PersonHeaderContent(
            state = PersonDetailUiState(person = eileen, isLoading = false),
            isLocalPerson = true,
            photoCount = 3,
            // The docs photos all carry the same demo month — a real range.
            dateRange = "Jun 2025" to "Jun 2025",
            blurProgress = null,
            onRenameClick = {},
            onBirthdayClick = {},
            onHideClick = {},
            onUnhideClick = {},
            onDeleteClick = {},
            onBlurEverywhereClick = {},
            canMerge = false,
            onMergeClick = {},
            onSetCoverClick = {},
        )
    }
}

// ── Edit backups ────────────────────────────────────────────────────────────

/**
 * A genuine backup pair: the bundled lake photo materialised to private cache
 * stands in as the pre-edit original, while a real `Saturation(0f)`-processed
 * JPEG is published as the edited media row — the Original/Edited toggle
 * therefore switches between visibly different pixels.
 */
@Composable
private fun DocsEditBackupsScene(state: String?) {
    val context = LocalContext.current
    val (media, backup) = remember {
        val originalFile = DocsMediaStoreFixture.materialise(context, "drawable/docs_lake.jpg")
        val source = BitmapFactory.decodeFile(originalFile.absolutePath)
            ?: error("docs lake fixture failed to decode: ${originalFile.absolutePath}")
        val edited = Saturation(0f).apply(source)
        val bytes = ByteArrayOutputStream().also { out ->
            edited.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }.toByteArray()
        val editedMedia = DocsMediaStoreFixture.generatedImage(
            context = context,
            displayName = "Docs_lake_edited.jpg",
            bytes = bytes,
            ageDays = 0L,
        )
        editedMedia to EditBackupManager.BackupInfo(
            mediaId = editedMedia.id,
            originalUri = editedMedia.getUri(),
            originalMimeType = "image/jpeg",
            backupPath = originalFile.absolutePath,
            sizeBytes = originalFile.length(),
            editTimestamp = DOCS_DEMO_EPOCH_SECONDS * 1_000L,
        )
    }
    val vmState = remember(backup) {
        EditBackupsViewModel.State(
            isLoading = false,
            totalSize = backup.sizeBytes,
            // Illustrative free space — real devices report filesDir.freeSpace.
            freeSpace = 1_000_000_000L,
            backups = listOf(backup),
            mediaList = listOf(media),
        )
    }
    DocsViewerProviders(listOf(media)) {
        EditBackupsViewerContent(
            state = vmState,
            onAutoCleanup = {},
            onDeleteAll = {},
            onDeleteSelected = { _, _ -> },
            initialShowingOriginal = state == "original",
            initialActionMenu = state == "options",
        )
    }
}

// ── Map ─────────────────────────────────────────────────────────────────────

/**
 * The real [MapLocationsContent] over three docs photos pinned around Moraine
 * Lake, Alberta — illustrative coordinates on the app's configured public tile
 * style, not EXIF claims. [requiredReadyTag] paces the capture on the map's
 * first fully-rendered frame rather than a guessed delay.
 */
@Composable
private fun DocsMapLocationsScene() {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val detail = rememberDocsMediaStoreImage(
        "drawable/docs_flower_detail.jpg", "Docs_flower_detail.jpg", ageDays = 1L,
    )
    val photos = listOf(lake, earth, flower)
    val coordinates = listOf(
        51.3217 to -116.1860,
        51.3224 to -116.1852,
        51.3209 to -116.1867,
    )
    val geoMedia = photos.mapIndexed { index, media ->
        GeoMedia(
            mediaId = media.id,
            latitude = coordinates[index].first,
            longitude = coordinates[index].second,
            locationCity = "Moraine Lake",
            locationCountry = "Canada",
            media = media,
        )
    }
    val locations = listOf(
        LocationMedia(
            media = detail,
            location = "Moraine Lake, Canada",
            city = "Moraine Lake",
            country = "Canada",
        )
    )
    var rendered by remember { mutableStateOf(false) }
    DocsViewerProviders(photos) {
        Box(Modifier.fillMaxSize()) {
            MapLocationsContent(
                metadataState = remember { mutableStateOf(MediaMetadataState()) },
                locations = locations,
                geoMedia = geoMedia,
                initialMediaId = lake.id,
                onMapFullyRendered = { rendered = true },
            )
            if (rendered) {
                Spacer(Modifier.size(0.dp).testTag("docs-map-rendered"))
            }
        }
    }
}

// ── Cloud albums ─────────────────────────────────────────────────────────────

/** The two fictional accounts every docs cloud fixture shares. */
private val docsCloudAccounts = mapOf(
    11L to CloudDestinationAccount(
        providerType = ProviderType.WEBDAV,
        title = "Family NAS",
        subtitle = "WebDAV · nas.example.com",
    ),
    22L to CloudDestinationAccount(
        providerType = ProviderType.IMMICH,
        title = "Home photos",
        subtitle = "Immich · photos.example.com",
    ),
)

/**
 * Three illustrative cloud albums across the two sample accounts. Identities
 * use the real [cloudAlbumId] scheme; thumbnails point at the owned docs
 * photos so the real album cells render actual imagery.
 */
private fun docsCloudAlbums(lake: UriMedia, flower: UriMedia, detail: UriMedia): List<Album> =
    listOf(
        Album(
            id = cloudAlbumId(ProviderType.IMMICH, 22L, "summer"),
            label = "Summer (Immich)",
            uri = lake.uri,
            pathToThumbnail = lake.path,
            relativePath = "cloud/IMMICH/",
            timestamp = lake.timestamp,
            count = 3,
            size = lake.size,
            cloudIdentity = CloudAlbumIdentity(ProviderType.IMMICH, 22L, "summer"),
            cloudName = "Summer",
        ),
        Album(
            id = cloudAlbumId(ProviderType.IMMICH, 22L, "family"),
            label = "Family (Immich)",
            uri = flower.uri,
            pathToThumbnail = flower.path,
            relativePath = "cloud/IMMICH/",
            timestamp = flower.timestamp,
            count = 2,
            size = flower.size,
            cloudIdentity = CloudAlbumIdentity(ProviderType.IMMICH, 22L, "family"),
            isCloudShared = true,
            isCloudOwned = true,
            cloudShareRole = "editor",
            cloudName = "Family",
        ),
        Album(
            id = cloudAlbumId(ProviderType.WEBDAV, 11L, "Trips"),
            label = "Trips (WebDAV)",
            uri = detail.uri,
            pathToThumbnail = detail.path,
            relativePath = "cloud/WEBDAV/",
            timestamp = detail.timestamp,
            count = 3,
            size = detail.size,
            cloudIdentity = CloudAlbumIdentity(ProviderType.WEBDAV, 11L, "Trips"),
            cloudName = "Trips",
        ),
    )

/**
 * The real Albums tab carrying a dedicated cloud section: the docs session is
 * rebuilt with a distributor whose `albumsCloud` holds the three fixture
 * albums, so [AlbumsContent] groups them under their provider headers exactly
 * as production does (the transfer sheet separately groups by account).
 */
@Composable
private fun DocsCloudAlbumsScene() {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg", favorite = true)
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val detail = rememberDocsMediaStoreImage("drawable/docs_flower_detail.jpg", "Docs_flower_detail.jpg")
    val store = LocalSettingsStore.current ?: error("LocalSettingsStore is not provided")
    val session = remember(lake, flower, detail) {
        DocsGallerySession(
            store,
            DocsMediaDistributor(
                // Real local album: the empty gate is `albums.isEmpty() &&
                // collections.isEmpty()` and albumsCloud alone does not count —
                // without a local album the cloud section never renders.
                listOf(lake, flower, detail),
                docsCloudAlbums(lake, flower, detail),
                // No collections for this fixture — the default would derive a
                // blank "Holiday" card.
                emptyList(),
            ),
        )
    }
    DocsViewerProviders(emptyList()) {
        CompositionLocalProvider(
            LocalDocsGallerySession provides session,
            LocalMediaDistributor provides session.distributor,
            LocalMediaSelector provides session.selector,
        ) {
            DocsGalleryHost(DocsGalleryPage.LIBRARY, null) { providerType, tint, modifier ->
                ProviderBrandIcon(providerType = providerType, modifier = modifier, tint = tint)
            }
        }
    }
}

/**
 * The real [CloudArchiveScreen] over three owned docs photos. The selection
 * overlay is stubbed to nothing — this scene has no selection, and the default
 * `SelectionSheet` pulls DI-bound services the docs host does not provide.
 */
@Composable
private fun DocsCloudArchiveScene(state: String?) {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    // "scrolled" repeats the three photos so the grid is tall enough to sit part-way down
    // under the transparent blur bar.
    val media = listOf(lake, flower, earth).let { owned ->
        if (state != "scrolled") owned
        else (0 until 16).flatMap { copy -> owned.map { it.copy(id = it.id + copy * 1_000_000L) } }
    }
    DocsViewerProviders(media) {
        DocsSharedScopes { sts, acs ->
            CloudArchiveScreen(
                paddingValues = WindowInsets.systemBars.asPaddingValues(),
                mediaState = remember { mutableStateOf(DocsGalleryData.mediaStateFor(media)) },
                metadataState = remember { mutableStateOf(MediaMetadataState()) },
                clearSelection = {},
                sharedTransitionScope = sts,
                animatedContentScope = acs,
                selectionContent = { _, _ -> },
                initialFirstVisibleItemIndex = if (state == "scrolled") 9 else 0,
            )
        }
    }
}

// ── Album transfer ───────────────────────────────────────────────────────────

/**
 * The real [CopyMediaSheetContent] as a shown modal sheet: `destinations`
 * poses the picker (local Camera + the two cloud accounts), `copying` the
 * transfer journey at 50%, and `complete` the result panel with an Open album action.
 * Every write/retry/open callback is inert — no worker, no connection.
 */
@Composable
private fun DocsAlbumTransferScene(state: String?) {
    val lake = rememberDocsMediaStoreImage("drawable/docs_lake.jpg", "Docs_lake.jpg")
    val flower = rememberDocsMediaStoreImage("drawable/docs_flower.jpg", "Docs_flower.jpg")
    val earth = rememberDocsMediaStoreImage("drawable/docs_earth.jpg", "Docs_earth.jpg")
    val media = listOf(lake, flower, earth)
    val cloudAlbums = docsCloudAlbums(lake, flower, earth)
    val summer = cloudAlbums.first()
    val trips = cloudAlbums.last()
    val cameraAlbum = Album(
        id = DocsGalleryData.ALBUM_CAMERA,
        label = "Camera",
        uri = lake.uri,
        pathToThumbnail = lake.path,
        relativePath = "DCIM/Camera",
        timestamp = lake.timestamp,
        count = media.size.toLong(),
        size = media.sumOf { it.size },
    )
    val sheetState = rememberAppBottomSheetState()
    LaunchedEffect(Unit) { sheetState.show() }
    var chosenDestination by remember { mutableStateOf<Album?>(null) }
    // Mirrors production's isCopyDestinationEnabled: a cloud album is a valid
    // destination only when its computed status is READY.
    val statusFor: (Album) -> CloudCopyDestinationStatus? = { album ->
        val identity = album.cloudIdentity
        when {
            identity == null -> null
            else -> CloudCopyDestinationStatus.READY
        }
    }
    val destination = chosenDestination
        ?: if (state == "copying" || state == "complete") summer else null
    DocsViewerProviders(media) {
        CopyMediaSheetContent(
            sheetState = sheetState,
            media = media,
            albums = listOf(cameraAlbum) + cloudAlbums,
            albumGroups = emptyList(),
            cloudAccounts = docsCloudAccounts,
            // Same index the app's portrait default resolves to.
            gridSizeIndex = Constants.albumCellsList.indexOf(GridCells.Fixed(2))
                .coerceAtLeast(0),
            operationActive = state == "copying",
            operationProgress = if (state == "copying") 0.5f else 1f,
            operationFinished = state == "complete",
            journeyVisible = state == "copying" || state == "complete",
            succeeded = state == "complete",
            failed = false,
            failureMessage = "",
            canRetry = false,
            destination = destination,
            destinationLabel = destination?.label.orEmpty(),
            openTarget = if (state == "complete") {
                TransferOpenTarget(TransferOpenTargetType.ALBUM, summer.id, summer.label)
            } else {
                null
            },
            hasFullStorageAccess = true,
            isDestinationEnabled = { album ->
                statusFor(album)?.let { it == CloudCopyDestinationStatus.READY } ?: true
            },
            destinationStatus = statusFor,
            onDestinationSelected = { chosenDestination = it },
            onNewAlbum = {},
            onRetry = {},
            onCloseResult = {},
            onDone = {},
            onOpen = {},
            providerIcon = { providerType, tint, modifier ->
                ProviderBrandIcon(providerType = providerType, modifier = modifier, tint = tint)
            },
            // Scroll to the first cloud header so the account sections fit the
            // frame — in this fixed empty-groups fixture the grid is
            // NewAlbum0/localHeader1/Camera2/ImmichHeader3, and without the seed
            // the read-only account's cells sit below the fold.
            destinationGridState = rememberLazyGridState(
                initialFirstVisibleItemIndex = if (state == "destinations") 3 else 0
            ),
        )
    }
}

// ── Selection controls ───────────────────────────────────────────────────────

/**
 * The real [SelectionAddon] (Close + count) and [SelectAllAddon] top-row
 * controls in the production arrangement, over the normal theme surface with
 * the Haze source behind them. `selected` seeds the isolated session selector
 * with the first three docs photos; `all` seeds all twelve — the same
 * [DocsGalleryData] universe the Select-all control reads.
 */
@Composable
private fun DocsSelectionControlsScene(state: String?) {
    val allMedia = remember { DocsGalleryData.mediaItems() }
    val allMediaState = remember { DocsGalleryData.mediaStateFor(allMedia) }
    val selector = LocalMediaSelector.current
    remember(state) {
        selector.rawUpdateSelection(
            if (state == "all") allMedia.map { it.id }.toSet()
            else allMedia.take(3).map { it.id }.toSet()
        )
    }
    val selectedIds by selector.selectedMedia.collectAsState()
    val scope = rememberCoroutineScope()
    DocsViewerProviders(emptyList()) {
        Box(Modifier.fillMaxSize()) {
            // The sampled backdrop is a sibling of the effect controls, not
            // their ancestor — hazeSource must not wrap the row it feeds.
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .hazeSource(LocalHazeState.current),
            )
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SelectionAddon(
                    onClick = { scope.launch { selector.clearSelection() } },
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    text = selectedIds.size.toString(),
                )
                SelectAllAddon(allMedia = allMediaState)
            }
        }
    }
}

// ── Subject cutout ───────────────────────────────────────────────────────────

/**
 * A genuine MobileSAM cutout over the bundled astronaut portrait: the two SAM
 * files bundled into the capture APK's androidTest assets are staged through
 * the real [ModelManager] (validation + checksum), the real
 * [CutoutHelper.CutoutSession] encodes the image and decodes the mask from two
 * include points (head at 43%/23%, torso at 45%/62% of the frame), and the
 * production [ZoomablePagerImage] draws the contour/point overlay through its
 * own zoom geometry. The real [CutoutControlsBar] renders from the published
 * controller. If any model or inference step fails the scene throws — a fake
 * silhouette is never drawn.
 */
@Composable
private fun DocsSubjectCutoutScene() {
    val context = LocalContext.current
    val media = rememberDocsMediaStoreImage(
        "files/docs_astronaut.png", "Docs_astronaut.png", mimeType = "image/png",
    )
    val cutoutState = remember { CutoutState() }
    var cutoutController by remember { mutableStateOf<CutoutController?>(null) }

    LaunchedEffect(media) {
        val manager = ModelManager(context)
        withContext(Dispatchers.IO) {
            ModelGroup.CUTOUT.files.forEach { name ->
                if (!manager.isModelFileValid(name)) {
                    installBundledModel(
                        destinationFile = manager.getDestinationFile(name),
                        expectedChecksum = ModelManager.EXPECTED_CHECKSUMS[name],
                    ) { context.assets.open(name) }
                }
            }
            manager.onDownloadComplete(ModelGroup.CUTOUT)
        }
        check(manager.isReady(ModelGroup.CUTOUT)) { "docs cutout models failed validation" }
        val session = CutoutHelper.CutoutSession(context, media, manager)
        // Ownership moves to cutoutState only after the result is published;
        // any failure or cancellation before that closes the session here.
        var published = false
        try {
            if (!session.initAndRunEncoder()) {
                printError("docs subject-cutout: encoder initialisation returned false")
                error("docs cutout encoder failed to initialise")
            }
            val points = listOf(
                CutoutHelper.PromptPoint(session.widthOrig * 0.43f, session.heightOrig * 0.23f, true),
                CutoutHelper.PromptPoint(session.widthOrig * 0.45f, session.heightOrig * 0.62f, true),
            )
            val result = session.runDecoder(points)
            if (result == null) {
                printError("docs subject-cutout: decoder returned no mask")
                error("docs cutout decoder returned no mask")
            }
            cutoutState.initSession(session, points)
            cutoutState.updateResult(result, null)
            published = true
        } finally {
            if (!published) session.close()
        }
    }
    DisposableEffect(Unit) { onDispose { cutoutState.dismiss() } }

    DocsViewerProviders(listOf(media)) {
        Box(Modifier.fillMaxSize()) {
            ZoomablePagerImage(
                media = media,
                rotationDisabled = true,
                onImageRotated = {},
                onItemClick = {},
                onSwipeDown = {},
                swipeToDismissEnabled = false,
                onCutoutController = { cutoutController = it },
                cutoutEnabled = true,
                cutoutState = cutoutState,
            )
            cutoutController?.let { controller ->
                CutoutControlsBar(
                    controller = controller,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
            }
            if (cutoutState.hasResult && cutoutController != null) {
                Spacer(Modifier.size(0.dp).testTag("docs-cutout-ready"))
            }
        }
    }
}
