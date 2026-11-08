package com.dot.gallery.editor.core.model

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** One tone curve control point, both axes normalized to 0..1. */
data class PhotoCurvePoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && x in 0f..1f) {
            "curve point x must be finite inside 0..1: $x"
        }
        require(y.isFinite() && y in 0f..1f) {
            "curve point y must be finite inside 0..1: $y"
        }
    }
}

/**
 * One tone curve — 2 to [MAX_POINTS] control points in strictly ascending x,
 * joined by a monotone cubic that cannot overshoot between points, so the curve
 * never folds back on itself. Outputs may fall as well as rise, and the end
 * points may move inward: everything left of the first point holds its output
 * and everything right of the last holds its own. [DEFAULT] is the identity
 * line. Both axes are display-encoded (sRGB) values, the space of the
 * histogram drawn behind the curve.
 */
data class PhotoToneCurve(
    val points: PersistentList<PhotoCurvePoint> = DEFAULT
) {
    init {
        require(points.size in 2..MAX_POINTS) {
            "a tone curve holds 2..$MAX_POINTS points: ${points.size}"
        }
        require((1 until points.size).all { points[it].x > points[it - 1].x }) {
            "tone curve points must ascend strictly in x: $points"
        }
    }

    /** The evaluator of these points, built once on first use. */
    val spline: PhotoToneSpline by lazy { PhotoToneSpline(points) }

    /**
     * True when the curve is the identity. Points left on the diagonal bend
     * nothing, so a tapped but unmoved point stays neutral for the renderer.
     * Whether the curve still counts as untouched is `points == DEFAULT`.
     */
    val isNeutral: Boolean
        get() = points.first() == DEFAULT.first() &&
            points.last() == DEFAULT.last() &&
            points.all { it.x == it.y }

    /**
     * Moves the point at [index], clamping x to keep [MIN_GAP] from its
     * neighbours and both axes inside 0..1. Values snap to whole 1/255 steps.
     */
    fun withPointMoved(index: Int, x: Float, y: Float): PhotoToneCurve {
        require(index in points.indices) {
            "tone curve point index out of range: $index"
        }
        require(x.isFinite() && y.isFinite()) {
            "tone curve point must be finite: $x, $y"
        }
        val low = if (index == 0) 0f else points[index - 1].x + MIN_GAP
        val high = if (index == points.lastIndex) 1f else points[index + 1].x - MIN_GAP
        // A project may hold points closer than the gap; those keep their x.
        val movedX = if (low <= high) snap(x.coerceIn(low, high)) else points[index].x
        return copy(
            points = points.set(index, PhotoCurvePoint(movedX, snap(y.coerceIn(0f, 1f))))
        )
    }

    /**
     * Adds a point on the curve at [x], or returns null when the curve is full,
     * [x] lies outside the end points or it would crowd a neighbour.
     */
    fun withPointInserted(x: Float): PhotoToneCurve? {
        if (points.size >= MAX_POINTS || !x.isFinite()) return null
        val snapped = snap(x.coerceIn(0f, 1f))
        val index = points.indexOfFirst { it.x >= snapped }
        if (index <= 0) return null
        if (snapped - points[index - 1].x < MIN_GAP_CHECK) return null
        if (points[index].x - snapped < MIN_GAP_CHECK) return null
        val y = snap(spline.evaluate(snapped).coerceIn(0f, 1f))
        return copy(points = points.add(index, PhotoCurvePoint(snapped, y)))
    }

    /** Removes an interior point; the end points cannot be removed. */
    fun withoutPoint(index: Int): PhotoToneCurve? {
        if (index <= 0 || index >= points.lastIndex) return null
        return copy(points = points.removeAt(index))
    }

    companion object {
        const val MAX_POINTS = 16

        /** The closest two points may sit: three of the 255 input steps. */
        const val MIN_GAP = 3f / 255f

        // Grid values carry rounding error, so "three steps apart" is
        // compared with half a step's slack.
        private const val MIN_GAP_CHECK = 2.5f / 255f

        val DEFAULT: PersistentList<PhotoCurvePoint> = persistentListOf(
            PhotoCurvePoint(0f, 0f),
            PhotoCurvePoint(1f, 1f)
        )

        /** Builds a curve from whole 0..255 input/output pairs. */
        fun ofSteps(vararg steps: Pair<Int, Int>): PhotoToneCurve = PhotoToneCurve(
            steps.map { (x, y) -> PhotoCurvePoint(x / 255f, y / 255f) }.toPersistentList()
        )

        /**
         * Reads the five quarter-spaced ordinates older projects stored.
         * Their identity decodes to [DEFAULT], which has no interior points.
         */
        fun ofQuarterOrdinates(ordinates: List<Float>): PhotoToneCurve {
            require(ordinates.size == 5) {
                "a legacy tone curve holds exactly 5 values: ${ordinates.size}"
            }
            require((1 until ordinates.size).all { ordinates[it] >= ordinates[it - 1] }) {
                "legacy tone curve values must be nondecreasing: $ordinates"
            }
            val points = ordinates.mapIndexed { i, y -> PhotoCurvePoint(i / 4f, y) }
            val curve = PhotoToneCurve(points.toPersistentList())
            return if (curve.isNeutral) PhotoToneCurve() else curve
        }

        private fun snap(value: Float): Float = (value * 255f).roundToInt() / 255f
    }
}

