/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.geometry

import com.dot.gallery.core.ocr.domain.NormalizedPoint
import com.dot.gallery.core.ocr.domain.NormalizedQuad
import com.dot.gallery.core.ocr.domain.OcrLine
import kotlin.math.abs
import kotlin.math.hypot

object OcrGeometry {
    fun bounds(quad: NormalizedQuad): NormalizedBounds {
        val points = quad.points
        return NormalizedBounds(
            left = points.minOf(NormalizedPoint::x),
            top = points.minOf(NormalizedPoint::y),
            right = points.maxOf(NormalizedPoint::x),
            bottom = points.maxOf(NormalizedPoint::y),
        )
    }

    fun area(quad: NormalizedQuad): Float = polygonArea(quad.points).toFloat()

    fun boundsIoU(first: NormalizedQuad, second: NormalizedQuad): Float {
        val a = bounds(first)
        val b = bounds(second)
        val intersection = a.intersection(b)?.area ?: 0f
        val union = a.area + b.area - intersection
        return if (union > 0f) (intersection / union).coerceIn(0f, 1f) else 0f
    }

    fun polygonIoU(first: NormalizedQuad, second: NormalizedQuad): Float {
        val firstArea = polygonArea(first.points)
        val secondArea = polygonArea(second.points)
        if (firstArea <= GEOMETRY_EPSILON || secondArea <= GEOMETRY_EPSILON) return 0f
        val intersection = clipConvex(first.points, second.points)
        val intersectionArea = polygonArea(intersection)
        val union = firstArea + secondArea - intersectionArea
        return if (union > GEOMETRY_EPSILON) (intersectionArea / union).toFloat().coerceIn(0f, 1f) else 0f
    }

    fun contains(quad: NormalizedQuad, point: NormalizedPoint, inflation: Float = 0f): Boolean {
        require(inflation.isFinite() && inflation >= 0f)
        if (pointInPolygon(quad.points, point)) return true
        if (inflation == 0f) return false
        val expanded = bounds(quad)
        if (point.x < expanded.left - inflation || point.x > expanded.right + inflation ||
            point.y < expanded.top - inflation || point.y > expanded.bottom + inflation
        ) return false
        return quad.points.indices.any { index ->
            val start = quad.points[index]
            val end = quad.points[(index + 1) % quad.points.size]
            distanceToSegment(point, start, end) <= inflation
        }
    }

    fun perspective(from: NormalizedQuad, to: NormalizedQuad): PerspectiveTransform? {
        val source = from.points
        val target = to.points
        val matrix = Array(8) { DoubleArray(9) }
        for (index in 0 until 4) {
            val x = source[index].x.toDouble()
            val y = source[index].y.toDouble()
            val u = target[index].x.toDouble()
            val v = target[index].y.toDouble()
            val first = index * 2
            matrix[first][0] = x
            matrix[first][1] = y
            matrix[first][2] = 1.0
            matrix[first][6] = -u * x
            matrix[first][7] = -u * y
            matrix[first][8] = u
            matrix[first + 1][3] = x
            matrix[first + 1][4] = y
            matrix[first + 1][5] = 1.0
            matrix[first + 1][6] = -v * x
            matrix[first + 1][7] = -v * y
            matrix[first + 1][8] = v
        }
        return solve(matrix)?.let(::PerspectiveTransform)
    }

    fun mapQuad(transform: PerspectiveTransform, quad: NormalizedQuad): NormalizedQuad? {
        val mapped = quad.points.map { transform.map(it) ?: return null }
        return mapped.toQuad()
    }

    fun mapExif(point: NormalizedPoint, orientation: ExifOrientation): NormalizedPoint = when (orientation) {
        ExifOrientation.NORMAL -> point
        ExifOrientation.FLIP_HORIZONTAL -> NormalizedPoint(1f - point.x, point.y)
        ExifOrientation.ROTATE_180 -> NormalizedPoint(1f - point.x, 1f - point.y)
        ExifOrientation.FLIP_VERTICAL -> NormalizedPoint(point.x, 1f - point.y)
        ExifOrientation.TRANSPOSE -> NormalizedPoint(point.y, point.x)
        ExifOrientation.ROTATE_90 -> NormalizedPoint(1f - point.y, point.x)
        ExifOrientation.TRANSVERSE -> NormalizedPoint(1f - point.y, 1f - point.x)
        ExifOrientation.ROTATE_270 -> NormalizedPoint(point.y, 1f - point.x)
    }

    fun mapExif(quad: NormalizedQuad, orientation: ExifOrientation): NormalizedQuad =
        quad.points.map { mapExif(it, orientation) }.toQuad()

    fun orientedDimensions(width: Int, height: Int, orientation: ExifOrientation): Pair<Int, Int> {
        require(width > 0 && height > 0)
        return if (orientation.swapsDimensions) height to width else width to height
    }

