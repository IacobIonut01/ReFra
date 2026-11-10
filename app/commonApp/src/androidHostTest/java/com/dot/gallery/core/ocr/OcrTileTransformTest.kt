/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr

import com.dot.gallery.core.ocr.decode.OcrSourceRect
import com.dot.gallery.core.ocr.decode.OcrTile
import com.dot.gallery.core.ocr.decode.OcrTileKind
import com.dot.gallery.core.ocr.decode.OcrTileTransform
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTileTransformTest {
    @Test
    fun mapsFineTileQuadImmediatelyIntoOrientedSourceSpace() {
        val tile = OcrTile(
            id = "fine-1-1",
            kind = OcrTileKind.FINE,
            source = OcrSourceRect(2_000, 1_000, 6_000, 3_000),
            decodeWidth = 2_048,
            decodeHeight = 1_024,
        )

        val mapped = OcrTileTransform.toNormalizedSource(
            tileLocalQuad = NormalizedQuad.axisAligned(0.25f, 0.25f, 0.75f, 0.75f),
            tile = tile,
            sourceWidth = 8_000,
            sourceHeight = 4_000,
        )

        assertEquals(0.375f, mapped.topLeft.x, 0.0001f)
        assertEquals(0.375f, mapped.topLeft.y, 0.0001f)
        assertEquals(0.625f, mapped.bottomRight.x, 0.0001f)
        assertEquals(0.625f, mapped.bottomRight.y, 0.0001f)
    }

    @Test
    fun mapsOverlappingTilesToTheSameSourceQuad() {
        val first = OcrTile("a", OcrTileKind.FINE, OcrSourceRect(0, 0, 6_000, 4_000), 2_048, 1_365)
        val second = OcrTile("b", OcrTileKind.FINE, OcrSourceRect(4_000, 0, 10_000, 4_000), 2_048, 1_365)

        val fromFirst = OcrTileTransform.toNormalizedSource(
            NormalizedQuad.axisAligned(0.75f, 0.25f, 5f / 6f, 0.5f), first, 10_000, 4_000,
        )
        val fromSecond = OcrTileTransform.toNormalizedSource(
            NormalizedQuad.axisAligned(1f / 12f, 0.25f, 1f / 6f, 0.5f), second, 10_000, 4_000,
        )

        assertEquals(fromFirst.topLeft.x, fromSecond.topLeft.x, 0.0001f)
        assertEquals(fromFirst.topRight.x, fromSecond.topRight.x, 0.0001f)
        assertEquals(fromFirst.topLeft.y, fromSecond.topLeft.y, 0.0001f)
    }
}
