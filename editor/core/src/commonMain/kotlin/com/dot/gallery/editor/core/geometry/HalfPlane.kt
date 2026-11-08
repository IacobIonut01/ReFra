package com.dot.gallery.editor.core.geometry

/** The closed half-plane `nx * x + ny * y <= c`. */
data class HalfPlane(val nx: Double, val ny: Double, val c: Double) {
    fun slack(p: Vec2): Double = c - (nx * p.x + ny * p.y)
}
