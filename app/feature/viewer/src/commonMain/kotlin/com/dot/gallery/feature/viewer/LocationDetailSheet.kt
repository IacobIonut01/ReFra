/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.sheetGroupedShape
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.domain.model.LocationData
import com.dot.gallery.domain.uri.Uri
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.location
import com.dot.gallery.feature.viewer.generated.resources.open_externally
import com.dot.gallery.feature.viewer.generated.resources.show_in_app
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun LocationDetailSheet(
    state: AppBottomSheetState,
    locationData: LocationData,
    mediaUri: Uri? = null,
    mapPreview: (@Composable (Modifier) -> Unit)? = null,
    onOpenMap: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    onShowInApp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ExpressiveSheet(sheetState = state, paneTitle = stringResource(Res.string.location)) {
        LocationDetailSheetContent(
            address = locationData.location,
            mediaUri = mediaUri,
            mapPreview = mapPreview,
            onOpenMap = {
                scope.launch { state.hide() }
                onOpenMap(locationData.latitude, locationData.longitude)
            },
            onShowInApp = {
                scope.launch { state.hide() }
                onShowInApp()
            },
        )
    }
}

/** Stateless body of [LocationDetailSheet]: map preview, address tile, Open in Maps / Show in app. */
@Composable
fun ColumnScope.LocationDetailSheetContent(
    address: String,
    mediaUri: Any?,
    mapPreview: (@Composable (Modifier) -> Unit)?,
    onOpenMap: () -> Unit,
    onShowInApp: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.location))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(bottom = 1.dp)
            .clip(sheetGroupedShape(Position.Top))
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
        mapPreview?.invoke(Modifier.fillMaxSize())
        if (mediaUri != null) {
            LocalMediaImageRenderer.current.RenderImage(
                model = mediaUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center)
                    .border(3.dp, Color.White, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
            )
        }
    }
    SheetOptionRow(
        title = address,
        icon = Icons.Outlined.LocationOn,
        position = Position.Bottom,
    )
    SheetActions {
        SheetButton(
            text = stringResource(Res.string.open_externally),
            onClick = onOpenMap,
            style = SheetButtonStyle.Tonal,
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            modifier = Modifier.weight(1f),
        )
        SheetButton(
            text = stringResource(Res.string.show_in_app),
            onClick = onShowInApp,
            icon = Icons.Outlined.Map,
            modifier = Modifier.weight(1f),
        )
    }
}
