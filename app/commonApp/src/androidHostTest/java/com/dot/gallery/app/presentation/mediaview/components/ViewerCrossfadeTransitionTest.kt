/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components

import com.dot.gallery.feature.viewer.components.media.VIEWER_CROSSFADE_MILLIS
import com.dot.gallery.feature.viewer.components.media.viewerCrossfadeTransition
import com.github.panpf.sketch.transition.CrossfadeTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerCrossfadeTransitionTest {

    @Test
    fun disabledAnimationsClearTheTransitionInsteadOfZeroDuration() {
        // Sketch throws "durationMillis must be > 0." for a zero-length cross-fade (#1335).
        assertNull(viewerCrossfadeTransition(animationsEnabled = false))
    }

    @Test
    fun enabledAnimationsCrossfadeForTheViewerDuration() {
        val factory = viewerCrossfadeTransition(animationsEnabled = true) as CrossfadeTransition.Factory
        assertEquals(VIEWER_CROSSFADE_MILLIS, factory.durationMillis)
    }
}
