/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.app.domain.model.rememberMediaDateCaption
import com.dot.gallery.app.presentation.cast.CastSessionState
import com.dot.gallery.app.presentation.cast.FCastDevice
import com.dot.gallery.app.presentation.cast.PlaybackState
import com.dot.gallery.app.presentation.cast.RemotePlaybackState
import com.dot.gallery.app.presentation.cast.components.CastButton
import com.dot.gallery.app.presentation.cast.components.FCastDevicePickerDialog
import com.dot.gallery.app.presentation.mediaview.MediaViewScreen
import com.dot.gallery.app.presentation.storycards.StoryViewerScreen
import com.dot.gallery.app.presentation.util.MockedMediaHandler
import com.dot.gallery.app.presentation.util.rememberMediaInfo
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.SketchMediaImageRenderer
import com.dot.gallery.core.presentation.util.LocalHazeGlassEnabled
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.presentation.util.rememberLocationData
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.docs.scenes.DocsSharedScopes
import com.dot.gallery.domain.date.formatEpochSec
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.StoryCard
import com.dot.gallery.domain.model.StoryCardType
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.domain.settings.MapSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.settings.SettingsStore
import com.dot.gallery.domain.util.MotionPhotoHelper
import com.dot.gallery.domain.util.MotionPhotoInfo
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isFavorite
import com.dot.gallery.feature.viewer.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature.viewer.LocalViewerRuntime
import com.dot.gallery.feature.viewer.MediaInfoSheetCard
import com.dot.gallery.feature.viewer.MediaViewQuickBottomBar
import com.dot.gallery.feature.viewer.MediaViewSheetActions
import com.dot.gallery.feature.viewer.MediaViewSheetDetails
import com.dot.gallery.feature.viewer.MediaViewerVisualPolicy
import com.dot.gallery.feature.viewer.components.media.MotionPhotoShotsSection
import com.dot.gallery.feature.viewer.components.media.MotionPhotoState
import com.dot.gallery.feature.viewer.components.media.PanoramaImageViewer
import com.dot.gallery.feature.viewer.scaledFilmstripFrameWidth
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Example receiver shown in the cast picker — reserved documentation IP. */
private val DOCS_CAST_DEVICE = FCastDevice(
    name = "Living room TV",
    host = "192.0.2.10",
    port = 46899,
)

/**
 * Media-viewer scenes: the real [MediaViewScreen] over MediaStore-backed docs
 * fixtures ([DocsMediaStoreFixture]) so Sketch decodes genuine `content://`
 * URIs and every local-action capability resolves exactly as in the app.
 * The cast seam receives a [DocsCastController] (no NSD/sockets); quickbar and
 * details slots render the real shared `feature.viewer` composables with
 * in-memory callbacks — no app ViewModels, no writes.
 */
val viewerScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "viewer-photo",
        title = "Photo viewer",
        pathTrace = listOf("Photo"),
        states = listOf("photo", "details", "immersive", "paging-demo"),
        recordedStates = listOf("paging-demo"),
        // The info sheet is a real bottom sheet and Sketch needs a beat to
        // decode the full-size JPEG — capture the window after a bounded settle.
        captureWindow = true,
        settleMillis = 1500L,
    ) { state -> DocsPhotoViewerScene(state) },
    DocsScene(
        id = "viewer-video",
        title = "Video viewer",
        pathTrace = listOf("Video"),
        states = listOf("paused", "controls", "seeking-demo"),
        recordedStates = listOf("seeking-demo"),
        // ExoPlayer prepares off-composition; SurfaceView needs the window shot.
        captureWindow = true,
        settleMillis = 2000L,
    ) { state -> DocsVideoViewerScene(state) },
    DocsScene(
        id = "cast-devices",
        title = "Cast devices",
        pathTrace = listOf("Photo", "Cast"),
        states = listOf("nearby", "connected"),
        // The picker is a real AlertDialog — it lives at window level.
        captureWindow = true,
        settleMillis = 1500L,
    ) { state -> DocsCastDevicesScene(state) },
    DocsScene(
        id = "panorama-viewer",
        title = "Panorama viewer",
        pathTrace = listOf("Panorama"),
        // GLSurfaceView content is only reachable through the window shot; GL
        // pipeline + texture upload get a longer settle.
        captureWindow = true,
        settleMillis = 3000L,
    ) { DocsPanoramaScene() },
    DocsScene(
        id = "memories-viewer",
        title = "Story viewer",
        pathTrace = listOf("Story"),
        captureWindow = true,
        settleMillis = 1500L,
    ) { DocsMemoriesViewerScene() },
    DocsScene(
        id = "motion-photo-controls",
        title = "Motion photo controls",
        pathTrace = listOf("Photo", "Info panel"),
        presentation = "component",
        widthDp = 412,
        heightDp = 470,
        // XMP parse, clip extraction and frame decode run on IO off the
        // compose clock — give them a bounded settle before the shot.
        settleMillis = 1500L,
    ) { DocsMotionPhotoControlsScene() },
)

