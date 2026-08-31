package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
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
import com.dot.gallery.feature_node.domain.model.editor.MarkupBrush
import com.dot.gallery.feature_node.domain.model.editor.PainterMotionEvent
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.domain.model.editor.TextAnnotation
import com.dot.gallery.feature_node.presentation.edit.utils.ImageObscure
import com.dot.gallery.feature_node.presentation.edit.utils.dragMotionEvent
import androidx.core.graphics.scale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal fun rescaleMarkupPaths(
    paths: List<Pair<Path, PathProperties>>,
    oldSize: IntSize,
    newSize: IntSize,
) {
    if (oldSize.width <= 0 || oldSize.height <= 0 || newSize.width <= 0 || newSize.height <= 0 || oldSize == newSize) return
    val scaleX = newSize.width / oldSize.width.toFloat()
    val scaleY = newSize.height / oldSize.height.toFloat()
    val matrix = Matrix().apply { setScale(scaleX, scaleY) }
    val strokeScale = minOf(newSize.width, newSize.height) /
            minOf(oldSize.width, oldSize.height).toFloat()
    paths.forEach { (path, properties) ->
        path.asAndroidPath().transform(matrix)
        properties.strokeWidth *= strokeScale
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MarkupPainter(
    modifier: Modifier = Modifier,
    bitmap: Any?,
    paths: List<Pair<Path, PathProperties>>,
    pathsUndone: List<Pair<Path, PathProperties>>,
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
    applyDrawing: (Bitmap, (Boolean) -> Unit) -> Unit,
    onNavigateBack: () -> Unit = {},
    requestApply: Boolean = false,
    onApplyHandled: () -> Unit = {},
    textAnnotations: List<TextAnnotation> = emptyList(),
    onTextAnnotationsChange: (List<TextAnnotation>) -> Unit = {},
    selectedTextIndex: Int = -1,
    onSelectedTextIndexChange: (Int) -> Unit = {},
    pendingFaceRegions: List<android.graphics.RectF> = emptyList(),
    onFaceRegionsConsumed: () -> Unit = {},
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
    var isRotatingText by remember { mutableStateOf(false) }
    // Rotation gesture tracking (single-finger rotate handle)
    var rotateCenter by remember { mutableStateOf(Offset.Zero) }
    var rotateStartAngle by remember { mutableFloatStateOf(0f) }
    var rotateStartRotation by remember { mutableFloatStateOf(0f) }
    var canvasLayoutSize by remember { mutableStateOf(IntSize.Zero) }

    // Keep fresh references for pointer-input lambdas (captured once per key change)
    val latestTextAnnotations by rememberUpdatedState(textAnnotations)
    val latestOnTextAnnotationsChange by rememberUpdatedState(onTextAnnotationsChange)
    val latestSelectedTextIndex by rememberUpdatedState(selectedTextIndex)
    val latestOnSelectedTextIndexChange by rememberUpdatedState(onSelectedTextIndexChange)

    val shouldSaveDrawing by remember(paths, textAnnotations, currentImage) {
        derivedStateOf { (paths.isNotEmpty() || textAnnotations.isNotEmpty()) && currentImage != null }
    }

    // Cache of processed (blurred/mosaicked) copies of the base image, keyed by
    // (brush, quantized-strength) and scaled to the current canvas size. Reset when either the
    // source bitmap or the canvas size changes so shader pixels always align with the strokes.
    val processedCache = remember(currentImage, canvasLayoutSize) {
        HashMap<Pair<MarkupBrush, Int>, Bitmap>()
    }

    fun shaderBrushFor(property: PathProperties): ShaderBrush? {
        if (property.brush == MarkupBrush.Solid) return null
        val base = currentImage ?: return null
        val w = canvasLayoutSize.width
        val h = canvasLayoutSize.height
        if (w <= 0 || h <= 0) return null
        val bucket = (property.effectStrength * 20f).roundToInt()
        val key = property.brush to bucket
        val bmp = processedCache.getOrPut(key) {
            val scaled = base.scale(w, h)
            val processed = when (property.brush) {
                MarkupBrush.Blur -> ImageObscure.blur(scaled, property.effectStrength)
                MarkupBrush.Mosaic -> ImageObscure.mosaic(scaled, property.effectStrength)
                else -> scaled
            }
            if (processed != scaled) scaled.recycle()
            processed
        }
        return ShaderBrush(BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }

    LaunchedEffect(requestApply) {
        if (requestApply && shouldSaveDrawing) {
            delay(100)
            val image = graphicsLayer.toImageBitmap().asAndroidBitmap()
            applyDrawing(image) { applied ->
                if (applied) {
                    onTextAnnotationsChange(emptyList())
                    onSelectedTextIndexChange(-1)
                    onNavigateBack()
                }
                onApplyHandled()
            }
        } else if (requestApply) {
            onNavigateBack()
            onApplyHandled()
        }
    }

    // Convert detected face rects (normalized) into filled oval markup regions using the current
    // effect brush. Runs once the canvas size is known, then clears the pending list.
    LaunchedEffect(pendingFaceRegions, canvasLayoutSize) {
        if (pendingFaceRegions.isNotEmpty() && canvasLayoutSize.width > 0 && canvasLayoutSize.height > 0) {
            val w = canvasLayoutSize.width.toFloat()
            val h = canvasLayoutSize.height.toFloat()
            val pad = 0.04f
            pendingFaceRegions.forEach { r ->
                val left = (r.left - pad).coerceIn(0f, 1f) * w
                val top = (r.top - pad).coerceIn(0f, 1f) * h
                val right = (r.right + pad).coerceIn(0f, 1f) * w
                val bottom = (r.bottom + pad).coerceIn(0f, 1f) * h
                if (right > left && bottom > top) {
                    val path = Path().apply { addOval(Rect(left, top, right, bottom)) }
                    addPath(
                        path,
                        currentPathProperty.copy(
                            eraseMode = false,
                            brush = MarkupBrush.Blur,
                            fillRegion = true
                        )
                    )
                }
            }
            onFaceRegionsConsumed()
        }
    }

    GlideImage(
        model = bitmap,
        contentDescription = null,
        modifier = Modifier
            .wrapContentSize()
            .onSizeChanged { newSize ->
                if (newSize.width > 0 && newSize.height > 0) {
                    rescaleMarkupPaths(
                        paths + pathsUndone + (currentPath to currentPathProperty),
                        canvasLayoutSize,
                        newSize
                    )
                    canvasLayoutSize = newSize
                }
            }
            // Pinch-to-zoom, two-finger pan, and two-finger text rotation
            .pointerInput(drawMode, currentPath) {
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
                                    // This handler runs outside the graphicsLayer, so the
                                    // centroid is in screen space. Map it back into content
                                    // space (graphicsLayer scales around the center).
                                    val layerCenter = Offset(sz.width / 2f, sz.height / 2f)
                                    val contentCentroid = layerCenter +
                                            (centroid - canvasOffset - layerCenter) / canvasScale
                                    val localCentroid =
                                        inverseRotatePoint(contentCentroid, bounds.center, ann.rotation)
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
                                val sz = Size(
                                    canvasLayoutSize.width.toFloat(),
                                    canvasLayoutSize.height.toFloat()
                                )
                                updated[latestSelectedTextIndex] = clampAnnotationCenter(
                                    ann.copy(
                                        rotation = ann.rotation + rotation,
                                        fontSize = (ann.fontSize * zoom).coerceIn(0.02f, 0.2f)
                                    ),
                                    sz
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
                                isRotatingText = false
                                pointerInputChange.consume()
                                return@dragMotionEvent
                            } else if (corner == 2) {
                                // Top-right = rotate
                                val ann = latestTextAnnotations[latestSelectedTextIndex]
                                val bounds = measureTextBounds(ann, sz)
                                rotateCenter = bounds.center
                                rotateStartAngle = kotlin.math.atan2(
                                    pos.y - bounds.center.y,
                                    pos.x - bounds.center.x
                                )
                                rotateStartRotation = ann.rotation
                                isRotatingText = true
                                isResizingText = false
                                isDraggingText = false
                                pointerInputChange.consume()
                            } else if (corner == 1 || corner == 3) {
                                // Bottom corners = resize
                                isResizingText = true
                                isDraggingText = false
                                isRotatingText = false
                                pointerInputChange.consume()
                            }
                        }
                        // Only do body hit-test when no corner handle was engaged
                        if (!isResizingText && !isRotatingText) {
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
                    } else if (drawMode == DrawMode.Text && isRotatingText && latestSelectedTextIndex >= 0) {
                        // Rotate the selected text annotation via the top-right handle
                        val pos = pointerInputChange.position
                        val currentAngle = kotlin.math.atan2(
                            pos.y - rotateCenter.y,
                            pos.x - rotateCenter.x
                        )
                        val deltaDeg =
                            Math.toDegrees((currentAngle - rotateStartAngle).toDouble()).toFloat()
                        val updated = latestTextAnnotations.toMutableList()
                        val ann = updated[latestSelectedTextIndex]
                        updated[latestSelectedTextIndex] = ann.copy(
                            rotation = rotateStartRotation + deltaDeg
                        )
                        latestOnTextAnnotationsChange(updated)
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Text && isResizingText && latestSelectedTextIndex >= 0) {
                        // Resize the selected text annotation via corner drag
                        val change = pointerInputChange.positionChange()
                        val dy = change.y / canvasLayoutSize.height.coerceAtLeast(1)
                        val updated = latestTextAnnotations.toMutableList()
                        val ann = updated[latestSelectedTextIndex]
                        val sz = Size(
                            canvasLayoutSize.width.toFloat(),
                            canvasLayoutSize.height.toFloat()
                        )
                        updated[latestSelectedTextIndex] = clampAnnotationCenter(
                            ann.copy(
                                fontSize = (ann.fontSize + dy).coerceIn(0.02f, 0.2f)
                            ),
                            sz
                        )
                        latestOnTextAnnotationsChange(updated)
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Text && isDraggingText && latestSelectedTextIndex >= 0) {
                        // Drag the selected text annotation, keeping its box within the canvas
                        val change = pointerInputChange.positionChange()
                        val dx = change.x / canvasLayoutSize.width.coerceAtLeast(1)
                        val dy = change.y / canvasLayoutSize.height.coerceAtLeast(1)
                        val updated = latestTextAnnotations.toMutableList()
                        val ann = updated[latestSelectedTextIndex]
                        val sz = Size(
                            canvasLayoutSize.width.toFloat(),
                            canvasLayoutSize.height.toFloat()
                        )
                        val bounds = measureTextBounds(ann, sz)
                        val normW = if (sz.width > 0f) bounds.width / sz.width else 0f
                        val normH = if (sz.height > 0f) bounds.height / sz.height else 0f
                        // position.x is the box left; box top (normalized) = position.y - fontSize
                        val (minX, maxX) = if (normW <= 1f) 0f to (1f - normW) else (1f - normW) to 0f
                        val (minTop, maxTop) = if (normH <= 1f) 0f to (1f - normH) else (1f - normH) to 0f
                        val newX = (ann.position.x + dx).coerceIn(minX, maxX)
                        val newTop = (ann.position.y - ann.fontSize + dy).coerceIn(minTop, maxTop)
                        updated[latestSelectedTextIndex] = clampAnnotationCenter(
                            ann.copy(position = Offset(newX, newTop + ann.fontSize)),
                            sz
                        )
                        latestOnTextAnnotationsChange(updated)
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Touch || (drawMode == DrawMode.Text && !isResizingText && !isDraggingText && !isRotatingText)) {
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
                        isRotatingText = false
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
                            // only for the one currently being drawn. copy() preserves ALL fields
                            // (brush, effectStrength, fillRegion, …) so blur/mosaic strokes keep
                            // their brush on the second and subsequent strokes.
                            setCurrentPathProperty(currentPathProperty.copy())
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
                                    val style = if (property.fillRegion) Fill else Stroke(
                                        width = property.strokeWidth,
                                        cap = property.strokeCap,
                                        join = property.strokeJoin
                                    )
                                    val shaderBrush = shaderBrushFor(property)
                                    if (shaderBrush != null) {
                                        drawPath(brush = shaderBrush, path = path, style = style)
                                    } else {
                                        drawPath(color = property.color, path = path, style = style)
                                    }
                                } else {

                                    // Source
                                    drawPath(
                                        color = Color.Transparent,
                                        path = path,
                                        style = Stroke(
                                            width = property.strokeWidth,
                                            cap = property.strokeCap,
                                            join = property.strokeJoin
                                        ),
                                        blendMode = BlendMode.Clear
                                    )
                                }
                            }

                            if (painterMotionEvent != PainterMotionEvent.Idle) {

                                if (!currentPathProperty.eraseMode) {
                                    val shaderBrush = shaderBrushFor(currentPathProperty)
                                    val style = Stroke(
                                        width = currentPathProperty.strokeWidth,
                                        cap = currentPathProperty.strokeCap,
                                        join = currentPathProperty.strokeJoin
                                    )
                                    if (shaderBrush != null) {
                                        drawPath(brush = shaderBrush, path = currentPath, style = style)
                                    } else {
                                        drawPath(color = currentPathProperty.color, path = currentPath, style = style)
                                    }
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
                                val style = if (property.fillRegion) Fill else Stroke(
                                    width = property.strokeWidth,
                                    cap = property.strokeCap,
                                    join = property.strokeJoin
                                )
                                val shaderBrush = shaderBrushFor(property)
                                if (shaderBrush != null) {
                                    drawPath(brush = shaderBrush, path = path, style = style)
                                } else {
                                    drawPath(color = property.color, path = path, style = style)
                                }
                            } else {

                                // Source
                                drawPath(
                                    color = Color.Transparent,
                                    path = path,
                                    style = Stroke(
                                        width = property.strokeWidth,
                                        cap = property.strokeCap,
                                        join = property.strokeJoin
                                    ),
                                    blendMode = BlendMode.Clear
                                )
                            }
                        }

                        if (painterMotionEvent != PainterMotionEvent.Idle) {

                            if (!currentPathProperty.eraseMode) {
                                val shaderBrush = shaderBrushFor(currentPathProperty)
                                val style = Stroke(
                                    width = currentPathProperty.strokeWidth,
                                    cap = currentPathProperty.strokeCap,
                                    join = currentPathProperty.strokeJoin
                                )
                                if (shaderBrush != null) {
                                    drawPath(brush = shaderBrush, path = currentPath, style = style)
                                } else {
                                    drawPath(color = currentPathProperty.color, path = currentPath, style = style)
                                }
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
 * Keep a text annotation fully within the canvas by clamping its bounding-box edges.
 *
 * When the box fits, it is kept entirely on-screen (left/top edges in [0, canvas - box]),
 * so text can never drift out of bounds. When the box is larger than the canvas (e.g. very
 * large font or many interior blank lines), it is clamped to cover the canvas instead of
 * escaping it, keeping it grabbable. This is stricter than center-only clamping, which let
 * up to half the box — and its handles — slide off-screen.
 */
private fun clampAnnotationCenter(annotation: TextAnnotation, canvasSize: Size): TextAnnotation {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return annotation
    val bounds = measureTextBounds(annotation, canvasSize)
    val newLeft = if (bounds.width <= canvasSize.width) {
        bounds.left.coerceIn(0f, canvasSize.width - bounds.width)
    } else {
        bounds.left.coerceIn(canvasSize.width - bounds.width, 0f)
    }
    val newTop = if (bounds.height <= canvasSize.height) {
        bounds.top.coerceIn(0f, canvasSize.height - bounds.height)
    } else {
        bounds.top.coerceIn(canvasSize.height - bounds.height, 0f)
    }
    val dxNorm = (newLeft - bounds.left) / canvasSize.width
    val dyNorm = (newTop - bounds.top) / canvasSize.height
    if (dxNorm == 0f && dyNorm == 0f) return annotation
    return annotation.copy(
        position = Offset(
            annotation.position.x + dxNorm,
            annotation.position.y + dyNorm
        )
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