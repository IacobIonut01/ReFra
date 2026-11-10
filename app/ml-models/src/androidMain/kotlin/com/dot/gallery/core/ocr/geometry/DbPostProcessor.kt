/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point in pixels of the image the detector read. */
data class PixelPoint(val x: Float, val y: Float)

/** A detected text box in pixels, with its corners in image orientation (as PaddleOCR orders them). */
data class PixelQuad(
    val topLeft: PixelPoint,
    val topRight: PixelPoint,
    val bottomRight: PixelPoint,
    val bottomLeft: PixelPoint,
) {
    val points: List<PixelPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Crop size as PaddleOCR's `get_rotate_crop_image` measures it: the longer of each pair of sides. */
    val cropWidth: Float get() = max(distance(topLeft, topRight), distance(bottomLeft, bottomRight))
    val cropHeight: Float get() = max(distance(topLeft, bottomLeft), distance(topRight, bottomRight))

    fun toNormalizedQuad(width: Int, height: Int): NormalizedQuad = NormalizedQuad(
        topLeft.toNormalized(width, height),
        topRight.toNormalized(width, height),
        bottomRight.toNormalized(width, height),
        bottomLeft.toNormalized(width, height),
    )

    private fun PixelPoint.toNormalized(width: Int, height: Int) = NormalizedPoint(
        (x / width).coerceIn(0f, 1f),
        (y / height).coerceIn(0f, 1f),
    )
}

internal fun distance(first: PixelPoint, second: PixelPoint): Float = hypot(second.x - first.x, second.y - first.y)

/**
 * Turns the probability map of a DB (differentiable binarization) text detector into text boxes, the
 * way PaddleOCR's `DBPostProcess` does: binarize at [threshold], take each connected region's minimum
 * area rectangle (approximated by its principal axes), drop regions whose mean probability inside
 * that rectangle is below the box threshold or whose short side is under [MIN_SIZE] map pixels, then
 * grow the rectangle by `area × unclipRatio ÷ perimeter` on every side. The detector is trained on
 * text kernels shrunk by that same distance, so the grown box covers the whole glyphs.
 */
object DbPostProcessor {
    const val MIN_SIZE = 3

