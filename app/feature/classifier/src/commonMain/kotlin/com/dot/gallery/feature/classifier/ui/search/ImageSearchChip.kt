/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.image_search_preview
import com.dot.gallery.feature.classifier.generated.resources.remove_image


/**
 * Pill shown at the start of the search field while a photo drives visual search: tapping the
 * thumbnail previews it, the close glyph clears the selection.
 */
@Composable
fun ImageSearchChip(
    media: UriMedia,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRenderer = LocalMediaImageRenderer.current
    Row(
        modifier = modifier
            .padding(start = 8.dp)
            .height(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        imageRenderer.RenderImage(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            model = media.getUri(),
            contentDescription = stringResource(Res.string.image_search_preview),
            contentScale = ContentScale.Crop,
            signature = media
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.remove_image),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
