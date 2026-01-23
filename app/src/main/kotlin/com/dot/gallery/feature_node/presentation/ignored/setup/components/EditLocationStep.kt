package com.dot.gallery.feature_node.presentation.ignored.setup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLocationStep(
    location: Int,
    onLocationChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val albumsLabel = stringResource(R.string.setup_location_options_albums)
    val timelineLabel = stringResource(R.string.setup_location_options_timeline)
    val bothLabel = stringResource(R.string.setup_location_options_both)
    
    val locationOptions = remember(albumsLabel, timelineLabel, bothLabel) {
        mapOf(
            albumsLabel to IgnoredAlbum.ALBUMS_ONLY,
            timelineLabel to IgnoredAlbum.TIMELINE_ONLY,
            bothLabel to IgnoredAlbum.ALBUMS_AND_TIMELINE,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_location),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(text = stringResource(R.string.action_continue))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.setup_location_location_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    locationOptions.entries.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = location == value,
                            onClick = { onLocationChanged(value) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = locationOptions.size
                            )
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditLocationStepAlbumsOnlyPreview() {
    PreviewHost {
        var location by remember { mutableIntStateOf(IgnoredAlbum.ALBUMS_ONLY) }
        EditLocationStep(
            location = location,
            onLocationChanged = { location = it },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditLocationStepTimelineOnlyPreview() {
    PreviewHost {
        var location by remember { mutableIntStateOf(IgnoredAlbum.TIMELINE_ONLY) }
        EditLocationStep(
            location = location,
            onLocationChanged = { location = it },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditLocationStepBothPreview() {
    PreviewHost {
        var location by remember { mutableIntStateOf(IgnoredAlbum.ALBUMS_AND_TIMELINE) }
        EditLocationStep(
            location = location,
            onLocationChanged = { location = it },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, name = "Landscape")
@Composable
private fun EditLocationStepLandscapePreview() {
    PreviewHost {
        var location by remember { mutableIntStateOf(IgnoredAlbum.TIMELINE_ONLY) }
        EditLocationStep(
            location = location,
            onLocationChanged = { location = it },
            onBack = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1024, heightDp = 768, name = "Tablet")
@Composable
private fun EditLocationStepTabletPreview() {
    PreviewHost {
        var location by remember { mutableIntStateOf(IgnoredAlbum.ALBUMS_AND_TIMELINE) }
        EditLocationStep(
            location = location,
            onLocationChanged = { location = it },
            onBack = {},
            onNext = {}
        )
    }
}
