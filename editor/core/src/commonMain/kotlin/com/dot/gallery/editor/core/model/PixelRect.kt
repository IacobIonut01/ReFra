package com.dot.gallery.editor.core.model

/**
 * An integer pixel rectangle with non-negative origin and positive size.
 * [rightExclusive]/[bottomExclusive] are checked at construction to stay
 * inside the `Int` range.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    init {
        require(left >= 0 && top >= 0) {
            "rect origin must be non-negative: ($left, $top)"
        }
        require(width > 0 && height > 0) {
            "rect size must be positive: ${width}x${height}"
        }
        require(width <= Int.MAX_VALUE - left && height <= Int.MAX_VALUE - top) {
            "rect extent overflows Int range: ($left, $top) ${width}x${height}"
        }
    }

    val rightExclusive: Int get() = left + width
    val bottomExclusive: Int get() = top + height

    operator fun contains(point: Pair<Int, Int>): Boolean =
        point.first in left until rightExclusive && point.second in top until bottomExclusive

    fun isInside(size: PixelSize): Boolean =
        left >= 0 && top >= 0 &&
            rightExclusive <= size.width && bottomExclusive <= size.height
}