    fun boxes(
        map: FloatArray,
        offset: Int,
        width: Int,
        height: Int,
        threshold: Float,
        boxThreshold: Float,
        unclipRatio: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        maximumDetections: Int,
    ): List<PixelQuad> {
        val pixelCount = width.toLong() * height
        if (pixelCount <= 0L || pixelCount > Int.MAX_VALUE || maximumDetections <= 0) return emptyList()
        if (offset < 0 || map.size - offset < pixelCount) return emptyList()
        val visited = BooleanArray(pixelCount.toInt())
        val queue = IntArray(pixelCount.toInt())
        val result = ArrayList<PixelQuad>()
        for (seed in visited.indices) {
            if (visited[seed] || map[offset + seed] < threshold) continue
            var head = 0
            var tail = 0
            queue[tail++] = seed
            visited[seed] = true
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                if (x > 0) tail = enqueue(index - 1, map, offset, threshold, visited, queue, tail)
                if (x + 1 < width) tail = enqueue(index + 1, map, offset, threshold, visited, queue, tail)
                if (y > 0) tail = enqueue(index - width, map, offset, threshold, visited, queue, tail)
                if (y + 1 < height) tail = enqueue(index + width, map, offset, threshold, visited, queue, tail)
            }
            box(queue, tail, map, offset, width, height, boxThreshold, unclipRatio, sourceWidth, sourceHeight)
                ?.let(result::add)
            if (result.size >= maximumDetections) break
        }
        return result.sortedWith(compareBy<PixelQuad>({ it.points.minOf(PixelPoint::y) }, { it.points.minOf(PixelPoint::x) }))
    }

    /**
     * Orders four corners like PaddleOCR's `get_mini_boxes`: the two leftmost points are the left side
     * (upper one first), the two rightmost the right side. The box therefore keeps the image's
     * orientation whatever the rectangle's angle, and a tall box stays tall.
     */
    fun orderCorners(corners: List<PixelPoint>): PixelQuad {
        require(corners.size == 4)
        val byX = corners.sortedWith(compareBy(PixelPoint::x, PixelPoint::y))
        val (topLeft, bottomLeft) = if (byX[1].y > byX[0].y) byX[0] to byX[1] else byX[1] to byX[0]
        val (topRight, bottomRight) = if (byX[3].y > byX[2].y) byX[2] to byX[3] else byX[3] to byX[2]
        return PixelQuad(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun box(
        component: IntArray,
        count: Int,
        map: FloatArray,
        offset: Int,
        mapWidth: Int,
        mapHeight: Int,
        boxThreshold: Float,
        unclipRatio: Float,
        sourceWidth: Int,
        sourceHeight: Int,
    ): PixelQuad? {
        if (count == 0) return null
        // Principal axes of the region's pixel centres.
        var meanX = 0.0
        var meanY = 0.0
        for (index in 0 until count) {
            meanX += component[index] % mapWidth + 0.5
            meanY += component[index] / mapWidth + 0.5
        }
        meanX /= count
        meanY /= count
        var covarianceX = 0.0
        var covarianceY = 0.0
        var covarianceXY = 0.0
        for (index in 0 until count) {
            val x = component[index] % mapWidth + 0.5 - meanX
            val y = component[index] / mapWidth + 0.5 - meanY
            covarianceX += x * x
            covarianceY += y * y
            covarianceXY += x * y
        }
        val angle = 0.5 * atan2(2.0 * covarianceXY, covarianceX - covarianceY)
        val axisX = cos(angle)
        val axisY = sin(angle)
        val normalX = -axisY
        val normalY = axisX
        var minAlong = Double.POSITIVE_INFINITY
        var maxAlong = Double.NEGATIVE_INFINITY
        var minAcross = Double.POSITIVE_INFINITY
        var maxAcross = Double.NEGATIVE_INFINITY
        for (index in 0 until count) {
            val x = component[index] % mapWidth + 0.5 - meanX
            val y = component[index] / mapWidth + 0.5 - meanY
            val along = x * axisX + y * axisY
            val across = x * normalX + y * normalY
            minAlong = minOf(minAlong, along)
            maxAlong = maxOf(maxAlong, along)
            minAcross = minOf(minAcross, across)
            maxAcross = maxOf(maxAcross, across)
        }
        // Short side measured between pixel centres, as on PaddleOCR's contour points.
        if (min(maxAlong - minAlong, maxAcross - minAcross) < MIN_SIZE) return null

        // The rectangle covering the region's pixels (each pixel is one unit wide).
        minAlong -= 0.5
        maxAlong += 0.5
        minAcross -= 0.5
        maxAcross += 0.5
        val score = meanInside(map, offset, mapWidth, mapHeight, meanX, meanY, axisX, axisY, minAlong, maxAlong, minAcross, maxAcross)
        if (score < boxThreshold) return null

        val length = maxAlong - minAlong
        val thickness = maxAcross - minAcross
        val grow = length * thickness * unclipRatio / (2.0 * (length + thickness))
        if (min(length, thickness) + 2.0 * grow < MIN_SIZE + 2) return null
        minAlong -= grow
        maxAlong += grow
        minAcross -= grow
        maxAcross += grow

        fun corner(along: Double, across: Double) = PixelPoint(
            x = ((meanX + along * axisX + across * normalX) / mapWidth * sourceWidth)
                .toFloat().coerceIn(0f, sourceWidth.toFloat()),
            y = ((meanY + along * axisY + across * normalY) / mapHeight * sourceHeight)
                .toFloat().coerceIn(0f, sourceHeight.toFloat()),
        )
        val quad = orderCorners(
            listOf(
                corner(minAlong, minAcross),
                corner(maxAlong, minAcross),
                corner(maxAlong, maxAcross),
                corner(minAlong, maxAcross),
            )
        )
        return quad.takeIf { it.cropWidth >= 2f && it.cropHeight >= 2f }
    }

    /**
     * Mean probability of the map pixels whose centres fall inside the rectangle, low ones included,
     * like PaddleOCR's `box_score_fast`. Averaging only the pixels above the threshold made the box
     * threshold reject almost nothing.
     */
    private fun meanInside(
        map: FloatArray,
        offset: Int,
        mapWidth: Int,
        mapHeight: Int,
        centerX: Double,
        centerY: Double,
        axisX: Double,
        axisY: Double,
        minAlong: Double,
        maxAlong: Double,
        minAcross: Double,
        maxAcross: Double,
    ): Float {
        val xs = doubleArrayOf(minAlong, maxAlong).flatMap { along ->
            doubleArrayOf(minAcross, maxAcross).map { across -> centerX + along * axisX - across * axisY }
        }
        val ys = doubleArrayOf(minAlong, maxAlong).flatMap { along ->
            doubleArrayOf(minAcross, maxAcross).map { across -> centerY + along * axisY + across * axisX }
        }
        val left = xs.min().toInt().coerceIn(0, mapWidth - 1)
        val right = xs.max().toInt().coerceIn(0, mapWidth - 1)
        val top = ys.min().toInt().coerceIn(0, mapHeight - 1)
        val bottom = ys.max().toInt().coerceIn(0, mapHeight - 1)
        var sum = 0.0
        var pixels = 0
        for (y in top..bottom) {
            val dy = y + 0.5 - centerY
            for (x in left..right) {
                val dx = x + 0.5 - centerX
                val along = dx * axisX + dy * axisY
                val across = -dx * axisY + dy * axisX
                if (along < minAlong || along > maxAlong || across < minAcross || across > maxAcross) continue
                sum += map[offset + y * mapWidth + x]
                pixels++
            }
        }
        return if (pixels == 0) 0f else (sum / pixels).toFloat()
    }

    private fun enqueue(
        index: Int,
        map: FloatArray,
        offset: Int,
        threshold: Float,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
    ): Int {
        if (visited[index] || map[offset + index] < threshold) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }
}
