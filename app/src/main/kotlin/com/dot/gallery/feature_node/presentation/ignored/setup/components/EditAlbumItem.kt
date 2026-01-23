package com.dot.gallery.feature_node.presentation.ignored.setup.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun EditAlbumItem(
    album: Album,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else Modifier
                )
                .clickable(onClick = onClick)
                .fillMaxSize(),
        ) {
            GlideImage(
                modifier = Modifier.fillMaxSize(),
                model = album.uri,
                contentDescription = album.label,
                contentScale = ContentScale.Crop,
            )
            // Selection indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                )
            }
        }

        Text(
            text = album.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

}

private val previewAlbum = Album(
    id = 1,
    label = "Camera",
    uri = Uri.EMPTY,
    pathToThumbnail = "",
    relativePath = "DCIM/Camera",
    timestamp = System.currentTimeMillis(),
    count = 100
)

@Preview(showBackground = true, widthDp = 150, heightDp = 200)
@Composable
private fun EditAlbumItemUnselectedPreview() {
    PreviewHost {
        EditAlbumItem(
            album = previewAlbum,
            isSelected = false,
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 150, heightDp = 200)
@Composable
private fun EditAlbumItemSelectedPreview() {
    PreviewHost {
        EditAlbumItem(
            album = previewAlbum,
            isSelected = true,
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 150, heightDp = 200)
@Composable
private fun EditAlbumItemLongLabelPreview() {
    PreviewHost {
        EditAlbumItem(
            album = previewAlbum.copy(label = "Very Long Album Name That Should Be Truncated"),
            isSelected = false,
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}
