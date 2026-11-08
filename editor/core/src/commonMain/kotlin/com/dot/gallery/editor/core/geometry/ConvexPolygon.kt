package com.dot.gallery.editor.core.geometry

import kotlin.math.abs

/**
 * A convex polygon in plane pixel units, stored with a positive signed
 * area (counter-clockwise in a y-up frame; the orientation is normalized
 * on construction so callers may pass either winding). An empty or
 * degenerate polygon has fewer than three vertices.
 */
class ConvexPolygon(vertices: List<Vec2>) {

    val vertices: List<Vec2> = if (signedArea(vertices) < 0.0) vertices.reversed() else vertices

    val isEmpty: Boolean get() = vertices.size < 3 || area <= 0.0

    val area: Double get() = abs(signedArea(vertices))

    /**
     * The polygon as unit-normal half-planes `n . p <= c`, one per edge —
     * the form the inscribed-rectangle solver consumes.
     */
    fun halfPlanes(): List<HalfPlane> {
        val out = ArrayList<HalfPlane>(vertices.size)
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val e = b - a
            val len = e.length
            if (len <= 1e-12) continue
            // Positive area: the interior lies to the left of a->b, so the
            // outward normal is the edge turned clockwise.
            val nx = e.y / len
            val ny = -e.x / len
            out += HalfPlane(nx, ny, nx * a.x + ny * a.y)
        }
        return out
    }

    fun contains(p: Vec2, tolerance: Double = 1e-9): Boolean =
        !isEmpty && halfPlanes().all { it.slack(p) >= -tolerance }

    /** Sutherland–Hodgman clip against one half-plane. */
    fun clip(plane: HalfPlane): ConvexPolygon {
        if (vertices.isEmpty()) return this
        val out = ArrayList<Vec2>(vertices.size + 1)
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val sa = plane.slack(a)
            val sb = plane.slack(b)
            if (sa >= 0.0) out += a
            if ((sa >= 0.0) != (sb >= 0.0)) {
                val t = sa / (sa - sb)
                out += a + (b - a) * t
            }
        }
        return ConvexPolygon(out)
    }

    fun clipToRect(left: Double, top: Double, right: Double, bottom: Double): ConvexPolygon =
        clip(HalfPlane(-1.0, 0.0, -left))
            .clip(HalfPlane(1.0, 0.0, right))
            .clip(HalfPlane(0.0, -1.0, -top))
            .clip(HalfPlane(0.0, 1.0, bottom))

    /**
     * The polygon with every edge moved inward by [distance] (empty when it
     * collapses).
     */
    fun inset(distance: Double): ConvexPolygon {
        if (isEmpty) return this
        val planes = halfPlanes()
        val xs = vertices.map { it.x }
        val ys = vertices.map { it.y }
        var out = ConvexPolygon(
            listOf(
                Vec2(xs.min() - 1, ys.min() - 1), Vec2(xs.max() + 1, ys.min() - 1),
                Vec2(xs.max() + 1, ys.max() + 1), Vec2(xs.min() - 1, ys.max() + 1)
            )
        )
        for (p in planes) out = out.clip(HalfPlane(p.nx, p.ny, p.c - distance))
        return out
    }

    /** The nearest point of the polygon (boundary included) to [p]. */
    fun closestPoint(p: Vec2): Vec2? {
        if (vertices.isEmpty()) return null
        if (vertices.size >= 3 && contains(p)) return p
        var best: Vec2 = vertices.first()
        var bestD = Double.MAX_VALUE
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val e = b - a
            val len2 = e dot e
            val t = if (len2 <= 0.0) 0.0 else (((p - a) dot e) / len2).coerceIn(0.0, 1.0)
            val q = a + e * t
            val d = (p - q).length
            if (d < bestD) {
                bestD = d
                best = q
            }
        }
        return best
    }

    fun centroid(): Vec2 {
        if (vertices.isEmpty()) return Vec2(0.0, 0.0)
        if (isEmpty) {
            val sx = vertices.sumOf { it.x }
            val sy = vertices.sumOf { it.y }
            return Vec2(sx / vertices.size, sy / vertices.size)
        }
        var cx = 0.0
        var cy = 0.0
        var a2 = 0.0
        for (i in vertices.indices) {
            val p = vertices[i]
            val q = vertices[(i + 1) % vertices.size]
            val c = p.cross(q)
            a2 += c
            cx += (p.x + q.x) * c
            cy += (p.y + q.y) * c
        }
        return Vec2(cx / (3.0 * a2), cy / (3.0 * a2))
    }

    override fun toString(): String = "ConvexPolygon($vertices)"

    private companion object {
        fun signedArea(v: List<Vec2>): Double {
            if (v.size < 3) return 0.0
            var s = 0.0
            for (i in v.indices) s += v[i].cross(v[(i + 1) % v.size])
            return s / 2.0
        }
    }
}
