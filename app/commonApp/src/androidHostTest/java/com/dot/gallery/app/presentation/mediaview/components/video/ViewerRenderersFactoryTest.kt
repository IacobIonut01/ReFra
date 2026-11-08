/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.mediaview.components.video

import com.dot.gallery.feature.viewer.components.video.codecMishandlesSetOutputSurface
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerRenderersFactoryTest {

    @Test
    fun emulatorGoldfishDecodersReinitInsteadOfSwappingSurface() {
        assertTrue(codecMishandlesSetOutputSurface("c2.goldfish.h264.decoder"))
        assertTrue(codecMishandlesSetOutputSurface("c2.goldfish.hevc.decoder"))
        assertTrue(codecMishandlesSetOutputSurface("OMX.android.goldfish.h264.decoder"))
    }

    @Test
    fun deviceAndSoftwareDecodersKeepSeamlessSurfaceSwap() {
        assertFalse(codecMishandlesSetOutputSurface("c2.android.avc.decoder"))
        assertFalse(codecMishandlesSetOutputSurface("c2.exynos.h264.decoder"))
        assertFalse(codecMishandlesSetOutputSurface("c2.qti.avc.decoder"))
    }
}