    /** Selects both endpoints and every line between them in stable reading order. */
    fun contiguousSelection(lines: List<OcrLine>, firstId: String, secondId: String): List<OcrLine> {
        val ordered = lines.sortedWith(compareBy(OcrLine::readingIndex, OcrLine::id))
        val first = ordered.indexOfFirst { it.id == firstId }
        val second = ordered.indexOfFirst { it.id == secondId }
        if (first < 0 || second < 0) return emptyList()
        return ordered.subList(minOf(first, second), maxOf(first, second) + 1).toList()
    }

    private fun solve(matrix: Array<DoubleArray>): DoubleArray? {
        for (column in 0 until 8) {
            var pivot = column
            for (row in column + 1 until 8) {
                if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
            }
            if (abs(matrix[pivot][column]) < 1e-12) return null
            val swap = matrix[column]
            matrix[column] = matrix[pivot]
            matrix[pivot] = swap
            val divisor = matrix[column][column]
            for (entry in column until 9) matrix[column][entry] /= divisor
            for (row in 0 until 8) {
                if (row == column) continue
                val factor = matrix[row][column]
                for (entry in column until 9) matrix[row][entry] -= factor * matrix[column][entry]
            }
        }
        return DoubleArray(8) { matrix[it][8] }.takeIf { values -> values.all(Double::isFinite) }
    }

    private fun clipConvex(subject: List<NormalizedPoint>, clip: List<NormalizedPoint>): List<NormalizedPoint> {
        if (subject.isEmpty()) return emptyList()
        var output = subject.map { DPoint(it.x.toDouble(), it.y.toDouble()) }
        val clipPoints = clip.map { DPoint(it.x.toDouble(), it.y.toDouble()) }
        val orientation = signedArea(clipPoints)
        for (index in clipPoints.indices) {
            val edgeStart = clipPoints[index]
            val edgeEnd = clipPoints[(index + 1) % clipPoints.size]
            val input = output
            output = ArrayList()
            if (input.isEmpty()) break
            var previous = input.last()
            for (current in input) {
                val currentInside = isInside(current, edgeStart, edgeEnd, orientation)
                val previousInside = isInside(previous, edgeStart, edgeEnd, orientation)
                if (currentInside) {
                    if (!previousInside) lineIntersection(previous, current, edgeStart, edgeEnd)?.let(output::add)
                    output.add(current)
                } else if (previousInside) {
                    lineIntersection(previous, current, edgeStart, edgeEnd)?.let(output::add)
                }
                previous = current
            }
        }
        return output.map { NormalizedPoint(it.x.coerceIn(0.0, 1.0).toFloat(), it.y.coerceIn(0.0, 1.0).toFloat()) }
    }

    private fun isInside(point: DPoint, start: DPoint, end: DPoint, orientation: Double): Boolean {
        val cross = cross(start, end, point)
        return if (orientation >= 0.0) cross >= -GEOMETRY_EPSILON else cross <= GEOMETRY_EPSILON
    }

    private fun lineIntersection(a: DPoint, b: DPoint, c: DPoint, d: DPoint): DPoint? {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val cdX = d.x - c.x
        val cdY = d.y - c.y
        val denominator = abX * cdY - abY * cdX
        if (abs(denominator) < GEOMETRY_EPSILON) return null
        val t = ((c.x - a.x) * cdY - (c.y - a.y) * cdX) / denominator
        return DPoint(a.x + t * abX, a.y + t * abY)
    }

    private fun pointInPolygon(polygon: List<NormalizedPoint>, point: NormalizedPoint): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if (distanceToSegment(point, previous, current) <= GEOMETRY_EPSILON) return true
            val crosses = (current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
            if (crosses) inside = !inside
            previous = current
        }
        return inside
    }

    private fun distanceToSegment(point: NormalizedPoint, start: NormalizedPoint, end: NormalizedPoint): Double {
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        if (dx == 0.0 && dy == 0.0) return hypot((point.x - start.x).toDouble(), (point.y - start.y).toDouble())
        val projection = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy))
            .coerceIn(0.0, 1.0)
        return hypot(point.x - (start.x + projection * dx), point.y - (start.y + projection * dy))
    }

    private fun polygonArea(points: List<NormalizedPoint>): Double = abs(
        points.indices.sumOf { index ->
            val current = points[index]
            val next = points[(index + 1) % points.size]
            current.x.toDouble() * next.y - next.x.toDouble() * current.y
        } / 2.0,
    )

    private fun signedArea(points: List<DPoint>): Double = points.indices.sumOf { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        current.x * next.y - next.x * current.y
    } / 2.0

    private fun cross(a: DPoint, b: DPoint, point: DPoint): Double =
        (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)

    private fun List<NormalizedPoint>.toQuad(): NormalizedQuad {
        require(size == 4)
        return NormalizedQuad(this[0], this[1], this[2], this[3])
    }

    private data class DPoint(val x: Double, val y: Double)
}

internal const val GEOMETRY_EPSILON = 1e-7
