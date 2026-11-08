/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.location

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.GalleryDetailAction
import com.dot.gallery.core.presentation.components.GalleryDetailCover
import com.dot.gallery.core.presentation.components.GalleryDetailHeader
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.place_detail_map
import com.dot.gallery.generated.resources.share
import com.dot.gallery.generated.resources.slideshow
import org.jetbrains.compose.resources.stringResource

/**
 * Header of the place detail screen: a rounded cover from the place's newest photo, the place
 * name, a stats line (item count, region and year range) and the Map, Slideshow and Share
 * actions. Actions whose callback is null are hidden.
 */
@Composable
fun PlaceDetailHeader(
    title: String,
    stats: String,
    coverModel: Any?,
    modifier: Modifier = Modifier,
    coverSignature: Any? = null,
    onMap: (() -> Unit)? = null,
    onSlideshow: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    GalleryDetailHeader(
        title = title,
        stats = stats,
        modifier = modifier.padding(top = 8.dp),
        cover = {
            GalleryDetailCover(
                model = coverModel,
                signature = coverSignature,
                fallbackIcon = Icons.Outlined.LocationOn,
            )
        },
        actions = if (onMap != null || onSlideshow != null || onShare != null) {
            {
                onMap?.let { GalleryDetailAction(stringResource(Res.string.place_detail_map), Icons.Outlined.Map, it) }
                onSlideshow?.let { GalleryDetailAction(stringResource(Res.string.slideshow), Icons.Outlined.Slideshow, it) }
                onShare?.let { GalleryDetailAction(stringResource(Res.string.share), Icons.Outlined.Share, it) }
            }
        } else null,
    )
}