/**
 * Fixed EXIF-style sample for the docs photographs — real bounds per asset and
 * a capture date derived from the fixture timestamp so it matches the header.
 */
internal fun docsMediaMetadata(
    context: Context,
    media: UriMedia,
    resourcePath: String,
    description: String,
): MediaMetadata {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(
        DocsMediaStoreFixture.materialise(context, resourcePath).absolutePath,
        opts,
    )
    return MediaMetadata(
        mediaId = media.id,
        imageDescription = description,
        dateTimeOriginal = (media.takenTimestamp?.div(1_000L) ?: media.timestamp)
            .formatEpochSec("yyyy:MM:dd HH:mm:ss"),
        manufacturerName = "Example Camera Co.",
        modelName = "DC-1 Mk II",
        lensModel = "24-70mm f/2.8",
        aperture = "f/4.0",
        exposureTime = "1/250",
        iso = "100",
        focalLength = 35.0,
        gpsLatitude = null,
        gpsLongitude = null,
        gpsLocationName = null,
        gpsLocationNameCountry = null,
        gpsLocationNameCity = null,
        imageWidth = opts.outWidth,
        imageHeight = opts.outHeight,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
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
}

/** Real container metadata for the bundled clip — read off the file itself. */
private fun docsVideoMetadata(
    context: Context,
    media: UriMedia,
    resourcePath: String,
): MediaMetadata {
    val file = DocsMediaStoreFixture.materialise(context, resourcePath)
    val retriever = MediaMetadataRetriever()
    val (width, height, duration, frameRate) = try {
        retriever.setDataSource(file.absolutePath)
        listOf(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),
        ).map { it?.toFloatOrNull() }
    } finally {
        retriever.release()
    }
    return MediaMetadata(
        mediaId = media.id,
        imageDescription = null,
        dateTimeOriginal = (media.takenTimestamp?.div(1_000L) ?: media.timestamp)
            .formatEpochSec("yyyy:MM:dd HH:mm:ss"),
        manufacturerName = null,
        modelName = null,
        lensModel = null,
        aperture = null,
        exposureTime = null,
        iso = null,
        focalLength = null,
        gpsLatitude = null,
        gpsLongitude = null,
        gpsLocationName = null,
        gpsLocationNameCountry = null,
        gpsLocationNameCity = null,
        imageWidth = width?.toInt() ?: 0,
        imageHeight = height?.toInt() ?: 0,
        imageResolutionX = null,
        imageResolutionY = null,
        resolutionUnit = null,
        durationMs = duration?.toLong(),
        videoWidth = width?.toInt(),
        videoHeight = height?.toInt(),
        frameRate = frameRate,
        bitRate = null,
        isNightMode = false,
        isPanorama = false,
        isPhotosphere = false,
        isLongExposure = false,
        isMotionPhoto = false,
    )
}

