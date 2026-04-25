package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.PainterMotionEvent
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import com.dot.gallery.feature_node.presentation.edit.utils.dragMotionEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MarkupPainter(
    modifier: Modifier = Modifier,
    bitmap: Any?,
    paths: List<Pair<Path, PathProperties>>,
    addPath: (Path, PathProperties) -> Unit,
    clearPathsUndone: () -> Unit,
    currentPosition: Offset,
    setCurrentPosition: (Offset) -> Unit,
    previousPosition: Offset,
    setPreviousPosition: (Offset) -> Unit,
    drawMode: DrawMode,
    currentPath: Path,
    setCurrentPath: (Path) -> Unit,
    currentPathProperty: PathProperties,
    setCurrentPathProperty: (PathProperties) -> Unit,
    currentImage: Bitmap?,
    applyDrawing: (Bitmap, () -> Unit) -> Unit,
    onNavigateBack: () -> Unit = {},
    requestApply: Boolean = false,
    onApplyHandled: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
    onSelectedTextIndexChange: (Int) -> Unit = {},
) {
    var graphicsLayer = rememberGraphicsLayer()

    /**
     * Canvas touch state. [PainterMotionEvent.Idle] by default, [PainterMotionEvent.Down] at first contact,
     * [PainterMotionEvent.Move] while dragging and [PainterMotionEvent.Up] when first pointer is up
     */
    var painterMotionEvent by remember { mutableStateOf(PainterMotionEvent.Idle) }

    // Zoom and pan state for navigating the canvas while drawing
    var canvasScale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    // Tracks whether a multi-touch (pinch/pan) gesture is in progress,
    // used to suppress accidental drawing from the first finger of a pinch
    var isZooming by remember { mutableStateOf(false) }

    // Text annotation drag state
    var isDraggingText by remember { mutableStateOf(false) }
    var isResizingText by remember { mutableStateOf(false) }
    var canvasLayoutSize by remember { mutableStateOf(IntSize.Zero) }

    // Keep fresh references for pointer-input lambdas (captured once per key change)
    val latestTextAnnotations by rememberUpdatedState(textAnnotations)
    val latestOnTextAnnotationsChange by rememberUpdatedState(onTextAnnotationsChange)
    val latestSelectedTextIndex by rememberUpdatedState(selectedTextIndex)
    val latestOnSelectedTextIndexChange by rememberUpdatedState(onSelectedTextIndexChange)

    val shouldSaveDrawing by remember(paths, textAnnotations, currentImage) {
        derivedStateOf { (paths.isNotEmpty() || textAnnotations.isNotEmpty()) && currentImage != null }
    }

    val mutex = remember { Mutex() }

    LaunchedEffect(requestApply) {
        if (requestApply && shouldSaveDrawing) {
            delay(100)
            mutex.withLock {
                val image = graphicsLayer.toImageBitmap().asAndroidBitmap()
                applyDrawing(image) {
                    onNavigateBack()
                }
            }
            onApplyHandled()
        } else if (requestApply) {
            onApplyHandled()
        }
    }

    GlideImage(
        model = bitmap,
        contentDescription = null,
        modifier = Modifier
            .wrapContentSize()
            .onSizeChanged { canvasLayoutSize = it }
            // Pinch-to-zoom, two-finger pan, and two-finger text rotation
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isTextTwoFingerRotating = false
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            if (!isZooming && !isTextTwoFingerRotating) {
                                // Decide: rotate selected text or zoom canvas
                                if (drawMode == DrawMode.Text &&
                                    latestSelectedTextIndex in latestTextAnnotations.indices
                                ) {
                                    val pressed = event.changes.filter { it.pressed }
                                    val centroid = Offset(
                                        pressed.map { it.position.x }.average().toFloat(),
                                        pressed.map { it.position.y }.average().toFloat()
                                    )
                                    val sz = Size(
                                        canvasLayoutSize.width.toFloat(),
                                        canvasLayoutSize.height.toFloat()
                                    )
                                    val ann = latestTextAnnotations[latestSelectedTextIndex]
                                    val bounds = measureTextBounds(ann, sz)
                                    val localCentroid =
                                        inverseRotatePoint(centroid, bounds.center, ann.rotation)
                                    val hitPad = 40f
                                    val hitRect = Rect(
                                        bounds.left - hitPad, bounds.top - hitPad,
                                        bounds.right + hitPad, bounds.bottom + hitPad
                                    )
                                    isTextTwoFingerRotating = hitRect.contains(localCentroid)
                                }
                                if (!isTextTwoFingerRotating) {
                                    isZooming = true
                                    currentPath.reset()
                                    painterMotionEvent = PainterMotionEvent.Idle
                                    setCurrentPosition(Offset.Unspecified)
                                }
                            }

                            if (isTextTwoFingerRotating &&
                                latestSelectedTextIndex in latestTextAnnotations.indices
                            ) {
                                // Two-finger rotation + pinch-to-resize on selected text
                                val rotation = event.calculateRotation()
                                val zoom = event.calculateZoom()
                                val ann = latestTextAnnotations[latestSelectedTextIndex]
                                val updated = latestTextAnnotations.toMutableList()
                                updated[latestSelectedTextIndex] = ann.copy(
                                    rotation = ann.rotation + rotation,
                                    fontSize = (ann.fontSize * zoom).coerceIn(0.02f, 0.2f)
                                )
                                latestOnTextAnnotationsChange(updated)
                            } else {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                canvasScale = (canvasScale * zoom).coerceIn(1f, 5f)
                                canvasOffset = if (canvasScale == 1f) {
                                    Offset.Zero
                                } else {
                                    canvasOffset + pan
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    // Reset once all fingers are lifted
                    isZooming = false
                }
            }
            .graphicsLayer {
                scaleX = canvasScale
                scaleY = canvasScale
                translationX = canvasOffset.x
                translationY = canvasOffset.y
            }
            .dragMotionEvent(
                key = drawMode,
                onDragStart = { pointerInputChange ->
                    if (drawMode == DrawMode.Text && !isZooming) {
                        val pos = pointerInputChange.position
                        val sz = Size(canvasLayoutSize.width.toFloat(), canvasLayoutSize.height.toFloat())
                        // First check corner handles on the selected annotation
                        if (latestSelectedTextIndex in latestTextAnnotations.indices) {
                            val corner = hitTestCornerHandle(
                                pos, latestTextAnnotations[latestSelectedTextIndex], sz
                            )
                            if (corner == 0) {
                                // Top-left = delete
                                val updated = latestTextAnnotations.toMutableList()
                                updated.removeAt(latestSelectedTextIndex)
                                latestOnTextAnnotationsChange(updated)
                                latestOnSelectedTextIndexChange(-1)
                                isDraggingText = false
                                isResizingText = false
                                pointerInputChange.consume()
                                return@dragMotionEvent
                            } else if (corner > 0) {
                                // Corner handles = resize
                                isResizingText = true
                                isDraggingText = false
                                pointerInputChange.consume()
                            }
                        }
                        // Only do body hit-test when no corner handle was engaged
                        if (!isResizingText) {
                            val hitIdx = hitTestTextAnnotation(pos, latestTextAnnotations, canvasLayoutSize)
                            if (hitIdx >= 0) {
                                latestOnSelectedTextIndexChange(hitIdx)
                                isDraggingText = true
                                isResizingText = false
                            } else {
                                latestOnSelectedTextIndexChange(-1)
                                isDraggingText = false
                                isResizingText = false
                            }
                        }
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Touch || isZooming) {
                        pointerInputChange.consume()
                    } else {
                        painterMotionEvent = PainterMotionEvent.Down
                        val pos = pointerInputChange.position
                        setCurrentPosition(pos)
                        if (pointerInputChange.pressed != pointerInputChange.previousPressed) pointerInputChange.consume()
                    }
                },
                onDrag = { pointerInputChange ->
                    if (isZooming) {
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Text && isResizingText && latestSelectedTextIndex >= 0) {
                        // Resize the selected text annotation via corner drag
                        val change = pointerInputChange.positionChange()
                        val dy = change.y / canvasLayoutSize.height.coerceAtLeast(1)
                        val updated = latestTextAnnotations.toMutableList()
                        val ann = updated[latestSelectedTextIndex]
                        updated[latestSelectedTextIndex] = ann.copy(
                            fontSize = (ann.fontSize + dy).coerceIn(0.02f, 0.2f)
                        )
                        latestOnTextAnnotationsChange(updated)
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Text && isDraggingText && latestSelectedTextIndex >= 0) {
                        // Drag the selected text annotation
                        val change = pointerInputChange.positionChange()
                        val dx = change.x / canvasLayoutSize.width.coerceAtLeast(1)
                        val dy = change.y / canvasLayoutSize.height.coerceAtLeast(1)
                        val updated = latestTextAnnotations.toMutableList()
                        val ann = updated[latestSelectedTextIndex]
                        updated[latestSelectedTextIndex] = ann.copy(
                            position = Offset(
                                (ann.position.x + dx).coerceIn(0f, 1f),
                                (ann.position.y + dy).coerceIn(0f, 1f)
                            )
                        )
                        latestOnTextAnnotationsChange(updated)
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Touch || (drawMode == DrawMode.Text && !isResizingText && !isDraggingText)) {
                        val change = pointerInputChange.positionChange()
                        canvasOffset += change * canvasScale
                        pointerInputChange.consume()
                    } else {
                        painterMotionEvent = PainterMotionEvent.Move
                        val pos = pointerInputChange.position
                        setCurrentPosition(pos)
                        if (pointerInputChange.positionChange() != Offset.Zero) pointerInputChange.consume()
                    }
                },
                onDragEnd = { pointerInputChange ->
                    if (isZooming) {
                        currentPath.reset()
                        setCurrentPath(Path())
                        painterMotionEvent = PainterMotionEvent.Idle
                        setCurrentPosition(Offset.Unspecified)
                    } else if (drawMode == DrawMode.Text) {
                        isDraggingText = false
                        isResizingText = false
                    } else if (drawMode != DrawMode.Touch) {
                        painterMotionEvent = PainterMotionEvent.Up
                    }
                    if (pointerInputChange.pressed != pointerInputChange.previousPressed) pointerInputChange.consume()
                }
            )
            .drawWithCache {
                when (painterMotionEvent) {

                    PainterMotionEvent.Down -> {
                        if (drawMode != DrawMode.Touch) {
                            currentPath.moveTo(currentPosition.x, currentPosition.y)
                        }
                        setPreviousPosition(currentPosition)
                    }

                    PainterMotionEvent.Move -> {
                        if (drawMode != DrawMode.Touch) {
                            currentPath.quadraticTo(
                                previousPosition.x,
                                previousPosition.y,
                                (previousPosition.x + currentPosition.x) / 2,
                                (previousPosition.y + currentPosition.y) / 2
                            )
                        }
                        setPreviousPosition(currentPosition)
                    }

                    PainterMotionEvent.Up -> {
                        if (drawMode != DrawMode.Touch) {
                            currentPath.lineTo(currentPosition.x, currentPosition.y)

                            // Pointer is up save current path
//                        paths[currentPath] = currentPathProperty
                            addPath(currentPath, currentPathProperty)

                            // Since paths are keys for map, use new one for each key
                            // and have separate path for each down-move-up gesture cycle
                            setCurrentPath(Path())

                            // Create new instance of path properties to have new path and properties
                            // only for the one currently being drawn
                            setCurrentPathProperty(
                                PathProperties(
                                    strokeWidth = currentPathProperty.strokeWidth,
                                    color = currentPathProperty.color,
                                    strokeCap = currentPathProperty.strokeCap,
                                    strokeJoin = currentPathProperty.strokeJoin,
                                    eraseMode = currentPathProperty.eraseMode
                                )
                            )
                        }

                        // Since new path is drawn no need to store paths to undone
                        clearPathsUndone()

                        // If we leave this state at MotionEvent.Up it causes current path to draw
                        // line from (0,0) if this composable recomposes when draw mode is changed
                        setCurrentPosition(Offset.Unspecified)
                        setPreviousPosition(currentPosition)
                        painterMotionEvent = PainterMotionEvent.Idle
                    }

                    else -> Unit
                }
                graphicsLayer = obtainGraphicsLayer().apply {
                    record {
                        with(drawContext.canvas.nativeCanvas) {
                            val checkPoint = saveLayer(null, null)
                            paths.forEach {
                                val path = it.first
                                val property = it.second
                                if (!property.eraseMode) {
                                    drawPath(
                                        color = property.color,
                                        path = path,
                                        style = Stroke(
                                            width = property.strokeWidth,
                                            cap = property.strokeCap,
                                            join = property.strokeJoin
                                        )
                                    )
                                } else {

                                    // Source
                                    drawPath(
                                        color = Color.Transparent,
                                        path = path,
                                        style = Stroke(
                                            width = currentPathProperty.strokeWidth,
                                            cap = currentPathProperty.strokeCap,
                                            join = currentPathProperty.strokeJoin
                                        ),
                                        blendMode = BlendMode.Clear
                                    )
                                }
                            }

                            if (painterMotionEvent != PainterMotionEvent.Idle) {

                                if (!currentPathProperty.eraseMode) {
                                    drawPath(
                                        color = currentPathProperty.color,
                                        path = currentPath,
                                        style = Stroke(
                                            width = currentPathProperty.strokeWidth,
                                            cap = currentPathProperty.strokeCap,
                                            join = currentPathProperty.strokeJoin
                                        )
                                    )
                                } else {
                                    drawPath(
                                        color = Color.Transparent,
                                        path = currentPath,
                                        style = Stroke(
                                            width = currentPathProperty.strokeWidth,
                                            cap = currentPathProperty.strokeCap,
                                            join = currentPathProperty.strokeJoin
                                        ),
                                        blendMode = BlendMode.Clear
                                    )
                                }
                            }
                            restoreToCount(checkPoint)

                            // Draw text annotations (for export)
                            drawTextAnnotations(textAnnotations, size)
                        }
                    }
                }
                onDrawWithContent {
                    drawContent()
                    with(drawContext.canvas.nativeCanvas) {
                        val checkPoint = saveLayer(null, null)
                        paths.forEach {
                            val path = it.first
                            val property = it.second
                            if (!property.eraseMode) {
                                drawPath(
                                    color = property.color,
                                    path = path,
                                    style = Stroke(
                                        width = property.strokeWidth,
                                        cap = property.strokeCap,
                                        join = property.strokeJoin
                                    )
                                )
                            } else {

                                // Source
                                drawPath(
                                    color = Color.Transparent,
                                    path = path,
                                    style = Stroke(
                                        width = currentPathProperty.strokeWidth,
                                        cap = currentPathProperty.strokeCap,
                                        join = currentPathProperty.strokeJoin
                                    ),
                                    blendMode = BlendMode.Clear
                                )
                            }
                        }

                        if (painterMotionEvent != PainterMotionEvent.Idle) {

                            if (!currentPathProperty.eraseMode) {
                                drawPath(
                                    color = currentPathProperty.color,
                                    path = currentPath,
                                    style = Stroke(
                                        width = currentPathProperty.strokeWidth,
                                        cap = currentPathProperty.strokeCap,
                                        join = currentPathProperty.strokeJoin
                                    )
                                )
                            } else {
                                drawPath(
                                    color = Color.Transparent,
                                    path = currentPath,
                                    style = Stroke(
                                        width = currentPathProperty.strokeWidth,
                                        cap = currentPathProperty.strokeCap,
                                        join = currentPathProperty.strokeJoin
                                    ),
                                    blendMode = BlendMode.Clear
                                )
                            }
                        }
                        restoreToCount(checkPoint)

                        // Draw text annotations (for display)
                        drawTextAnnotations(textAnnotations, size)
                    }

                    // Draw selection bounding box + handles for selected text
                    if (selectedTextIndex in textAnnotations.indices && drawMode == DrawMode.Text) {
                        val ann = textAnnotations[selectedTextIndex]
                        val bounds = measureTextBounds(ann, size)
                        val padding = 12f
                        val boxRect = Rect(
                            left = bounds.left - padding,
                            top = bounds.top - padding,
                            right = bounds.right + padding,
                            bottom = bounds.bottom + padding
                        )
                        val cx = bounds.center.x
                        val cy = bounds.center.y

                        // Apply rotation to the entire selection UI
                        if (ann.rotation != 0f) {
                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.rotate(ann.rotation, cx, cy)
                        }

                        // Rounded rectangle border
                        val selPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = boxRect,
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            )
                        }
                        drawPath(
                            path = selPath,
                            color = Color.White.copy(alpha = 0.7f),
                            style = Stroke(width = 2f)
                        )
                        // Delete handle at top-left (white circle with X)
                        val handleRadius = 28f
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = Offset(boxRect.left, boxRect.top),
                            style = Fill
                        )
                        drawCircle(
                            color = Color.Gray,
                            radius = handleRadius,
                            center = Offset(boxRect.left, boxRect.top),
                            style = Stroke(width = 1.5f)
                        )
                        val xSize = handleRadius * 0.35f
                        val xCenter = Offset(boxRect.left, boxRect.top)
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(xCenter.x - xSize, xCenter.y - xSize),
                            end = Offset(xCenter.x + xSize, xCenter.y + xSize),
                            strokeWidth = 3.5f
                        )
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(xCenter.x + xSize, xCenter.y - xSize),
                            end = Offset(xCenter.x - xSize, xCenter.y + xSize),
                            strokeWidth = 3.5f
                        )
                        // Rotation handle at top-right (white circle with curved arrow)
                        val rotateCenter = Offset(boxRect.right, boxRect.top)
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = rotateCenter,
                            style = Fill
                        )
                        drawCircle(
                            color = Color.Gray,
                            radius = handleRadius,
                            center = rotateCenter,
                            style = Stroke(width = 1.5f)
                        )
                        // Draw rotation arrow icon: 240° arc with forward-pointing arrowhead
                        val iconR = handleRadius * 0.45f
                        drawArc(
                            color = Color.DarkGray,
                            startAngle = 50f,
                            sweepAngle = 240f,
                            useCenter = false,
                            topLeft = Offset(rotateCenter.x - iconR, rotateCenter.y - iconR),
                            size = Size(iconR * 2, iconR * 2),
                            style = Stroke(width = 2.5f)
                        )
                        // Arrowhead extends FORWARD past the arc tip (50+240 = 290°)
                        val endRad = Math.toRadians(290.0)
                        val arcTipX = rotateCenter.x + iconR * kotlin.math.cos(endRad).toFloat()
                        val arcTipY = rotateCenter.y + iconR * kotlin.math.sin(endRad).toFloat()
                        val tx = -kotlin.math.sin(endRad).toFloat()  // tangent (CW direction)
                        val ty = kotlin.math.cos(endRad).toFloat()
                        val nx = kotlin.math.cos(endRad).toFloat()   // normal (outward)
                        val ny = kotlin.math.sin(endRad).toFloat()
                        val hl = 7f
                        val hw = hl * 0.5f
                        val arrowHead = Path().apply {
                            // Tip extends forward along tangent
                            moveTo(arcTipX + tx * hl, arcTipY + ty * hl)
                            // Two base points at arc tip, spread along normal
                            lineTo(arcTipX + nx * hw, arcTipY + ny * hw)
                            lineTo(arcTipX - nx * hw, arcTipY - ny * hw)
                            close()
                        }
                        drawPath(arrowHead, Color.DarkGray)
                        // Resize handles at bottom-left and bottom-right
                        val resizeCorners = listOf(
                            Offset(boxRect.left, boxRect.bottom),
                            Offset(boxRect.right, boxRect.bottom)
                        )
                        val cornerRadius = 16f
                        resizeCorners.forEach { corner ->
                            drawCircle(
                                color = Color.White,
                                radius = cornerRadius,
                                center = corner,
                                style = Fill
                            )
                            drawCircle(
                                color = Color.Gray,
                                radius = cornerRadius,
                                center = corner,
                                style = Stroke(width = 1.5f)
                            )
                        }

                        if (ann.rotation != 0f) {
                            drawContext.canvas.nativeCanvas.restore()
                        }
                    }
                }
            }
            .then(modifier)
    )
}

