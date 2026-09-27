/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.MemoryInfo
import com.dot.gallery.core.presentation.components.LoadingMedia
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen() {
    val viewModel = hiltViewModel<MemoriesViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.hazeEffectScaled(
                    state = LocalHazeState.current,
                    style = LocalHazeBlurStyle.current
                ),
                title = { Text(stringResource(R.string.cloud_memories)) },
                navigationIcon = { NavigationBackButton() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        when {
            state.isLoading -> {
                LoadingMedia(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            state.memories.isEmpty() -> {
                EmptyMemories(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDir) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDir) + 16.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.memories, key = { it.accountKey }) { memory ->
                        MemoryCard(memory = memory)
                    }
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyMemories(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(
            modifier = Modifier.size(128.dp),
            imageVector = Icons.Outlined.PhotoAlbum,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.cloud_memories_empty),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.cloud_memories_empty_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemoryCard(memory: MemoryInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (memory.year > 0) "${memory.year}" else stringResource(R.string.cloud_memories_memory),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (memory.year > 0) stringResource(R.string.cloud_memories_years_ago, java.time.Year.now().value - memory.year)
                else memory.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cloud_person_photo_count, memory.assetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