/**
 * Providers the real viewer reads through composition locals — the app would
 * inject AppComponent/VM services here; docs substitute the in-memory variants
 * and the same Haze locals [MainActivity] provides (shared state + regular
 * material style) so `allowBlur` produces the real translucent chrome.
 */
@Composable
internal fun DocsViewerProviders(
    media: List<UriMedia>,
    prefs: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val distributor = remember(media) { DocsMediaDistributor(media) }
    val runtime = remember { DocsViewerRuntime(context) }
    val hazeState = rememberHazeState()
    (LocalSettingsStore.current as? MapSettingsStore)?.let { store ->
        remember(prefs) { store.seed(prefs) }
    }
    CompositionLocalProvider(
        LocalMediaDistributor provides distributor,
        LocalMediaHandler provides MockedMediaHandler(),
        LocalViewerRuntime provides runtime,
        LocalMediaImageRenderer provides SketchMediaImageRenderer,
        LocalHazeState provides hazeState,
        LocalHazeBlurStyle provides HazeMaterials.regular(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ).then { blurEnabled(true) },
        LocalHazeGlassEnabled provides false,
        content = content,
    )
}

/** The shared [MediaViewScreen] call every viewer scene rides on. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsMediaViewer(
    media: List<UriMedia>,
    mediaId: Long,
    metadataState: androidx.compose.runtime.State<MediaMetadataState>,
    uiVisible: Boolean,
    infoExpanded: Boolean,
) {
    val distributor = LocalMediaDistributor.current as DocsMediaDistributor
    val mediaState = remember { mutableStateOf(DocsGalleryData.mediaStateFor(media)) }
    val album = remember(media) {
        media.groupBy { it.albumID }.map { (id, items) ->
            Album(
                id = id,
                label = items.first().albumLabel,
                uri = items.first().uri,
                pathToThumbnail = items.first().path,
                relativePath = items.first().relativePath,
                timestamp = items.first().timestamp,
                count = items.size.toLong(),
                size = items.sumOf { it.size },
            )
        }
    }
    val albumsState = remember {
        mutableStateOf(
            AlbumState(
                albums = album,
                albumsWithBlacklisted = album,
                albumsUnpinned = album,
                isLoading = false,
            ),
        )
    }
    val vaultState = remember {
        mutableStateOf(VaultState(vaults = DocsGalleryData.vaults(), isLoading = false))
    }
    val favoriteOverrides by distributor.favoriteOverrides.collectAsStateWithLifecycle()
    val castController = remember { DocsCastController() }
    val viewerEntryKey = rememberSaveable { UUID.randomUUID().toString() }

    DocsSharedScopes { sts, acs ->
        MediaViewScreen<UriMedia>(
            toggleRotate = {},
            viewerEntryKey = viewerEntryKey,
            paddingValues = WindowInsets.systemBars.asPaddingValues(),
            isStandalone = true,
            initialUiVisible = uiVisible,
            mediaId = mediaId,
            mediaState = mediaState,
            metadataState = metadataState,
            albumsState = albumsState,
            vaultState = vaultState,
            allowBlur = true,
            sharedTransitionScope = sts,
            animatedContentScope = acs,
            castController = castController,
            quickActionsContent = { m, enabled, isBottomDark, autoContrast, placement ->
                val castState by castController.state.collectAsStateWithLifecycle()
                MediaViewQuickBottomBar(
                    currentMedia = m,
                    enabled = enabled,
                    platformSupportsFavorite = true,
                    showDeleteButton = true,
                    isImageDark = isBottomDark,
                    autoContrast = autoContrast,
                    isFavorite = m?.let {
                        favoriteOverrides[it.id] ?: it.isFavorite
                    } == true,
                    onToggleFavorite = { mm, fav ->
                        distributor.setFavoriteOverride(mm.id, fav)
                    },
                    trashSupported = true,
                    placement = placement,
                    castButton = { followTheme ->
                        CastButton(
                            isConnected = castState.connectedDevice != null,
                            isConnecting = castState.isConnecting,
                            followTheme = followTheme,
                            onClick = {},
                        )
                    },
                )
            },
            detailsContent = { m ->
                val current = m ?: return@MediaViewScreen
                val metadata = metadataState.value.metadataMap[current.id]
                val moveSheetState = rememberAppBottomSheetState()
                MediaViewSheetDetails(
                    metadata = metadata,
                    currentMedia = current,
                    dateCaption = rememberMediaDateCaption(metadata, current),
                    mediaInfoList = rememberMediaInfo(current, metadata) {},
                    locationData = rememberLocationData(metadata),
                    hasAlbums = true,
                    canReplaceOriginal = true,
                    metadataSheetState = rememberAppBottomSheetState(),
                    captureDateSheetState = rememberAppBottomSheetState(),
                    moveSheetState = moveSheetState,
                    actionsContent = { mm, moveState ->
                        MediaViewSheetActions(
                            media = mm,
                            hasAlbums = true,
                            hasVaults = true,
                            restoreMedia = null,
                            currentVault = null,
                            moveSheetState = moveState,
                            platformSupportsFavorite = true,
                        )
                    },
                )
            },
            initialInfoExpanded = infoExpanded,
        )
    }
}

/** Bundled photo assets + their factual descriptions, viewer order. */
private data class DocsPhotoAsset(
    val resourcePath: String,
    val displayName: String,
    val ageDays: Long,
    val favorite: Boolean,
    val description: String,
)

