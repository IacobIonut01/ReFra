@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package com.dot.gallery.feature_node.presentation.util


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.PlaceHolderSize.Companion.contentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun <T: Media> Modifier.mediaSharedElement(
    media: T,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(key = media.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.mediaSharedElement(
    album: Album,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(key = album.idLessKey, animatedVisibilityScope = animatedVisibilityScope)

context(SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.mediaSharedElement(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier {
    val shouldAnimate by Settings.Misc.rememberSharedElements()
    val boundsModifier = sharedBounds(
        rememberSharedContentState(key = "media_$key"),
        animatedVisibilityScope = animatedVisibilityScope,
        placeHolderSize = contentSize,
        boundsTransform = { _, _ -> tween(250) }
    )
    return if (shouldAnimate) boundsModifier else Modifier
}