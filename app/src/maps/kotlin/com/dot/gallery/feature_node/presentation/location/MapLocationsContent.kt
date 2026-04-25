/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.presentation.classifier.CategoriesViewModel
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.rememberSurfaceCapture
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.condition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.heatmapDensity
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.HeatmapLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor

private const val OPEN_FREE_MAP_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val OPEN_FREE_MAP_DARK = "https://tiles.openfreemap.org/styles/dark"

@Suppress("ComposeRules")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
) {
    val sheetHazeState = LocalHazeState.current
    val allowBlur by rememberAllowBlur()
    val composeView: View = LocalView.current
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

    // Adaptive layout detection
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val useWideLayout =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val sheetPeekHeight = 280.dp
    val sheetPeekHeightPx = with(LocalDensity.current) { sheetPeekHeight.toPx() }
    var currentSheetPaddingPx by remember { mutableFloatStateOf(if (useWideLayout) 0f else sheetPeekHeightPx) }
    val density = LocalDensity.current

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = savedLng, latitude = savedLat),
            zoom = savedZoom,
            tilt = 0.0,
            bearing = 0.0,
            padding = if (!useWideLayout) PaddingValues(bottom = sheetPeekHeight) else PaddingValues(0.dp)
        )
    )

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

    val accentComposeColor = ComposeColor(accentArgb)
    val surfaceComposeColor = ComposeColor(surfaceArgb)

    // ── Load circular thumbnail for selected media marker ──
    var selectedThumbBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(selectedGeoMedia) {
        val item = selectedGeoMedia ?: run {
            selectedThumbBitmap = null
            return@LaunchedEffect
        }
        selectedThumbBitmap = null
        withContext(Dispatchers.IO) {
            runCatching {
                val thumbSize = 192
                val uri = item.media.getUri()
                val bitmap = Glide.with(context.applicationContext)
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
                    (thumbSize - bitmap.width) / 2f,
                    (thumbSize - bitmap.height) / 2f,
                    paint
                )
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = surfaceArgb
                }
                canvas.drawCircle(half, half, half - 1.5f, borderPaint)

                selectedThumbBitmap = output.asImageBitmap()
            }
        }
    }

    // ── Capture map SurfaceView for haze blur ──
    var mapReady by remember { mutableStateOf(false) }
    val mapCaptureState = rememberSurfaceCapture(
        view = composeView,
        enabled = allowBlur && mapReady
    )

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

    // Fly camera when selected location changes
    val selectedLocationKey = remember(selectedGeoMedia) {
        selectedGeoMedia?.let { "${it.latitude},${it.longitude}" } ?: ""
    }
    LaunchedEffect(selectedLocationKey) {
        val item = selectedGeoMedia ?: return@LaunchedEffect
        val bottomPaddingDp = with(density) { currentSheetPaddingPx.toDp() }
        cameraState.animateTo(
            finalPosition = CameraPosition(
                target = Position(longitude = item.longitude, latitude = item.latitude),
                zoom = 12.0,
                tilt = 0.0,
                bearing = 0.0,
                padding = PaddingValues(bottom = bottomPaddingDp)
            ),
            duration = 500.milliseconds
        )
    }

    // Save camera position for config changes (debounced to avoid recomposition storm)
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.position }
            .debounce(500)
            .collect { pos ->
                savedLat = pos.target.latitude
                savedLng = pos.target.longitude
                savedZoom = pos.zoom
            }
    }

    // ── Build GeoJSON sources reactively ──
    val heatmapGeoJson = remember(geoMedia) {
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                geoMedia.forEach { item ->
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

    // ── Shared composable: Map ──
    val mapOptions = remember {
        MapOptions(
            ornamentOptions = OrnamentOptions(
                isLogoEnabled = true,
                isAttributionEnabled = false,
                isCompassEnabled = false,
                isScaleBarEnabled = false
            )
        )
    }

    val baseStyle = remember(isDark) {
        BaseStyle.Uri(if (isDark) OPEN_FREE_MAP_DARK else OPEN_FREE_MAP_LIGHT)
    }

    val mapContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            MapBlurOverlay(mapCaptureState, sheetHazeState)

            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                cameraState = cameraState,
                options = mapOptions,
                baseStyle = baseStyle,
                onMapLoadFinished = { mapReady = true },
                onMapClick = { pos, _ ->
                    val closest = sortedGeoMedia.minByOrNull { geo ->
                        val dx = geo.longitude - pos.longitude
                        val dy = geo.latitude - pos.latitude
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
                    org.maplibre.compose.util.ClickResult.Consume
                }
            ) {
                if (geoMedia.isNotEmpty()) {
                    val heatmapSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(heatmapGeoJson),
                        options = GeoJsonOptions(
                            cluster = true,
                            clusterRadius = 50,
                            clusterMaxZoom = 15
                        )
                    )

                    // ── Heatmap layer ──
                    HeatmapLayer(
                        id = "media-heatmap",
                        source = heatmapSource,
                        weight = switch(
                            condition(
                                feature.has("point_count"),
                                feature.get("point_count").asNumber()
                            ),
                            fallback = const(1.0f)
                        ),
                        intensity = interpolate(
                            type = linear(),
                            input = zoom(),
                            0 to const(0.1f),
                            5 to const(0.15f),
                            9 to const(0.25f),
                            14 to const(0.4f)
                        ),
                        color = interpolate(
                            type = linear(),
                            input = heatmapDensity(),
                            0.0 to const(ComposeColor(0x00000000)),
                            0.1 to const(ComposeColor(0xB38000FF.toInt())),
                            0.25 to const(ComposeColor(0xBF0064FF.toInt())),
                            0.4 to const(ComposeColor(0xCC00C864.toInt())),
                            0.55 to const(ComposeColor(0xD9FFDC00.toInt())),
                            0.75 to const(ComposeColor(0xE6FF8C00.toInt())),
                            1.0 to const(ComposeColor(0xF2FF3200.toInt()))
                        ),
                        radius = interpolate(
                            type = linear(),
                            input = zoom(),
                            0 to const(10.dp),
                            5 to const(18.dp),
                            10 to const(30.dp),
                            15 to const(50.dp)
                        ),
                        opacity = interpolate(
                            type = linear(),
                            input = zoom(),
                            0 to const(0.9f),
                            10 to const(0.7f),
                            15 to const(0.4f),
                            20 to const(0.0f)
                        )
                    )
                }

                // ── Selected point marker ──
                val selectedSource = rememberGeoJsonSource(
                    data = GeoJsonData.JsonString(selectedGeoJson)
                )
                val thumbBitmap = selectedThumbBitmap
                val hasThumb = thumbBitmap != null

                // Circle fallback (hidden once thumbnail loads)
                CircleLayer(
                    id = "media-selected-circle",
                    source = selectedSource,
                    visible = !hasThumb,
                    radius = const(14.dp),
                    color = const(accentComposeColor),
                    strokeWidth = const(3.dp),
                    strokeColor = const(surfaceComposeColor)
                )

                // Thumbnail icon (hidden until thumbnail loads)
                SymbolLayer(
                    id = "media-selected-thumb",
                    source = selectedSource,
                    visible = hasThumb,
                    iconImage = if (thumbBitmap != null) image(thumbBitmap) else nil(),
                    iconSize = const(1.5f),
                    iconAllowOverlap = const(true),
                    iconIgnorePlacement = const(true)
                )
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
        val surfaceColorLocal = MaterialTheme.colorScheme.surface

        @OptIn(ExperimentalHazeMaterialsApi::class)
        val sheetHazeStyle = HazeMaterials.regular(
            containerColor = surfaceColorLocal
        )
        val sheetBackgroundModifier = remember(allowBlur, surfaceColorLocal) {
            when {
                !allowBlur -> Modifier.background(
                    color = surfaceColorLocal,
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
                    val containerHeight = with(density) { screenHeight.toPx() }
                    val sheetVisiblePx = (containerHeight - offset).coerceAtLeast(0f)
                    currentSheetPaddingPx = sheetVisiblePx
                    val bottomDp = with(density) { sheetVisiblePx.toDp() }
                    cameraState.position = cameraState.position.copy(
                        padding = PaddingValues(bottom = bottomDp)
                    )
                }
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
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
