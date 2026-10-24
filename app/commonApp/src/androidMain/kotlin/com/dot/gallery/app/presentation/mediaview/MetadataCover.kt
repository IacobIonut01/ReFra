/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.feature.exif.ui.MetadataViewContent
import com.dot.gallery.injection.injectViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * "View all metadata" opened on top of a photo that should still be there when it closes.
 */
data class MetadataCoverRequest(
    val mediaUri: String,
    val isVideo: Boolean,
)

/**
 * The metadata route from the viewer's info sheet, or null when [route] is some other screen.
 * A null result keeps the existing "leave the viewer, then open the destination" path.
 */
fun metadataCoverRequest(route: String): MetadataCoverRequest? {
    if (route.substringBefore('?') != Screen.MetadataViewScreen.route) return null
    val query = route.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator < 0) null else {
                val key = parameter.substring(0, separator)
                val value = URLDecoder.decode(
                    parameter.substring(separator + 1),
                    StandardCharsets.UTF_8.name(),
                )
                key to value
            }
        }
        .toMap()
    val mediaUri = query["mediaUri"]?.takeIf { it.isNotEmpty() } ?: return null
    return MetadataCoverRequest(
        mediaUri = mediaUri,
        isVideo = query["isVideo"].toBoolean(),
    )
}

/**
 * In the main app the metadata screen covers the open photo. Standalone review already
 * keeps that photo behind its own metadata screen, so it keeps the old navigation.
 */
fun metadataCoverOverMedia(route: String, isStandalone: Boolean): MetadataCoverRequest? {
    if (isStandalone) return null
    return metadataCoverRequest(route)
}

@Composable
fun MetadataCoverLayer(
    request: MetadataCoverRequest,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = injectViewModel { metadataViewViewModel }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        viewModel.loadMetadata(request.mediaUri, request.isVideo)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // The photo is still composed underneath. Eat taps that miss the list so they
            // cannot dismiss or page that photo.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        MetadataViewContent(state = state, onBack = onBack)
    }
}
