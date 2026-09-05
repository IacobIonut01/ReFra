/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dot.gallery.feature_node.presentation.util.printError
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun GalleryMapView(
    modifier: Modifier = Modifier,
    mapState: GalleryMapState,
    styleUri: String,
    isLogoEnabled: Boolean = true,
    isAttributionEnabled: Boolean = true,
    isCompassEnabled: Boolean = false,
    onMapClick: ((LatLng) -> Boolean)? = null,
    onCameraMoved: ((GalleryCameraPosition) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnMapClick = rememberUpdatedState(onMapClick)
    val currentOnCameraMoved = rememberUpdatedState(onCameraMoved)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentIsLogoEnabled = rememberUpdatedState(isLogoEnabled)
    val currentIsAttributionEnabled = rememberUpdatedState(isAttributionEnabled)
    val currentIsCompassEnabled = rememberUpdatedState(isCompassEnabled)

    // Remember the MapView — call onCreate immediately so the native
    // renderer initialises without waiting for a lifecycle event.
    val mapView = remember(context, lifecycleOwner, mapState, mapState.mapViewGeneration) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { mv ->
            mv.onCreate(null)
            mapState.mapView = mv
        }
    }

    LaunchedEffect(mapState, styleUri) {
        mapState.loadStyle(styleUri)
    }

    DisposableEffect(mapView, mapState, lifecycleOwner) {
        var disposed = false
        var started = false
        var resumed = false
        var destroyed = false
        var tearingDown = false
        var attachedMap: MapLibreMap? = null
        var failureListener: MapView.OnDidFailLoadingMapListener? = null
        var cameraMoveListener: MapLibreMap.OnCameraMoveListener? = null
        var cameraMoveStartedListener: MapLibreMap.OnCameraMoveStartedListener? = null
        var mapClickListener: MapLibreMap.OnMapClickListener? = null

        fun startMap() {
            if (!started && !destroyed) {
                mapView.onStart()
                started = true
            }
        }

        fun resumeMap() {
            if (!resumed && !destroyed) {
                mapView.onResume()
                resumed = true
            }
        }

        fun pauseMap() {
            if (resumed && !destroyed) {
                mapView.onPause()
                resumed = false
            }
        }

        fun stopMap() {
            if (started && !destroyed) {
                mapView.onStop()
                started = false
            }
        }

        fun detachListeners() {
            failureListener?.let(mapView::removeOnDidFailLoadingMapListener)
            failureListener = null
            attachedMap?.let { mapLibreMap ->
                cameraMoveListener?.let(mapLibreMap::removeOnCameraMoveListener)
                cameraMoveStartedListener?.let(mapLibreMap::removeOnCameraMoveStartedListener)
                mapClickListener?.let(mapLibreMap::removeOnMapClickListener)
            }
            cameraMoveListener = null
            cameraMoveStartedListener = null
            mapClickListener = null
            attachedMap = null
        }

        fun destroyMap() {
            if (!destroyed) {
                tearingDown = true
                val ownedMap = attachedMap
                detachListeners()
                pauseMap()
                stopMap()
                mapState.onMapViewDestroyed(mapView, ownedMap)
                mapView.onDestroy()
                destroyed = true
            }
        }

        failureListener = MapView.OnDidFailLoadingMapListener { error ->
            if (!disposed && !destroyed && !tearingDown) {
                printError("MapLibre failed to load map: $error")
            }
        }
        mapView.addOnDidFailLoadingMapListener(requireNotNull(failureListener))

        mapView.getMapAsync { mapLibreMap ->
            if (disposed || destroyed) return@getMapAsync
            attachedMap = mapLibreMap

            // Ornament settings
            mapLibreMap.uiSettings.apply {
                this.isLogoEnabled = currentIsLogoEnabled.value
                this.isAttributionEnabled = currentIsAttributionEnabled.value
                this.isCompassEnabled = currentIsCompassEnabled.value
            }

            // Set initial camera
            mapLibreMap.cameraPosition = mapState.cameraPosition.toNative()

            mapState.attachMap(mapLibreMap)

            // Camera listener — sync back to Compose state
            cameraMoveListener = MapLibreMap.OnCameraMoveListener {
                val pos = mapLibreMap.cameraPosition
                val galleryPos = GalleryCameraPosition(
                    latitude = pos.target?.latitude ?: 0.0,
                    longitude = pos.target?.longitude ?: 0.0,
                    zoom = pos.zoom,
                    tilt = pos.tilt,
                    bearing = pos.bearing,
                )
                mapState.cameraPosition = galleryPos
                currentOnCameraMoved.value?.invoke(galleryPos)
            }.also(mapLibreMap::addOnCameraMoveListener)

            // Detect user-initiated gestures (pan, zoom, rotate)
            cameraMoveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    currentOnUserInteraction.value?.invoke()
                }
            }.also(mapLibreMap::addOnCameraMoveStartedListener)

            mapClickListener = MapLibreMap.OnMapClickListener { latLng ->
                currentOnMapClick.value?.invoke(latLng) ?: false
            }.also(mapLibreMap::addOnMapClickListener)
        }

        // Lifecycle management — catch up with current state then observe future events.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startMap()
                Lifecycle.Event.ON_RESUME -> resumeMap()
                Lifecycle.Event.ON_PAUSE -> pauseMap()
                Lifecycle.Event.ON_STOP -> stopMap()
                Lifecycle.Event.ON_DESTROY -> destroyMap()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // The composable likely enters when the lifecycle is already RESUMED,
        // so the observer above would never get ON_START / ON_RESUME.
        // Catch up manually so the render thread starts immediately.
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) startMap()
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeMap()

        onDispose {
            disposed = true
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyMap()
        }
    }

    LaunchedEffect(
        mapState.isStyleLoaded,
        isLogoEnabled,
        isAttributionEnabled,
        isCompassEnabled,
    ) {
        mapState.map?.uiSettings?.apply {
            this.isLogoEnabled = isLogoEnabled
            this.isAttributionEnabled = isAttributionEnabled
            this.isCompassEnabled = isCompassEnabled
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )
}
