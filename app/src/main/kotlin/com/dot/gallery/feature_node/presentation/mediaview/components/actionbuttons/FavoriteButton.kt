package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.isFavorite
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.util.rememberActivityResult
import kotlinx.coroutines.launch

@Composable
fun <T : Media> FavoriteButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean = false
) {
    val handler = LocalMediaHandler.current
    val scope = rememberCoroutineScope()
    var lastFavorite = remember(media) { media.isFavorite }
    val result = rememberActivityResult(
        onResultOk = {
            lastFavorite = !lastFavorite
        }
    )
    val favoriteIcon by remember(lastFavorite) {
        mutableStateOf(
            if (lastFavorite)
                Icons.Filled.Favorite
            else Icons.Outlined.FavoriteBorder
        )
    }
    if (!media.readUriOnly) {
        MediaViewButton(
            currentMedia = media,
            imageVector = favoriteIcon,
            followTheme = followTheme,
            title = stringResource(R.string.favorite),
            enabled = enabled
        ) {
            scope.launch {
                handler.toggleFavorite(result = result, arrayListOf(it), it.favorite != 1)
            }
        }
    }
}