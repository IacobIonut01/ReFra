/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.animation.PathInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.WeakHashMap
import kotlin.math.roundToInt

const val SYSTEM_BARS_HIDE_DURATION_MS = 120L
private const val SYSTEM_BARS_CONTROL_TIMEOUT_MS = 250L

private val systemBarsHideInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
private val latestSystemBarsRequest = WeakHashMap<WindowInsetsControllerCompat, Boolean>()

fun systemBarsHideDurationMs(animatorsEnabled: Boolean): Long =
    if (animatorsEnabled) SYSTEM_BARS_HIDE_DURATION_MS else 0L

fun lerpInsets(from: Insets, to: Insets, fraction: Float): Insets {
    val f = fraction.coerceIn(0f, 1f)
    fun lerpSide(fromSide: Int, toSide: Int) = (fromSide + (toSide - fromSide) * f).roundToInt()
    return Insets.of(
        lerpSide(from.left, to.left),
        lerpSide(from.top, to.top),
        lerpSide(from.right, to.right),
        lerpSide(from.bottom, to.bottom),
    )
}

fun WindowInsetsControllerCompat.toggleSystemBars(show: Boolean) {
    val alreadyHidden = latestSystemBarsRequest[this] == false
    latestSystemBarsRequest[this] = show
    if (show) {
        show(WindowInsetsCompat.Type.systemBars())
        return
    }
    if (alreadyHidden || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        hide(WindowInsetsCompat.Type.systemBars())
        return
    }
    val durationMs = systemBarsHideDurationMs(ValueAnimator.areAnimatorsEnabled())
    val compatController = this
    var animator: ValueAnimator? = null
    var settled = false
    val handler = Handler(Looper.getMainLooper())
    val watchdog = Runnable {
        if (!settled && latestSystemBarsRequest[compatController] == false) {
            compatController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    handler.postDelayed(watchdog, SYSTEM_BARS_CONTROL_TIMEOUT_MS)
    controlWindowInsetsAnimation(
        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(),
        durationMs,
        systemBarsHideInterpolator,
        null,
        object : WindowInsetsAnimationControlListenerCompat {
            override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                settled = true
                handler.removeCallbacks(watchdog)
                if (durationMs == 0L) {
                    controller.finish(false)
                    return
                }
                val shown = controller.shownStateInsets
                val hidden = controller.hiddenStateInsets
                var canceled = false
                val animation = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = durationMs
                    interpolator = systemBarsHideInterpolator
                    addUpdateListener { anim ->
                        val fraction = anim.animatedValue as Float
                        if (controller.isReady) {
                            controller.setInsetsAndAlpha(
                                lerpInsets(shown, hidden, fraction),
                                1f,
                                fraction,
                            )
                        }
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(anim: Animator) {
                            canceled = true
                        }

                        override fun onAnimationEnd(anim: Animator) {
                            if (!canceled && controller.isReady) controller.finish(false)
                        }
                    })
                }
                animator = animation
                animation.start()
            }

            override fun onFinished(controller: WindowInsetsAnimationControllerCompat) = Unit

            override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) {
                settled = true
                handler.removeCallbacks(watchdog)
                animator?.cancel()
                animator = null
                if (controller == null &&
                    latestSystemBarsRequest[compatController] == false
                ) {
                    compatController.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        },
    )
}
