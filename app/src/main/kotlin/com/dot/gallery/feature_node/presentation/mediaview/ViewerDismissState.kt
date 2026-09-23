package com.dot.gallery.feature_node.presentation.mediaview

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val DISMISS_THRESHOLD_FRACTION = 0.12f
private const val DISMISS_FADE_DISTANCE_FRACTION = 0.35f
private const val BACKDROP_FADE_SPEED = 2.5f
private const val FLIGHT_DURATION_MS = 260
private const val BACK_FLIGHT_FINISH_MS = 140

internal fun viewerDismissProgress(offsetY: Float, height: Int): Float {
    if (height <= 0) return 0f
    return (offsetY.coerceAtLeast(0f) / (height * DISMISS_FADE_DISTANCE_FRACTION))
        .coerceIn(0f, 1f)
}

internal fun shouldCommitViewerDismiss(offsetY: Float, height: Int): Boolean =
    height > 0 && offsetY >= height * DISMISS_THRESHOLD_FRACTION

/**
 * Tracks the interactive swipe-down state of an overlay viewer.
 *
 * The drag offset is applied to the translating content *inside* the shared-element card —
 * never to the gesture-handling container, so pointer positions always arrive in untranslated
 * screen space, and the blurred backdrop (a sibling of the offset content) stays pinned to the
 * screen. Committing runs a manual return flight:
 * [ViewerDismissBridge.flight] morphs a root-level thumbnail from the release bounds to the
 * source cell's live bounds while scrim/chrome fade with gesture progress; cancelling springs
 * the card offset back to rest. The shared element itself is suppressed for the gesture so no
 * bounds animation fights the finger.
 *
 * Predictive back reuses the same flight machinery: [onPredictiveBack] scrubs the morph
 * fullscreen→cell with gesture progress instead of a timed animation.
 *
 * Without a bridge (route-based viewer), the same offset/card mechanics apply — only the
 * flight hand-off is skipped.
 */
