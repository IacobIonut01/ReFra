package com.dot.gallery.feature_node.presentation.mediaview.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.LocationData
import com.dot.gallery.feature_node.presentation.util.StaticMapURL
import com.dot.gallery.feature_node.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.feature_node.presentation.util.launchMap
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@Suppress("KotlinConstantConditions")
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    iconBackgroundModifier: Modifier = Modifier,
    locationData: LocationData?,
    mediaUri: Uri? = null,
    onShowInApp: (() -> Unit)? = null,
) {
    val mapsEnabled = remember { BuildConfig.MAPS_ENABLED }
    val locationSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    val mapAppearance by Settings.Misc.rememberMapAppearance()
    val userCartoKey by Settings.Misc.rememberCartoBasemapKey()
    val effectiveAppIsDark = isDarkTheme()
    val cartoKey = remember(userCartoKey) {
        effectiveCartoBasemapKey(BuildConfig.CARTO_BASEMAP_KEY, userCartoKey)
    }

    AnimatedVisibility(
        visible = locationData != null,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        if (locationData != null) {
            val context = LocalContext.current
            val mapTileUrl = remember(
                locationData.latitude,
                locationData.longitude,
                mapAppearance,
                effectiveAppIsDark,
                cartoKey,
            ) {
                StaticMapURL(
                    latitude = locationData.latitude,
                    longitude = locationData.longitude,
                    appearance = mapAppearance,
                    effectiveAppIsDark = effectiveAppIsDark,
                    apiKey = cartoKey,
                )
            }
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (mapsEnabled) {
                            scope.launch { locationSheetState.show() }
                        } else {
                            context.launchMap(locationData.latitude, locationData.longitude)
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(iconBackgroundModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.location),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = locationData.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = mapsEnabled) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = stringResource(R.string.location_map_cd),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GlideImage(
                            model = mapTileUrl,
                            contentDescription = stringResource(R.string.location_map_cd),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            requestBuilderTransform = {
                                it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            }
                        )
                    }
                }
            }

            if (mapsEnabled && locationSheetState.isVisible) {
                LocationDetailSheet(
                    state = locationSheetState,
                    locationData = locationData,
                    mediaUri = mediaUri,
                    onShowInApp = onShowInApp ?: {},
                )
            }
        }
    }
}