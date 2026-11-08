package com.dot.gallery.editor.core.geometry

import kotlin.math.sqrt

/** A continuous 2-D point/vector in plane pixel units. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    fun cross(o: Vec2) = x * o.y - y * o.x
    val length: Double get() = sqrt(x * x + y * y)
}
