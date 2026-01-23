package com.dot.gallery.feature_node.presentation.ignored.setup.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.PreviewHost

/**
 * A chip showing a selected album with thumbnail and remove capability.
 * Used in the horizontal list of selected albums in multiple selection mode.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SelectedAlbumChip(
    album: Album,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onRemove)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlideImage(
            model = album.uri,
            contentDescription = album.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp)),
            requestBuilderTransform = {
                it.signature(GlideInvalidation.signature(album))
            }
        )

        Text(
            text = album.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(end = 4.dp)
        )
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun SelectedAlbumChipPreview() {
    PreviewHost {
        SelectedAlbumChip(
            album = Album(
                id = 1,
                label = "Camera",
                uri = Uri.EMPTY,
                pathToThumbnail = "/DCIM/Camera/photo.jpg",
                relativePath = "DCIM/Camera",
                timestamp = 0,
                count = 42
            ),
            onRemove = {}
        )
    }
}

@Preview(showBackground = true, name = "Long Label")
@Composable
private fun SelectedAlbumChipLongLabelPreview() {
    PreviewHost {
        SelectedAlbumChip(
            album = Album(
                id = 2,
                label = "Very Long Album Name",
                uri = Uri.EMPTY,
                pathToThumbnail = "/Pictures/LongName/img.jpg",
                relativePath = "Pictures/LongName",
                timestamp = 0,
                count = 10
            ),
            onRemove = {}
        )
    }
}
