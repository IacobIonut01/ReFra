package com.dot.gallery.editor.core.geometry

import kotlin.math.abs
import kotlin.math.min

/**
 * Axis-aligned rectangles of a fixed aspect inside a convex region — the
 * crop tool's "never blank edges" constraint.
 *
 * For a rectangle of height `t` and aspect `a` (width / height) centred at
 * `c`, containment in a half-plane `n . p <= k` depends only on its worst
 * corner, so it reduces to the linear constraint
 * `n . c + t * w <= k` with `w = |nx| * a / 2 + |ny| / 2`. Containment in
 * the whole region is the intersection of those constraints: the largest
 * rectangle is a three-variable linear programme (solved exactly by vertex
 * enumeration — a clipped quadrilateral has at most eight edges), and for
 * a fixed height the legal centres form the region shrunk by `t * w`.
 */
object InscribedCrop {

    private const val EPS = 1e-9

    /** The largest height (and one centre achieving it) at [aspect], or null for an empty region. */
    fun largest(region: ConvexPolygon, aspect: Double): Pair<Double, Vec2>? {
        require(aspect.isFinite() && aspect > 0.0) { "aspect must be positive: $aspect" }
        if (region.isEmpty) return null
        val planes = region.halfPlanes()
        val scale = region.vertices.maxOf { maxOf(abs(it.x), abs(it.y)) }.coerceAtLeast(1.0)
        val tol = EPS * scale
        // Rows: nx * cx + ny * cy + w * t <= k, plus t >= 0 (-t <= 0).
        val rows = planes.map { doubleArrayOf(it.nx, it.ny, weight(it, aspect), it.c) } +
            listOf(doubleArrayOf(0.0, 0.0, -1.0, 0.0))
        var bestT = -1.0
        var bestC: Vec2? = null
        for (i in rows.indices) for (j in i + 1 until rows.size) for (k in j + 1 until rows.size) {
            val sol = solve3(rows[i], rows[j], rows[k]) ?: continue
            val (cx, cy, t) = sol
            if (t < -tol || t <= bestT) continue
            if (rows.all { r -> r[0] * cx + r[1] * cy + r[2] * t <= r[3] + tol }) {
                bestT = t
                bestC = Vec2(cx, cy)
            }
        }
        val c = bestC ?: return null
        return bestT.coerceAtLeast(0.0) to c
    }

    /**
     * The rectangle at [aspect] of height `min(preferredHeight, largest)`,
     * centred as close to [preferredCenter] as the region allows. Null only
     * for an empty region.
     */
    fun fit(
        region: ConvexPolygon,
        aspect: Double,
        preferredCenter: Vec2,
        preferredHeight: Double = Double.POSITIVE_INFINITY
    ): PlaneRect? {
        val (maxT, maxC) = largest(region, aspect) ?: return null
        val t = min(preferredHeight, maxT).coerceAtLeast(0.0)
        // Shave a hair off the optimum so the shrunk centre region keeps
        // some area — at exactly maxT it collapses to a point or segment.
        val tSafe = if (t >= maxT) maxT * (1.0 - 1e-9) else t
        var centres = boundingBox(region, preferredCenter)
        for (p in region.halfPlanes()) {
            centres = centres.clip(HalfPlane(p.nx, p.ny, p.c - tSafe * weight(p, aspect)))
        }
        val c = if (centres.vertices.isEmpty()) maxC
        else centres.closestPoint(preferredCenter) ?: maxC
        // The full height at the chosen centre: the shave only kept the
        // centre search non-degenerate, it must not leak into the result.
        return PlaneRect.centered(c, t * aspect, t)
    }

    /** True when every corner of [rect] lies in [region] (with a pixel-scale tolerance). */
    fun contains(region: ConvexPolygon, rect: PlaneRect, tolerance: Double = 1e-6): Boolean =
        rect.corners().all { region.contains(it, tolerance) }

    /**
     * The furthest point along the straight edge interpolation from a
     * legal [from] toward [to] that stays inside [region]. Because the
     * region is convex and corners interpolate linearly, legality is
     * monotone along the path and a bisection finds the boundary.
     */
    fun clampToward(region: ConvexPolygon, from: PlaneRect, to: PlaneRect): PlaneRect {
        if (contains(region, to)) return to
        if (!contains(region, from)) return from
        var lo = 0.0
        var hi = 1.0
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (contains(region, from.lerp(to, mid))) lo = mid else hi = mid
        }
        return from.lerp(to, lo)
    }

    private fun weight(p: HalfPlane, aspect: Double) = abs(p.nx) * aspect / 2.0 + abs(p.ny) / 2.0

    private fun boundingBox(region: ConvexPolygon, extra: Vec2): ConvexPolygon {
        val xs = region.vertices.map { it.x } + extra.x
        val ys = region.vertices.map { it.y } + extra.y
        val l = xs.min() - 1.0
        val r = xs.max() + 1.0
        val t = ys.min() - 1.0
        val b = ys.max() + 1.0
        return ConvexPolygon(listOf(Vec2(l, t), Vec2(r, t), Vec2(r, b), Vec2(l, b)))
    }

    private fun solve3(a: DoubleArray, b: DoubleArray, c: DoubleArray): Triple<Double, Double, Double>? {
        val det = det3(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2])
        if (abs(det) < 1e-12) return null
        val dx = det3(a[3], a[1], a[2], b[3], b[1], b[2], c[3], c[1], c[2])
        val dy = det3(a[0], a[3], a[2], b[0], b[3], b[2], c[0], c[3], c[2])
        val dt = det3(a[0], a[1], a[3], b[0], b[1], b[3], c[0], c[1], c[3])
        return Triple(dx / det, dy / det, dt / det)
    }

    private fun det3(
        a: Double, b: Double, c: Double,
        d: Double, e: Double, f: Double,
        g: Double, h: Double, i: Double
    ) = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
}
