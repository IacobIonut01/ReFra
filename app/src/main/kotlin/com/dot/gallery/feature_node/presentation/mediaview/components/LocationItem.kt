package com.dot.gallery.feature_node.presentation.mediaview.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.BuildConfig
import com.dot.gallery.R
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.LocationData
import com.dot.gallery.feature_node.presentation.util.StaticMapPreview
import com.dot.gallery.feature_node.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.feature_node.presentation.util.launchMap
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.ui.theme.isDarkTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@Suppress("KotlinConstantConditions")
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
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StaticMapPreview(
                            latitude = locationData.latitude,
                            longitude = locationData.longitude,
                            appearance = mapAppearance,
                            effectiveAppIsDark = effectiveAppIsDark,
                            zoom = 12,
                            apiKey = cartoKey,
                            contentDescription = stringResource(R.string.location_map_cd),
                            modifier = Modifier.fillMaxSize(),
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

/**
 * Shown in place of the location row when a local image reports no coordinates and
 * ACCESS_MEDIA_LOCATION is missing — without it MediaProvider serves EXIF with the
 * GPS ranges zeroed, so the most likely reason there is nothing to show is redaction.
 * First tap asks for the permission; if the user already denied once, further taps go
 * to the app details settings page (same escalation as the setup screen).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationAccessItem(
    modifier: Modifier = Modifier,
    iconBackgroundModifier: Modifier = Modifier,
    onGranted: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    var requestAttempted by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_MEDIA_LOCATION
    ) { granted ->
        if (granted) onGranted()
    }
    AnimatedVisibility(
        visible = !locationPermission.status.isGranted,
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (requestAttempted) {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    } else {
                        requestAttempted = true
                        locationPermission.launchPermissionRequest()
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
                    text = stringResource(R.string.location_access_row_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}