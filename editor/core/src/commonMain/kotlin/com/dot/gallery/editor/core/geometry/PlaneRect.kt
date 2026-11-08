package com.dot.gallery.editor.core.geometry

/**
 * An axis-aligned rectangle in plane pixel units — the crop as the user
 * sees it. Unlike [com.dot.gallery.editor.core.model.NormalizedCrop] it is
 * not normalized and carries no ordering guarantees beyond `left <= right`
 * and `top <= bottom`.
 */
data class PlaneRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: Vec2 get() = Vec2((left + right) / 2.0, (top + bottom) / 2.0)
    val aspect: Double get() = width / height

    fun corners(): List<Vec2> = listOf(
        Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom)
    )

    /** Linear interpolation of every edge toward [other]. */
    fun lerp(other: PlaneRect, t: Double) = PlaneRect(
        left + (other.left - left) * t,
        top + (other.top - top) * t,
        right + (other.right - right) * t,
        bottom + (other.bottom - bottom) * t
    )

    fun translate(dx: Double, dy: Double) =
        PlaneRect(left + dx, top + dy, right + dx, bottom + dy)

    companion object {
        fun centered(center: Vec2, width: Double, height: Double) = PlaneRect(
            center.x - width / 2.0, center.y - height / 2.0,
            center.x + width / 2.0, center.y + height / 2.0
        )
    }
}
