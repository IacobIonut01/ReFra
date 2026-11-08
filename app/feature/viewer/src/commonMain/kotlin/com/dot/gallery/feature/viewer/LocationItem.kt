package com.dot.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Position
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.domain.model.LocationData
import com.dot.gallery.domain.uri.Uri
import kotlinx.coroutines.launch


/**
 * Location group of the media-details sheet: a 104 dp map strip (when [mapPreview] is given)
 * over the place row. With maps the strip and the row open the location detail sheet;
 * without, the row goes straight to an external map via [onOpenMap].
 */
@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    locationData: LocationData?,
    mediaUri: Uri? = null,
    mapPreview: (@Composable (Modifier) -> Unit)? = null,
    onOpenMap: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onShowInApp: (() -> Unit)? = null,
) {
    val mapsEnabled = mapPreview != null
    val locationSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        modifier = modifier,
        visible = locationData != null,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        if (locationData != null) {
            val open: () -> Unit = {
                if (mapsEnabled) {
                    scope.launch { locationSheetState.show() }
                } else {
                    onOpenMap(locationData.latitude, locationData.longitude)
                }
            }
            val coordinates = remember(locationData) {
                formatCoordinates(locationData.latitude, locationData.longitude)
            }
            ViewerSheetGroup {
                if (mapPreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(viewerSheetCorners(Position.Top).toShape())
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = open),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        mapPreview(Modifier.fillMaxSize())
                    }
                }
                ViewerSheetRow(
                    title = locationData.location.ifBlank { coordinates },
                    summary = coordinates.takeIf { locationData.location.isNotBlank() },
                    icon = Icons.Outlined.LocationOn,
                    position = if (mapPreview != null) Position.Bottom else Position.Alone,
                    onClick = open,
                    trailing = {
                        ViewerSheetTrailingIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight)
                    },
                )
            }

            if (mapsEnabled && locationSheetState.isVisible) {
                LocationDetailSheet(
                    state = locationSheetState,
                    locationData = locationData,
                    mediaUri = mediaUri,
                    mapPreview = mapPreview,
                    onOpenMap = onOpenMap,
                    onShowInApp = onShowInApp ?: {},
                )
            }
        }
    }
}
