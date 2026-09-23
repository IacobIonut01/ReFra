package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.LocalMediaDistributor
import com.dot.gallery.core.LocalMediaHandler
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
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
    val distributor = LocalMediaDistributor.current
    val scope = rememberCoroutineScope()
    // The override makes the toggle feel instant: the heart flips immediately and the
    // distributor's optimistic mutation carries it into every media flow until the
    // underlying source catches up (or the entry expires/is reconciled).
    val favoriteOverrides by distributor.favoriteOverrides.collectAsStateWithLifecycle()
    val isFavorite = favoriteOverrides[media.id] ?: media.isFavorite
    val pulse = remember { Animatable(1f) }
    // "Animate media items" off — the heart flips with no pulse.
    val animationsEnabled = LocalMediaViewerVisualPolicy.current.animationsEnabled
    val result = rememberActivityResult(
        onResultCanceled = { distributor.clearFavoriteOverride(media.id) }
    )
    if (!media.readUriOnly) {
        MediaViewButton(
            currentMedia = media,
            imageVector = if (isFavorite) Icons.Filled.Favorite
            else Icons.Outlined.FavoriteBorder,
            followTheme = followTheme,
            title = stringResource(R.string.favorite),
            iconModifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
            enabled = enabled
        ) {
            scope.launch {
                val target = !isFavorite
                distributor.setFavoriteOverride(it.id, target)
                if (animationsEnabled) {
                    launch {
                        pulse.animateTo(1.35f, tween(110))
                        pulse.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    }
                }
                handler.toggleFavorite(result = result, arrayListOf(it), target)
            }
        }
    }
}
