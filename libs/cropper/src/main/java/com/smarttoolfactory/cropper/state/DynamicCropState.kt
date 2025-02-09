package com.smarttoolfactory.cropper.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.IntSize
import com.smarttoolfactory.cropper.TouchRegion
import com.smarttoolfactory.cropper.model.AspectRatio
import com.smarttoolfactory.cropper.settings.CropProperties
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * State for cropper with dynamic overlay. Overlay of this state can be moved or resized
 * using handles or touching inner position of overlay. When overlay overflow out of image bounds
 * or moves out of bounds it animates back to valid size and position
 *
 * @param handleSize size of the handle to control, move or scale dynamic overlay
 * @param imageSize size of the **Bitmap**
 * @param containerSize size of the Composable that draws **Bitmap**
 * @param maxZoom maximum zoom value
 * @param fling when set to true dragging pointer builds up velocity. When last
 * pointer leaves Composable a movement invoked against friction till velocity drops below
 * to threshold
 * @param zoomable when set to true zoom is enabled
 * @param pannable when set to true pan is enabled
 * @param rotatable when set to true rotation is enabled
 * @param limitPan limits pan to bounds of parent Composable. Using this flag prevents creating
 * @param minDimension minimum size of the overlay, if null defaults to handleSize * 2
 * @param fixedAspectRatio when set to true aspect ratio of overlay is fixed
 * empty space on sides or edges of parent
 */