/**
 * The monotone cubic (Fritsch–Carlson) through a curve's points, as one
 * polynomial per segment in `t = x - starts[segment]`. The CPU processor reads
 * [evaluate] and the runtime shader uploads the same [starts] and
 * [coefficients], so both evaluate one set of numbers.
 *
 * Below 0 and above 1, which only out-of-range pixels reach, the curve
 * continues at unit slope so those values keep their distance from the rails.
 */
class PhotoToneSpline internal constructor(points: List<PhotoCurvePoint>) {
    val firstX: Float = points.first().x
    val firstY: Float = points.first().y
    val lastX: Float = points.last().x
    val lastY: Float = points.last().y
    val segments: Int = points.size - 1

    /** Each segment's starting x. */
    val starts: FloatArray = FloatArray(segments) { points[it].x }

    /** Four per segment: the constant, linear, quadratic and cubic terms. */
    val coefficients: FloatArray = FloatArray(segments * 4)

    init {
        val widths = FloatArray(segments) { points[it + 1].x - points[it].x }
        val slopes = FloatArray(segments) { (points[it + 1].y - points[it].y) / widths[it] }
        val tangents = FloatArray(points.size)
        tangents[0] = slopes[0]
        tangents[segments] = slopes[segments - 1]
        for (i in 1 until segments) {
            tangents[i] =
                if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }
        for (i in 0 until segments) {
            if (slopes[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
                continue
            }
            val a = tangents[i] / slopes[i]
            val b = tangents[i + 1] / slopes[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                tangents[i] = t * a * slopes[i]
                tangents[i + 1] = t * b * slopes[i]
            }
        }
        for (i in 0 until segments) {
            val h = widths[i]
            val base = i * 4
            coefficients[base] = points[i].y
            coefficients[base + 1] = tangents[i]
            coefficients[base + 2] = (3f * slopes[i] - 2f * tangents[i] - tangents[i + 1]) / h
            coefficients[base + 3] = (tangents[i] + tangents[i + 1] - 2f * slopes[i]) / (h * h)
        }
    }

    fun evaluate(x: Float): Float {
        if (x <= firstX) return firstY + if (x < 0f) x else 0f
        if (x >= lastX) return lastY + if (x > 1f) x - 1f else 0f
        var segment = 0
        while (segment + 1 < segments && x >= starts[segment + 1]) segment++
        val t = x - starts[segment]
        val base = segment * 4
        return coefficients[base] + t * (
            coefficients[base + 1] + t * (coefficients[base + 2] + t * coefficients[base + 3])
            )
    }
}
