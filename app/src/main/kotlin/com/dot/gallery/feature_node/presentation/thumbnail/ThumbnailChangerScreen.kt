package com.dot.gallery.feature_node.presentation.thumbnail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ThumbnailChangerScreen(
    navigateUp: () -> Unit,
) {
    val viewModel = hiltViewModel<ThumbnailChangerViewModel>()

    val hasAlbumThumbnail by viewModel.hasAlbumThumbnail.collectAsStateWithLifecycle()
    val albumThumbnail by viewModel.albumThumbnail.collectAsStateWithLifecycle()
    val albumDetails by viewModel.albumDetails.collectAsStateWithLifecycle()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

        }
    }
}