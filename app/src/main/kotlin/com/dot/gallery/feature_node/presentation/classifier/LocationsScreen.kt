/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.classifier

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.presentation.common.components.TwoLinedDateToolbarTitle
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.ui.theme.BlackScrim
import com.dot.gallery.ui.theme.WhiterBlackScrim
import com.dot.gallery.ui.theme.isDarkTheme

/**
 * Data class to group locations by country
 */
data class CountryLocations(
    val country: String,
    val locations: List<LocationMedia>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun LocationsScreen(
    metadataState: State<MediaMetadataState>,
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<CategoriesViewModel>()
    
    // Get locations from the ViewModel
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    
    // Group locations by country
    val locationsByCountry by remember(locations) {
        derivedStateOf {
            locations
                .groupBy { locationMedia ->
                    // Extract country from "City, Country" format
                    locationMedia.location.substringAfterLast(", ").trim()
                }
                .map { (country, locationMediaList) ->
                    CountryLocations(
                        country = country,
                        locations = locationMediaList.sortedBy { it.location }
                    )
                }
                .sortedBy { it.country }
        }
    }
    
    val totalLocationsCount by remember(locations) {
        derivedStateOf { locations.size }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    TwoLinedDateToolbarTitle(
                        albumName = stringResource(R.string.locations),
                        dateHeader = stringResource(R.string.locations_count, totalLocationsCount)
                    )
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            
            // Location rows grouped by country
            items(
                items = locationsByCountry,
                key = { it.country }
            ) { countryLocations ->
                CountryLocationRow(
                    countryLocations = countryLocations,
                    onLocationClick = { location ->
                        val gpsLocationNameCity = location.substringBefore(",").trim()
                        val gpsLocationNameCountry = location.substringAfterLast(", ").trim()
                        eventHandler.navigate(
                            Screen.LocationTimelineScreen.location(
                                gpsLocationNameCity = gpsLocationNameCity,
                                gpsLocationNameCountry = gpsLocationNameCountry
                            )
                        )
                    }
                )
            }
            
            // Empty state
            if (locationsByCountry.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_locations_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CountryLocationRow(
    countryLocations: CountryLocations,
    onLocationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Country header
        Text(
            text = countryLocations.country,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Horizontal scroll of locations for this country
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = countryLocations.locations,
                key = { it.location }
            ) { locationMedia ->
                LocationCard(
                    locationMedia = locationMedia,
                    onClick = { onLocationClick(locationMedia.location) }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun LocationCard(
    locationMedia: LocationMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isDarkTheme()
    val allowBlur by rememberAllowBlur()
    val followTheme = remember(allowBlur) { !allowBlur }
    val gradientColor by animateColorAsState(
        if (followTheme) {
            if (isDarkTheme) BlackScrim else WhiterBlackScrim
        } else BlackScrim,
        label = "gradientColor"
    )
    
    // Extract just the city name for display
    val cityName = locationMedia.location.substringBefore(",").trim()
    
    Box(
        modifier = modifier
            .width(164.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Get the URI properly based on media type
        val media = locationMedia.media
        val uri = when (media) {
            is Media.UriMedia -> media.getUri()
            else -> null
        }
        
        if (uri != null) {
            GlideImage(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                model = uri,
                contentDescription = locationMedia.location,
                requestBuilderTransform = {
                    if (media is Media.UriMedia) {
                        it.signature(GlideInvalidation.signature(media))
                    } else {
                        it
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        // Gradient overlay with city name
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, gradientColor)
                    )
                )
                .padding(16.dp)
        ) {
            Text(
                text = cityName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