private val DOCS_PHOTO_ASSETS = listOf(
    DocsPhotoAsset(
        "drawable/docs_lake.jpg", "Docs_lake.jpg",
        ageDays = 0, favorite = true, description = "Moraine Lake, Canada",
    ),
    DocsPhotoAsset(
        "drawable/docs_earth.jpg", "Docs_earth.jpg",
        ageDays = 1, favorite = false, description = "The blue marble",
    ),
    DocsPhotoAsset(
        "drawable/docs_flower.jpg", "Docs_flower.jpg",
        ageDays = 2, favorite = false, description = "Foxgloves in the garden",
    ),
)

@Composable
private fun docsPhotos(): List<UriMedia> = listOf(
    rememberDocsMediaStoreImage(
        DOCS_PHOTO_ASSETS[0].resourcePath, DOCS_PHOTO_ASSETS[0].displayName,
        ageDays = DOCS_PHOTO_ASSETS[0].ageDays, favorite = DOCS_PHOTO_ASSETS[0].favorite,
    ),
    rememberDocsMediaStoreImage(
        DOCS_PHOTO_ASSETS[1].resourcePath, DOCS_PHOTO_ASSETS[1].displayName,
        ageDays = DOCS_PHOTO_ASSETS[1].ageDays, favorite = DOCS_PHOTO_ASSETS[1].favorite,
    ),
    rememberDocsMediaStoreImage(
        DOCS_PHOTO_ASSETS[2].resourcePath, DOCS_PHOTO_ASSETS[2].displayName,
        ageDays = DOCS_PHOTO_ASSETS[2].ageDays, favorite = DOCS_PHOTO_ASSETS[2].favorite,
    ),
)

private fun docsPhotoMetadataState(context: Context, photos: List<UriMedia>) =
    MediaMetadataState(
        metadata = photos.mapIndexed { i, m ->
            docsMediaMetadata(context, m, DOCS_PHOTO_ASSETS[i].resourcePath,
                DOCS_PHOTO_ASSETS[i].description)
        },
    )

@Composable
private fun DocsPhotoViewerScene(state: String?) {
    val context = LocalContext.current
    val photos = docsPhotos()
    val lake = photos.first()

    DocsViewerProviders(photos) {
        val metadataState = remember { mutableStateOf(docsPhotoMetadataState(context, photos)) }
        DocsMediaViewer(
            media = photos,
            mediaId = lake.id,
            metadataState = metadataState,
            uiVisible = state != "immersive",
            infoExpanded = state == "details",
        )
    }
}

