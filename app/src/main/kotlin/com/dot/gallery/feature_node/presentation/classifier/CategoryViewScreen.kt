package com.dot.gallery.feature_node.presentation.classifier

import android.app.Activity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.common.MediaScreen
import com.dot.gallery.feature_node.presentation.util.Screen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryViewScreen(
    navigateUp: () -> Unit,
    navigate: (String) -> Unit,
    toggleNavbar: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    category: String
) {
    val viewModel = hiltViewModel<CategoryViewModel>().apply {
        this.category = category
    }

    LaunchedEffect(category) {
        if (category.isEmpty()) {
            navigateUp()
        }
    }

    val mediaState = viewModel.mediaByCategory.collectAsStateWithLifecycle()

    MediaScreen(
        albumName = category,
        customDateHeader = stringResource(R.string.s_items,  mediaState.value.media.size),
        selectedMedia = viewModel.selectedMedia,
        selectionState = viewModel.selectionState,
        mediaState = mediaState,
        target = "category_$category",
        toggleSelection = {
            viewModel.toggleSelection(mediaState.value, it)
        },
        navigateUp = navigateUp,
        handler = viewModel.handler,
        navActionsContent = { expandedDropDown, result ->
        },
        navigate = navigate,
        onActivityResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.selectedMedia.clear()
                viewModel.selectionState.value = false
            }
        },
        toggleNavbar = toggleNavbar,
        customViewingNavigation = { media ->
            navigate(Screen.MediaViewScreen.idAndCategory(media.id, category))
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope
    )
}