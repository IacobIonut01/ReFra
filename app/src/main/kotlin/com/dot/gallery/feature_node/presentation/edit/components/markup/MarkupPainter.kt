package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.PainterMotionEvent
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.utils.dragMotionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MarkupPainter(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
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

    val scope = rememberCoroutineScope { Dispatchers.IO }
    val shouldSaveDrawing by remember(paths, currentImage) {
        derivedStateOf { paths.isNotEmpty() && currentImage != null }
    }

    val mutex = remember { Mutex() }

    BackHandler(shouldSaveDrawing) {
        scope.launch {
            delay(100)
            mutex.withLock {
                val image = graphicsLayer.toImageBitmap().asAndroidBitmap()
                applyDrawing(image) {
                    onNavigateBack()
                }
            }
        }
    }

    GlideImage(
        model = bitmap,
        contentDescription = null,
        modifier = Modifier
            .wrapContentSize()
            // Pinch-to-zoom and two-finger pan gesture
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            if (!isZooming) {
                                // Transitioning to zoom — discard any in-progress drawing
                                isZooming = true
                                currentPath.reset()
                                painterMotionEvent = PainterMotionEvent.Idle
                                setCurrentPosition(Offset.Unspecified)
                            }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            canvasScale = (canvasScale * zoom).coerceIn(1f, 5f)
                            canvasOffset = if (canvasScale == 1f) {
                                Offset.Zero
                            } else {
                                canvasOffset + pan
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
                    if (drawMode == DrawMode.Touch || isZooming) {
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
                        // Suppress drawing while a pinch gesture is active
                        pointerInputChange.consume()
                    } else if (drawMode == DrawMode.Touch) {
                        // Pan the canvas with single finger in Touch mode
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
                        // Discard the path that was started before the pinch
                        currentPath.reset()
                        setCurrentPath(Path())
                        painterMotionEvent = PainterMotionEvent.Idle
                        setCurrentPosition(Offset.Unspecified)
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
                    }
                }
            }
            .then(modifier)
    )
}

private fun DrawScope.drawText(text: String, x: Float, y: Float, paint: Paint) {

    val lines = text.split("\n")
    // There is not a built-in function as of 1.0.0
    // for drawing text so we get the native canvas to draw text and use a Paint object
    val nativeCanvas = drawContext.canvas.nativeCanvas

    lines.indices.withIndex().forEach { (posY, i) ->
        nativeCanvas.drawText(lines[i], x, posY * 40 + y, paint)
    }
}