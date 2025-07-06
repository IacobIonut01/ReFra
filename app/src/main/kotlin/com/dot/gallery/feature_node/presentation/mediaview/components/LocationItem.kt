package com.dot.gallery.feature_node.presentation.mediaview.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.feature_node.domain.model.LocationData
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.MapBoxURL
import com.dot.gallery.feature_node.presentation.util.connectivityState
import com.dot.gallery.feature_node.presentation.util.launchMap
import com.dot.gallery.ui.theme.Shapes
import com.github.panpf.sketch.AsyncImage
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.ExperimentalCoroutinesApi

@Suppress("KotlinConstantConditions")
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    locationData: LocationData?
) {
    val isBlurEnabled by rememberAllowBlur()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val sheetCardLocationHazeStyle = HazeMaterials.ultraThick(
        containerColor = surfaceColor.copy(alpha = 0.4f)
    )
    val sheetCardLocationBackgroundModifier = remember(isBlurEnabled) {
        if (!isBlurEnabled) Modifier.background(
            color = surfaceColor.copy(alpha = 0.4f),
            shape = RoundedCornerShape(2.dp)
        ) else Modifier
    }
    AnimatedVisibility(
        visible = locationData != null,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        if (locationData != null) {
            val context = LocalContext.current
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .then(sheetCardLocationBackgroundModifier)
                    .hazeEffect(
                        state = LocalHazeState.current,
                        style = sheetCardLocationHazeStyle,
                    )
                    .clickable {
                        context.launchMap(locationData.latitude, locationData.longitude)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                var locationDataHeight by rememberSaveable(locationData) {
                    mutableFloatStateOf(0f)
                }
                val density = LocalDensity.current.density
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .onGloballyPositioned {
                            val newHeight = it.size.height / density
                            if (locationDataHeight != newHeight) {
                                locationDataHeight = newHeight
                            }
                        }
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.location),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                    Text(
                        text = locationData.location,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                val connection by connectivityState()

                AnimatedVisibility(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(locationDataHeight.dp)
                        .aspectRatio(1f)
                        .clip(Shapes.large),
                    visible = remember(connection) {
                        connection.isConnected() && BuildConfig.MAPS_TOKEN != "DEBUG"
                    }
                ) {
                    AsyncImage(
                        uri = MapBoxURL(
                            latitude = locationData.latitude,
                            longitude = locationData.longitude,
                            darkTheme = isSystemInDarkTheme()
                        ),
                        contentScale = ContentScale.Crop,
                        contentDescription = stringResource(R.string.location_map_cd),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(Shapes.large)
                    )
                }
            }
        }
    }
}