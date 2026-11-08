package com.dot.gallery.editor.render

import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoColourTone
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.core.model.PhotoEffects
import com.dot.gallery.editor.core.model.PhotoMarkup
import com.dot.gallery.editor.core.model.PhotoMarkupStroke
import com.dot.gallery.editor.core.model.PhotoMarkupText
import com.dot.gallery.editor.core.model.PhotoMarkupType
import com.dot.gallery.editor.core.model.PhotoRetouch
import com.dot.gallery.editor.core.model.PixelSize
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

class PhotoRetouchTest {

    private val size = PixelSize(64, 48)

    /** A gradient so blur, mosaic and edges all have something to change. */
    private suspend fun gradient(): LinearRgbaFrame {
        val pixels = IntArray(size.width * size.height) { index ->
            val x = index % size.width
            val y = index / size.width
            (0xFF shl 24) or ((x * 4) shl 16) or ((y * 5) shl 8) or ((x + y) * 2)
        }
        return SrgbPixels(size, pixels).toLinearFrame()
    }

    private suspend fun render(retouch: PhotoRetouch, frame: LinearRgbaFrame): IntArray =
        PhotoRetouchRenderer(retouch, size).process(frame).toSrgbPixels().copyPixels()

    private fun stroke(type: PhotoMarkupType, color: Int = -0x10000) = PhotoMarkupStroke(
        type = type,
        points = persistentListOf(NormalizedPoint(0.2f, 0.5f), NormalizedPoint(0.8f, 0.5f)),
        size = 60f,
        strength = 80f,
        color = color
    )

    @Test
    fun neutralRecipeReturnsTheSameFrame() = runTest {
        val frame = gradient()
        assertSame(frame, PhotoRetouchRenderer(PhotoRetouch(), size).process(frame))
    }

    @Test
    fun penPaintsItsColourAlongTheStroke() = runTest {
        val out = render(
            PhotoRetouch(markup = PhotoMarkup(persistentListOf(stroke(PhotoMarkupType.Pen)))),
            gradient()
        )
        assertEquals(0xFFFF0000.toInt(), out[size.height / 2 * size.width + size.width / 2])
    }

    @Test
    fun blurOverPenHidesTheInkBeneathIt() = runTest {
        val frame = gradient()
        val blurOnly = render(
            PhotoRetouch(markup = PhotoMarkup(persistentListOf(stroke(PhotoMarkupType.Blur)))),
            frame
        )
        val penThenBlur = render(
            PhotoRetouch(
                markup = PhotoMarkup(
                    persistentListOf(stroke(PhotoMarkupType.Pen), stroke(PhotoMarkupType.Blur))
                )
            ),
            frame
        )
        val centre = size.height / 2 * size.width + size.width / 2
        // Both read the frame from before markup, so red never leaks through.
        assertEquals(blurOnly[centre], penThenBlur[centre])
    }

    @Test
    fun mosaicFlattensABlockToOneColour() = runTest {
        val out = render(
            PhotoRetouch(markup = PhotoMarkup(persistentListOf(stroke(PhotoMarkupType.Mosaic)))),
            gradient()
        )
        val row = size.height / 2 * size.width
        assertEquals(out[row + 30], out[row + 31])
    }