@Stable
internal class ViewerDismissState(
    private val bridge: ViewerDismissBridge? = null,
) {
    /**
     * Mirrors the "Animate media items" preference. When false, committed drags dismiss
     * instantly (no return flight), cancelled drags snap back, enter flights are skipped, and
     * predictive back resolves as a plain dismiss — the container's exit is instant too, so
     * nothing needs a flight to cover it.
     */
    var animationsEnabled: Boolean = true

    private var sizePx by mutableStateOf(IntSize.Zero)
    private var dragOffsetY by mutableFloatStateOf(0f)
    private var gestureActive by mutableStateOf(false)

    // The media (and its shared-element key) whose drag is armed — kept so the committed
    // flight can seed from the release bounds and hand the visual to a thumbnail of it.
    private var armedKey by mutableStateOf<Any?>(null)
    private var armedMedia by mutableStateOf<Media?>(null)

    // Set once a committed dismiss hands the visual off to the return flight; blocks any
    // further drag from arming while the retained content fades out.
    private var committed by mutableStateOf(false)

    // The shared-element key whose media must stay hidden in place for the rest of the
    // overlay's life after a committed dismiss — while the flight runs the root layer draws
    // it, and through the container's exit fade the real source cell is already back on
    // screen, so letting the element reappear fullscreen would flash it over the grid.
    private var hiddenElementKey by mutableStateOf<Any?>(null)

    // Predictive back: while the system back gesture scrubs, the return flight is driven by
    // gesture progress rather than a timed animation.
    private var backActive by mutableStateOf(false)
    private var backProgress by mutableFloatStateOf(0f)

    private val dragAnimation = Animatable(0f)

    /**
     * The live vertical drag distance. Drives the translating content's offset (media +
     * thumbnail ride it; the blurred backdrop stays pinned) and the progress-driven scrim /
     * chrome alpha. Stays at the release value after a committed dismiss so the chrome doesn't
     * snap back to full alpha while the exit fade runs.
     */
    val offsetY: Float
        get() = if (gestureActive) dragOffsetY else dragAnimation.value

    val chromeAlpha: Float
        get() = 1f - maxOf(viewerDismissProgress(offsetY, heightPx), backProgress)

    /**
     * Fast fade for the pinned blurred backdrop during a drag/back scrub — dissolves at
     * [BACKDROP_FADE_SPEED]× the chrome rate so it's gone around the commit threshold and the
     * reveal reads as media-over-grid rather than a lingering blur veil. Tracking offsetY
     * (not a timed animation) also fades it back in smoothly when a cancel springs the card
     * home.
     */
    val backdropAlpha: Float
        get() = (1f - maxOf(
            viewerDismissProgress(offsetY, heightPx) * BACKDROP_FADE_SPEED,
            backProgress,
        )).coerceIn(0f, 1f)

    val isActive: Boolean
        get() = gestureActive || committed || backActive || offsetY > 0f

    /** True once a committed dismiss has handed the visual to the return flight. */
    val isCommitted: Boolean
        get() = committed

    /**
     * True when the viewer is presented as an overlay with a dismiss bridge — the card offset
     * and flight hand-off are managed by this state. Route viewers share the same mechanics;
     * the flag remains so call sites can keep overlay-vs-route behavior explicit.
     */
    val usesOverlayTransform: Boolean
        get() = bridge != null

    /**
     * True when this element's in-place copy must not draw because a manual flight owns its
     * visual: during the enter flight (root layer morphs cell→fullscreen), during a predictive
     * back scrub, and from a committed dismiss through the overlay's exit fade (root layer
     * morphs release→cell, then the real source cell is back so a reappearing fullscreen
     * element would flash over the grid).
     */
    fun isDismissedVisualHidden(key: Any): Boolean =
        usesOverlayTransform &&
            (((committed || backActive) && hiddenElementKey == key) ||
                bridge?.flight?.let { it.isEnter && it.key == key } == true)

    private val heightPx: Int
        get() = sizePx.height

    fun updateSize(size: IntSize) {
        sizePx = size
    }

    /**
     * Deterministic enter flight for the overlay open: morphs the media thumbnail from the
     * source cell's live bounds to fullscreen while the container's fade-in runs underneath,
     * then hands the visual back to the in-place element. Replaces the shared-element bounds
     * morph on the enter direction — under a deferred transition its bounds DeferredAnimation
     * is recreated mid-enter, after the transition's currentState already reads the target, so
     * it initializes at the end bounds and the media pops instead of morphing.
     *
     * The caller owns releasing [ViewerDismissBridge.suppressedElementKey] once the enter
     * transition has settled (clearing earlier could let the framework attach its own bounds
     * morph on top of the landed flight).
     */
    suspend fun runEnterFlight(key: Any, media: Media, cellBounds: Rect) {
        val b = bridge ?: return
        if (!animationsEnabled || gestureActive || committed || backActive) return
        // The screen's effect can resume before the first layout pass — wait for the box size.
        val size = withTimeoutOrNull(800) {
            snapshotFlow { sizePx }.first { it.width > 0 && it.height > 0 }
        } ?: return
        val full = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        b.suppressedElementKey = key
        b.flight = ViewerDismissFlight(key = key, media = media, bounds = cellBounds, isEnter = true)
        val progress = Animatable(0f)
        try {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(FLIGHT_DURATION_MS, easing = FastOutSlowInEasing),
            ) {
                b.flight?.let { f ->
                    // The flight slot may have been taken over (e.g. a predictive-back
                    // return flight armed mid-enter) — this coroutine only ever writes
                    // its own enter flight.
                    if (f.isEnter) {
                        f.bounds = Rect(
                            left = lerp(cellBounds.left, full.left, value),
                            top = lerp(cellBounds.top, full.top, value),
                            right = lerp(cellBounds.right, full.right, value),
                            bottom = lerp(cellBounds.bottom, full.bottom, value),
                        )
                        f.progress = value
                    }
                }
            }
        } finally {
            b.flight?.takeIf { it.isEnter }?.let { b.flight = null }
        }
    }

    /**
     * @param elementKey the shared-element key of the media being dragged — both its source
     * cell and the viewer element are suppressed for the gesture so the media rides the
     * card offset in place instead of converging toward the cell mid-gesture.
     */
    fun start(scope: CoroutineScope, elementKey: Any? = null, media: Media? = null) {
        if (gestureActive || committed || backActive) return
        dragOffsetY = dragAnimation.value
        gestureActive = true
        armedKey = elementKey
        armedMedia = media
        bridge?.let {
            // A drag armed mid-enter-flight drops the morph — the media rides the card
            // offset from here (the in-place element unhides once flight is cleared).
            it.flight?.takeIf { f -> f.isEnter }?.let { _ -> it.flight = null }
            it.suppressedElementKey = elementKey
        }
        scope.launch { dragAnimation.stop() }
    }

    fun dragBy(deltaY: Float) {
        if (!gestureActive) return
        dragOffsetY = (dragOffsetY + deltaY).coerceIn(0f, heightPx * 1.05f)
    }

    fun finish(scope: CoroutineScope, onDismiss: () -> Unit) {
        if (!gestureActive) return
        if (!shouldCommitViewerDismiss(dragOffsetY, heightPx)) {
            cancel(scope)
            return
        }
        scope.launch {
            // Freeze the offset at the release point so the chrome doesn't snap back to full
            // alpha while the exit runs.
            dragAnimation.snapTo(dragOffsetY)
            gestureActive = false
            committed = true
            hiddenElementKey = armedKey
            val b = bridge
            val media = armedMedia
            val target = armedKey?.let { b?.cellBounds?.get(it) }
            if (!animationsEnabled || b == null || media == null || target == null || target.isEmpty) {
                // Route viewer, an unmapped cell, or animations disabled: no manual flight —
                // just dismiss and let the container (and any route-level shared element)
                // exit. The suppression stays armed until the overlay is fully inactive (the
                // host clears it) so the cell can't re-match mid-exit and hang the transition.
                onDismiss()
                armedKey = null
                armedMedia = null
                return@launch
            }
            // The exit fade runs underneath the flight so the card keeps the release offset;
            // the in-place media is hidden (isDismissedVisualHidden) and the root-level flight
            // layer carries the visual to the cell. Backdrop/chrome fade via progress.
            val release = Rect(
                left = 0f,
                top = dragOffsetY,
                right = sizePx.width.toFloat(),
                bottom = sizePx.height + dragOffsetY,
            )
            b.flight = ViewerDismissFlight(key = armedKey!!, media = media, bounds = release)
            launch {
                dragAnimation.animateTo(
                    targetValue = heightPx * DISMISS_FADE_DISTANCE_FRACTION,
                    animationSpec = tween(FLIGHT_DURATION_MS),
                )
            }
            val progress = Animatable(0f)
            try {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(FLIGHT_DURATION_MS, easing = FastOutSlowInEasing),
                ) {
                    b.flight?.let { f ->
                        f.bounds = Rect(
                            left = lerp(release.left, target.left, value),
                            top = lerp(release.top, target.top, value),
                            right = lerp(release.right, target.right, value),
                            bottom = lerp(release.bottom, target.bottom, value),
                        )
                        f.progress = value
                    }
                }
            } finally {
                // If the owning scope dies mid-flight the flight layer must still clear — a
                // lingering non-null flight would freeze a stale box over the grid.
                b.flight = null
            }
            onDismiss()
            // Keep suppressedElementKey armed until the host observes the overlay fully
            // inactive and clears it — releasing it mid-exit would let the cell re-match and
            // re-attach a bounds animation to the closing transition.
            armedKey = null
            armedMedia = null
        }
    }

    fun cancel(scope: CoroutineScope) {
        val startOffset = dragOffsetY
        scope.launch {
            // Snap before flipping gestureActive so offsetY keeps reading the same value —
            // the getter switches from dragOffsetY to dragAnimation.value on the flip.
            dragAnimation.snapTo(startOffset)
            gestureActive = false
            dragOffsetY = 0f
            if (animationsEnabled) {
                dragAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
            } else {
                dragAnimation.snapTo(0f)
            }
            bridge?.suppressedElementKey = null
            armedKey = null
            armedMedia = null
        }
    }

    /**
     * Predictive back for the overlay viewer: [events] is the handler's progress flow. Each
     * event scrubs the return flight fullscreen→cell (the in-place element is hidden so the
     * root layer owns the visual) and fades scrim/chrome via [backProgress]. Completing the
     * gesture finishes the morph and calls [onDismiss]; cancelling restores the viewer.
     */
    suspend fun onPredictiveBack(
        events: Flow<BackEventCompat>,
        elementKey: Any?,
        media: Media?,
        onDismiss: () -> Unit,
    ) {
        val b = bridge
        val target = elementKey?.let { b?.cellBounds?.get(it) }?.takeUnless { it.isEmpty }
        if (!animationsEnabled || b == null || elementKey == null || media == null || target == null ||
            gestureActive || committed || backActive
        ) {
            // No mapped cell to morph to (or a gesture already owns the state): still fade the
            // scrim with gesture progress, then resolve the back press as a plain dismiss.
            try {
                events.collect { backProgress = it.progress }
            } catch (e: CancellationException) {
                backProgress = 0f
                throw e
            }
            onDismiss()
            backProgress = 0f
            return
        }
        backActive = true
        hiddenElementKey = elementKey
        b.suppressedElementKey = elementKey
        val full = Rect(0f, 0f, sizePx.width.toFloat(), sizePx.height.toFloat())
        b.flight = ViewerDismissFlight(key = elementKey, media = media, bounds = full)
        fun Rect.lerpTo(other: Rect, t: Float) = Rect(
            left = lerp(left, other.left, t),
            top = lerp(top, other.top, t),
            right = lerp(right, other.right, t),
            bottom = lerp(bottom, other.bottom, t),
        )
        try {
            events.collect { event ->
                backProgress = event.progress
                b.flight?.let { f ->
                    f.bounds = full.lerpTo(target, event.progress)
                    f.progress = event.progress
                }
            }
            // Completed: finish the last stretch of the morph, then hand off to the exit.
            committed = true
            backProgress = 1f
            val progress = Animatable(b.flight?.progress ?: 1f)
            try {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(BACK_FLIGHT_FINISH_MS, easing = FastOutSlowInEasing),
                ) {
                    b.flight?.let { f ->
                        f.bounds = full.lerpTo(target, value)
                        f.progress = value
                    }
                }
            } finally {
                b.flight = null
            }
            onDismiss()
        } catch (e: CancellationException) {
            b.flight = null
            b.suppressedElementKey = null
            hiddenElementKey = null
            backProgress = 0f
            backActive = false
            throw e
        }
        backActive = false
    }
}
