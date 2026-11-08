package com.dot.gallery.editor.core.geometry

import com.dot.gallery.editor.core.model.PhotoWarp
import kotlin.math.abs
import kotlin.math.sqrt

/** Which axis a guide segment should end up parallel to. */
enum class GuideAxis { Vertical, Horizontal }

/**
 * One user-drawn guide: a segment along a line that should be straight,
 * with endpoints in rigid-frame pixel coordinates (the unwarped photo),
 * so the guide stays attached to the scene while the warp changes.
 */
data class UprightGuide(val a: Vec2, val b: Vec2, val axis: GuideAxis)

/**
 * Solves rotation + keystone so that every guide becomes axis-aligned in
 * the warp plane.
 *
 * Free parameters depend on what the guides can determine: rotation is
 * always solved; two or more vertical guides additionally free the
 * vertical keystone (converging verticals), two or more horizontal guides
 * the horizontal keystone. A single guide, or one of each, solves rotation
 * alone. The residual per guide is the sine of its angle from the target
 * axis after warping; a damped Gauss–Newton (Levenberg–Marquardt) with a
 * numeric Jacobian minimises the sum of squares inside the legal ranges.
 * [PhotoWarp.scale] is preserved.
 */
object GuidedUpright {

    const val MAX_ROTATION = 45.0
    const val MAX_PERSPECTIVE = 50.0
    const val MAX_GUIDES_PER_AXIS = 2

    /**
     * Returns the solved warp, or null when no guide is usable (fewer than
     * one non-degenerate segment). [width]/[height] are the rigid frame.
     */
    fun solve(
        width: Double,
        height: Double,
        guides: List<UprightGuide>,
        base: PhotoWarp = PhotoWarp()
    ): PhotoWarp? {
        val usable = guides.filter { (it.b - it.a).length > 1e-6 }
        if (usable.isEmpty()) return null
        val vertical = usable.count { it.axis == GuideAxis.Vertical }
        val horizontal = usable.count { it.axis == GuideAxis.Horizontal }
        val freeVertical = vertical >= 2
        val freeHorizontal = horizontal >= 2

        // Parameter vector: [rotation, vertical, horizontal]; fixed entries stay 0.
        val free = booleanArrayOf(true, freeVertical, freeHorizontal)
        val bounds = doubleArrayOf(MAX_ROTATION, MAX_PERSPECTIVE, MAX_PERSPECTIVE)
        val scale = base.scale

        fun warpOf(p: DoubleArray) = PhotoWarp(
            rotationDegrees = p[0].toFloat(),
            verticalPerspective = p[1].toFloat(),
            horizontalPerspective = p[2].toFloat(),
            scale = scale
        )

        fun residuals(p: DoubleArray): DoubleArray? {
            val plane = WarpPlane(width, height, warpOf(p))
            val out = DoubleArray(usable.size)
            for ((i, g) in usable.withIndex()) {
                val a = plane.toPlane(g.a.x, g.a.y) ?: return null
                val b = plane.toPlane(g.b.x, g.b.y) ?: return null
                val d = b - a
                val len = d.length
                if (len <= 1e-9) return null
                out[i] = if (g.axis == GuideAxis.Vertical) d.x / len else d.y / len
            }
            return out
        }

        fun cost(r: DoubleArray) = r.sumOf { it * it }

        fun clamp(p: DoubleArray) = DoubleArray(3) { i ->
            if (free[i]) p[i].coerceIn(-bounds[i], bounds[i]) else 0.0
        }

        var p = DoubleArray(3)
        var r = residuals(p) ?: return null
        var c = cost(r)
        var lambda = 1e-3
        val h = doubleArrayOf(1e-4, 1e-3, 1e-3)
        val indices = (0 until 3).filter { free[it] }
        repeat(100) {
            // Numeric Jacobian (central differences) over the free parameters.
            val jac = Array(usable.size) { DoubleArray(indices.size) }
            for ((col, k) in indices.withIndex()) {
                val plus = p.copyOf().also { it[k] += h[k] }
                val minus = p.copyOf().also { it[k] -= h[k] }
                val rp = residuals(clampSoft(plus, bounds)) ?: return@repeat
                val rm = residuals(clampSoft(minus, bounds)) ?: return@repeat
                for (row in usable.indices) jac[row][col] = (rp[row] - rm[row]) / (2 * h[k])
            }
            val n = indices.size
            val jtj = Array(n) { DoubleArray(n) }
            val jtr = DoubleArray(n)
            for (row in usable.indices) {
                for (a in 0 until n) {
                    jtr[a] += jac[row][a] * r[row]
                    for (b in 0 until n) jtj[a][b] += jac[row][a] * jac[row][b]
                }
            }
            var improved = false
            for (attempt in 0 until 12) {
                val m = Array(n) { a -> DoubleArray(n) { b -> jtj[a][b] + if (a == b) lambda * (1.0 + jtj[a][a]) else 0.0 } }
                val step = solveLinear(m, jtr.map { -it }.toDoubleArray()) ?: break
                val candidate = p.copyOf()
                for ((col, k) in indices.withIndex()) candidate[k] += step[col]
                val clamped = clamp(candidate)
                val rc = residuals(clamped)
                if (rc != null && cost(rc) < c) {
                    p = clamped
                    r = rc
                    val prev = c
                    c = cost(rc)
                    lambda = (lambda / 3.0).coerceAtLeast(1e-12)
                    improved = prev - c > 1e-18
                    break
                }
                lambda *= 4.0
            }
            if (!improved || c < 1e-20) return finish(p, scale)
        }
        return finish(p, scale)
    }

    private fun finish(p: DoubleArray, scale: Float): PhotoWarp {
        // Round to the rulers' resolution: 0.1° and whole keystone steps
        // are what the inspector can display and nudge.
        fun round(v: Double, step: Double) = (kotlin.math.round(v / step) * step)
        return PhotoWarp(
            rotationDegrees = round(p[0], 0.01).toFloat().coerceIn(-45f, 45f),
            verticalPerspective = round(p[1], 0.01).toFloat().coerceIn(-100f, 100f),
            horizontalPerspective = round(p[2], 0.01).toFloat().coerceIn(-100f, 100f),
            scale = scale
        )
    }

    private fun clampSoft(p: DoubleArray, bounds: DoubleArray) =
        DoubleArray(3) { i -> p[i].coerceIn(-bounds[i] * 1.01, bounds[i] * 1.01) }

    /** Gaussian elimination with partial pivoting for the tiny normal equations. */
    private fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (abs(m[row][col]) > abs(m[pivot][col])) pivot = row
            if (abs(m[pivot][col]) < 1e-15) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (row in 0 until n) {
                if (row == col) continue
                val f = m[row][col] / m[col][col]
                for (k in col..n) m[row][k] -= f * m[col][k]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    /** Total residual (root mean square of the guides' axis sines) for [warp]. */
    fun error(width: Double, height: Double, guides: List<UprightGuide>, warp: PhotoWarp): Double {
        val plane = WarpPlane(width, height, warp)
        if (guides.isEmpty()) return 0.0
        var sum = 0.0
        for (g in guides) {
            val a = plane.toPlane(g.a.x, g.a.y) ?: return Double.POSITIVE_INFINITY
            val b = plane.toPlane(g.b.x, g.b.y) ?: return Double.POSITIVE_INFINITY
            val d = b - a
            val s = if (g.axis == GuideAxis.Vertical) d.x / d.length else d.y / d.length
            sum += s * s
        }
        return sqrt(sum / guides.size)
    }
}
