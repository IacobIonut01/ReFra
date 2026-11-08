package com.dot.gallery.app.presentation.mediaview.components.media

import com.dot.gallery.feature.viewer.components.media.resolveUsePixelPerfect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test



class PixelPerfectPolicyTest {

    @Test
    fun jxlUsesPixelPerfectEvenThoughItIsFlaggedAnimated() {
        // media.isJxl was folded into isAnimated, so every JXL stayed on the
        // smoothed path — "Disable smoothing" silently did nothing for the format.
        // JXL is exempt because animation is decided inside SketchAnimatedJxlDecoder:
        // the animated painter plays under the canvas-drawn path, and JxlRegionDecoder
        // self-rejects animated codestreams.
        assertTrue(
            resolveUsePixelPerfect(
                disableSmoothing = true,
                isJxl = true,
                isAnimated = true,
                isAnimatedRaster = false,
            )
        )
    }

    @Test
    fun frameAnimatedRastersKeepTheSmoothPath() {
        // GIF / APNG / animated WebP cannot be tiled and their painter zoom path is
        // the smooth one — they must stay excluded.
        listOf(true, false).forEach { isJxl ->
            assertFalse(
                resolveUsePixelPerfect(
                    disableSmoothing = true,
                    isJxl = isJxl,
                    isAnimated = true,
                    isAnimatedRaster = true,
                )
            )
        }
    }

    @Test
    fun nonJxlAnimatedFormatsKeepTheSmoothPath() {
        // Animated AVIF / HEIF sequences are neither JXL nor raster — unchanged.
        assertFalse(
            resolveUsePixelPerfect(
                disableSmoothing = true,
                isJxl = false,
                isAnimated = true,
                isAnimatedRaster = false,
            )
        )
    }

    @Test
    fun stillFormatsUsePixelPerfectAsBefore() {
        assertTrue(
            resolveUsePixelPerfect(
                disableSmoothing = true,
                isJxl = false,
                isAnimated = false,
                isAnimatedRaster = false,
            )
        )
    }

    @Test
    fun smoothingEnabledNeverUsesPixelPerfect() {
        assertFalse(
            resolveUsePixelPerfect(
                disableSmoothing = false,
                isJxl = true,
                isAnimated = true,
                isAnimatedRaster = false,
            )
        )
        assertFalse(
            resolveUsePixelPerfect(
                disableSmoothing = false,
                isJxl = false,
                isAnimated = false,
                isAnimatedRaster = false,
            )
        )
    }
}
