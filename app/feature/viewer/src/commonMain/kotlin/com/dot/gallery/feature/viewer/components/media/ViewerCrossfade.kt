package com.dot.gallery.feature.viewer.components.media

import com.github.panpf.sketch.transition.CrossfadeTransition
import com.github.panpf.sketch.transition.Transition

const val VIEWER_CROSSFADE_MILLIS = 200

/**
 * Transition for viewer requests that cross-fade (animated and encrypted media). Sketch's
 * [CrossfadeTransition.Factory] rejects non-positive durations, so disabled media animations
 * clear the transition instead of requesting a zero-length cross-fade.
 */
fun viewerCrossfadeTransition(animationsEnabled: Boolean): Transition.Factory? =
    if (animationsEnabled) CrossfadeTransition.Factory(durationMillis = VIEWER_CROSSFADE_MILLIS) else null
