package com.dot.gallery

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.adjustments.Markup
import com.dot.gallery.feature_node.presentation.edit.bake.EditReplay
import com.dot.gallery.feature_node.presentation.edit.components.markup.rescaleMarkupPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkupAdjustmentTest {
    @Test
    fun replayCompositesMarkupOntoProxyAndFullResolutionBases() {
        val proxyBase = solidBitmap(4, 4, Color.BLUE)
        val overlay = solidBitmap(4, 4, Color.TRANSPARENT).apply {
            for (y in 0 until height) {
                for (x in 0 until width / 2) {
                    setPixel(x, y, Color.argb(128, 255, 0, 0))
                }
            }
        }
        val adjustment = Markup(overlay)
        val liveProxy = adjustment.apply(proxyBase)
        val fullBaseSoftware = solidBitmap(8, 8, Color.GREEN)
        val fullBase = fullBaseSoftware.copy(Bitmap.Config.HARDWARE, false)
        val fullResult = EditReplay.replay(fullBase, listOf(adjustment))

        try {
            assertTrue(EditReplay.matchesLiveResult(proxyBase, listOf(adjustment), liveProxy))
            assertNotEquals(Color.GREEN, fullResult.getPixel(0, 0))
            assertEquals(Color.GREEN, fullResult.getPixel(7, 7))
        } finally {
            proxyBase.recycle()
            overlay.recycle()
            liveProxy.recycle()
            fullBaseSoftware.recycle()
            fullBase.recycle()
            fullResult.recycle()
        }
    }

    @Test
    fun hardwareOverlayCompositesOntoSoftwareBase() {
        val softwareOverlay = solidBitmap(4, 4, Color.TRANSPARENT).apply {
            setPixel(0, 0, Color.RED)
        }
        val hardwareOverlay = softwareOverlay.copy(Bitmap.Config.HARDWARE, false)
        val base = solidBitmap(4, 4, Color.BLUE)
        val result = Markup(hardwareOverlay).apply(base)

        try {
            assertEquals(Color.RED, result.getPixel(0, 0))
            assertEquals(Color.BLUE, result.getPixel(3, 3))
        } finally {
            softwareOverlay.recycle()
            hardwareOverlay.recycle()
            base.recycle()
            result.recycle()
        }
    }

    @Test
    fun markupPathsTrackAnimatedCanvasSizeChanges() {
        val path = Path().apply {
            moveTo(1f, 2f)
            lineTo(4f, 6f)
        }
        val properties = PathProperties(strokeWidth = 5f)

        rescaleMarkupPaths(
            paths = listOf(path to properties),
            oldSize = IntSize(10, 10),
            newSize = IntSize(20, 10),
        )
        rescaleMarkupPaths(
            paths = listOf(path to properties),
            oldSize = IntSize(20, 10),
            newSize = IntSize(20, 30),
        )

        val bounds = path.getBounds()
        assertEquals(2f, bounds.left, 0f)
        assertEquals(6f, bounds.top, 0f)
        assertEquals(8f, bounds.right, 0f)
        assertEquals(18f, bounds.bottom, 0f)
        assertEquals(10f, properties.strokeWidth, 0f)
    }

    @Test
    fun tiledReplayUsesTheMatchingFullImageOverlayRegion() {
        val overlay = solidBitmap(4, 4, Color.TRANSPARENT).apply {
            for (y in 0 until height) {
                for (x in width / 2 until width) {
                    setPixel(x, y, Color.RED)
                }
            }
        }
        val tile = solidBitmap(2, 4, Color.GREEN)
        val result = Markup(overlay).applyTile(
            tile = tile,
            fullWidth = 4,
            fullHeight = 4,
            tileX = 2,
            tileY = 0,
        )

        try {
            for (y in 0 until result.height) {
                for (x in 0 until result.width) {
                    assertEquals(Color.RED, result.getPixel(x, y))
                }
            }
        } finally {
            overlay.recycle()
            tile.recycle()
            result.recycle()
        }
    }

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