    @Test
    fun colourToneChangesPixelsAndBlackWhiteRemovesChroma() = runTest {
        val frame = gradient()
        val out = render(
            PhotoRetouch(tone = PhotoColourTone(blackWhite = 1f)),
            frame
        )
        val pixel = out[size.width * 10 + 20]
        assertEquals((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF)
        assertEquals((pixel shr 8) and 0xFF, pixel and 0xFF)
    }

    @Test
    fun posterizeLeavesFewLevels() = runTest {
        val out = render(PhotoRetouch(effects = PhotoEffects(posterize = 1f)), gradient())
        assertTrue(out.map { (it shr 16) and 0xFF }.toSet().size <= 2)
        assertEquals(2, posterizeLevels(1f))
        assertEquals(24, posterizeLevels(0f))
    }

    @Test
    fun edgesReadTrueImageBordersAsNoEdge() = runTest {
        val out = render(PhotoRetouch(effects = PhotoEffects(edges = 1f)), gradient())
        assertEquals(0xFF000000.toInt(), out[0])
        assertNotEquals(0xFF000000.toInt(), out[size.width * 10 + 10])
    }

    @Test
    fun borderPaintsAnInsetRingWithoutGrowingTheFrame() = runTest {
        val frame = gradient()
        val colour = 0xFF2962FF.toInt()
        val result = PhotoRetouchRenderer(
            PhotoRetouch(effects = PhotoEffects(borderWidth = 1f, borderColor = colour)),
            size
        ).process(frame).toSrgbPixels()
        assertEquals(size, result.size)
        assertEquals(colour, result.argbAt(0, 0))
        assertEquals(colour, result.argbAt(size.width - 1, size.height - 1))
        assertNotEquals(colour, result.argbAt(size.width / 2, size.height / 2))
    }

    @Test
    fun matteMakesTheBackgroundTransparentAndKeepsTheSubject() = runTest {
        val alpha = ByteArray(4)
        alpha[1] = 0xFF.toByte()
        val out = render(
            PhotoRetouch(matte = PhotoCutoutMatte(2, 2, alpha)),
            gradient()
        )
        assertEquals(0, out[0] ushr 24)
        assertTrue((out[size.width - 1] ushr 24) > 200)
    }

    @Test
    fun rotatedTextPaintsAlongItsRotation() = runTest {
        val frame = gradient()
        val ink = 0xFFFF0000.toInt()
        val base = frame.toSrgbPixels().copyPixels()
        fun redExtent(pixels: IntArray): Pair<Int, Int> {
            var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
            for (y in 0 until size.height) for (x in 0 until size.width) {
                // Only what the label changed counts, not the gradient's own reds.
                if (pixels[y * size.width + x] != base[y * size.width + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            return (maxX - minX) to (maxY - minY)
        }
        fun label(rotation: Float) = PhotoRetouch(
            markup = PhotoMarkup(
                texts = persistentListOf(
                    PhotoMarkupText(1L, "IIIIIIII", 0.5f, 0.5f, ink, 0.2f, rotation)
                )
            )
        )
        val (flatW, flatH) = redExtent(render(label(0f), frame))
        val (uprightW, uprightH) = redExtent(render(label(90f), frame))
        assertTrue(flatW > flatH, "flat text runs across: $flatW x $flatH")
        assertTrue(uprightH > uprightW, "rotated text runs down: $uprightW x $uprightH")
    }

    @Test
    fun rotatedAndMultilineTextMatchAcrossBands() = runTest {
        val frame = gradient()
        val retouch = PhotoRetouch(
            markup = PhotoMarkup(
                texts = persistentListOf(
                    PhotoMarkupText(1L, "Top\nBottom", 0.5f, 0.5f, -1, 0.2f, 33f)
                )
            )
        )
        val renderer = PhotoRetouchRenderer(retouch, size)
        val full = renderer.process(frame).toSrgbPixels().copyPixels()
        val source = frame.copyPixels()
        val banded = IntArray(full.size)
        var y = 0
        while (y < size.height) {
            val rows = minOf(5, size.height - y)
            val slice = source.copyOfRange(y * size.width * 4, (y + rows) * size.width * 4)
            val band = LinearRgbaFrame(PixelSize(size.width, rows), slice)
            renderer.process(band, originY = y).toSrgbPixels().copyPixels()
                .copyInto(banded, y * size.width)
            y += rows
        }
        assertContentEquals(full, banded)
    }

    @Test
    fun bandedRenderMatchesTheFullFrame() = runTest {
        val frame = gradient()
        val retouch = PhotoRetouch(
            tone = PhotoColourTone(skin = 0.3f),
            effects = PhotoEffects(edges = 0.4f, borderWidth = 0.2f),
            markup = PhotoMarkup(
                persistentListOf(stroke(PhotoMarkupType.Blur), stroke(PhotoMarkupType.Mosaic))
            )
        )
        val renderer = PhotoRetouchRenderer(retouch, size)
        val full = renderer.process(frame).toSrgbPixels().copyPixels()
        val source = frame.copyPixels()
        val banded = IntArray(full.size)
        val rows = 7
        var y = 0
        while (y < size.height) {
            val bandRows = minOf(rows, size.height - y)
            val halo = renderer.halo(y, bandRows)
            val top = maxOf(0, y - halo)
            val bottom = minOf(size.height, y + bandRows + halo)
            val slice = source.copyOfRange(
                top * size.width * 4, bottom * size.width * 4
            )
            val band = LinearRgbaFrame(PixelSize(size.width, bottom - top), slice)
            val out = renderer.process(band, originY = top).toSrgbPixels().copyPixels()
            out.copyInto(
                banded, y * size.width,
                (y - top) * size.width, (y - top + bandRows) * size.width
            )
            y += bandRows
        }
        assertContentEquals(full, banded)
    }
}
