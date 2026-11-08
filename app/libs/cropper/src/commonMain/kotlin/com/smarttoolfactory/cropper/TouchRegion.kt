package com.smarttoolfactory.cropper

/**
 * Enum for detecting which section of Composable user has initially touched
 */
enum class TouchRegion {
    TopLeft, TopRight, BottomLeft, BottomRight, LeftEdge, RightEdge, TopEdge, BottomEdge, Inside, None
}

fun handlesTouched(touchRegion: TouchRegion) = touchRegion == TouchRegion.TopLeft ||
        touchRegion == TouchRegion.TopRight ||
        touchRegion == TouchRegion.BottomLeft ||
        touchRegion == TouchRegion.BottomRight ||
        touchRegion == TouchRegion.LeftEdge ||
        touchRegion == TouchRegion.RightEdge ||
        touchRegion == TouchRegion.TopEdge ||
        touchRegion == TouchRegion.BottomEdge
