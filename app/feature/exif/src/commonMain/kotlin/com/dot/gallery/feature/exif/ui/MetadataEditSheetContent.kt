/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetSummaryTile
import com.dot.gallery.core.presentation.components.SheetTextField
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.fileExtension
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isVideo
import com.dot.gallery.feature.albums.ui.util.formatSize
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.exif.generated.resources.Res
import com.dot.gallery.feature.exif.generated.resources.action_cancel
import com.dot.gallery.feature.exif.generated.resources.action_save
import com.dot.gallery.feature.exif.generated.resources.description
import com.dot.gallery.feature.exif.generated.resources.edit_name_and_description
import com.dot.gallery.feature.exif.generated.resources.media_name
import com.dot.gallery.feature.exif.generated.resources.video_description_local_only

/**
 * Name + description editor sheet: a tile for the file being edited, a filled Name field, a
 * multi-line Description field and, for videos, a notice that the description stays on the
 * device. Applying the update (rename, description write, media scan) is the caller's job —
 * the content reports the intent via [onSave].
 */
@Composable
fun MetadataEditSheetContent(
    state: AppBottomSheetState,
    media: Media,
    metadata: MediaMetadata?,
    onSave: (newLabel: String, imageDescription: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var imageDescription by rememberSaveable(metadata) {
        mutableStateOf(metadata?.imageDescription)
    }
    var newLabel by rememberSaveable { mutableStateOf(media.label) }
    val title = stringResource(Res.string.edit_name_and_description)
    val changed = newLabel != media.label ||
        (imageDescription.orEmpty() != metadata?.imageDescription.orEmpty())

    ExpressiveSheet(sheetState = state, paneTitle = title) {
        SheetHeader(title = title)
        SheetSummaryTile(
            title = media.label,
            summary = listOfNotNull(
                media.fileExtension.uppercase().takeIf { it.isNotBlank() },
                media.size.takeIf { it > 0 }?.let { formatSize(it) },
            ).joinToString(" · ").ifEmpty { null },
            leading = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val model = media.getUri()
                    LocalMediaImageRenderer.current.RenderImage(
                        modifier = Modifier.matchParentSize(),
                        model = model,
                        contentScale = ContentScale.Crop,
                        contentDescription = null,
                        signature = model,
                    )
                }
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SheetTextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            label = stringResource(Res.string.media_name),
        )
        SheetTextField(
            value = imageDescription ?: "",
            onValueChange = { imageDescription = it },
            label = stringResource(Res.string.description),
            singleLine = false,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 112.dp),
        )

        // Video descriptions live in the app database only; say so before the user types one.
        AnimatedVisibility(visible = media.isVideo) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(Res.string.video_description_local_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        SheetDismissConfirmActions(
            dismissText = stringResource(Res.string.action_cancel),
            onDismiss = onCancel,
            confirmText = stringResource(Res.string.action_save),
            onConfirm = { onSave(newLabel, imageDescription) },
            confirmEnabled = changed && newLabel.isNotBlank(),
        )
    }
}