class DynamicCropState internal constructor(
    private var handleSize: Float,
    imageSize: IntSize,
    containerSize: IntSize,
    drawAreaSize: IntSize,
    aspectRatio: AspectRatio,
    overlayRatio: Float,
    maxZoom: Float,
    fling: Boolean,
    zoomable: Boolean,
    pannable: Boolean,
    rotatable: Boolean,
    limitPan: Boolean,
    private val minDimension: IntSize?,
    private val fixedAspectRatio: Boolean,
) : CropState(
    imageSize = imageSize,
    containerSize = containerSize,
    drawAreaSize = drawAreaSize,
    aspectRatio = aspectRatio,
    overlayRatio = overlayRatio,
    maxZoom = maxZoom,
    fling = fling,
    zoomable = zoomable,
    pannable = pannable,
    rotatable = rotatable,
    limitPan = limitPan
) {

    /**
     * Rectangle that covers bounds of Composable. This is a rectangle uses [containerSize] as
     * size and [Offset.Zero] as top left corner
     */
    private val rectBounds = Rect(
        offset = Offset.Zero,
        size = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
    )

    // This rectangle is needed to set bounds set at first touch position while
    // moving to constraint current bounds to temp one from first down
    // When pointer is up
    private var rectTemp = Rect.Zero

    // Touch position for edge of the rectangle, used for not jumping to edge of rectangle
    // when user moves a handle. We set positionActual as position of selected handle
    // and using this distance as offset to not have a jump from touch position
    private var distanceToEdgeFromTouch = Offset.Zero

    private var doubleTapped = false

    // Check if transform gesture has been invoked
    // inside overlay but with multiple pointers to zoom
    private var gestureInvoked = false

    override suspend fun updateProperties(cropProperties: CropProperties, forceUpdate: Boolean) {
        handleSize = cropProperties.handleSize
        super.updateProperties(cropProperties, forceUpdate)
    }

    override suspend fun onDown(change: PointerInputChange) {

        rectTemp = overlayRect.copy()

        val position = change.position
        val touchPositionScreenX = position.x
        val touchPositionScreenY = position.y

        val touchPositionOnScreen = Offset(touchPositionScreenX, touchPositionScreenY)

        // Get whether user touched outside, handles of rectangle or inner region or overlay
        // rectangle. Depending on where is touched we can move or scale overlay
        touchRegion = getTouchRegion(
            position = touchPositionOnScreen,
            rect = overlayRect,
            threshold = handleSize
        )

        // This is the difference between touch position and edge
        // This is required for not moving edge of draw rect to touch position on move
        distanceToEdgeFromTouch =
            getDistanceToEdgeFromTouch(touchRegion, rectTemp, touchPositionOnScreen)
    }

    override suspend fun onMove(changes: List<PointerInputChange>) {

        if (changes.isEmpty()) {
            touchRegion = TouchRegion.None
            return
        }

        gestureInvoked = changes.size > 1 && (touchRegion == TouchRegion.Inside)

        // If overlay is touched and pointer size is one update
        // or pointer size is bigger than one but touched any handles update
        if (touchRegion != TouchRegion.None && changes.size == 1 && !gestureInvoked) {

            val change = changes.first()

            // Default min dimension is handle size * 2
            val doubleHandleSize = handleSize * 2
            val defaultMinDimension =
                IntSize(doubleHandleSize.roundToInt(), doubleHandleSize.roundToInt())

            // update overlay rectangle based on where its touched and touch position to corners
            // This function moves and/or scales overlay rectangle
            val newRect = updateOverlayRect(
                distanceToEdgeFromTouch = distanceToEdgeFromTouch,
                touchRegion = touchRegion,
                minDimension = minDimension ?: defaultMinDimension,
                rectTemp = rectTemp,
                overlayRect = overlayRect,
                change = change,
                aspectRatio = getAspectRatio(),
                fixedAspectRatio = fixedAspectRatio,
            )

            snapOverlayRectTo(newRect)
        }
    }

    private fun getAspectRatio(): Float {
        return if (aspectRatio == AspectRatio.Original) {
            imageSize.width / imageSize.height.toFloat()
        } else {
            aspectRatio.value
        }
    }

    override suspend fun onUp(change: PointerInputChange) = coroutineScope {
        if (touchRegion != TouchRegion.None) {

            val isInContainerBounds = isRectInContainerBounds(overlayRect)
            if (!isInContainerBounds) {

                // Calculate new overlay since it's out of Container bounds
                rectTemp = calculateOverlayRectInBounds(rectBounds, overlayRect)

                // Animate overlay to new bounds inside container
                animateOverlayRectTo(rectTemp)
            }

            // Update and animate pan, zoom and image draw area after overlay position is updated
            animateTransformationToOverlayBounds(overlayRect, true)

            // Update image draw area after animating pan, zoom or rotation is completed
            drawAreaRect = updateImageDrawRectFromTransformation()

            touchRegion = TouchRegion.None
        }

        gestureInvoked = false
    }

    override suspend fun onGesture(
        centroid: Offset,
        panChange: Offset,
        zoomChange: Float,
        rotationChange: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>
    ) {

        if (touchRegion == TouchRegion.None || gestureInvoked) {
            doubleTapped = false

            val newPan = if (gestureInvoked) Offset.Zero else panChange

            updateTransformState(
                centroid = centroid,
                zoomChange = zoomChange,
                panChange = newPan,
                rotationChange = rotationChange
            )

            // Update image draw rectangle based on pan, zoom or rotation change
            drawAreaRect = updateImageDrawRectFromTransformation()

            // Fling Gesture
            if (pannable && fling) {
                if (changes.size == 1) {
                    addPosition(mainPointer.uptimeMillis, mainPointer.position)
                }
            }
        }
    }

    override suspend fun onGestureStart() = Unit

    override suspend fun onGestureEnd(onBoundsCalculated: () -> Unit) {

        if (touchRegion == TouchRegion.None || gestureInvoked) {

            // Gesture end might be called after second tap and we don't want to fling
            // or animate back to valid bounds when doubled tapped
            if (!doubleTapped) {

                if (pannable && fling && !gestureInvoked && zoom > 1) {
                    fling {
                        // We get target value on start instead of updating bounds after
                        // gesture has finished
                        drawAreaRect = updateImageDrawRectFromTransformation()
                        onBoundsCalculated()
                    }
                } else {
                    onBoundsCalculated()
                }

                animateTransformationToOverlayBounds(overlayRect, animate = true)
            }
        }
    }

    override suspend fun onDoubleTap(
        offset: Offset,
        zoom: Float,
        onAnimationEnd: () -> Unit
    ) {
        doubleTapped = true

        if (fling) {
            resetTracking()
        }
        resetWithAnimation(pan = pan, zoom = zoom, rotation = rotation)

        // We get target value on start instead of updating bounds after
        // gesture has finished
        drawAreaRect = updateImageDrawRectFromTransformation()


        if (!isOverlayInImageDrawBounds()) {
            // Moves rectangle to bounds inside drawArea Rect while keeping aspect ratio
            // of current overlay rect
            animateOverlayRectTo(
                getOverlayFromAspectRatio(
                    containerSize.width.toFloat(),
                    containerSize.height.toFloat(),
                    drawAreaSize.width.toFloat(),
                    aspectRatio,
                    overlayRatio
                )
            )

            animateTransformationToOverlayBounds(overlayRect, false)
        }
        onAnimationEnd()
    }


//    //TODO Change pan when zoom is bigger than 1f and touchRegion is inside overlay rect
//    private suspend fun moveOverlayToBounds(change: PointerInputChange, newRect: Rect) {
//        val bounds = drawAreaRect
//
//        val positionChange = change.positionChangeIgnoreConsumed()
//
//        // When zoom is bigger than 100% and dynamic overlay is not at any edge of
//        // image we can pan in the same direction motion goes towards when touch region
//        // of rectangle is not one of the handles but region inside
//        val isPanRequired = touchRegion == TouchRegion.Inside && zoom > 1f
//
//        // Overlay moving right
//        if (isPanRequired && newRect.right < bounds.right) {
//            println("Moving right newRect $newRect, bounds: $bounds")
//            snapOverlayRectTo(newRect.translate(-positionChange.x, 0f))
//            snapPanXto(pan.x - positionChange.x * zoom)
//            // Overlay moving left
//        } else if (isPanRequired && pan.x < bounds.left && newRect.left <= 0f) {
//            snapOverlayRectTo(newRect.translate(-positionChange.x, 0f))
//            snapPanXto(pan.x - positionChange.x * zoom)
//        } else if (isPanRequired && pan.y < bounds.top && newRect.top <= 0f) {
//            // Overlay moving top
//            snapOverlayRectTo(newRect.translate(0f, -positionChange.y))
//            snapPanYto(pan.y - positionChange.y * zoom)
//        } else if (isPanRequired && -pan.y < bounds.bottom && newRect.bottom >= containerSize.height) {
//            // Overlay moving bottom
//            snapOverlayRectTo(newRect.translate(0f, -positionChange.y))
//            snapPanYto(pan.y - positionChange.y * zoom)
//        } else {
//            snapOverlayRectTo(newRect)
//        }
//        if (touchRegion != TouchRegion.None) {
//            change.consume()
//        }
//    }

    /**
     * When pointer is up calculate valid position and size overlay can be updated to inside
     * a virtual rect between `topLeft = (0,0)` to `bottomRight=(containerWidth, containerHeight)`
     *
     * [overlayRect] might be shrunk or moved up/down/left/right to container bounds when
     * it's out of Composable region
     */
    private fun calculateOverlayRectInBounds(rectBounds: Rect, rectCurrent: Rect): Rect {

        var width = rectCurrent.width
        var height = rectCurrent.height

        if (width > rectBounds.width) {
            width = rectBounds.width
        }

        if (height > rectBounds.height) {
            height = rectBounds.height
        }

        var rect = Rect(offset = rectCurrent.topLeft, size = Size(width, height))

        if (rect.left < rectBounds.left) {
            rect = rect.translate(rectBounds.left - rect.left, 0f)
        }

        if (rect.top < rectBounds.top) {
            rect = rect.translate(0f, rectBounds.top - rect.top)
        }

        if (rect.right > rectBounds.right) {
            rect = rect.translate(rectBounds.right - rect.right, 0f)
        }

        if (rect.bottom > rectBounds.bottom) {
            rect = rect.translate(0f, rectBounds.bottom - rect.bottom)
        }

        return rect
    }

    /**
     * Update overlay rectangle based on touch gesture.
     */
    private fun updateOverlayRect(
        distanceToEdgeFromTouch: Offset,
        touchRegion: TouchRegion,
        minDimension: IntSize,
        rectTemp: Rect,
        overlayRect: Rect,
        change: PointerInputChange,
        aspectRatio: Float,
        fixedAspectRatio: Boolean,
    ): Rect {
        val position = change.position
        // Adjust the touch position using the distance from the edge so that the
        // rectangle doesn’t “jump” when starting the gesture.
        val screenPositionX = position.x + distanceToEdgeFromTouch.x
        val screenPositionY = position.y + distanceToEdgeFromTouch.y

        return when (touchRegion) {
            // --- Corner Cases (existing behavior) ---
            TouchRegion.TopLeft -> {
                val left = screenPositionX.coerceAtMost(rectTemp.right - minDimension.width)
                val top = if (fixedAspectRatio) {
                    // For fixed aspect ratio, calculate the height based on the new width.
                    val width = rectTemp.right - left
                    val height = width / aspectRatio
                    rectTemp.bottom - height
                } else {
                    screenPositionY.coerceAtMost(rectTemp.bottom - minDimension.height)
                }
                Rect(
                    left = left,
                    top = top,
                    right = rectTemp.right,
                    bottom = rectTemp.bottom
                )
            }
            TouchRegion.TopRight -> {
                val right = screenPositionX.coerceAtLeast(rectTemp.left + minDimension.width)
                val top = if (fixedAspectRatio) {
                    val width = right - rectTemp.left
                    val height = width / aspectRatio
                    rectTemp.bottom - height
                } else {
                    screenPositionY.coerceAtMost(rectTemp.bottom - minDimension.height)
                }
                Rect(
                    left = rectTemp.left,
                    top = top,
                    right = right,
                    bottom = rectTemp.bottom
                )
            }
            TouchRegion.BottomLeft -> {
                val left = screenPositionX.coerceAtMost(rectTemp.right - minDimension.width)
                val bottom = if (fixedAspectRatio) {
                    val width = rectTemp.right - left
                    val height = width / aspectRatio
                    rectTemp.top + height
                } else {
                    screenPositionY.coerceAtLeast(rectTemp.top + minDimension.height)
                }
                Rect(
                    left = left,
                    top = rectTemp.top,
                    right = rectTemp.right,
                    bottom = bottom
                )
            }
            TouchRegion.BottomRight -> {
                val right = screenPositionX.coerceAtLeast(rectTemp.left + minDimension.width)
                val bottom = if (fixedAspectRatio) {
                    val width = right - rectTemp.left
                    val height = width / aspectRatio
                    rectTemp.top + height
                } else {
                    screenPositionY.coerceAtLeast(rectTemp.top + minDimension.height)
                }
                Rect(
                    left = rectTemp.left,
                    top = rectTemp.top,
                    right = right,
                    bottom = bottom
                )
            }

            // --- New Edge Cases ---
            TouchRegion.TopEdge -> {
                if (fixedAspectRatio) {
                    // Anchor the bottom edge; calculate new height from the new top.
                    val top = screenPositionY.coerceAtMost(rectTemp.bottom - minDimension.height)
                    val newHeight = rectTemp.bottom - top
                    val newWidth = newHeight * aspectRatio
                    // Center horizontally relative to the original rectangle.
                    val centerX = rectTemp.center.x
                    val left = centerX - newWidth / 2
                    val right = centerX + newWidth / 2
                    Rect(left, top, right, rectTemp.bottom)
                } else {
                    val top = screenPositionY.coerceAtMost(rectTemp.bottom - minDimension.height)
                    Rect(rectTemp.left, top, rectTemp.right, rectTemp.bottom)
                }
            }
            TouchRegion.BottomEdge -> {
                if (fixedAspectRatio) {
                    // Anchor the top edge; calculate new height from the new bottom.
                    val bottom = screenPositionY.coerceAtLeast(rectTemp.top + minDimension.height)
                    val newHeight = bottom - rectTemp.top
                    val newWidth = newHeight * aspectRatio
                    val centerX = rectTemp.center.x
                    val left = centerX - newWidth / 2
                    val right = centerX + newWidth / 2
                    Rect(left, rectTemp.top, right, bottom)
                } else {
                    val bottom = screenPositionY.coerceAtLeast(rectTemp.top + minDimension.height)
                    Rect(rectTemp.left, rectTemp.top, rectTemp.right, bottom)
                }
            }
            TouchRegion.LeftEdge -> {
                if (fixedAspectRatio) {
                    // Anchor the right edge; calculate new width from the new left.
                    val left = screenPositionX.coerceAtMost(rectTemp.right - minDimension.width)
                    val newWidth = rectTemp.right - left
                    val newHeight = newWidth / aspectRatio
                    val centerY = rectTemp.center.y
                    val top = centerY - newHeight / 2
                    val bottom = centerY + newHeight / 2
                    Rect(left, top, rectTemp.right, bottom)
                } else {
                    val left = screenPositionX.coerceAtMost(rectTemp.right - minDimension.width)
                    Rect(left, rectTemp.top, rectTemp.right, rectTemp.bottom)
                }
            }
            TouchRegion.RightEdge -> {
                if (fixedAspectRatio) {
                    // Anchor the left edge; calculate new width from the new right.
                    val right = screenPositionX.coerceAtLeast(rectTemp.left + minDimension.width)
                    val newWidth = right - rectTemp.left
                    val newHeight = newWidth / aspectRatio
                    val centerY = rectTemp.center.y
                    val top = centerY - newHeight / 2
                    val bottom = centerY + newHeight / 2
                    Rect(rectTemp.left, top, right, bottom)
                } else {
                    val right = screenPositionX.coerceAtLeast(rectTemp.left + minDimension.width)
                    Rect(rectTemp.left, rectTemp.top, right, rectTemp.bottom)
                }
            }

            // --- Dragging the entire rectangle ---
            TouchRegion.Inside -> {
                val drag = change.positionChangeIgnoreConsumed()
                // Translate the whole rectangle by the drag amount.
                overlayRect.translate(drag.x, drag.y)
            }

            // --- Default: no change ---
            else -> overlayRect
        }
    }


    /**
     * get [TouchRegion] based on touch position on screen relative to [overlayRect].
     */
    private fun getTouchRegion(
        position: Offset,
        rect: Rect,
        threshold: Float
    ): TouchRegion {
        // Check for corners first
        if (abs(position.x - rect.left) <= threshold && abs(position.y - rect.top) <= threshold) {
            return TouchRegion.TopLeft
        }
        if (abs(position.x - rect.right) <= threshold && abs(position.y - rect.top) <= threshold) {
            return TouchRegion.TopRight
        }
        if (abs(position.x - rect.right) <= threshold && abs(position.y - rect.bottom) <= threshold) {
            return TouchRegion.BottomRight
        }
        if (abs(position.x - rect.left) <= threshold && abs(position.y - rect.bottom) <= threshold) {
            return TouchRegion.BottomLeft
        }

        // Then check for the edges. Note that we exclude the corners by checking that the
        // touch is not too near the endpoints (using an offset of 'threshold').
        if (abs(position.x - rect.left) <= threshold &&
            position.y in (rect.top + threshold)..(rect.bottom - threshold)
        ) {
            return TouchRegion.LeftEdge
        }
        if (abs(position.x - rect.right) <= threshold &&
            position.y in (rect.top + threshold)..(rect.bottom - threshold)
        ) {
            return TouchRegion.RightEdge
        }
        if (abs(position.y - rect.top) <= threshold &&
            position.x in (rect.left + threshold)..(rect.right - threshold)
        ) {
            return TouchRegion.TopEdge
        }
        if (abs(position.y - rect.bottom) <= threshold &&
            position.x in (rect.left + threshold)..(rect.right - threshold)
        ) {
            return TouchRegion.BottomEdge
        }

        // Finally, if the touch is within the rectangle, return inside.
        if (rect.contains(position)) {
            return TouchRegion.Inside
        }

        return TouchRegion.None
    }

    /**
     * Returns how far user touched to corner or center of sides of the screen. [TouchRegion]
     * where user exactly has touched is already passed to this function. For instance user
     * touched top left then this function returns distance to top left from user's position so
     * we can add an offset to not jump edge to position user touched.
     */
    private fun getDistanceToEdgeFromTouch(
        touchRegion: TouchRegion,
        rect: Rect,
        touchPosition: Offset
    ) = when (touchRegion) {
        TouchRegion.TopLeft -> {
            rect.topLeft - touchPosition
        }

        TouchRegion.TopRight -> {
            rect.topRight - touchPosition
        }

        TouchRegion.BottomLeft -> {
            rect.bottomLeft - touchPosition
        }

        TouchRegion.BottomRight -> {
            rect.bottomRight - touchPosition
        }

        else -> {
            Offset.Zero
        }
    }
}
