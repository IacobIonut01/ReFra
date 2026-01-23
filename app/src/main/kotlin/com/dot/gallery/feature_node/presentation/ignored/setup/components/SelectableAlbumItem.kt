package com.dot.gallery.feature_node.presentation.ignored.setup.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * A selectable album grid item with thumbnail, selection indicator,
 * and label. Supports disabled state for already-ignored albums.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SelectableAlbumItem(
    album: Album,
    isSelected: Boolean,
    isDisabled: Boolean,
    showCheckmark: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (isDisabled) 0.4f else 1f,
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDisabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                GlideImage(
                    model = album.uri,
                    contentDescription = album.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    alpha = alpha,
                    requestBuilderTransform = {
                        it.signature(GlideInvalidation.signature(album))
                    }
                )

                // Selection indicator
                if (showCheckmark && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = album.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 8.dp)
            )

            Text(
                text = "${album.count} items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)
            )
        }
    }
}

// ========== Previews ==========

@Preview(showBackground = true)
@Composable
private fun SelectableAlbumItemUnselectedPreview() {
    PreviewHost {
        SelectableAlbumItem(
            album = Album(
                id = 1,
                label = "Camera",
                uri = Uri.EMPTY,
                pathToThumbnail = "/DCIM/Camera/photo.jpg",
                relativePath = "DCIM/Camera",
                timestamp = 0,
                count = 42
            ),
            isSelected = false,
            isDisabled = false,
            showCheckmark = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectableAlbumItemSelectedPreview() {
    PreviewHost {
        SelectableAlbumItem(
            album = Album(
                id = 2,
                label = "Screenshots",
                uri = Uri.EMPTY,
                pathToThumbnail = "/Pictures/Screenshots/shot.png",
                relativePath = "Pictures/Screenshots",
                timestamp = 0,
                count = 156
            ),
            isSelected = true,
            isDisabled = false,
            showCheckmark = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Disabled")
@Composable
private fun SelectableAlbumItemDisabledPreview() {
    PreviewHost {
        SelectableAlbumItem(
            album = Album(
                id = 3,
                label = "Already Hidden",
                uri = Uri.EMPTY,
                pathToThumbnail = "/Pictures/Hidden/img.jpg",
                relativePath = "Pictures/Hidden",
                timestamp = 0,
                count = 10
            ),
            isSelected = false,
            isDisabled = true,
            showCheckmark = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Long Label")
@Composable
private fun SelectableAlbumItemLongLabelPreview() {
    PreviewHost {
        SelectableAlbumItem(
            album = Album(
                id = 4,
                label = "Very Long Album Name That Gets Truncated",
                uri = Uri.EMPTY,
                pathToThumbnail = "/Pictures/LongName/img.jpg",
                relativePath = "Pictures/LongName",
                timestamp = 0,
                count = 99
            ),
            isSelected = false,
            isDisabled = false,
            showCheckmark = true,
            onClick = {}
        )
    }
}
