/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.classifier.CategoriesViewModel
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import com.dot.gallery.ui.theme.isDarkTheme
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.heatmapLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor

private const val HEATMAP_SOURCE_ID = "media-heatmap-source"
private const val HEATMAP_LAYER_ID = "media-heatmap"
private const val SELECTED_SOURCE_ID = "media-selected-source"
private const val SELECTED_LAYER_ID = "media-selected-point"

@Suppress("ComposeRules")
@OptIn(
    MapboxExperimental::class,
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
) {
    val sheetHazeState = LocalHazeState.current
    val allowBlur by rememberAllowBlur()
    val context = LocalContext.current
    val eventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<CategoriesViewModel>()
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()

    val geoMedia by viewModel.geoMedia.collectAsStateWithLifecycle()

    // Sort by timestamp descending (most recent first)
    val sortedGeoMedia = remember(geoMedia) {
        geoMedia.sortedByDescending { it.media.definedTimestamp }
    }

    // Build grid items: date headers + media cells (same format as TimelineScreen)
    val gridItems = remember(sortedGeoMedia) {
        buildList {
            var lastDateGroup = ""
            for (item in sortedGeoMedia) {
                val dateGroup = item.media.definedTimestamp.getDate(
                    "EEE, d MMM",
                    "EEEE",
                    "EEE, d MMM yyyy",
                    "Today",
                    "Yesterday"
                )
                if (dateGroup != lastDateGroup) {
                    add(MapGridItem.Header(dateGroup))
                    lastDateGroup = dateGroup
                }
                add(MapGridItem.MediaCell(item))
            }
        }
    }

    LaunchedEffect(BuildConfig.MAPS_TOKEN) {
        MapboxOptions.accessToken = BuildConfig.MAPS_TOKEN
    }

    // Saveable state for configuration changes
    var savedLat by rememberSaveable { mutableDoubleStateOf(30.0) }
    var savedLng by rememberSaveable { mutableDoubleStateOf(10.0) }
    var savedZoom by rememberSaveable { mutableDoubleStateOf(2.0) }
    var selectedMediaId by rememberSaveable { mutableLongStateOf(-1L) }

    val selectedGeoMedia = remember(selectedMediaId, sortedGeoMedia) {
        if (selectedMediaId != -1L) sortedGeoMedia.firstOrNull { it.mediaId == selectedMediaId }
        else null
    }

    val gridState = rememberLazyGridState()

    val density = LocalDensity.current
    val sheetPeekHeightPx = remember(density) { with(density) { 280.dp.toPx() } }

    // Adaptive layout detection
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val useWideLayout =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            zoom(savedZoom)
            center(Point.fromLngLat(savedLng, savedLat))
            pitch(0.0)
            bearing(0.0)
            if (!useWideLayout) {
                padding(EdgeInsets(0.0, 0.0, sheetPeekHeightPx.toDouble(), 0.0))
            }
        }
    }

    var mapReady by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var currentSheetPaddingPx by remember { mutableFloatStateOf(if (useWideLayout) 0f else sheetPeekHeightPx) }

    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    val accentArgb = remember(accentColor) {
        Color.argb(
            (accentColor.alpha * 255).toInt(),
            (accentColor.red * 255).toInt(),
            (accentColor.green * 255).toInt(),
            (accentColor.blue * 255).toInt()
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

    // Set initial selection when data loads
    LaunchedEffect(sortedGeoMedia) {
        if (selectedMediaId == -1L && sortedGeoMedia.isNotEmpty()) {
            selectedMediaId = sortedGeoMedia.first().mediaId
        }
    }

    // Track first visible grid item → update selected media (only when user scrolls the grid)
    LaunchedEffect(gridState, gridItems) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index ->
                val mediaItem = (index until gridItems.size)
                    .firstNotNullOfOrNull { i ->
                        (gridItems.getOrNull(i) as? MapGridItem.MediaCell)?.geoMedia
                    }
                if (mediaItem != null && mediaItem.mediaId != selectedMediaId) {
                    selectedMediaId = mediaItem.mediaId
                }
            }
    }

    // Fly camera when selected location changes (skip if triggered by map drag)
    val selectedLocationKey = remember(selectedGeoMedia) {
        selectedGeoMedia?.let { "${it.latitude},${it.longitude}" } ?: ""
    }
    LaunchedEffect(selectedLocationKey) {
        val item = selectedGeoMedia ?: return@LaunchedEffect
        mapViewportState.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(item.longitude, item.latitude))
                .zoom(12.0)
                .padding(EdgeInsets(0.0, 0.0, currentSheetPaddingPx.toDouble(), 0.0))
                .build()
        )
    }

    // Save camera position for config changes
    LaunchedEffect(mapReady) {
        if (!mapReady) return@LaunchedEffect
        snapshotFlow {
            mapViewRef?.mapboxMap?.cameraState
        }.collect { cameraState ->
            if (cameraState != null) {
                savedLat = cameraState.center.latitude()
                savedLng = cameraState.center.longitude()
                savedZoom = cameraState.zoom
            }
        }
    }

    // Load thumbnail for the selected media on demand
    var selectedThumbId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedGeoMedia, mapReady) {
        val item = selectedGeoMedia ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        val thumbId = "thumb-${item.mediaId}"
        selectedThumbId = null
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val thumbSize = 192
                    val appContext = context.applicationContext
                    val uri = item.media.getUri()
                    val bitmap: Bitmap = Glide.with(appContext)
                        .asBitmap()
                        .load(uri)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(GlideInvalidation.signature(item.media))
                        .submit(thumbSize, thumbSize)
                        .get()

                    val output = createBitmap(thumbSize, thumbSize)
                    val canvas = Canvas(output)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val half = thumbSize / 2f
                    canvas.drawCircle(half, half, half, paint)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(
                        bitmap,
                        ((thumbSize - bitmap.width) / 2f),
                        ((thumbSize - bitmap.height) / 2f),
                        paint
                    )
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = surfaceArgb
                    }
                    canvas.drawCircle(half, half, half - 1.5f, borderPaint)

                    withContext(Dispatchers.Main) {
                        runCatching {
                            mapViewRef?.mapboxMap?.style?.addImage(thumbId, output)
                        }
                        selectedThumbId = thumbId
                    }
                }
            }
        }
    }

    // Update selected marker on map
    LaunchedEffect(selectedGeoMedia, selectedThumbId, mapReady) {
        val item = selectedGeoMedia ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        val mapboxMap = mapViewRef?.mapboxMap ?: return@LaunchedEffect

        mapboxMap.getStyle { style ->
            runCatching { style.removeStyleLayer(SELECTED_LAYER_ID) }
            runCatching { style.removeStyleSource(SELECTED_SOURCE_ID) }

            val thumbReady = selectedThumbId == "thumb-${item.mediaId}"

            val feature = Feature.fromGeometry(
                Point.fromLngLat(item.longitude, item.latitude)
            ).apply {
                if (thumbReady) {
                    addStringProperty("thumbIcon", selectedThumbId!!)
                }
            }

            style.addSource(
                geoJsonSource(SELECTED_SOURCE_ID) {
                    featureCollection(FeatureCollection.fromFeatures(listOf(feature)))
                }
            )

            if (thumbReady) {
                style.addLayer(
                    symbolLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID) {
                        iconImage(Expression.get { literal("thumbIcon") })
                        iconSize(1.5)
                        iconAllowOverlap(true)
                        iconIgnorePlacement(true)
                    }
                )
            } else {
                style.addLayer(
                    circleLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID) {
                        circleColor(accentArgb)
                        circleRadius(14.0)
                        circleStrokeWidth(3.0)
                        circleStrokeColor(surfaceArgb)
                    }
                )
            }
        }
    }

    // Helper: navigate to media viewer for a given media
    fun openMediaViewer(geoMedia: GeoMedia) {
        val city = geoMedia.locationCity
        val country = geoMedia.locationCountry
        if (!city.isNullOrEmpty() && !country.isNullOrEmpty()) {
            eventHandler.navigate(
                Screen.LocationTimelineScreen.location(city, country)
            )
            eventHandler.navigate(
                Screen.MediaViewScreen.idAndLocation(
                    geoMedia.mediaId,
                    city,
                    country
                )
            )
        }
    }

    // ── Capture map SurfaceView for haze blur ──
    val mapCapture by rememberSurfaceCapture(
        view = mapViewRef,
        enabled = allowBlur && mapReady
    )

    // ── Shared composable: Map ──
    val mapContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            // Captured map bitmap as hazeSource — sits in Compose layer behind the
            // SurfaceView hole so haze can read it for position-correct blur.
            mapCapture?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(sheetHazeState)
                )
            }

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                composeMapInitOptions = with(LocalDensity.current) {
                    remember { ComposeMapInitOptions(density.density) }
                },
                mapViewportState = mapViewportState,
                scaleBar = {},
                logo = {},
                attribution = {},
                onMapClickListener = OnMapClickListener { clickedPoint ->
                    val closest = sortedGeoMedia.minByOrNull { geo ->
                        val dx = geo.longitude - clickedPoint.longitude()
                        val dy = geo.latitude - clickedPoint.latitude()
                        dx * dx + dy * dy
                    }
                    if (closest != null) {
                        selectedMediaId = closest.mediaId
                        val index = gridItems.indexOfFirst {
                            it is MapGridItem.MediaCell && it.geoMedia.mediaId == closest.mediaId
                        }
                        if (index >= 0) {
                            scope.launch { gridState.animateScrollToItem(index) }
                        }
                    }
                    true
                },
                style = {
                    MapStyle(style = if (isDark) Style.DARK else Style.LIGHT)
                }
            ) {
                MapEffect(geoMedia, isDark) { mapView ->
                    mapViewRef = mapView
                    val mapboxMap = mapView.mapboxMap

                    mapboxMap.getStyle { style ->
                        // Clean up existing layers and sources
                        runCatching { style.removeStyleLayer(SELECTED_LAYER_ID) }
                        runCatching { style.removeStyleLayer(HEATMAP_LAYER_ID) }
                        runCatching { style.removeStyleSource(SELECTED_SOURCE_ID) }
                        runCatching { style.removeStyleSource(HEATMAP_SOURCE_ID) }

                        if (geoMedia.isEmpty()) return@getStyle

                        // ── Source: clustered points for heatmap ──
                        val heatmapFeatures = geoMedia.map { item ->
                            Feature.fromGeometry(
                                Point.fromLngLat(item.longitude, item.latitude)
                            )
                        }
                        style.addSource(
                            geoJsonSource(HEATMAP_SOURCE_ID) {
                                featureCollection(FeatureCollection.fromFeatures(heatmapFeatures))
                                cluster(true)
                                clusterRadius(50)
                                clusterMaxZoom(15)
                            }
                        )

                        // ── Heatmap layer ──
                        // Weight = point_count so cluster size drives density.
                        // Color mapped by density:
                        // 1-2 media → purple, 3-5 → blue, 6-8 → green,
                        // 9-12 → yellow, 13-20 → orange, 21+ → red
                        style.addLayer(
                            heatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID) {
                                heatmapWeight(
                                    Expression.switchCase(
                                        Expression.has(Expression.literal("point_count")),
                                        Expression.get(Expression.literal("point_count")),
                                        Expression.literal(1.0)
                                    )
                                )
                                heatmapIntensity(
                                    Expression.interpolate {
                                        linear()
                                        zoom()
                                        stop { literal(0); literal(0.1) }
                                        stop { literal(5); literal(0.15) }
                                        stop { literal(9); literal(0.25) }
                                        stop { literal(14); literal(0.4) }
                                    }
                                )
                                heatmapColor(
                                    Expression.interpolate {
                                        linear()
                                        heatmapDensity()
                                        stop { literal(0.0); rgba(0.0, 0.0, 0.0, 0.0) }
                                        stop { literal(0.1); rgba(128.0, 0.0, 255.0, 0.7) }
                                        stop { literal(0.25); rgba(0.0, 100.0, 255.0, 0.75) }
                                        stop { literal(0.4); rgba(0.0, 200.0, 100.0, 0.8) }
                                        stop { literal(0.55); rgba(255.0, 220.0, 0.0, 0.85) }
                                        stop { literal(0.75); rgba(255.0, 140.0, 0.0, 0.9) }
                                        stop { literal(1.0); rgba(255.0, 50.0, 0.0, 0.95) }
                                    }
                                )
                                heatmapRadius(
                                    Expression.interpolate {
                                        linear()
                                        zoom()
                                        stop { literal(0); literal(10.0) }
                                        stop { literal(5); literal(18.0) }
                                        stop { literal(10); literal(30.0) }
                                        stop { literal(15); literal(50.0) }
                                    }
                                )
                                heatmapOpacity(
                                    Expression.interpolate {
                                        linear()
                                        zoom()
                                        stop { literal(0); literal(0.9) }
                                        stop { literal(10); literal(0.7) }
                                        stop { literal(15); literal(0.4) }
                                        stop { literal(20); literal(0.0) }
                                    }
                                )
                            }
                        )

                        mapReady = true
                    }

                }
            }

            // Back button
            @OptIn(ExperimentalHazeMaterialsApi::class)
            NavigationBackButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
                containerColor = if (allowBlur) ComposeColor.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                containerModifier = if (allowBlur) Modifier
                    .clip(CircleShape)
                    .hazeEffect(
                        state = sheetHazeState,
                        style = HazeMaterials.regular(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                else Modifier
            )

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
    val stringToday = stringResource(R.string.header_today)
    val stringYesterday = stringResource(R.string.header_yesterday)

    val mediaGridContent: @Composable (Modifier) -> Unit = { modifier ->
        MediaGridPanel(
            modifier = modifier,
            gridState = gridState,
            gridItems = gridItems,
            stringToday = stringToday,
            stringYesterday = stringYesterday,
            onMediaClick = { geoMedia -> openMediaViewer(geoMedia) }
        )
    }

    // ── Layout: Adaptive (wide = side-by-side, compact = bottom sheet) ──
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
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded
            )
        )

        val screenHeight = LocalWindowInfo.current.containerDpSize.height
        val sheetMaxHeight = screenHeight / 2

        // Blur support
        val surfaceColor = MaterialTheme.colorScheme.surface

        @OptIn(ExperimentalHazeMaterialsApi::class)
        val sheetHazeStyle = HazeMaterials.regular(
            containerColor = surfaceColor
        )
        val sheetBackgroundModifier = remember(allowBlur, surfaceColor) {
            when {
                !allowBlur -> Modifier.background(
                    color = surfaceColor,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )

                else -> Modifier
            }
        }

        // Dynamically update camera padding as sheet is swiped
        LaunchedEffect(scaffoldState, mapReady) {
            if (!mapReady) return@LaunchedEffect
            snapshotFlow {
                runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull()
            }.collect { offset ->
                if (offset != null) {
                    val mapboxMap = mapViewRef?.mapboxMap ?: return@collect
                    val containerHeight = mapboxMap.getSize().height
                    val sheetVisiblePx = (containerHeight - offset).coerceAtLeast(0f)
                    currentSheetPaddingPx = sheetVisiblePx
                    mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .padding(
                                EdgeInsets(
                                    0.0, 0.0, sheetVisiblePx.toDouble(), 0.0
                                )
                            )
                            .build()
                    )
                }
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 280.dp,
            sheetContainerColor = if (allowBlur) ComposeColor.Transparent else MaterialTheme.colorScheme.surface,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetDragHandle = {},
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .then(sheetBackgroundModifier)
                        .hazeEffect(
                            state = sheetHazeState,
                            style = sheetHazeStyle
                        )
                ) {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
}
