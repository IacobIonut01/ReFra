package com.dot.gallery.core.presentation.util

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.SharedContentConfig
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.ExperimentalDeferredTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.Media
import com.dot.gallery.core.LocalViewerDismissBridge
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.core.ViewerDismissBridge




/**
 * Deterministic bounds spec for every shared element. The default spring gets clipped by short
 * exit transitions (the bounds animation stops when the transition ends); a tween slightly
 * shorter than the container's exit fade guarantees the return flight completes.
 */
val SharedElementBoundsTransform = BoundsTransform { initial, target ->
    tween(durationMillis = 280, easing = FastOutSlowInEasing)
}

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
    animatedVisibilityScope: AnimatedVisibilityScope,
    permitTransformDuringDeferredTransition: Boolean = false,
    cornerRadius: Dp = 0.dp,
): Modifier = mediaSharedElement(
    allowAnimation = allowAnimation,
    key = MediaSharedElementKey.MediaKey(media.id),
    animatedVisibilityScope = animatedVisibilityScope,
    permitTransformDuringDeferredTransition = permitTransformDuringDeferredTransition,
    cornerRadius = cornerRadius,
)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    album: Album,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cornerRadius: Dp = 0.dp,
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.AlbumKey(album.id), animatedVisibilityScope = animatedVisibilityScope, cornerRadius = cornerRadius)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.categorySharedElement(
    allowAnimation: Boolean = true,
    categoryId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cornerRadius: Dp = 0.dp,
): Modifier = mediaSharedElement(allowAnimation = allowAnimation, key = MediaSharedElementKey.CategoryKey(categoryId), animatedVisibilityScope = animatedVisibilityScope, cornerRadius = cornerRadius)

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.storyCardSharedElement(
    allowAnimation: Boolean = true,
    cardId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope,
    permitTransformDuringDeferredTransition: Boolean = false,
    cornerRadius: Dp = 0.dp,
): Modifier = mediaSharedElement(
    allowAnimation = allowAnimation,
    key = MediaSharedElementKey.StoryCardKey(cardId),
    animatedVisibilityScope = animatedVisibilityScope,
    permitTransformDuringDeferredTransition = permitTransformDuringDeferredTransition,
    cornerRadius = cornerRadius,
)

/**
 * Clips a shared element's overlay copy with a corner radius that follows the bounds animation.
 *
 * A story card is rounded while the viewer it opens into is flat. Both sides register their
 * resting corner radius and lookahead size on [ViewerDismissBridge]; this clip interpolates
 * between that pair using the animated bounds' size, so the corners merge with the card on the
 * way back instead of popping from square to rounded at the end.
 *
 * When both ends share the same rounding there is nothing to morph; the element's own clip
 * already handles it, so this returns `null` (no overlay clip).
 */
private class CornerMorphOverlayClip(
    private val dismissBridge: ViewerDismissBridge?,
    private val key: MediaSharedElementKey,
    private val restingRadius: Dp,
) : OverlayClip {
    /** Lookahead size of this element in px, reported from layout (the animated bounds' target). */
    var ownSize: IntSize = IntSize.Zero

    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        val sourceSize = dismissBridge?.cellCornerSizes?.get(key) ?: return null
        if (sourceSize == IntSize.Zero) return null
        val sourceRadius = dismissBridge.cellCornerRadii[key] ?: 0.dp
        // Prefer the explicitly registered target side so both the source card and the viewer
        // entry resolve to the same radius at any animated bounds. Fall back to this element's
        // own lookahead size when there is no target entry.
        val otherSize = dismissBridge.targetCornerSizes[key] ?: ownSize
        if (otherSize == IntSize.Zero) return null
        val otherRadius = dismissBridge.targetCornerRadii[key] ?: restingRadius
        if (sourceRadius == otherRadius) return null

        val sourceArea = sourceSize.width.toFloat() * sourceSize.height
        val otherArea = otherSize.width.toFloat() * otherSize.height
        if (sourceArea == otherArea) return null
        val sourceIsSmaller = sourceArea < otherArea
        val smallRadius = if (sourceIsSmaller) sourceRadius else otherRadius
        val largeRadius = if (sourceIsSmaller) otherRadius else sourceRadius
        val smallSize = if (sourceIsSmaller) sourceSize else otherSize
        val largeSize = if (sourceIsSmaller) otherSize else sourceSize
        val fraction = boundsProgress(
            current = Size(bounds.width, bounds.height),
            small = Size(smallSize.width.toFloat(), smallSize.height.toFloat()),
            large = Size(largeSize.width.toFloat(), largeSize.height.toFloat()),
        )
        val smallPx = with(density) { smallRadius.toPx() }
        val largePx = with(density) { largeRadius.toPx() }
        val radiusPx = smallPx + (largePx - smallPx) * fraction
        return if (radiusPx > 0.01f) roundedPath(bounds, radiusPx) else null
    }

    private fun roundedPath(bounds: Rect, radiusPx: Float): Path {
        path.reset()
        path.addRoundRect(
            RoundRect(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                radiusX = radiusPx,
                radiusY = radiusPx,
            )
        )
        return path
    }
}

