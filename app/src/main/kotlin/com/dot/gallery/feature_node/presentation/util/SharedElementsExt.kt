@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package com.dot.gallery.feature_node.presentation.util


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize.Companion.contentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun <T: Media> Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    media: T,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = media.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    album: Album,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = album.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier {
    val shouldAnimate by Settings.Misc.rememberSharedElements()
    val boundsModifier = sharedBounds(
        rememberSharedContentState(key = "media_$key"),
        animatedVisibilityScope = animatedVisibilityScope,
        placeHolderSize = contentSize,
        boundsTransform = { _, _ -> tween(350) }
    )
    return remember(shouldAnimate, allowAnimation) {
        if (shouldAnimate && allowAnimation) boundsModifier else Modifier
    }
}