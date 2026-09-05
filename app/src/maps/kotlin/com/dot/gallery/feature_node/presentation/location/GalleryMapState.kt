/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

enum class GalleryMapLoadState {
    LOADING,
    READY,
    FAILED,
}

@Stable
class GalleryMapState(
    initialPosition: GalleryCameraPosition,
) {
    // ── Compose-observable state ──
    var isStyleLoaded by mutableStateOf(false)
        internal set
    var loadState by mutableStateOf(GalleryMapLoadState.LOADING)
        private set
    var loadAttempt by mutableStateOf(0L)
        private set
    var mapViewGeneration by mutableStateOf(0L)
        private set

    var cameraPosition by mutableStateOf(initialPosition)

    // ── Internal refs ──
    var mapView: MapView? by mutableStateOf(null)
        internal set
    @PublishedApi internal var map: MapLibreMap? = null
    @PublishedApi internal var style: Style? = null
    private var requestedStyleUri: String? = null
    private var loadedStyleUri: String? = null
    private var styleGeneration: Long = 0L

    // ── Public API ──

    /**
     * Execute [block] only when the style is fully loaded.
     * Safe to call at any time; silently no-ops if the style isn't ready.
     */
    inline fun withStyle(block: Style.(MapLibreMap) -> Unit) {
        val s = style ?: return
        val m = map ?: return
        block(s, m)
    }

    /**
     * Move camera instantly (no animation).
     */
    fun moveCamera(position: GalleryCameraPosition) {
        cameraPosition = position
        map?.moveCamera(
            CameraUpdateFactory.newCameraPosition(position.toNative())
        )
    }

    /**
     * Animate camera with a given duration.
     */
    fun animateCamera(position: GalleryCameraPosition, durationMs: Int = 500) {
        cameraPosition = position
        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(position.toNative()),
            durationMs
        )
    }

    /**
     * Set camera padding (e.g. for bottom sheet).
     * Uses CameraPosition.padding (v13 API) instead of the deprecated setPadding.
     */
    fun setCameraPadding(
        left: Double = 0.0,
        top: Double = 0.0,
        right: Double = 0.0,
        bottom: Double = 0.0,
    ) {
        val m = map ?: return
        val cur = m.cameraPosition
        val newPos = CameraPosition.Builder(cur)
            .padding(left, top, right, bottom)
            .build()
        m.moveCamera(CameraUpdateFactory.newCameraPosition(newPos))
    }

    fun visibleBounds(): MapGeoBounds? {
        val bounds = map?.projection?.visibleRegion?.latLngBounds ?: return null
        return MapGeoBounds(
            south = bounds.latitudeSouth,
            west = bounds.longitudeWest,
            north = bounds.latitudeNorth,
            east = bounds.longitudeEast,
            crossesAntimeridian = bounds.longitudeWest > bounds.longitudeEast,
        )
    }

    fun renderedMarkerId(latitude: Double, longitude: Double, layerId: String): String? {
        val currentMap = map ?: return null
        val point = currentMap.projection.toScreenLocation(LatLng(latitude, longitude))
        return currentMap.queryRenderedFeatures(point, layerId)
            .firstNotNullOfOrNull { feature ->
                if (feature.hasProperty("renderId")) feature.getStringProperty("renderId") else null
            }
    }

    fun fitCluster(cluster: MapPhotoCluster, paddingPx: Int, durationMs: Int = 450) {
        val currentMap = map ?: return
        if (cluster.bounds.crossesAntimeridian || cluster.bounds.west == cluster.bounds.east) {
            animateCamera(
                cameraPosition.copy(
                    latitude = cluster.latitude,
                    longitude = cluster.longitude,
                    zoom = (cameraPosition.zoom + 2.0).coerceAtMost(20.0),
                ),
                durationMs,
            )
            return
        }
        val bounds = LatLngBounds.Builder()
            .include(LatLng(cluster.bounds.south, cluster.bounds.west))
            .include(LatLng(cluster.bounds.north, cluster.bounds.east))
            .build()
        currentMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), durationMs)
    }

    /**
     * Add or update a GeoJSON source. If the source already exists, its data is updated.
     */
    fun setGeoJsonSource(
        id: String,
        geoJson: String,
        cluster: Boolean = false,
        clusterRadius: Int = 50,
        clusterMaxZoom: Int = 15,
    ) {
        val s = style ?: return
        val existing = s.getSourceAs<GeoJsonSource>(id)
        if (existing != null) {
            existing.setGeoJson(geoJson)
        } else {
            val options = if (cluster) {
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(clusterRadius)
                    .withClusterMaxZoom(clusterMaxZoom)
            } else {
                null
            }
            val source = if (options != null) {
                GeoJsonSource(id, geoJson, options)
            } else {
                GeoJsonSource(id, geoJson)
            }
            s.addSource(source)
        }
    }

    /**
     * Remove a source by id (no-op if it doesn't exist).
     */
    fun removeSource(id: String) {
        style?.removeSource(id)
    }

    /**
     * Add a HeatmapLayer if it doesn't already exist.
     */
    fun addHeatmapLayer(
        id: String,
        sourceId: String,
        weight: Expression? = null,
        intensity: Expression? = null,
        color: Expression? = null,
        radius: Expression? = null,
        opacity: Expression? = null,
        belowLayerId: String? = null,
    ) {
        val s = style ?: return
        if (s.getLayer(id) != null) return
        val layer = HeatmapLayer(id, sourceId)
        val props = buildList {
            weight?.let { add(PropertyFactory.heatmapWeight(it)) }
            intensity?.let { add(PropertyFactory.heatmapIntensity(it)) }
            color?.let { add(PropertyFactory.heatmapColor(it)) }
            radius?.let { add(PropertyFactory.heatmapRadius(it)) }
            opacity?.let { add(PropertyFactory.heatmapOpacity(it)) }
        }
        if (props.isNotEmpty()) layer.setProperties(*props.toTypedArray())
        if (belowLayerId != null && s.getLayer(belowLayerId) != null) {
            s.addLayerBelow(layer, belowLayerId)
        } else {
            s.addLayer(layer)
        }
    }

    /**
     * Add a CircleLayer if it doesn't already exist, or update its visibility.
     */
    fun addOrUpdateCircleLayer(
        id: String,
        sourceId: String,
        visible: Boolean = true,
        radius: Float? = null,
        color: String? = null,
        strokeWidth: Float? = null,
        strokeColor: String? = null,
        aboveLayerId: String? = null,
    ) {
        val s = style ?: return
        val existing = s.getLayerAs<CircleLayer>(id)
        if (existing != null) {
            existing.setProperties(
                PropertyFactory.visibility(if (visible) "visible" else "none")
            )
            return
        }
        val layer = CircleLayer(id, sourceId)
        val props = buildList {
            add(PropertyFactory.visibility(if (visible) "visible" else "none"))
            radius?.let { add(PropertyFactory.circleRadius(it)) }
            color?.let { add(PropertyFactory.circleColor(it)) }
            strokeWidth?.let { add(PropertyFactory.circleStrokeWidth(it)) }
            strokeColor?.let { add(PropertyFactory.circleStrokeColor(it)) }
        }
        layer.setProperties(*props.toTypedArray())
        if (aboveLayerId != null && s.getLayer(aboveLayerId) != null) {
            s.addLayerAbove(layer, aboveLayerId)
        } else {
            s.addLayer(layer)
        }
    }

    /**
     * Add a SymbolLayer if it doesn't already exist, or update its icon + visibility.
     */
    fun addOrUpdateSymbolLayer(
        id: String,
        sourceId: String,
        iconImageName: String? = null,
        iconImageExpression: Expression? = null,
        visible: Boolean = true,
        iconSize: Float = 1f,
        iconAllowOverlap: Boolean = true,
        iconIgnorePlacement: Boolean = true,
        aboveLayerId: String? = null,
    ) {
        val s = style ?: return
        val existing = s.getLayerAs<SymbolLayer>(id)
        if (existing != null) {
            val props = buildList {
                add(PropertyFactory.visibility(if (visible) "visible" else "none"))
                iconImageExpression?.let { add(PropertyFactory.iconImage(it)) }
                    ?: iconImageName?.let { add(PropertyFactory.iconImage(it)) }
                add(PropertyFactory.iconSize(iconSize))
                add(PropertyFactory.iconAllowOverlap(iconAllowOverlap))
                add(PropertyFactory.iconIgnorePlacement(iconIgnorePlacement))
            }
            existing.setProperties(*props.toTypedArray())
            return
        }
        val layer = SymbolLayer(id, sourceId)
        val props = buildList {
            add(PropertyFactory.visibility(if (visible) "visible" else "none"))
            iconImageExpression?.let { add(PropertyFactory.iconImage(it)) }
                ?: iconImageName?.let { add(PropertyFactory.iconImage(it)) }
            add(PropertyFactory.iconSize(iconSize))
            add(PropertyFactory.iconAllowOverlap(iconAllowOverlap))
            add(PropertyFactory.iconIgnorePlacement(iconIgnorePlacement))
        }
        layer.setProperties(*props.toTypedArray())
        if (aboveLayerId != null && s.getLayer(aboveLayerId) != null) {
            s.addLayerAbove(layer, aboveLayerId)
        } else {
            s.addLayer(layer)
        }
    }

    /**
     * Add or replace a bitmap image in the style.
     */
    fun setImage(name: String, bitmap: Bitmap) {
        val s = style ?: return
        s.addImage(name, bitmap)
    }

    /**
     * Remove a bitmap image from the style.
     */
    fun removeImage(name: String) {
        style?.removeImage(name)
    }

    /**
     * Remove a layer by id (no-op if it doesn't exist).
     */
    fun removeLayer(id: String) {
        style?.removeLayer(id)
    }

    // ── Internal ──

    internal fun attachMap(mapLibreMap: MapLibreMap) {
        map = mapLibreMap
        val styleUri = requestedStyleUri ?: return
        if (loadState != GalleryMapLoadState.LOADING || loadAttempt != styleGeneration) {
            loadStyle(styleUri, force = true)
        } else {
            applyStyle(mapLibreMap, styleUri, styleGeneration)
        }
    }

    internal fun loadStyle(styleUri: String, force: Boolean = false) {
        requestedStyleUri = styleUri
        if (!force && loadedStyleUri == styleUri && isStyleLoaded) return
        val generation = ++styleGeneration
        loadAttempt = generation
        loadState = GalleryMapLoadState.LOADING
        isStyleLoaded = false
        style = null
        map?.let { applyStyle(it, styleUri, generation) }
    }

    private fun applyStyle(currentMap: MapLibreMap, styleUri: String, generation: Long) {
        currentMap.setStyle(styleUri) { loadedStyle ->
            if (generation != styleGeneration || map !== currentMap) return@setStyle
            style = loadedStyle
            loadedStyleUri = styleUri
            stripHeavyLayers(loadedStyle)
            isStyleLoaded = true
            loadState = GalleryMapLoadState.READY
        }
    }

    fun retryStyle() {
        val styleUri = requestedStyleUri ?: return
        val recreateMapView = map == null
        loadedStyleUri = null
        loadStyle(styleUri, force = true)
        if (recreateMapView) mapViewGeneration++
    }

    internal fun onMapLoadFailed() {
        if (loadState != GalleryMapLoadState.LOADING) return
        isStyleLoaded = false
        loadState = GalleryMapLoadState.FAILED
    }

    internal fun onMapLoadTimedOut(attempt: Long) {
        if (attempt == styleGeneration && loadState == GalleryMapLoadState.LOADING) {
            onMapLoadFailed()
        }
    }

    /**
     * Remove layers that are unnecessary for a photo-gallery map.
     * This reduces rendering cost and speeds up tile display.
     */
    private fun stripHeavyLayers(s: Style) {
        val removePrefixes = listOf(
            "building-3d",       // fill-extrusion is GPU-heavy
            "poi_",              // points of interest — not needed
            "aeroway_",          // airports / runways
            "airport",           // airport labels
            "tunnel_",           // detailed tunnel rendering
            "road_one_way",      // one-way arrows
            "highway-shield",    // road shields
            "road_shield",       // road shields (US)
            "natural_earth",     // raster satellite overlay at low zoom
        )
        val removeExact = setOf(
            "road_area_pattern", // pedestrian area patterns
            "landcover_wetland", // pattern-fill layer
        )
        for (layer in s.layers.toList()) {
            val id = layer.id
            if (id in removeExact || removePrefixes.any { id.startsWith(it) }) {
                s.removeLayer(id)
            }
        }
        // Also remove the raster source itself to stop tile fetches
        try { s.removeSource("ne2_shaded") } catch (_: Exception) {}
    }

    internal fun onMapViewDestroyed(ownerView: MapView, ownerMap: MapLibreMap?) {
        val ownsCurrentView = mapView === ownerView
        if (ownsCurrentView) mapView = null
        if (ownerMap != null && map === ownerMap) {
            map = null
            style = null
            loadedStyleUri = null
            isStyleLoaded = false
        }
        if (ownsCurrentView) styleGeneration++
    }
}

@Composable
fun rememberGalleryMapState(
    initialPosition: GalleryCameraPosition = GalleryCameraPosition(),
): GalleryMapState {
    return remember { GalleryMapState(initialPosition) }
}
