/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.dot.gallery.domain.model.GeoMedia
import com.dot.gallery.domain.model.LocationMedia
import com.dot.gallery.data.model.MediaMetadataState


/** This build has no map: the Locations screen opens straight on the places page. */
internal const val HasInteractiveMap = false

@Suppress("UNUSED_PARAMETER")
@Composable
fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
    geoMedia: List<GeoMedia> = emptyList(),
    initialMediaId: Long = -1L,
    onMapFullyRendered: () -> Unit = {},
    onShowPlaces: (() -> Unit)? = null,
) {
    ListLocationsContent(metadataState = metadataState, locations = locations, geoMedia = geoMedia)
}
