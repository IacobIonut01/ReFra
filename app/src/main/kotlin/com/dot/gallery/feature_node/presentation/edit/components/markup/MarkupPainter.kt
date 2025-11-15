package com.dot.gallery.feature_node.presentation.edit.components.markup

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.feature_node.domain.model.editor.DrawMode
import com.dot.gallery.feature_node.domain.model.editor.PainterMotionEvent
import com.dot.gallery.feature_node.domain.model.editor.PathProperties
import com.dot.gallery.feature_node.presentation.edit.utils.dragMotionEvent
import com.dot.gallery.feature_node.presentation.util.goBack
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
) {
    var graphicsLayer = rememberGraphicsLayer()

    /**
     * Canvas touch state. [PainterMotionEvent.Idle] by default, [PainterMotionEvent.Down] at first contact,
     * [PainterMotionEvent.Move] while dragging and [PainterMotionEvent.Up] when first pointer is up
     */
    var painterMotionEvent by remember { mutableStateOf(PainterMotionEvent.Idle) }

    val scope = rememberCoroutineScope { Dispatchers.IO }
    val context = LocalContext.current
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
                    context.goBack()
                }
            }
        }
    }

    GlideImage(
        model = bitmap,
        contentDescription = null,
        modifier = Modifier
            .wrapContentSize()
            .clipToBounds()
            .dragMotionEvent(
                onDragStart = { pointerInputChange ->
                    painterMotionEvent = PainterMotionEvent.Down
                    setCurrentPosition(pointerInputChange.position)
                    if (pointerInputChange.pressed != pointerInputChange.previousPressed) pointerInputChange.consume()

                },
                onDrag = { pointerInputChange ->
                    painterMotionEvent = PainterMotionEvent.Move
                    setCurrentPosition(pointerInputChange.position)

                    if (drawMode == DrawMode.Touch) {
                        val change = pointerInputChange.positionChange()
                        paths.forEach { entry ->
                            val path: Path = entry.first
                            path.translate(change)
                        }
                        currentPath.translate(change)
                    }
                    if (pointerInputChange.positionChange() != Offset.Zero) pointerInputChange.consume()

                },
                onDragEnd = { pointerInputChange ->
                    painterMotionEvent = PainterMotionEvent.Up
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