@Composable
private fun DocsVideoViewerScene(state: String?) {
    val context = LocalContext.current
    val clip = rememberDocsMediaStoreVideo("files/docs_flower.mp4", "Docs_flower.mp4")

    // Autoplay off: the captured frame must show the paused player, not a
    // frame racing mid-playback.
    DocsViewerProviders(
        listOf(clip),
        prefs = mapOf(
            SettingsKeys.videoAutoplay.name to SettingsKeys.videoAutoplay.encode(false),
        ),
    ) {
        val metadataState = remember {
            mutableStateOf(
                MediaMetadataState(
                    metadata = listOf(
                        docsVideoMetadata(context, clip, "files/docs_flower.mp4"),
                    ),
                ),
            )
        }
        DocsMediaViewer(
            media = listOf(clip),
            mediaId = clip.id,
            metadataState = metadataState,
            uiVisible = state in setOf("controls", "seeking-demo"),
            infoExpanded = false,
        )
    }
}

@Composable
private fun DocsCastDevicesScene(state: String?) {
    val photos = docsPhotos()
    val lake = photos.first()

    DocsViewerProviders(photos) {
        Box(Modifier.fillMaxSize()) {
            DocsMediaViewer(
                media = photos,
                mediaId = lake.id,
                metadataState = remember { mutableStateOf(MediaMetadataState()) },
                uiVisible = true,
                infoExpanded = false,
            )
            val dialogState = when (state) {
                "connected" -> CastSessionState(
                    connectedDevice = DOCS_CAST_DEVICE,
                    castingMediaId = lake.id,
                    playback = RemotePlaybackState(state = PlaybackState.PLAYING),
                )
                else -> CastSessionState(
                    isDiscovering = true,
                    discoveredDevices = listOf(DOCS_CAST_DEVICE),
                )
            }
            FCastDevicePickerDialog(
                state = dialogState,
                onDeviceSelected = {},
                onCastMedia = {},
                onStopCasting = {},
                onDisconnect = {},
                onDismiss = {},
            )
        }
    }
}

