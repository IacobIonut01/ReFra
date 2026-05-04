/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.dot.gallery.feature_node.domain.model.GeoMedia
import com.dot.gallery.feature_node.domain.model.LocationMedia
import com.dot.gallery.feature_node.domain.model.MediaMetadataState

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun MapLocationsContent(
    metadataState: State<MediaMetadataState>,
    locations: List<LocationMedia> = emptyList(),
    geoMedia: List<GeoMedia> = emptyList(),
) {
    ListLocationsContent(metadataState = metadataState, locations = locations)
}
