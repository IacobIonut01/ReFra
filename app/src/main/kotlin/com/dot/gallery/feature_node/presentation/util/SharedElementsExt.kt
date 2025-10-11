@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package com.dot.gallery.feature_node.presentation.util


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun <T: Media> Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    media: T,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = media.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    album: Album,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = album.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(namedSharedTransitionScope) {
    val shouldAnimate by Settings.Misc.rememberSharedElements()
    val boundsModifier = sharedBounds(
        sharedContentState = rememberSharedContentState(key =  "media_$key"),
        animatedVisibilityScope = animatedVisibilityScope,
        enter = fadeIn(),
        exit = fadeOut(),
        resizeMode = scaleToBounds(contentScale = ContentScale.Crop),
    )
    return remember(shouldAnimate, allowAnimation) {
        if (shouldAnimate && allowAnimation) boundsModifier else Modifier
    }
}