/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import com.dot.gallery.core.presentation.util.LocalHazeState
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.window.core.layout.WindowSizeClass
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.navigate
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.data.model.MediaMetadataState
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.core.presentation.util.connectivityState
import com.dot.gallery.core.presentation.util.rememberSurfaceCapture
import com.dot.gallery.app.presentation.util.rememberWindowInsetsController
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.style.expressions.Expression
import androidx.compose.ui.graphics.Color as ComposeColor
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.domain.model.Media
import com.dot.gallery.ui.theme.seed
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.core.presentation.util.update
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.location.MapStyles
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.core.presentation.util.rememberSurfaceTopBarBlurStyle
import com.dot.gallery.domain.model.definedTimestamp
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.locations
import com.dot.gallery.generated.resources.map_attribution
import com.dot.gallery.generated.resources.map_load_failed
import com.dot.gallery.generated.resources.retry
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.runtime.CompositionLocalProvider
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction
import com.dot.gallery.core.presentation.components.galleryDetailTopBarHeight
import com.dot.gallery.generated.resources.place_detail_map
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode


/** This build has the interactive map; its places action opens the places page. */
internal const val HasInteractiveMap = true

private const val PHOTO_MARKER_SOURCE = "photo-marker-source"
private const val PHOTO_MARKER_LAYER = "photo-marker-layer"