/**
 * Inverse-rotate a point around a center by the given angle (degrees).
 * Used to transform touch coordinates into the annotation's local space.
 */
private fun inverseRotatePoint(point: Offset, center: Offset, angleDegrees: Float): Offset {
    if (angleDegrees == 0f) return point
    val rad = Math.toRadians(-angleDegrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Offset(
        center.x + dx * cos - dy * sin,
        center.y + dx * sin + dy * cos
    )
}

/**
 * Hit-test text annotations in canvas coordinates.
 * Returns the index of the hit annotation or -1.
 */
private fun hitTestTextAnnotation(
    pos: Offset,
    annotations: List<TextAnnotation>,
    canvasSize: androidx.compose.ui.unit.IntSize
): Int {
    val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    // Check in reverse order (topmost first)
    for (i in annotations.indices.reversed()) {
        val bounds = measureTextBounds(annotations[i], size)
        val localPos = inverseRotatePoint(pos, bounds.center, annotations[i].rotation)
        val padding = 20f
        val hitRect = Rect(
            left = bounds.left - padding,
            top = bounds.top - padding,
            right = bounds.right + padding,
            bottom = bounds.bottom + padding
        )
        if (hitRect.contains(localPos)) return i
    }
    return -1
}

/**
 * Hit-test the 4 corner handles of a text annotation.
 * Returns corner index: 0=top-left (delete), 1=bottom-left (resize),
 * 2=top-right (rotate), 3=bottom-right (resize), or -1 if none hit.
 */
private fun hitTestCornerHandle(
    pos: Offset,
    annotation: TextAnnotation,
    canvasSize: Size
): Int {
    val bounds = measureTextBounds(annotation, canvasSize)
    val localPos = inverseRotatePoint(pos, bounds.center, annotation.rotation)
    val padding = 12f
    val boxRect = Rect(
        left = bounds.left - padding,
        top = bounds.top - padding,
        right = bounds.right + padding,
        bottom = bounds.bottom + padding
    )
    val corners = listOf(
        Offset(boxRect.left, boxRect.top),     // 0 = delete
        Offset(boxRect.left, boxRect.bottom),  // 1 = resize
        Offset(boxRect.right, boxRect.top),    // 2 = rotate
        Offset(boxRect.right, boxRect.bottom)  // 3 = resize
    )
    val hitRadius = 48f // generous touch target matching 28f visual radius
    for (i in corners.indices) {
        if ((localPos - corners[i]).getDistance() <= hitRadius) return i
    }
    return -1
}

/**
 * Measure the bounding rectangle of a text annotation in canvas coordinates.
 */
private fun measureTextBounds(annotation: TextAnnotation, canvasSize: Size): Rect {
    val textSize = annotation.fontSize * canvasSize.height
    val x = annotation.position.x * canvasSize.width
    val y = annotation.position.y * canvasSize.height
    val paint = Paint().apply {
        this.textSize = textSize
        isAntiAlias = true
    }
    val lines = annotation.text.split("\n")
    var maxWidth = 0f
    lines.forEach { line ->
        val w = paint.measureText(line)
        if (w > maxWidth) maxWidth = w
    }
    val totalHeight = lines.size * textSize * 1.2f
    return Rect(
        left = x,
        top = y - textSize, // baseline offset
        right = x + maxWidth,
        bottom = y - textSize + totalHeight
    )
}

/**
 * Draw text annotations on the native canvas using normalized coordinates.
 */
private fun DrawScope.drawTextAnnotations(
    annotations: List<TextAnnotation>,
    canvasSize: Size
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    annotations.forEach { annotation ->
        val textSize = annotation.fontSize * canvasSize.height
        val x = annotation.position.x * canvasSize.width
        val y = annotation.position.y * canvasSize.height
        val textPaint = Paint().apply {
            color = annotation.color.toArgb()
            this.textSize = textSize
            isAntiAlias = true
        }
        val lines = annotation.text.split("\n")
        if (annotation.rotation != 0f) {
            val bounds = measureTextBounds(annotation, canvasSize)
            val cx = bounds.center.x
            val cy = bounds.center.y
            nativeCanvas.save()
            nativeCanvas.rotate(annotation.rotation, cx, cy)
        }
        lines.forEachIndexed { index, line ->
            nativeCanvas.drawText(
                line,
                x,
                y + index * (textSize * 1.2f),
                textPaint
            )
        }
        if (annotation.rotation != 0f) {
            nativeCanvas.restore()
        }
    }
}