/** Normalizes [current] between [small] and [large]; both dimensions animate with the same tween. */
private fun boundsProgress(current: Size, small: Size, large: Size): Float {
    val dw = large.width - small.width
    val dh = large.height - small.height
    val tw = if (abs(dw) > 0.5f) (current.width - small.width) / dw else null
    val th = if (abs(dh) > 0.5f) (current.height - small.height) / dh else null
    val fraction = when {
        tw != null && th != null -> (tw + th) / 2f
        tw != null -> tw
        th != null -> th
        else -> 0f
    }
    return fraction.coerceIn(0f, 1f)
}

/**
 * Config that lets an overlay viewer's armed swipe-dismiss suppress the matching entries.
 * While a drag defers the overlay transition, both the source cell and the viewer element
 * drop out of the match: the media then renders in place inside the offset container and
 * simply rides the finger, while the committed return flight is driven manually by
 * [ViewerDismissBridge.flight] — no bounds animation tracks the gesture.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
private class DismissAwareSharedContentConfig(
    private val dismissBridge: ViewerDismissBridge?,
    private val permitTransform: Boolean,
) : SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
        get() = dismissBridge?.suppressedElementKey != key

    /**
     * Default true keeps a suppressed entry enabled while its bounds animation exists — which
     * would let the source cell keep re-configuring the match during the drag-defer
     * (isAnimating survives until the DeferredAnimation is recreated). Returning false makes
     * the suppression take effect immediately.
     */
    override val shouldKeepEnabledForOngoingAnimation: Boolean
        get() = false

    override val permitTransformDuringDeferredTransition: Boolean
        get() = permitTransform
}

context(namedSharedTransitionScope: SharedTransitionScope)
@Composable
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalDeferredTransitionApi::class)
private fun Modifier.mediaSharedElement(
    allowAnimation: Boolean = true,
    key: MediaSharedElementKey,
    animatedVisibilityScope: AnimatedVisibilityScope,
    permitTransformDuringDeferredTransition: Boolean = false,
    cornerRadius: Dp = 0.dp,
): Modifier = with(namedSharedTransitionScope) {
    val shouldAnimate by rememberPreference(SettingsKeys.sharedElements)
    // Source elements (grid cells, story cards) keep the flag false so they stay pinned during
    // the viewer's deferred swipe-dismiss instead of sliding with the mutating container. The
    // viewer element inside the overlay opts in so a system predictive-back transform still
    // applies to it.
    val dismissBridge = LocalViewerDismissBridge.current
    val cornerClip = remember(dismissBridge, key, cornerRadius) {
        CornerMorphOverlayClip(dismissBridge, key, cornerRadius)
    }
    val boundsModifier = sharedBounds(
        sharedContentState = rememberSharedContentState(
            key = key,
            config = remember(dismissBridge, permitTransformDuringDeferredTransition) {
                DismissAwareSharedContentConfig(dismissBridge, permitTransformDuringDeferredTransition)
            },
        ),
        animatedVisibilityScope = animatedVisibilityScope,
        boundsTransform = SharedElementBoundsTransform,
        clipInOverlayDuringTransition = cornerClip,
    )
    // Source entries keep their root bounds reported so a committed dismiss can fly the media
    // back to the live cell rect — even after the cell re-laid out under the open viewer.
    // Their resting corner radius rides along so the manual dismiss/enter flight can morph it
    // into the flat viewer instead of landing with square corners over a rounded card. Every
    // entry also reports its lookahead size so [CornerMorphOverlayClip] can align the corner
    // morph with the framework's own bounds animation.
    val reporter = if (dismissBridge != null) {
        Modifier.onGloballyPositioned { coords ->
            cornerClip.ownSize = coords.size
            if (!permitTransformDuringDeferredTransition) {
                dismissBridge.cellBounds[key] = coords.boundsInRoot()
                if (dismissBridge.cellCornerSizes[key] != coords.size) {
                    dismissBridge.cellCornerSizes[key] = coords.size
                }
                if (dismissBridge.cellCornerRadii[key] != cornerRadius) {
                    dismissBridge.cellCornerRadii[key] = cornerRadius
                }
            } else {
                if (dismissBridge.targetCornerSizes[key] != coords.size) {
                    dismissBridge.targetCornerSizes[key] = coords.size
                }
                if (dismissBridge.targetCornerRadii[key] != cornerRadius) {
                    dismissBridge.targetCornerRadii[key] = cornerRadius
                }
            }
        }
    } else Modifier
    return remember(shouldAnimate, allowAnimation) {
        if (shouldAnimate && allowAnimation) boundsModifier else Modifier
    }.then(reporter)
}
