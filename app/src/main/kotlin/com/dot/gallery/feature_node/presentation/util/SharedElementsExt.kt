package com.dot.gallery.feature_node.presentation.util


import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dot.gallery.core.Settings
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.Media

sealed interface MediaSharedElementKey {
    data class MediaKey(val id: Long) : MediaSharedElementKey
    data class AlbumKey(val id: Long) : MediaSharedElementKey
    data class CategoryKey(val id: Long) : MediaSharedElementKey
    data class StoryCardKey(val id: Long) : MediaSharedElementKey
}

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun <T: Media> Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    media: T,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.MediaKey(media.id), animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    album: Album,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.AlbumKey(album.id), animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.categorySharedElement(
    allowAnimation: Boolean = true,
    categoryId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.CategoryKey(categoryId), animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.storyCardSharedElement(
    allowAnimation: Boolean = true,
    cardId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.StoryCardKey(cardId), animatedVisibilityScope = animatedVisibilityScope)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    key: MediaSharedElementKey,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(namedSharedTransitionScope) {
    val shouldAnimate by Settings.Misc.rememberSharedElements()
    val boundsModifier = sharedBounds(
        sharedContentState = rememberSharedContentState(key = key),
        animatedVisibilityScope = animatedVisibilityScope
    )
    return remember(shouldAnimate, allowAnimation) {
        if (shouldAnimate && allowAnimation) boundsModifier else Modifier
    }
}