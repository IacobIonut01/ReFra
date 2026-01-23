package com.dot.gallery.feature_node.presentation.ignored.setup.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.Constants.albumCellsList
import com.dot.gallery.core.Settings.Album.rememberAlbumGridSize
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.matchesAlbum
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlbumsStep(
    isWildcard: Boolean,
    isMultiple: Boolean,
    regex: String,
    selectedAlbums: List<Album>,
    albumState: State<AlbumState>,
    onRegexChanged: (String) -> Unit,
    onAlbumToggled: (Album) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val albums by remember(albumState.value.albumsWithBlacklisted) {
        derivedStateOf { albumState.value.albumsWithBlacklisted }
    }
    val gridState = rememberLazyGridState()
    val albumGridSize by rememberAlbumGridSize()
    val gridCells = remember(albumGridSize) {
        albumCellsList[albumGridSize.coerceAtLeast(4)]
    }

    val regexMatchedAlbums = remember(regex, albums) {
        if (isWildcard && regex.isNotEmpty()) {
            try {
                val regexPattern = regex.toRegex()
                albums.filter(regexPattern::matchesAlbum)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val canProceed by remember(isWildcard, isMultiple, regex, regexMatchedAlbums, selectedAlbums) {
        derivedStateOf {
            when {
                isWildcard -> regex.isNotEmpty() && regexMatchedAlbums.isNotEmpty()
                isMultiple -> selectedAlbums.size >= 2
                else -> selectedAlbums.isNotEmpty()
            }
        }
    }

    val displayAlbums by remember(isWildcard, regexMatchedAlbums, albums) {
        derivedStateOf {
            if (isWildcard) regexMatchedAlbums else albums
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_albums),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    NavigationBackButton(forcedAction = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Button(
                onClick = onNext,
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(text = stringResource(R.string.action_continue))
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            state = gridState,
            columns = gridCells,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isWildcard) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = regex,
                            onValueChange = onRegexChanged,
                            label = { Text(stringResource(R.string.setup_album_regex_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        if (regexMatchedAlbums.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.setup_album_regex_matched, regexMatchedAlbums.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (isMultiple) {
                            stringResource(R.string.setup_album_multiple_select)
                        } else {
                            stringResource(R.string.setup_album_single_select)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(
                items = displayAlbums,
                key = { it.id }
            ) { album ->
                val isSelected = if (isWildcard) true else album in selectedAlbums
                EditAlbumItem(
                    album = album,
                    isSelected = isSelected,
                    onClick = { if (!isWildcard) onAlbumToggled(album) }
                )
            }
        }
    }
}

private val previewAlbums = listOf(
    Album(
        id = 1,
        label = "Camera",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "DCIM/Camera",
        timestamp = System.currentTimeMillis(),
        count = 100
    ),
    Album(
        id = 2,
        label = "Screenshots",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Pictures/Screenshots",
        timestamp = System.currentTimeMillis(),
        count = 50
    ),
    Album(
        id = 3,
        label = "Downloads",
        uri = Uri.EMPTY,
        pathToThumbnail = "",
        relativePath = "Download",
        timestamp = System.currentTimeMillis(),
        count = 25
    )
)

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun EditAlbumsStepSinglePreview() {
    PreviewHost {
        val selectedAlbums = remember { mutableStateListOf<Album>() }
        EditAlbumsStep(
            isWildcard = false,
            isMultiple = false,
            regex = "",
            selectedAlbums = selectedAlbums,
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = {},
            onAlbumToggled = { album ->
                selectedAlbums.clear()
                selectedAlbums.add(album)
            },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun EditAlbumsStepMultiplePreview() {
    PreviewHost {
        val selectedAlbums = remember { mutableStateListOf(previewAlbums[0], previewAlbums[1]) }
        EditAlbumsStep(
            isWildcard = false,
            isMultiple = true,
            regex = "",
            selectedAlbums = selectedAlbums,
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = {},
            onAlbumToggled = { album ->
                if (album in selectedAlbums) {
                    selectedAlbums.remove(album)
                } else {
                    selectedAlbums.add(album)
                }
            },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun EditAlbumsStepRegexEmptyPreview() {
    PreviewHost {
        var regex by remember { mutableStateOf("") }
        EditAlbumsStep(
            isWildcard = true,
            isMultiple = false,
            regex = regex,
            selectedAlbums = emptyList(),
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = { regex = it },
            onAlbumToggled = {},
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun EditAlbumsStepRegexWithMatchesPreview() {
    PreviewHost {
        var regex by remember { mutableStateOf(".*Camera.*") }
        EditAlbumsStep(
            isWildcard = true,
            isMultiple = false,
            regex = regex,
            selectedAlbums = emptyList(),
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = { regex = it },
            onAlbumToggled = {},
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, name = "Landscape")
@Composable
private fun EditAlbumsStepLandscapePreview() {
    PreviewHost {
        EditAlbumsStep(
            isWildcard = false,
            isMultiple = true,
            regex = "",
            selectedAlbums = listOf(previewAlbums[0], previewAlbums[2]),
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = {},
            onAlbumToggled = {},
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 768, name = "Tablet")
@Composable
private fun EditAlbumsStepTabletPreview() {
    PreviewHost {
        var regex by remember { mutableStateOf(".*Music.*") }
        EditAlbumsStep(
            isWildcard = true,
            isMultiple = false,
            regex = regex,
            selectedAlbums = emptyList(),
            albumState = remember { mutableStateOf(AlbumState(albumsWithBlacklisted = previewAlbums)) },
            onRegexChanged = { regex = it },
            onAlbumToggled = {},
            onBack = {},
            onNext = {}
        )
    }
}