@Suppress("ComposeRules", "UNUSED_PARAMETER")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    FlowPreview::class,
)
@Composable
fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
    geoMedia: List<GeoMedia> = emptyList(),
    initialMediaId: Long = -1L,
    /**
     * Fires once MapLibre reports a fully-rendered frame (style + viewport
     * tiles fetched and drawn). Stronger than [GalleryMapState.isStyleLoaded],
     * which only tracks the style JSON.
     */
    onMapFullyRendered: () -> Unit = {},
    /** Opens the places list; null hides the bar's places action. */
    onShowPlaces: (() -> Unit)? = null,
) {
    val sheetHazeState = LocalHazeState.current
    val allowBlur by rememberAllowBlur()
    val context = LocalContext.current
    val eventHandler = LocalEventHandler.current
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()
    var mapAppearance by Settings.Misc.rememberMapAppearance()
    val effectiveMapIsDark = mapAppearance.resolvesDark(isDark)
    val windowInsetsController = rememberWindowInsetsController()
    DisposableEffect(windowInsetsController) {
        val previousLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
        onDispose {
            windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
    SideEffect {
        windowInsetsController.isAppearanceLightStatusBars = !effectiveMapIsDark
    }

    // Sort + build grid items off the main thread. Named entries that lack a complete coordinate
    // pair remain in the panel and can still open their media directly.
    val locationsTitle = stringResource(Res.string.locations)
    var sortedGeoMedia by remember { mutableStateOf(emptyList<GeoMedia>()) }
    var gridItems by remember { mutableStateOf(emptyList<MapGridItem>()) }
    LaunchedEffect(geoMedia, locations, locationsTitle) {
        withContext(Dispatchers.Default) {
            val sorted = geoMedia.sortedByDescending { it.media.definedTimestamp }
            val geoIds = sorted.mapTo(HashSet()) { it.mediaId }
            val locationsWithoutCoordinates = locations
                .filterNot { it.media.id in geoIds }
                .sortedByDescending { it.media.definedTimestamp }
            val items = buildList {
                var lastDateGroup = ""
                for (item in sorted) {
                    val dateGroup = item.media.definedTimestamp.getDate("MMMM yyyy")
                    if (dateGroup != lastDateGroup) {
                        add(MapGridItem.Header(dateGroup))
                        lastDateGroup = dateGroup
                    }
                    add(MapGridItem.MediaCell(item))
                }
                if (locationsWithoutCoordinates.isNotEmpty()) {
                    add(MapGridItem.Header(locationsTitle))
                    locationsWithoutCoordinates.forEach { add(MapGridItem.LocationCell(it)) }
                }
            }
            sortedGeoMedia = sorted
            gridItems = items
        }
    }

    // Saveable state for configuration changes
    var savedLat by rememberSaveable { mutableDoubleStateOf(30.0) }
    var savedLng by rememberSaveable { mutableDoubleStateOf(10.0) }
    var savedZoom by rememberSaveable { mutableDoubleStateOf(12.0) }
    var selectedMediaId by rememberSaveable { mutableLongStateOf(-1L) }

    val selectedGeoMedia = remember(selectedMediaId, sortedGeoMedia) {
        if (selectedMediaId != -1L) sortedGeoMedia.firstOrNull { it.mediaId == selectedMediaId }
        else null
    }

    val gridState = rememberLazyGridState()

    // Adaptive layout detection
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val useWideLayout =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val sheetPeekHeight = 280.dp
    val sheetPeekHeightPx = with(LocalDensity.current) { sheetPeekHeight.toPx() }
    var currentSheetPaddingPx by remember { mutableFloatStateOf(if (useWideLayout) 0f else sheetPeekHeightPx) }
    val density = LocalDensity.current
    // The top bar floats over the full-bleed map; the camera keeps pins clear of it.
    val topBarHeight = galleryDetailTopBarHeight()
    val topBarPaddingPx = with(density) { topBarHeight.toPx() }

    val mapState = rememberGalleryMapState(
        initialPosition = GalleryCameraPosition(
            latitude = savedLat,
            longitude = savedLng,
            zoom = savedZoom,
        )
    )
    val connection by connectivityState()
    var wasConnected by remember { mutableStateOf(connection.isConnected()) }
    LaunchedEffect(connection) {
        val isConnected = connection.isConnected()
        if (isConnected && !wasConnected && mapState.loadState == GalleryMapLoadState.FAILED) {
            mapState.retryStyle()
        }
        wasConnected = isConnected
    }
    LaunchedEffect(mapState.loadAttempt) {
        val attempt = mapState.loadAttempt
        if (attempt > 0L && mapState.loadState == GalleryMapLoadState.LOADING) {
            delay(15_000L)
            mapState.onMapLoadTimedOut(attempt)
        }
    }
    var visibleClusters by remember { mutableStateOf(emptyList<MapPhotoCluster>()) }
    var clusterSheet by remember { mutableStateOf<MapPhotoCluster?>(null) }
    val geoById = remember(sortedGeoMedia) { sortedGeoMedia.associateBy { it.mediaId } }
    LaunchedEffect(sortedGeoMedia, mapState) {
        snapshotFlow { mapState.cameraPosition }
            .debounce(120)
            .collectLatest { position ->
                val points = sortedGeoMedia.map {
                    MapPhotoPoint(
                        mediaId = it.mediaId,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        timestamp = it.media.definedTimestamp,
                    )
                }
                val clusters = withContext(Dispatchers.Default) {
                    MapPhotoClusterer.cluster(points, position.zoom)
                }
                visibleClusters = MapPhotoClusterer.visible(
                    clusters = clusters,
                    // Native MapLibre calls require Android Main even under a
                    // Compose test dispatcher.
                    bounds = withContext(Dispatchers.Main) { mapState.visibleBounds() },
                    limit = 160,
                    centerLatitude = position.latitude,
                    centerLongitude = position.longitude,
                )
            }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val surfaceColor = MaterialTheme.colorScheme.surface

    val accentArgb = remember(accentColor) {
        Color.argb(
            (accentColor.alpha * 255).toInt(),
            (accentColor.red * 255).toInt(),
            (accentColor.green * 255).toInt(),
            (accentColor.blue * 255).toInt()
        )
    }
    val onAccentArgb = remember(onAccentColor) {
        Color.argb(
            (onAccentColor.alpha * 255).toInt(),
            (onAccentColor.red * 255).toInt(),
            (onAccentColor.green * 255).toInt(),
            (onAccentColor.blue * 255).toInt()
        )
    }
    val surfaceArgb = remember(surfaceColor) {
        Color.argb(
            (surfaceColor.alpha * 255).toInt(),
            (surfaceColor.red * 255).toInt(),
            (surfaceColor.green * 255).toInt(),
            (surfaceColor.blue * 255).toInt()
        )
    }

    val accentComposeColor = ComposeColor(accentArgb)
    val surfaceComposeColor = ComposeColor(surfaceArgb)

    // ── Load the accent-ringed pin for the selected media marker ──
    var selectedThumbBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val selectedClusterCount = remember(selectedMediaId, visibleClusters) {
        visibleClusters.firstOrNull { cluster -> cluster.members.any { it.mediaId == selectedMediaId } }?.count ?: 1
    }
    LaunchedEffect(selectedGeoMedia) { selectedThumbBitmap = null }
    LaunchedEffect(selectedGeoMedia, selectedClusterCount) {
        val item = selectedGeoMedia ?: return@LaunchedEffect
        val count = selectedClusterCount
        selectedThumbBitmap = withContext(Dispatchers.IO) {
            MapMarkerIconFactory.load(
                context = context,
                media = item.media,
                count = count,
                sizePx = with(density) { 64.dp.roundToPx() },
                borderColor = surfaceArgb,
                badgeColor = accentArgb,
                badgeContentColor = onAccentArgb,
                selected = true,
            )
        }?.asImageBitmap()
    }

    // ── Capture map SurfaceView for haze blur ──
    val mapCaptureState = rememberSurfaceCapture(
        view = mapState.mapView,
        enabled = allowBlur && mapState.isStyleLoaded,
        intervalMs = 50L
    )

    // Set initial selection when data loads — also set the camera position directly
    // (no animation) so the map opens already centred on the selected media. When the
    // screen was opened for a specific media (e.g. "Show in app" from the viewer), that
    // media is the seed instead of the newest item.
    //
    // seededMediaId records which media the camera settled on: MIN_VALUE = not seeded
    // yet, -1 = seeded on the newest item (no deep link), otherwise the deep-linked
    // mediaId. An entry restored with saved state (tab popUpTo saveState) re-seeds only
    // when the deep link names a different media that is actually present — a refresh
    // after the user scrolled elsewhere must not yank the camera back.
    var hasSetInitialPosition by rememberSaveable { mutableStateOf(false) }
    var seededMediaId by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    LaunchedEffect(sortedGeoMedia) {
        if (sortedGeoMedia.isEmpty() || seededMediaId == initialMediaId) return@LaunchedEffect
        if (hasSetInitialPosition &&
            (initialMediaId == -1L || sortedGeoMedia.none { it.mediaId == initialMediaId })
        ) return@LaunchedEffect
        val target = sortedGeoMedia.firstOrNull { it.mediaId == initialMediaId }
            ?: sortedGeoMedia.first()
        selectedMediaId = target.mediaId
        hasSetInitialPosition = true
        seededMediaId = if (target.mediaId == initialMediaId) initialMediaId else -1L
        mapState.moveCamera(
            GalleryCameraPosition(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = 12.0,
                paddingTop = topBarPaddingPx.toDouble(),
                paddingBottom = currentSheetPaddingPx.toDouble(),
            )
        )
    }

    // When opened with a specific mediaId (e.g. from a city timeline or the viewer's
    // "Show in app"), scroll the grid to that media once items are ready. The seeded
    // selection is already in place — this just brings the cell into view. A restored
    // entry re-scrolls when the deep link targets a different media.
    var hasScrolledToInitial by rememberSaveable { mutableStateOf(false) }
    var initialScrollInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(initialMediaId, gridItems) {
        if (initialMediaId == -1L || gridItems.isEmpty()) return@LaunchedEffect
        if (hasScrolledToInitial && selectedMediaId == initialMediaId) return@LaunchedEffect
        val targetIndex = gridItems.indexOfFirst {
            it is MapGridItem.MediaCell && it.geoMedia.mediaId == initialMediaId
        }
        if (targetIndex >= 0) {
            initialScrollInFlight = true
            try {
                gridState.scrollToItem(targetIndex)
            } finally {
                initialScrollInFlight = false
            }
            hasScrolledToInitial = true
        }
    }

    // Track first visible grid item → update selected media (only when user scrolls the
    // grid). Positions emitted while the entry-media scroll is in flight are transient —
    // ignoring them keeps the seeded selection from flipping to the first item and back.
    LaunchedEffect(gridState, gridItems) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index ->
                if (initialScrollInFlight) return@collect
                val mediaItem = (index until gridItems.size)
                    .firstNotNullOfOrNull { i ->
                        (gridItems.getOrNull(i) as? MapGridItem.MediaCell)?.geoMedia
                    }
                if (mediaItem != null && mediaItem.mediaId != selectedMediaId) {
                    selectedMediaId = mediaItem.mediaId
                }
            }
    }

    // Fly camera when selected location changes
    val selectedLocationKey = remember(selectedGeoMedia) {
        selectedGeoMedia?.let { "${it.latitude},${it.longitude}" } ?: ""
    }
    var skipInitialAnimation by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(selectedLocationKey, mapState.isStyleLoaded) {
        if (!mapState.isStyleLoaded) return@LaunchedEffect
        val item = selectedGeoMedia ?: return@LaunchedEffect
        // Skip the first fire — initial position was already set directly above
        if (skipInitialAnimation) {
            skipInitialAnimation = false
            return@LaunchedEffect
        }
        mapState.animateCamera(
            GalleryCameraPosition(
                latitude = item.latitude,
                longitude = item.longitude,
                zoom = 12.0,
                paddingTop = topBarPaddingPx.toDouble(),
                paddingBottom = currentSheetPaddingPx.toDouble(),
            ),
            durationMs = 500
        )
    }

    // Save camera position for config changes (debounced to avoid recomposition storm)
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.cameraPosition }
            .debounce(500)
            .collect { pos ->
                savedLat = pos.latitude
                savedLng = pos.longitude
                savedZoom = pos.zoom
            }
    }

    val markerGeoJson = remember(visibleClusters) {
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                visibleClusters.forEach { cluster ->
                    addJsonObject {
                        put("type", "Feature")
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") {
                                add(cluster.longitude)
                                add(cluster.latitude)
                            }
                        }
                        putJsonObject("properties") {
                            put("renderId", cluster.renderId)
                            put("iconId", cluster.renderId)
                            put("mediaId", cluster.representativeMediaId)
                            put("count", cluster.count)
                        }
                    }
                }
            }
        }.toString()
    }

    val selectedGeoJson = remember(selectedGeoMedia) {
        val item = selectedGeoMedia
            ?: return@remember "{\"type\":\"FeatureCollection\",\"features\":[]}"
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") {
                            add(item.longitude)
                            add(item.latitude)
                        }
                    }
                    putJsonObject("properties") {}
                }
            }
        }.toString()
    }

    // Keep the map as the viewer's direct back-stack parent. Location routes need a matching
    // city-timeline parent ViewModel and must not be silently inserted for a map tap.
    fun openMediaViewer(geoMedia: GeoMedia) {
        eventHandler.navigate(mapMediaViewerRoute(geoMedia.mediaId))
    }

    // ── Imperative layer/source management ──
    // All map mutations happen in LaunchedEffects that check isStyleLoaded.
    // Sources are added BEFORE layers — no composition lifecycle race.

    val accentHex = remember(accentArgb) { String.format("#%08X", accentArgb) }
    val surfaceHex = remember(surfaceArgb) { String.format("#%08X", surfaceArgb) }

    val markerSizePx = with(density) { 64.dp.roundToPx() }
    val registeredMarkerIds = remember { mutableSetOf<String>() }
    LaunchedEffect(mapState.isStyleLoaded, markerGeoJson, visibleClusters, markerSizePx, surfaceArgb, accentArgb, onAccentArgb) {
        if (!mapState.isStyleLoaded) return@LaunchedEffect
        val currentIds = visibleClusters.mapTo(HashSet()) { it.renderId }
        registeredMarkerIds.filter { it !in currentIds }.forEach(mapState::removeImage)
        registeredMarkerIds.retainAll(currentIds)
        visibleClusters.forEach { cluster ->
            mapState.setImage(
                cluster.renderId,
                MapMarkerIconFactory.placeholder(markerSizePx, surfaceArgb, accentArgb),
            )
            registeredMarkerIds.add(cluster.renderId)
        }
        mapState.setGeoJsonSource(PHOTO_MARKER_SOURCE, markerGeoJson)
        mapState.addOrUpdateSymbolLayer(
            id = PHOTO_MARKER_LAYER,
            sourceId = PHOTO_MARKER_SOURCE,
            iconImageExpression = Expression.get("iconId"),
            iconSize = 1f,
            iconAllowOverlap = true,
            iconIgnorePlacement = true,
        )
        val semaphore = Semaphore(4)
        coroutineScope {
            visibleClusters.forEach { cluster ->
                launch {
                    val media = geoById[cluster.representativeMediaId]?.media ?: return@launch
                    val bitmap = withContext(Dispatchers.IO) {
                        semaphore.withPermit {
                            MapMarkerIconFactory.load(
                                context = context,
                                media = media,
                                count = cluster.count,
                                sizePx = markerSizePx,
                                borderColor = surfaceArgb,
                                badgeColor = accentArgb,
                                badgeContentColor = onAccentArgb,
                            )
                        }
                    } ?: return@launch
                    // Native MapLibre calls require Android Main even under a
                    // Compose test dispatcher.
                    withContext(Dispatchers.Main) {
                        if (mapState.isStyleLoaded && visibleClusters.any { it.renderId == cluster.renderId }) {
                            mapState.setImage(cluster.renderId, bitmap)
                        }
                    }
                }
            }
        }
    }

    // Selected point source + layers (circle fallback + thumbnail icon)
    LaunchedEffect(mapState.isStyleLoaded, selectedGeoJson, selectedThumbBitmap, accentHex, surfaceHex) {
        if (!mapState.isStyleLoaded) return@LaunchedEffect
        mapState.setGeoJsonSource(id = "selected-source", geoJson = selectedGeoJson)
        val hasThumb = selectedThumbBitmap != null
        mapState.addOrUpdateCircleLayer(
            id = "media-selected-circle",
            sourceId = "selected-source",
            visible = !hasThumb,
            radius = 14f,
            color = accentHex,
            strokeWidth = 3f,
            strokeColor = surfaceHex,
            aboveLayerId = PHOTO_MARKER_LAYER
        )
        val thumbBmp = selectedThumbBitmap
        if (thumbBmp != null) {
            val androidBitmap = createBitmap(thumbBmp.width, thumbBmp.height)
            val buffer = IntArray(thumbBmp.width * thumbBmp.height)
            thumbBmp.readPixels(buffer)
            androidBitmap.setPixels(buffer, 0, thumbBmp.width, 0, 0, thumbBmp.width, thumbBmp.height)
            mapState.setImage("selected-thumb", androidBitmap)
        }
        mapState.addOrUpdateSymbolLayer(
            id = "media-selected-thumb",
            sourceId = "selected-source",
            iconImageName = if (hasThumb) "selected-thumb" else null,
            visible = hasThumb,
            iconSize = 1f,
            aboveLayerId = "media-selected-circle"
        )
    }

    // ── Shared composable: Map ──
    val styleUri = remember(mapAppearance, isDark) {
        MapStyles.interactiveStyle(mapAppearance, isDark)
    }
    val attributionBottomPadding = with(density) { currentSheetPaddingPx.toDp() } + 6.dp
    val attributionColor = if (effectiveMapIsDark) ComposeColor.White else ComposeColor.Black
    val attributionShadowColor = if (effectiveMapIsDark) ComposeColor.Black else ComposeColor.White

    val mapContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            MapBlurOverlay(mapCaptureState, sheetHazeState)

            GalleryMapView(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                styleUri = styleUri,
                isLogoEnabled = false,
                isAttributionEnabled = false,
                onFullyRendered = onMapFullyRendered,
                onMapClick = { latLng ->
                    val renderId = mapState.renderedMarkerId(latLng.latitude, latLng.longitude, PHOTO_MARKER_LAYER)
                    val cluster = visibleClusters.firstOrNull { it.renderId == renderId }
                    if (cluster == null) {
                        false
                    } else if (cluster.isCluster) {
                        val cannotSplit = mapState.cameraPosition.zoom >= 18.0 ||
                            (cluster.bounds.west == cluster.bounds.east && cluster.bounds.south == cluster.bounds.north)
                        if (cannotSplit) {
                            clusterSheet = cluster
                        } else {
                            mapState.fitCluster(
                                cluster = cluster,
                                paddingPx = with(density) { 72.dp.roundToPx() },
                                topPaddingPx = with(density) { (topBarHeight + 24.dp).roundToPx() },
                            )
                        }
                        true
                    } else {
                        selectedMediaId = cluster.representativeMediaId
                        val index = gridItems.indexOfFirst {
                            it is MapGridItem.MediaCell && it.geoMedia.mediaId == selectedMediaId
                        }
                        if (index >= 0) scope.launch { gridState.animateScrollToItem(index) }
                        true
                    }
                }
            )

            Text(
                text = stringResource(Res.string.map_attribution),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = attributionBottomPadding),
                color = attributionColor.copy(alpha = 0.62f),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    shadow = Shadow(
                        color = attributionShadowColor.copy(alpha = 0.8f),
                        offset = Offset(0f, 1f),
                        blurRadius = 2f,
                    ),
                ),
            )

            when (mapState.loadState) {
                GalleryMapLoadState.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }

                GalleryMapLoadState.FAILED -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(Res.string.map_load_failed),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = mapState::retryStyle) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }

                GalleryMapLoadState.READY -> Unit
            }

            // The map is always under the bar, so it is always the blur bar. It blurs the map
            // capture the sheet already uses; with blur off it falls back to the opaque surface.
            // The title follows the map style, which can be darker or lighter than the theme.
            CompositionLocalProvider(LocalHazePerformanceMode provides HazePerformanceMode.Performance) {
                GalleryPageTopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    title = stringResource(Res.string.place_detail_map),
                    count = sortedGeoMedia.size.takeIf { it > 0 }?.toString(),
                    hazeState = sheetHazeState,
                    scrolled = true,
                    blurStyle = rememberSurfaceTopBarBlurStyle(),
                    titleColor = if (!allowBlur || effectiveMapIsDark == isDark) ComposeColor.Unspecified
                    else MaterialTheme.colorScheme.inverseOnSurface,
                    actions = {
                        if (onShowPlaces != null) {
                            GalleryPageTopBarAction(
                                icon = Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = stringResource(Res.string.locations),
                                onClick = onShowPlaces,
                            )
                        }
                        MapAppearanceAction(
                            appearance = mapAppearance,
                            onAppearanceChange = { mapAppearance = it },
                        )
                    },
                )
            }

            // Loading indicator
            if (metadataState.value.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

        }
    }

    // ── Shared composable: Media grid panel ──
    val panelPlace = remember(selectedGeoMedia, sortedGeoMedia) {
        selectedGeoMedia?.let { selected -> mapPanelPlace(selected, sortedGeoMedia) }
    }

    val mediaGridContent: @Composable (Modifier) -> Unit = { modifier ->
        MediaGridPanel(
            modifier = modifier,
            gridState = gridState,
            gridItems = gridItems,
            place = panelPlace,
            onOpenPlace = { place ->
                eventHandler.navigate(
                    Screen.LocationTimelineScreen.location(
                        gpsLocationNameCity = place.city.orEmpty(),
                        gpsLocationNameCountry = place.country.orEmpty(),
                        latitude = place.latitude,
                        longitude = place.longitude,
                    )
                )
            },
            selectedMediaId = selectedMediaId,
            onMediaClick = { geoMedia -> openMediaViewer(geoMedia) },
            onLocationClick = { locationMedia ->
                val city = locationMedia.city
                val country = locationMedia.country
                val latitude = locationMedia.latitude
                val longitude = locationMedia.longitude
                if (!city.isNullOrBlank() || !country.isNullOrBlank() ||
                    latitude != null && longitude != null
                ) {
                    eventHandler.navigate(
                        Screen.LocationTimelineScreen.location(
                            gpsLocationNameCity = city.orEmpty(),
                            gpsLocationNameCountry = country.orEmpty(),
                            latitude = latitude,
                            longitude = longitude,
                        )
                    )
                } else {
                    eventHandler.navigate(mapMediaViewerRoute(locationMedia.media.id))
                }
            },
        )
    }

    // ── Layout: Adaptive (wide = side-by-side, compact = bottom sheet) ──~
    if (useWideLayout) {
        Row(modifier = Modifier.fillMaxSize()) {
            mapContent(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                mediaGridContent(Modifier.fillMaxSize())
            }
        }
    } else {
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                enabledValues = setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
            )
        )

        val screenHeight = LocalWindowInfo.current.containerDpSize.height
        val sheetMaxHeight = screenHeight / 2

        // Blur support
        val surfaceColorLocal = MaterialTheme.colorScheme.surfaceContainerLow
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

        val sheetHazeStyle = HazeMaterials.regular(
            containerColor = surfaceColorLocal
        )
        val sheetBackgroundModifier = remember(allowBlur, surfaceColorLocal) {
            when {
                !allowBlur -> Modifier.background(
                    color = surfaceColorLocal,
                    shape = sheetShape
                )

                else -> Modifier
            }
        }

        // Dynamically update camera padding as sheet is swiped
        LaunchedEffect(scaffoldState, mapState.isStyleLoaded) {
            if (!mapState.isStyleLoaded) return@LaunchedEffect
            snapshotFlow {
                runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull()
            }.collect { offset ->
                if (offset != null) {
                    val containerHeight = with(density) { screenHeight.toPx() }
                    val sheetVisiblePx = (containerHeight - offset).coerceAtLeast(0f)
                    currentSheetPaddingPx = sheetVisiblePx
                    mapState.setCameraPadding(top = topBarPaddingPx.toDouble(), bottom = sheetVisiblePx.toDouble())
                }
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetContainerColor = if (allowBlur) ComposeColor.Transparent else surfaceColorLocal,
            sheetShape = sheetShape,
            sheetDragHandle = {},
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .then(sheetBackgroundModifier)
                        .hazeEffectScaled(
                            state = sheetHazeState,
                            style = sheetHazeStyle
                        )
                ) {
                    DragHandle(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 5.dp, bottom = 1.dp)
                    )
                    mediaGridContent(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = sheetMaxHeight)
                            .navigationBarsPadding()
                    )
                }
            }
        ) {
            mapContent(
                Modifier.fillMaxSize()
            )
        }
    }

    clusterSheet?.let { cluster ->
        MapClusterSheet(
            media = cluster.members.mapNotNull { geoById[it.mediaId] },
            onDismiss = { clusterSheet = null },
            onMediaClick = { item ->
                clusterSheet = null
                openMediaViewer(item)
            },
        )
    }
}

/**
 * Isolated composable that reads the [mapCaptureState] and renders it as a [hazeSource].
 * Because the capture state updates every ~50 ms, keeping this read in its own composable
 * scope prevents recomposition from propagating into the sibling [MaplibreMap].
 */
@Composable
private fun MapBlurOverlay(
    mapCaptureState: State<ImageBitmap?>,
    hazeState: HazeState,
) {
    mapCaptureState.value?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        )
    }
}
