/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri


/**
 * A card showing a static map preview with a circular photo thumbnail,
 * used in the Library screen to replace the plain "Locations" header.
 *
 * [mapContent] is the platform map renderer slot — the app supplies its
 * `StaticMapPreview` (basemap key + appearance resolution live app-side);
 * null leaves the placeholder icon, matching "maps disabled" builds.
 */
@Composable
fun MapPreviewCard(
    modifier: Modifier = Modifier,
    latestMedia: UriMedia?,
    latitude: Double?,
    longitude: Double?,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
    /** Null lets [modifier] size the card. */
    height: Dp? = 200.dp,
    thumbnailSize: Dp = 72.dp,
    thumbnailBottomInset: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // Map tile background
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (latitude != null && longitude != null) {
            mapContent?.invoke(this, latitude, longitude)
        }

        // Circular photo thumbnail at center
        if (latestMedia != null) {
            LocalMediaImageRenderer.current.RenderImage(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = thumbnailBottomInset)
                    .size(thumbnailSize)
                    .clip(CircleShape),
                model = latestMedia.getUri(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                signature = latestMedia
            )
        }
    }
}
