package com.dot.gallery.feature_node.presentation.util

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.feature_node.presentation.location.MapAppearance
import kotlin.math.roundToInt

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun StaticMapPreview(
    latitude: Double,
    longitude: Double,
    appearance: MapAppearance,
    effectiveAppIsDark: Boolean,
    zoom: Int,
    apiKey: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    BoxWithConstraints(modifier.then(semanticsModifier).clipToBounds()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val placements = remember(latitude, longitude, zoom, widthPx, heightPx) {
            StaticMapURL.centeredTiles(
                latitude = latitude,
                longitude = longitude,
                zoom = zoom,
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
            )
        }
        StaticMapTileGrid(placements, Modifier.fillMaxSize()) { placement ->
            GlideImage(
                model = StaticMapURL.tileUrl(
                    tileX = placement.tileX,
                    tileY = placement.tileY,
                    appearance = appearance,
                    effectiveAppIsDark = effectiveAppIsDark,
                    zoom = zoom,
                    apiKey = apiKey,
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                requestBuilderTransform = {
                    it.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                },
            )
        }
    }
}

@Composable
internal fun StaticMapTileGrid(
    placements: List<StaticMapTilePlacement>,
    modifier: Modifier = Modifier,
    tileContent: @Composable (StaticMapTilePlacement) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            placements.forEach { placement ->
                key(placement.tileX, placement.tileY) {
                    tileContent(placement)
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val size = placements[index].sizePx.roundToInt()
            measurable.measure(Constraints.fixed(size, size))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.place(placement.leftPx.roundToInt(), placement.topPx.roundToInt())
            }
        }
    }
}
