/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.decode

import com.dot.gallery.core.ocr.domain.OcrLimits

data class OcrTilePlan(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val tiles: List<OcrTile>,
) {
    init {
        require(OcrLimits.checkedSourceArea(sourceWidth, sourceHeight) != null)
        require(tiles.isNotEmpty() && tiles.size <= OcrLimits.MAX_TILES)
        require(tiles.first().kind == OcrTileKind.OVERVIEW)
        require(tiles.count { it.kind == OcrTileKind.OVERVIEW } == 1)
        require(tiles.all { it.source.right <= sourceWidth && it.source.bottom <= sourceHeight })
    }

    val overview: OcrTile get() = tiles.first()
    val fineTiles: List<OcrTile> get() = tiles.drop(1)
}
