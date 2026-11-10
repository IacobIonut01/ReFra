/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad

/** Maps detector geometry into normalized, oriented source coordinates before a tile is released. */
object OcrTileTransform {
    fun toNormalizedSource(
        tileLocalQuad: NormalizedQuad,
        tile: OcrTile,
        sourceWidth: Int,
        sourceHeight: Int,
    ): NormalizedQuad {
        require(tile.source.right <= sourceWidth && tile.source.bottom <= sourceHeight)
        return NormalizedQuad(
            topLeft = map(tileLocalQuad.topLeft, tile.source, sourceWidth, sourceHeight),
            topRight = map(tileLocalQuad.topRight, tile.source, sourceWidth, sourceHeight),
            bottomRight = map(tileLocalQuad.bottomRight, tile.source, sourceWidth, sourceHeight),
            bottomLeft = map(tileLocalQuad.bottomLeft, tile.source, sourceWidth, sourceHeight),
        )
    }

    private fun map(
        point: NormalizedPoint,
        region: OcrSourceRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): NormalizedPoint = NormalizedPoint(
        x = ((region.left + point.x * region.width) / sourceWidth).coerceIn(0f, 1f),
        y = ((region.top + point.y * region.height) / sourceHeight).coerceIn(0f, 1f),
    )
}
