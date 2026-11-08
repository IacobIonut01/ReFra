package com.dot.gallery.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.LensBlurMode
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_blur_centre
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

internal const val TAG_LENS_BLUR_OVERLAY = "editor.lensBlur.overlay"
internal const val TAG_LENS_BLUR_CENTER = "editor.lensBlur.centre"
internal const val TAG_LENS_BLUR_RINGS = "editor.lensBlur.rings"
internal const val TAG_LENS_BLUR_LINE = "editor.lensBlur.line"

/**
 * The Lens blur canvas overlay, drawn exactly on the
 * [EditorImageLayout] the canvas reports: Radial paints the focus
 * boundary ring and the transition's outer ring — true circles,
 * because the recipe's aspect correction collapses to a radius in
 * output pixels — and Linear paints the rotated centre line the band
 * tilts around. The 12 dp centre marker carries an invisible 48 dp
 * hit region: one drag rewrites `centerX`/`centerY` through
 * `updateEdits` inside a single interaction draft — one undo step,
 * commit on release, cancel on drop or disposal.
 */
@Composable
internal fun LensBlurOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    var gestureOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val blur = edits.spatial.lensBlur
    val centreLabel = stringResource(Res.string.editor_blur_centre)
    val toolTitle = photoToolTitle(EditorToolIds.PhotoLensBlur)

    fun canStart(): Boolean = latestState.document != null &&
        !latestState.interactionActive && !gestureOpen

    // If the interaction ended through another path (Apply, a tool
    // switch), drop the flag; a disposed mid-gesture overlay cancels
    // the draft it opened.
    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) gestureOpen = false
    }
    DisposableEffect(Unit) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                latestActions.cancelEdit()
            }
        }
    }

    val boxW = layout.width
    val boxH = layout.height
    Box(
        modifier = modifier
            .offset(x = layout.left, y = layout.top)
            .size(width = boxW, height = boxH)
            .testTag(TAG_LENS_BLUR_OVERLAY)
    ) {
        when (blur.mode) {
            LensBlurMode.Radial -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val cx = blur.centerX * size.width
                        val cy = blur.centerY * size.height
                        // The mask distance is measured in output
                        // pixels after aspect correction — in layout
                        // space both rings are circles with radius
                        // d * layout height.
                        val boundary = 0.18f * size.height
                        val rampEnd = (0.18f + 0.04f +
                            blur.transition * 0.45f) * size.height
                        val lw = 1.5.dp.toPx()
                        drawCircle(
                            color = onSurface,
                            radius = boundary,
                            center = Offset(cx, cy),
                            style = Stroke(lw)
                        )
                        drawCircle(
                            color = onSurface.copy(alpha = 0.5f),
                            radius = rampEnd,
                            center = Offset(cx, cy),
                            style = Stroke(lw)
                        )
                    }
                    .testTag(TAG_LENS_BLUR_RINGS)
            )
            LensBlurMode.Linear -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val cx = blur.centerX * size.width
                        val cy = blur.centerY * size.height
                        val radians = Math.toRadians(
                            blur.angleDegrees.toDouble()
                        )
                        // The band runs perpendicular to the
                        // measurement normal (cos a, sin a).
                        val tx = -sin(radians).toFloat()
                        val ty = cos(radians).toFloat()
                        val reach = size.width + size.height
                        drawLine(
                            color = onSurface,
                            start = Offset(
                                cx - tx * reach, cy - ty * reach
                            ),
                            end = Offset(
                                cx + tx * reach, cy + ty * reach
                            ),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                    .testTag(TAG_LENS_BLUR_LINE)
            )
        }
        // The centre marker — a 48 dp invisible target centred on the
        // focus point with the 12 dp dot painted at its middle.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = boxW * blur.centerX - 24.dp,
                    y = boxH * blur.centerY - 24.dp
                )
                .size(48.dp)
                .semantics { contentDescription = centreLabel }
                .pointerInput(blur.mode) {
                    detectDragGestures(
                        onDragStart = {
                            if (canStart()) {
                                try {
                                    latestActions.beginToolEdit(toolTitle)
                                    latestActions.beginEdit(
                                        "Move blur centre"
                                    )
                                    gestureOpen = true
                                } catch (
                                    rejected: IllegalStateException
                                ) {
                                    // `canStart` read a published
                                    // snapshot — the session's
                                    // nested-draft check is the live
                                    // verdict; this drag was never ours.
                                }
                            }
                        },
                        onDragEnd = {
                            if (gestureOpen) {
                                gestureOpen = false
                                latestActions.commitEdit()
                            }
                        },
                        onDragCancel = {
                            if (gestureOpen) {
                                gestureOpen = false
                                latestActions.cancelEdit()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (!gestureOpen) return@detectDragGestures
                            change.consume()
                            val current = latestState.document
                                ?: return@detectDragGestures
                            val boxWpx = with(density) { boxW.toPx() }
                            val boxHpx = with(density) { boxH.toPx() }
                            if (boxWpx <= 0f || boxHpx <= 0f) {
                                return@detectDragGestures
                            }
                            val currentEdits = current.toEdits()
                            val currentBlur = currentEdits.spatial.lensBlur
                            latestActions.updateEdits(
                                currentEdits.copy(
                                    spatial = currentEdits.spatial.copy(
                                        lensBlur = currentBlur.copy(
                                            centerX = (currentBlur.centerX +
                                                dragAmount.x / boxWpx)
                                                .coerceIn(0f, 1f),
                                            centerY = (currentBlur.centerY +
                                                dragAmount.y / boxHpx)
                                                .coerceIn(0f, 1f)
                                        )
                                    )
                                )
                            )
                        }
                    )
                }
                .testTag(TAG_LENS_BLUR_CENTER)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(12.dp)
                    .background(primary, CircleShape)
                    .border(2.dp, surface, CircleShape)
            )
        }
    }
}
