package com.dot.gallery.feature_node.presentation.classifier

import android.app.Activity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.navigate
import com.dot.gallery.core.navigateUp
import com.dot.gallery.feature_node.domain.model.MediaMetadataState
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.clear

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryViewScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    metadataState: State<MediaMetadataState>,
    category: String
) {
    val eventHandler = LocalEventHandler.current
    val viewModel = hiltViewModel<CategoryViewModel>().apply {
        this.category = category
    }

    LaunchedEffect(category) {
        if (category.isEmpty()) {
            eventHandler.navigateUp()
        }
    }

    val mediaState = viewModel.mediaByCategory.collectAsStateWithLifecycle()

    MediaScreen(
        albumName = category,
        customDateHeader = stringResource(R.string.s_items,  mediaState.value.media.size),
        mediaState = mediaState,
        metadataState = metadataState,
        target = "category_$category",
        navActionsContent = { expandedDropDown, result ->
        },
        onActivityResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.selectedMedia.clear()
                viewModel.selectionState.value = false
            }
        },
        customViewingNavigation = { media ->
            eventHandler.navigate(Screen.MediaViewScreen.idAndCategory(media.id, category))
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )
}