@Composable
private fun DocsPanoramaScene() {
    val context = LocalContext.current
    val pano = rememberDocsMediaStoreImage(
        "files/docs_venice_panorama.jpg", "Docs_venice_panorama.jpg",
    )
    DocsViewerProviders(listOf(pano)) {
        CompositionLocalProvider(
            LocalMediaViewerVisualPolicy provides MediaViewerVisualPolicy(
                allowBlur = true,
                forceDarkBackground = true,
                animationsEnabled = false,
            ),
        ) {
            PanoramaImageViewer(
                media = pano,
                isPhotosphere = true,
                captureBlur = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DocsMemoriesViewerScene() {
    val context = LocalContext.current
    val photos = docsPhotos()
    DocsViewerProviders(
        photos,
        prefs = mapOf(
            SettingsKeys.storyViewerAutoAdvance.name to
                SettingsKeys.storyViewerAutoAdvance.encode(false),
        ),
    ) {
        val card = remember(photos) {
            StoryCard(
                id = 1L,
                type = StoryCardType.MEMORIES,
                title = "A day at the lake",
                subtitle = "Example memory",
                thumbnailMedia = photos.first(),
                mediaList = photos,
                year = 2025,
            )
        }
        val metadataMap = remember(photos) {
            docsPhotoMetadataState(context, photos).metadataMap
        }
        DocsSharedScopes { sts, acs ->
            StoryViewerScreen(
                cards = listOf(card),
                initialCardId = card.id,
                metadataMap = metadataMap,
                onEnsureMetadata = {},
                onDismiss = {},
                sharedTransitionScope = sts,
                animatedVisibilityScope = acs,
            )
        }
    }
}

// ── Motion photo ────────────────────────────────────────────────────────────

/** Same 12-frame/108px strip recipe the viewer ViewModel stitches. */
private const val DOCS_FILMSTRIP_FRAMES = 12
private const val DOCS_FILMSTRIP_HEIGHT_PX = 108

private data class DocsMotionExtraction(
    val info: MotionPhotoInfo,
    val durationMs: Long,
    val composite: Bitmap,
)

/**
 * Scale frames to a fixed thumb height and stitch them side-by-side — the same
 * pass `MediaViewViewModel.stitchFrames` performs (it is private; this mirrors
 * its algorithm via the shared [scaledFilmstripFrameWidth]).
 */
private fun docsStitchFilmstrip(frames: List<Bitmap>, thumbHeight: Int): Bitmap {
    val scaled = frames.map { bmp ->
        Bitmap.createScaledBitmap(
            bmp,
            scaledFilmstripFrameWidth(bmp.width, bmp.height, thumbHeight),
            thumbHeight,
            true,
        )
    }
    val composite = Bitmap.createBitmap(scaled.sumOf { it.width }, thumbHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(composite)
    var x = 0f
    scaled.forEachIndexed { i, bmp ->
        canvas.drawBitmap(bmp, x, 0f, null)
        x += bmp.width
        if (bmp !== frames[i]) bmp.recycle()
    }
    return composite
}

/**
 * The real motion-photo frame-strip UI: the bundled `docs_motion_photo.jpg`
 * (a genuine Google Microvideo container — XMP header + appended MP4) goes
 * through [MotionPhotoHelper.parseInfo]/`extractVideo`/`extractFrames` exactly
 * as in the app, then into [MotionPhotoShotsSection] plus the real
 * sheet-actions grid whose "Extract frames" / "Save as video" cells show for
 * `isMotionPhoto`. A parse or extraction failure throws — the scene never
 * fakes a strip.
 */
@Composable
private fun DocsMotionPhotoControlsScene() {
    val context = LocalContext.current
    val media = rememberDocsMediaStoreImage(
        "files/docs_motion_photo.jpg", "Docs_motion_photo.jpg",
    )
    val extraction by produceState<DocsMotionExtraction?>(initialValue = null, media.id) {
        value = withContext(Dispatchers.IO) {
            val uri = media.getUri()
            val info = MotionPhotoHelper.parseInfo(context, uri)
                ?: error("docs motion-photo fixture not detected: ${media.uri}")
            val clip = MotionPhotoHelper.extractVideo(context, uri, info)
                ?: error("embedded clip extraction failed: ${media.uri}")
            val durationMs = MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(clip.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
            val frames = MotionPhotoHelper.extractFrames(clip, DOCS_FILMSTRIP_FRAMES)
            require(frames.isNotEmpty()) { "embedded clip yielded no frames: ${media.uri}" }
            DocsMotionExtraction(
                info = info,
                durationMs = durationMs,
                composite = docsStitchFilmstrip(frames, DOCS_FILMSTRIP_HEIGHT_PX),
            )
        }
    }
    val motionState = remember {
        var state: MotionPhotoState? = null
        // Scrub/tap callbacks just move the marker — no player behind the strip.
        state = MotionPhotoState(
            onSeek = { state?.positionMs = it },
            onSeekAndPause = { state?.positionMs = it },
        )
        state
    }
    extraction?.let { ex ->
        motionState.motionInfo = ex.info
        motionState.compositeFilmstrip = ex.composite
        motionState.durationMs = ex.durationMs
        motionState.videoReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaInfoSheetCard(innerPadding = Modifier.padding(16.dp)) {
            MotionPhotoShotsSection(state = motionState)
        }
        MediaViewSheetActions(
            media = media,
            hasAlbums = true,
            hasVaults = true,
            restoreMedia = null,
            currentVault = null,
            moveSheetState = rememberAppBottomSheetState(),
            isMotionPhoto = extraction != null,
            platformSupportsFavorite = true,
        )
    }
}
