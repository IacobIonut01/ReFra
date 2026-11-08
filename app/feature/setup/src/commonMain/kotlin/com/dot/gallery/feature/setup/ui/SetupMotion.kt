/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * True when the system animator scale is 0. Pages then cross-fade without the stagger, and
 * Start browsing finishes without the exit burst. Provided by the app shell.
 */
val LocalSetupReduceMotion = compositionLocalOf { false }

/** Receives the hero's bounds in root coordinates, so the exit burst can grow out of it. */
internal val LocalSetupHeroBoundsSink = compositionLocalOf<((Offset) -> Unit)?> { null }

/** Material 3 emphasized easings, for elements entering and leaving the screen. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Page change: the old page drifts a little against the direction of travel, shrinks and fades
 * fast; the new page slides in a short way. The shared chrome morphs on top, and each page
 * staggers its own hero, heading and panel through [setupStaggered] / [setupHeroMotion].
 */
internal fun AnimatedContentTransitionScope<Int>.setupPageTransform(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) {
        return (fadeIn(tween(150)) togetherWith fadeOut(tween(150))).using(SizeTransform(clip = false))
    }
    val dir = if (targetState >= initialState) 1 else -1
    val enter = fadeIn(tween(220, delayMillis = 60)) +
        slideInHorizontally(tween(420, easing = EmphasizedDecelerate)) { full -> dir * full / 5 }
    val exit = fadeOut(tween(140)) +
        slideOutHorizontally(tween(260, easing = EmphasizedAccelerate)) { full -> -dir * full / 8 } +
        scaleOut(tween(260), targetScale = 0.94f)
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/**
 * Staggered entrance for the [order]th block of a page (0 = first): it rises and fades in a beat
 * after the one above it. Leaves are not staggered; the page exit covers them.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun Modifier.setupStaggered(order: Int): Modifier {
    val scope = LocalSetupAnimatedVisibilityScope.current ?: return this
    if (LocalSetupReduceMotion.current) return this
    val delay = 80 + order * 50
    return with(scope) {
        animateEnterExit(
            enter = fadeIn(tween(260, delayMillis = delay)) +
                slideInVertically(tween(420, delayMillis = delay, easing = EmphasizedDecelerate)) { it / 4 },
            exit = fadeOut(tween(120)),
        )
    }
}

/**
 * The hero shape springs in from a small, turned-back size; it reports its centre for the exit
 * burst.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun Modifier.setupHeroMotion(): Modifier {
    val sink = LocalSetupHeroBoundsSink.current
    val tracked = if (sink != null) onGloballyPositioned { sink(it.boundsInRoot().center) } else this
    val scope = LocalSetupAnimatedVisibilityScope.current ?: return tracked
    if (LocalSetupReduceMotion.current) return tracked
    val rotation by scope.transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow) },
        label = "setup-hero-rotation",
    ) { state -> if (state == EnterExitState.Visible) 0f else -50f }
    return with(scope) {
        tracked
            .animateEnterExit(
                enter = scaleIn(
                    spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.3f,
                ) + fadeIn(tween(180)),
                exit = fadeOut(tween(120)),
            )
            .graphicsLayer { rotationZ = rotation }
    }
}

/** Last known hero centre, fed by [LocalSetupHeroBoundsSink]. Not snapshot state: only read on exit. */
internal class SetupHeroTracker {
    var center: Offset? = null
}

/**
 * Start browsing: the Done hero's burst grows from its place until it covers the screen, turning
 * as it goes, then fades away with the page. [progress] runs 0 to 1.
 */
@Composable
internal fun SetupExitBurst(
    progress: () -> Float,
    heroCenter: Offset?,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val p = progress()
        if (p <= 0f) return@Canvas
        val base = 96.dp.toPx()
        val center = heroCenter?.minus(origin) ?: Offset(size.width / 2f, size.height / 3f)
        val reach = maxOf(
            hypot(center.x, center.y),
            hypot(size.width - center.x, center.y),
            hypot(center.x, size.height - center.y),
            hypot(size.width - center.x, size.height - center.y),
        )
        // The burst's inner points sit near 70 % of its radius, so overshoot to cover the corners.
        val cover = reach * 2f / (base * 0.7f)
        val scale = 1f + (cover - 1f) * p
        val alpha = if (p < 0.65f) 1f else (1f - (p - 0.65f) / 0.35f).coerceIn(0f, 1f)
        val outline = shape.createOutline(Size(base, base), layoutDirection, this)
        translate(center.x - base / 2f, center.y - base / 2f) {
            val pivot = Offset(base / 2f, base / 2f)
            scale(scale, pivot) {
                rotate(90f * p, pivot) {
                    drawOutline(outline, color, alpha = alpha)
                }
            }
        }
    }
}
