package com.dot.gallery.feature_node.presentation.ignored.setup.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.ui.core.icons.RegularExpression
import com.dot.gallery.feature_node.presentation.common.components.OptionItem as OptionItemData
import com.dot.gallery.ui.core.Icons as GalleryIcons

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun OptionsMenuStep(
    ignoredAlbum: IgnoredAlbum,
    albumState: State<AlbumState>,
    isWildcard: Boolean,
    isMultiple: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    // Get thumbnail URIs for display
    val matchingAlbums by remember(ignoredAlbum, albumState.value.albumsWithBlacklisted) {
        derivedStateOf {
            val albumIdsToMatch = ignoredAlbum.albumIds.ifEmpty { listOf(ignoredAlbum.id) }
            albumIdsToMatch.take(2).mapNotNull { albumId ->
                albumState.value.albumsWithBlacklisted.find { it.id == albumId }
            }
        }
    }
    val primaryAlbum by remember(matchingAlbums) {
        derivedStateOf { matchingAlbums.getOrNull(0) }
    }
    val secondaryAlbum by remember(matchingAlbums) {
        derivedStateOf { matchingAlbums.getOrNull(1) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // Header with album info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Album Thumbnail(s)
                IgnoredAlbumThumbnail(
                    primaryAlbum = primaryAlbum,
                    secondaryAlbum = secondaryAlbum,
                    isMultiple = isMultiple,
                    isWildcard = isWildcard,
                    matchedCount = ignoredAlbum.matchedAlbums.size
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ignoredAlbum.label
                            ?: ignoredAlbum.matchedAlbums.firstOrNull()
                            ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isWildcard -> stringResource(R.string.setup_type_regex)
                            isMultiple -> stringResource(R.string.setup_type_multiple)
                            else -> stringResource(R.string.setup_type_single)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            val resources = LocalResources.current
            val containerColor = MaterialTheme.colorScheme.errorContainer
            val contentColor = MaterialTheme.colorScheme.onErrorContainer
            val options = remember(resources) {
                mutableStateListOf(
                    OptionItemData(
                        icon = Icons.Outlined.Edit,
                        text = resources.getString(R.string.action_edit),
                        onClick = { onEdit() }
                    ),
                    OptionItemData(
                        icon = Icons.Outlined.Delete,
                        text = resources.getString(R.string.action_delete),
                        containerColor = containerColor,
                        contentColor = contentColor,
                        onClick = { onDelete() }
                    )
                )
            }

            OptionLayout(
                modifier = Modifier.navigationBarsPadding(),
                optionList = options
            )
        }

    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun IgnoredAlbumThumbnail(
    primaryAlbum: Album?,
    secondaryAlbum: Album?,
    isMultiple: Boolean,
    isWildcard: Boolean,
    matchedCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(128.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isMultiple && primaryAlbum != null && secondaryAlbum != null) {
            // Stacked thumbnails
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                GlideImage(
                    model = secondaryAlbum.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .offset(x = 4.dp, y = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                GlideImage(
                    model = primaryAlbum.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Badge
            if (matchedCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$matchedCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    primaryAlbum != null -> {
                        GlideImage(
                            model = primaryAlbum.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    isWildcard -> {
                        Icon(
                            imageVector = GalleryIcons.RegularExpression,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.PhotoAlbum,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Preview helpers
private val previewAlbum = Album(
    id = 1,
    label = "Camera",
    uri = Uri.EMPTY,
    pathToThumbnail = "",
    relativePath = "DCIM/Camera",
    timestamp = System.currentTimeMillis(),
    count = 100
)

private val previewAlbum2 = Album(
    id = 2,
    label = "Screenshots",
    uri = Uri.EMPTY,
    pathToThumbnail = "",
    relativePath = "Pictures/Screenshots",
    timestamp = System.currentTimeMillis(),
    count = 50
)

private val previewIgnoredSingle = IgnoredAlbum(
    id = 1,
    label = "Single #1",
    location = IgnoredAlbum.ALBUMS_ONLY,
    matchedAlbums = listOf("Camera")
)

private val previewIgnoredMultiple = IgnoredAlbum(
    id = 100,
    label = "Multiple #1",
    albumIds = listOf(1, 2, 3),
    location = IgnoredAlbum.ALBUMS_AND_TIMELINE,
    matchedAlbums = listOf("Camera", "Screenshots", "Downloads")
)

private val previewIgnoredRegex = IgnoredAlbum(
    id = 200,
    label = "Regex #1",
    wildcard = ".*Music.*",
    location = IgnoredAlbum.TIMELINE_ONLY,
    matchedAlbums = listOf("Music", "Music Videos")
)

@Preview(showBackground = true)
@Composable
private fun OptionsMenuStepSinglePreview() {
    PreviewHost {
        OptionsMenuStep(
            ignoredAlbum = previewIgnoredSingle,
            albumState = remember { mutableStateOf(AlbumState()) },
            isWildcard = false,
            isMultiple = false,
            onEdit = {},
            onDelete = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionsMenuStepMultiplePreview() {
    PreviewHost {
        OptionsMenuStep(
            ignoredAlbum = previewIgnoredMultiple,
            albumState = remember { mutableStateOf(AlbumState()) },
            isWildcard = false,
            isMultiple = true,
            onEdit = {},
            onDelete = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionsMenuStepRegexPreview() {
    PreviewHost {
        OptionsMenuStep(
            ignoredAlbum = previewIgnoredRegex,
            albumState = remember { mutableStateOf(AlbumState()) },
            isWildcard = true,
            isMultiple = false,
            onEdit = {},
            onDelete = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IgnoredAlbumThumbnailSinglePreview() {
    PreviewHost {
        IgnoredAlbumThumbnail(
            primaryAlbum = null,
            secondaryAlbum = null,
            isMultiple = false,
            isWildcard = false,
            matchedCount = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IgnoredAlbumThumbnailWildcardPreview() {
    PreviewHost {
        IgnoredAlbumThumbnail(
            primaryAlbum = null,
            secondaryAlbum = null,
            isMultiple = false,
            isWildcard = true,
            matchedCount = 5
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, name = "Landscape")
@Composable
private fun OptionsMenuStepLandscapePreview() {
    PreviewHost {
        OptionsMenuStep(
            ignoredAlbum = previewIgnoredMultiple,
            albumState = remember { mutableStateOf(AlbumState()) },
            isWildcard = false,
            isMultiple = true,
            onEdit = {},
            onDelete = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 768, name = "Tablet")
@Composable
private fun OptionsMenuStepTabletPreview() {
    PreviewHost {
        OptionsMenuStep(
            ignoredAlbum = previewIgnoredRegex,
            albumState = remember { mutableStateOf(AlbumState()) },
            isWildcard = true,
            isMultiple = false,
            onEdit = {},
            onDelete = {},
            onCancel = {}
        )
    }
}
