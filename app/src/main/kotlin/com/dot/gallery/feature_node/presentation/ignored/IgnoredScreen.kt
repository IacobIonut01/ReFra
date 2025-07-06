package com.dot.gallery.feature_node.presentation.ignored

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.ui.core.icons.RegularExpression
import com.dot.gallery.ui.core.Icons as GalleryIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredScreen(
    startSetup: () -> Unit,
    albumsState: State<AlbumState>,
) {
    val vm = hiltViewModel<IgnoredViewModel>()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val state by vm.blacklistState.collectAsStateWithLifecycle(IgnoredState())
    var toBeRemoved by remember(state) {
        mutableStateOf<IgnoredAlbum?>(null)
    }
    val context = LocalContext.current
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ignored_albums),
                    )
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        modifier = Modifier
                            .padding(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(16.dp),
                        text = stringResource(R.string.ignored_albums_text),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                if (state.albums.isEmpty()) {
                    item {
                        NoIgnoredAlbums()
                    }
                } else {
                    item {
                        SettingsItem(
                            item = SettingsEntity.Header(stringResource(R.string.created))
                        )
                    }
                }
                itemsIndexed(
                    items = state.albums,
                    key = { _, album -> album.id }
                ) { index, blacklistedAlbum ->
                    val position = remember(state.albums) {
                        if (index == 0) {
                            if (state.albums.size == 1) Position.Alone
                            else Position.Top
                        } else if (index == state.albums.size - 1) Position.Bottom
                        else Position.Middle
                    }
                    val wildcardSummary: String = remember(blacklistedAlbum, albumsState) {
                        if (blacklistedAlbum.wildcard != null) {
                            context.getString(
                                R.string.wildcard_summary_first,
                                blacklistedAlbum.wildcard,
                                blacklistedAlbum.matchedAlbums.joinToString()
                            )
                        } else context.getString(
                            R.string.matched_albums,
                            blacklistedAlbum.matchedAlbums.joinToString()
                        )
                    }
                    SettingsItem(
                        item = SettingsEntity.Preference(
                            icon = remember(blacklistedAlbum) {
                                if (blacklistedAlbum.wildcard != null) {
                                    GalleryIcons.RegularExpression
                                } else Icons.Outlined.PhotoAlbum
                            },
                            title = blacklistedAlbum.label,
                            summary = wildcardSummary,
                            screenPosition = position,
                            onClick = {
                                toBeRemoved = blacklistedAlbum
                            }
                        )
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    startSetup()
                },
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.BottomEnd),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
            }

        }
    }

    if (toBeRemoved != null) {
        AlertDialog(
            onDismissRequest = { toBeRemoved = null },
            confirmButton = {
                Button(
                    onClick = {
                        vm.removeFromBlacklist(toBeRemoved!!)
                        toBeRemoved = null
                    }
                ) {
                    Text(text = stringResource(id = R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { toBeRemoved = null }
                ) {
                    Text(text = stringResource(id = R.string.action_cancel))
                }
            },
            title = {
                Text(text = stringResource(R.string.remove_from_ignored))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.remove_from_ignored_summary,
                        toBeRemoved!!.label
                    )
                )
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}

@Composable
fun NoIgnoredAlbums(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val alphas = floatArrayOf(0.6f, 0.4f, 0.2f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            alphas.forEach {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = it),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        Text(
            text = stringResource(R.string.no_ignored_albums),
            style = MaterialTheme.typography.titleLarge ,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    }
}

