/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.LocationData
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.StaticMapURL
import com.dot.gallery.feature_node.presentation.util.effectiveCartoBasemapKey
import com.dot.gallery.feature_node.presentation.util.launchMap
import com.dot.gallery.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun LocationDetailSheet(
    state: AppBottomSheetState,
    locationData: LocationData,
    mediaUri: Uri? = null,
    onShowInApp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mapAppearance by Settings.Misc.rememberMapAppearance()
    val userCartoKey by Settings.Misc.rememberCartoBasemapKey()
    val effectiveAppIsDark = isDarkTheme()
    val cartoKey = remember(userCartoKey) {
        effectiveCartoBasemapKey(BuildConfig.CARTO_BASEMAP_KEY, userCartoKey)
    }

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
            zoom = 14,
            apiKey = cartoKey,
        )
    }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch { state.hide() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Map preview with media thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
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

                    if (mediaUri != null) {
                        GlideImage(
                            model = mediaUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .align(Alignment.Center),
                            contentScale = ContentScale.Crop,
                            requestBuilderTransform = {
                                it.diskCacheStrategy(DiskCacheStrategy.ALL)
                            }
                        )
                    }
                }

                // Location info row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetupButton(
                        onClick = {
                            scope.launch { state.hide() }
                            onShowInApp()
                        },
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.show_in_app)
                    )
                    SetupButton(
                        onClick = {
                            scope.launch { state.hide() }
                            context.launchMap(locationData.latitude, locationData.longitude)
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.open_externally)
                    )
                }
            }
        }
    }
}
