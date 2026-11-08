package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.ui.generated.resources.editor_crop_free
import com.dot.gallery.editor.ui.generated.resources.editor_crop_original
import com.dot.gallery.editor.ui.generated.resources.editor_crop_output
import com.dot.gallery.editor.ui.generated.resources.editor_crop_square
import com.dot.gallery.editor.ui.icons.Crop
import com.dot.gallery.editor.ui.icons.EditorSymbols
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.editor.core.geometry.CropFitting
import com.dot.gallery.editor.core.geometry.GuideAxis
import com.dot.gallery.editor.core.geometry.GuidedUpright
import com.dot.gallery.editor.core.geometry.PlaneRect
import com.dot.gallery.editor.core.geometry.Vec2
import com.dot.gallery.editor.core.model.PhotoGeometry
import com.dot.gallery.editor.core.model.PixelSize
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_crop_frame
import com.dot.gallery.editor.ui.generated.resources.editor_crop_frame_state
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val TAG_CROP_BOX = "editor.crop.box"
internal const val TAG_CROP_GRID = "editor.crop.grid"
internal const val TAG_CROP_GRID_DENSE = "editor.crop.grid.dense"
internal const val TAG_CROP_INFO = "editor.crop.info"
internal const val TAG_CROP_CANVAS = "editor.crop.canvas"
internal const val TAG_CROP_HANDLE_TL = "editor.crop.handle.tl"
internal const val TAG_CROP_HANDLE_TR = "editor.crop.handle.tr"
internal const val TAG_CROP_HANDLE_BL = "editor.crop.handle.bl"
internal const val TAG_CROP_HANDLE_BR = "editor.crop.handle.br"

/** Frame inset inside the media card, so handles stay grabbable. */
private val FRAME_MARGIN = 40.dp
/** The floating read-out pill's height and its gap above the frame. */
private val INFO_PILL_HEIGHT = 36.dp
private val INFO_PILL_GAP = 8.dp
/** Half of the handle/guide grab area — a few dp wider than the 48 dp
 *  marker so a grab on the marker's exact centre can never fall outside. */
private val GRAB = 26.dp
/** Smallest on-screen frame side. */
private val MIN_FRAME = 48.dp
private const val MAX_VIEW_SCALE = 8f
private const val VIEW_ANIMATION_MS = 220

/** The camera: `screen = scale * fit + (tx, ty)` (canvas-region px). */
private data class CropView(val scale: Float, val tx: Float, val ty: Float) {
    fun lerp(o: CropView, t: Float) =
        CropView(scale + (o.scale - scale) * t, tx + (o.tx - tx) * t, ty + (o.ty - ty) * t)
}

/** Which part of the frame a press grabbed. */
private enum class Grip(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TopLeft(true, true, false, false), Top(false, true, false, false),
    TopRight(false, true, true, false), Right(false, false, true, false),
    BottomRight(false, false, true, true), Bottom(false, false, false, true),
    BottomLeft(true, false, false, true), Left(true, false, false, false),
    Move(false, false, false, false);

    val isCorner get() = (left || right) && (top || bottom)
}

/**
 * The Crop & straighten canvas: a fixed, centred frame with the photo
 * moving beneath it.
 *
 * The canvas draws the crop-dropped display document — the whole warp
 * plane — at its resting fit. This overlay owns a camera
 * ([PhotoToolUiState.cropView], applied by [EditorCanvas] to the media
 * layer) that always frames the crop: centred and fitted inside the media
 * card. Straighten/keystone changes therefore move the photo while the
 * frame stays put. Handles resize the frame against a frozen camera; on
 * release the camera re-centres and zooms to the new crop (animated).
 * A drag inside the frame slides the photo beneath it.
 *
 * Every handle/move result is clamped into the warped photo, so the frame
 * can never expose a blank edge. A gesture is one local interaction
 * (`beginEdit`/`updateEdits`/`commitEdit`); disposal mid-gesture cancels
 * it. With guided upright on, drags draw/move guide segments instead and
 * each change re-solves the warp.
 *
 * Keyboard: arrows nudge the frame (Shift = 10×), `[`/`]` rotate 0.1°
 * (Shift = 1°), `R` turns left 90°, `X` swaps the aspect orientation,
 * Enter commits via [onCommit]; Escape is the workspace's cancel.
 */
@Composable
internal fun EditorCropWindow(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    upright: PixelSize?,
    modifier: Modifier = Modifier,
    onCommit: () -> Unit = {}
) {
    val ui = LocalPhotoToolUiState.current ?: remember { PhotoToolUiState() }
    val ops = rememberCropToolOps(state, actions, ui)
    val density = LocalDensity.current
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val latestCommit by rememberUpdatedState(onCommit)

    // The overlay's presence owns both the crop-dropped display document
    // and the canvas camera.
    DisposableEffect(latestActions) {
        latestActions.setCropPreviewMode(true)
        onDispose { latestActions.setCropPreviewMode(false) }
    }
    DisposableEffect(ui) {
        ui.cropView.active = true
        onDispose {
            ui.cropView.reset()
            ui.cropAdjusting = false
        }
    }

    val geometry = state.document?.geometry ?: PhotoGeometry()
    val plane = upright?.let { CropFitting.planeOf(it, geometry) }

    with(density) {
        val fitL = layout.left.toPx()
        val fitT = layout.top.toPx()
        val fitW = layout.width.toPx()
        val fieldL = layout.fieldLeft.toPx()
        val fieldT = layout.fieldTop.toPx()
        val fieldW = layout.fieldWidth.toPx()
        val fieldH = layout.fieldHeight.toPx()
        val margin = FRAME_MARGIN.toPx()
        // Large text grows the read-out pill, so its lane grows with it.
        val fontGrowth = ((density.fontScale - 1f).coerceAtLeast(0f) * 32f).dp
        val verticalMargin =
            max(margin, (INFO_PILL_HEIGHT + INFO_PILL_GAP + 8.dp + fontGrowth).toPx())
        val fs = if (plane != null && plane.width > 0) fitW / plane.width.toFloat() else 1f

        fun fitOf(x: Double, y: Double) = Offset(fitL + x.toFloat() * fs, fitT + y.toFloat() * fs)

        fun targetView(rect: PlaneRect): CropView {
            val availW = (fieldW - 2 * margin).coerceAtLeast(1f)
            // The top lane belongs to the read-out pill; reserving it on
            // both sides keeps the frame centred while leaving the pill a
            // clear gap above it on short windows.
            val availH = (fieldH - 2 * verticalMargin).coerceAtLeast(1f)
            val rw = (rect.width.toFloat() * fs).coerceAtLeast(1e-3f)
            val rh = (rect.height.toFloat() * fs).coerceAtLeast(1e-3f)
            val s = min(availW / rw, availH / rh).coerceIn(0.05f, MAX_VIEW_SCALE)
            val c = fitOf(rect.center.x, rect.center.y)
            return CropView(s, fieldL + fieldW / 2 - s * c.x, fieldT + fieldH / 2 - s * c.y)
        }

        // Gesture-local state (not recipe): the frozen camera during a
        // handle drag, the open draft flag, and a camera the user steered
        // by panning/zooming outside the frame.
        var gestureOpen by remember { mutableStateOf(false) }
        var frozen by remember { mutableStateOf<CropView?>(null) }
        var manual by remember { mutableStateOf<CropView?>(null) }
        var view by remember { mutableStateOf<CropView?>(null) }
        val anim = remember { Animatable(1f) }
        var animFrom by remember { mutableStateOf<CropView?>(null) }

        val rect = if (plane != null && upright != null) CropFitting.planeRect(upright, geometry) else null
        val target = rect?.let { targetView(it) }
        val latestTarget by rememberUpdatedState(target)

        // Camera policy: a handle drag freezes it, a user pan/zoom
        // (`manual`) overrides the fit, and otherwise it follows the crop.
        // External changes (aspect pick, undo, handle release) animate;
        // moves/straighten follow immediately so the frame never drifts.
        LaunchedEffect(target, frozen, manual) {
            val t = target ?: return@LaunchedEffect
            val fz = frozen ?: manual
            if (fz != null) {
                anim.snapTo(1f)
                animFrom = null
                view = fz
                return@LaunchedEffect
            }
            val current = view
            val followImmediately = current == null || ui.cropAdjusting || gestureOpen
            if (followImmediately) {
                anim.snapTo(1f)
                animFrom = null
                view = t
            } else if (current != t) {
                animFrom = current
                anim.snapTo(0f)
                view = t
                anim.animateTo(1f, tween(VIEW_ANIMATION_MS, easing = FastOutSlowInEasing))
                animFrom = null
            }
        }
        // A camera the user steered by hand stays put through crop edits:
        // the frame moves inside their viewport instead of the viewport
        // jumping back to the fit under them.
        val shown: CropView? = view?.let { v ->
            val from = animFrom
            if (from != null && anim.value < 1f) from.lerp(v, anim.value) else v
        }
        val latestShown by rememberUpdatedState(shown)
        // Publish the camera to the canvas media layer.
        SideEffect {
            if (shown != null) ui.cropView.set(shown.scale, shown.tx, shown.ty)
        }

        fun toScreen(v: CropView, x: Double, y: Double): Offset {
            val f = fitOf(x, y)
            return Offset(v.scale * f.x + v.tx, v.scale * f.y + v.ty)
        }

        val frame: Rect? = if (shown != null && rect != null) {
            val a = toScreen(shown, rect.left, rect.top)
            val b = toScreen(shown, rect.right, rect.bottom)
            Rect(a, b)
        } else null

        // Interaction end through another path (tool switch, Apply).
        LaunchedEffect(state.interactionActive) {
            if (!state.interactionActive) {
                gestureOpen = false
                frozen = null
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                if (gestureOpen) {
                    gestureOpen = false
                    latestActions.cancelEdit()
                }
            }
        }

        fun begin(): Boolean {
            if (gestureOpen) return true
            val s = latestState
            if (s.document == null || s.interactionActive || s.info?.uprightSize == null) return false
            return try {
                latestActions.beginEdit("Crop")
                gestureOpen = true
                true
            } catch (rejected: IllegalStateException) {
                false
            }
        }

        fun pushRect(r: PlaneRect) {
            val doc = latestState.document ?: return
            val up = latestState.info?.uprightSize ?: return
            val crop = CropFitting.planeOf(up, doc.geometry).cropOf(r, CropFitting.minSide(up))
            if (crop == doc.geometry.crop) return
            latestActions.updateEdits(doc.toEdits().copy(geometry = doc.geometry.copy(crop = crop)))
        }

        // Commit on the next dispatch, not inside the pointer handler:
        // removing the overlay mid-drag delivers a synthetic release before
        // disposal, and that must cancel (onDispose below), never commit.
        // A disposed composition cancels this scope first.
        val commitScope = rememberCoroutineScope()
        fun finish() {
            commitScope.launch {
                frozen = null
                if (gestureOpen) {
                    gestureOpen = false
                    latestActions.commitEdit()
                }
            }
        }

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        val onSurface = Color.White
        // A fixed dim: the photo outside the frame reads as context, in either theme.
        val scrim = Color.Black.copy(alpha = 0.6f)
        val guideColor = MaterialTheme.colorScheme.primary
        val frameDesc = stringResource(Res.string.editor_crop_frame)
        val frameState = rect?.let {
            stringResource(
                Res.string.editor_crop_frame_state,
                it.width.roundToInt().toString(), it.height.roundToInt().toString()
            )
        }.orEmpty()

        Box(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (event.isCtrlPressed || event.isMetaPressed) return@onKeyEvent false
                    val v = latestShown ?: return@onKeyEvent false
                    val big = if (event.isShiftPressed) 10.0 else 1.0
                    // One screen dp of frame movement, in plane pixels.
                    val unit = 1.dp.toPx() / (v.scale * fs).toDouble()
                    when (event.key) {
                        Key.DirectionLeft -> ops.nudge(-unit * big, 0.0)
                        Key.DirectionRight -> ops.nudge(unit * big, 0.0)
                        Key.DirectionUp -> ops.nudge(0.0, -unit * big)
                        Key.DirectionDown -> ops.nudge(0.0, unit * big)
                        Key.LeftBracket, Key.RightBracket -> {
                            val delta = (if (event.isShiftPressed) 1f else 0.1f) *
                                (if (event.key == Key.LeftBracket) -1f else 1f)
                            val next = ((ops.valueOf(CropTab.Straighten) + delta) * 10f).roundToInt() / 10f
                            ops.stepValue("Straighten", CropTab.Straighten, next)
                        }
                        Key.R -> ops.rotateLeft()
                        Key.X -> ops.swapOrientation()
                        Key.Enter, Key.NumPadEnter -> {
                            if (latestState.interactionActive) return@onKeyEvent false
                            latestCommit()
                        }
                        else -> return@onKeyEvent false
                    }
                    true
                }
                .focusable()
                .pointerInput(
                    plane?.width, plane?.height, fs, fitL, fitT,
                    fieldL, fieldT, fieldW, fieldH
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val v0 = latestShown ?: return@awaitEachGesture
                        val up = latestState.info?.uprightSize ?: return@awaitEachGesture
                        val doc = latestState.document ?: return@awaitEachGesture
                        val g0 = doc.geometry
                        val cp = CropFitting.planeOf(up, g0)
                        val warpPlane = cp.plane(g0.warp)
                        val grab = GRAB.toPx()
                        fun screenToPlane(p: Offset): Vec2 {
                            val fx = (p.x - v0.tx) / v0.scale
                            val fy = (p.y - v0.ty) / v0.scale
                            return Vec2(((fx - fitL) / fs).toDouble(), ((fy - fitT) / fs).toDouble())
                        }
                        fun planeToScreen(p: Vec2) = toScreen(v0, p.x, p.y)

                        if (ui.guidesMode) {
                            down.consume()
                            guideGesture(
                                down, ui, warpPlane, ::screenToPlane, ::planeToScreen, grab
                            ) { ops.solveGuides() }
                            return@awaitEachGesture
                        }

                        val r0 = CropFitting.planeRect(up, g0)
                        val f0 = Rect(planeToScreen(Vec2(r0.left, r0.top)), planeToScreen(Vec2(r0.right, r0.bottom)))

                        // The camera a pan/pinch steers. `latestShown`
                        // trails the state write by one composition, so the
                        // gesture keeps its own copy between events.
                        var cam = v0
                        // The frame's centre in fitted canvas coordinates:
                        // the pan clamp holds it inside the field, so the
                        // crop can never be panned out of reach.
                        val frameCenter = Offset(
                            (f0.center.x - v0.tx) / v0.scale,
                            (f0.center.y - v0.ty) / v0.scale
                        )
                        val camPad = 12.dp.toPx()
                        fun applyCamera(next: CropView) {
                            cam = next
                            manual = next
                            view = next
                        }
                        fun transform(event: PointerEvent) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid == Offset.Unspecified) return
                            // Zoom is relative to the fit, so the control
                            // always spans "see the whole crop" to "inspect
                            // pixels", whatever the crop's size.
                            val fit = latestTarget ?: cam
                            val s = (cam.scale * event.calculateZoom())
                                .coerceIn(fit.scale * 0.6f, fit.scale * 6f)
                            // Pinching all the way out is the way back to
                            // the resting crop view: at the zoom-out floor
                            // the camera snaps to the fit instead of
                            // parking a slightly-smaller frame.
                            if (s <= fit.scale * 0.61f) {
                                cam = fit
                                manual = null
                                view = fit
                                return
                            }
                            val k = s / cam.scale
                            val pan = event.calculatePan()
                            var tx = centroid.x + pan.x - (centroid.x - cam.tx) * k
                            var ty = centroid.y + pan.y - (centroid.y - cam.ty) * k
                            val cx = tx + s * frameCenter.x
                            val cy = ty + s * frameCenter.y
                            val lx = fieldL + camPad
                            val rx = (fieldL + fieldW - camPad).coerceAtLeast(lx)
                            val ty0 = fieldT + camPad
                            val by = (fieldT + fieldH - camPad).coerceAtLeast(ty0)
                            tx += cx.coerceIn(lx, rx) - cx
                            ty += cy.coerceIn(ty0, by) - cy
                            applyCamera(CropView(s, tx, ty))
                        }

                        // Outside the frame the canvas is a viewer, not an
                        // edit surface: drag pans and pinch zooms the photo
                        // beneath the frame so the area around the crop can
                        // be inspected. The camera is view state only.
                        val grip = gripAt(down.position, f0, grab)
                        if (grip == null) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                transform(event)
                                event.changes.forEach { it.consume() }
                            }
                            return@awaitEachGesture
                        }

                        down.consume()
                        val aspect = ui.cropAspect(g0, up)
                        val minPlane = max(CropFitting.minSide(up), MIN_FRAME.toPx() / (v0.scale * fs).toDouble())
                        var last = if (CropFitting.isValid(up, g0)) r0
                        else CropFitting.planeRect(up, CropFitting.constrained(up, g0, aspect))
                        var total = Offset.Zero
                        var started = false
                        var camera = false
                        if (grip != Grip.Move) frozen = v0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val pressed = event.changes.count { it.pressed }
                            if (camera) {
                                if (pressed == 0) break
                                transform(event)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (pressed > 1) {
                                if (started) continue
                                // A second finger that lands before the
                                // crop moved is a pinch: hand the gesture
                                // to the camera instead of resizing.
                                frozen = null
                                camera = true
                                transform(event)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val delta = change.positionChange()
                            if (delta == Offset.Zero) continue
                            total += delta
                            if (!started) {
                                if (grip == Grip.Move && total.getDistance() < viewConfiguration.touchSlop) continue
                                if (!begin()) break
                                started = true
                            }
                            change.consume()
                            val scale = (v0.scale * fs).toDouble()
                            val dx = total.x / scale
                            val dy = total.y / scale
                            val proposed = if (grip == Grip.Move) {
                                // The drag must travel with the finger.
                                // With the auto-fit camera the frame stays
                                // put and the photo moves, so the crop
                                // moves against the drag; with a camera the
                                // user panned (fixed on screen) the frame
                                // itself must follow the drag, or it reads
                                // reversed.
                                val sign = if (manual != null) 1.0 else -1.0
                                r0.translate(sign * dx, sign * dy)
                            } else {
                                resized(r0, grip, dx, dy, aspect, minPlane)
                            }
                            val next = CropFitting.clampedRect(up, g0, last, proposed)
                            last = next
                            pushRect(next)
                        }
                        if (!camera) {
                            if (started) finish() else frozen = null
                        }
                    }
                }
                .semantics {
                    contentDescription = frameDesc
                    stateDescription = frameState
                }
                .testTag(TAG_CROP_CANVAS)
        ) {
            if (frame != null && shown != null) {
                val dense = ui.cropAdjusting
                // The scrim belongs to the photo, not the whole field: it
                // dims the image around the frame wherever the photo is,
                // including behind the floating chrome, and never tints the
                // bars or the letterbox. It must not be clipped to the
                // frame's field, or a zoomed photo shows a hard seam.
                val imageRect = plane?.let { p ->
                    val a = toScreen(shown, 0.0, 0.0)
                    val b = toScreen(shown, p.width.toDouble(), p.height.toDouble())
                    Rect(
                        min(a.x, b.x), min(a.y, b.y),
                        max(a.x, b.x), max(a.y, b.y)
                    )
                }
                // A 2 dp bleed keeps the frame's edge-centred chrome whole.
                val bleed = 2.dp.toPx()
                val frameClipL = max(fieldL, (imageRect?.left ?: fieldL) - bleed)
                val frameClipT = max(fieldT, (imageRect?.top ?: fieldT) - bleed)
                val frameClipR = min(
                    fieldL + fieldW, (imageRect?.right ?: (fieldL + fieldW)) + bleed
                )
                val frameClipB = min(
                    fieldT + fieldH, (imageRect?.bottom ?: (fieldT + fieldH)) + bleed
                )
                Canvas(Modifier.fillMaxSize()) {
                    if (imageRect != null) {
                        clipRect(
                            imageRect.left, imageRect.top,
                            imageRect.right, imageRect.bottom
                        ) {
                            drawScrim(frame, scrim)
                        }
                    } else {
                        clipRect(fieldL, fieldT, fieldL + fieldW, fieldT + fieldH) {
                            drawScrim(frame, scrim)
                        }
                    }
                    clipRect(frameClipL, frameClipT, frameClipR, frameClipB) {
                        drawGrid(frame, onSurface, dense)
                        drawFrame(frame, onSurface)
                        if (ui.guidesMode || ui.guides.isNotEmpty()) {
                            val wp = plane?.plane(geometry.warp)
                            if (wp != null) for (g in ui.guides) {
                                val a = wp.toPlane(g.a.x, g.a.y) ?: continue
                                val b = wp.toPlane(g.b.x, g.b.y) ?: continue
                                val sa = toScreen(shown, a.x, a.y)
                                val sb = toScreen(shown, b.x, b.y)
                                drawLine(guideColor, sa, sb, 2.dp.toPx())
                                for (p in listOf(sa, sb)) {
                                    drawCircle(guideColor, 7.dp.toPx(), p)
                                    drawCircle(Color.White, 7.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                                }
                            }
                        }
                    }
                }
                // The frame's read-out rides above it: the applied aspect
                // and the exact output size the mapping will write.
                val output = state.document?.let {
                    PhotoMapping(it.source.size, 1, it.geometry).outputSize
                }
                if (output != null) {
                    val preset = ui.cropPreset
                    val presetLabel = when (preset) {
                        CropAspectPreset.Free ->
                            stringResource(Res.string.editor_crop_free)
                        CropAspectPreset.Original ->
                            stringResource(Res.string.editor_crop_original)
                        CropAspectPreset.Square ->
                            stringResource(Res.string.editor_crop_square)
                        else -> preset.ratioLabel(ui.cropPortrait)
                    }
                    val pillHeight = INFO_PILL_HEIGHT
                    val gap = INFO_PILL_GAP
                    var measuredPillPx by remember { mutableIntStateOf(0) }
                    val pillY = with(density) {
                        val measured = if (measuredPillPx > 0) {
                            measuredPillPx.toFloat()
                        } else {
                            pillHeight.toPx()
                        }
                        (frame.top - measured - gap.toPx())
                            .coerceAtLeast(fieldT + gap.toPx())
                    }
                    EditorFloatingPanel(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, pillY.roundToInt()) }
                            .onSizeChanged { measuredPillPx = it.height }
                            .testTag(TAG_CROP_INFO)
                    ) {
                        Row(
                            // Sized to the text, not a width cap: a
                            // capped row wrapped "px" onto a second line.
                            // One line is the contract; at very large text
                            // the window constraint ellipsizes it.
                            modifier = Modifier.heightIn(min = pillHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = EditorSymbols.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    Res.string.editor_crop_output,
                                    presetLabel,
                                    output.width,
                                    output.height
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    lineHeight = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .testTag(TAG_CROP_OUTPUT)
                            )
                        }
                    }
                }
                // Semantics-only markers tracking the frame for tests and
                // accessibility tooling; the drawn frame owns hit testing.
                Box(
                    Modifier
                        .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                        .size(DpSize(frame.width.toDp(), frame.height.toDp()))
                        .testTag(TAG_CROP_BOX)
                ) {
                    Spacer(Modifier.fillMaxSize().testTag(if (dense) TAG_CROP_GRID_DENSE else TAG_CROP_GRID))
                    listOf(
                        TAG_CROP_HANDLE_TL to Alignment.TopStart,
                        TAG_CROP_HANDLE_TR to Alignment.TopEnd,
                        TAG_CROP_HANDLE_BL to Alignment.BottomStart,
                        TAG_CROP_HANDLE_BR to Alignment.BottomEnd
                    ).forEach { (tag, align) ->
                        Spacer(Modifier.align(align).size(48.dp).testTag(tag))
                    }
                }
            }
        }
    }
}

/** The grip under [p]: 48 dp corner/edge zones, else inside = move. */
private fun gripAt(p: Offset, f: Rect, grab: Float): Grip? {
    val nearL = abs(p.x - f.left) <= grab
    val nearR = abs(p.x - f.right) <= grab
    val nearT = abs(p.y - f.top) <= grab
    val nearB = abs(p.y - f.bottom) <= grab
    val inX = p.x >= f.left - grab && p.x <= f.right + grab
    val inY = p.y >= f.top - grab && p.y <= f.bottom + grab
    if (!inX || !inY) return null
    // Small frames: prefer the nearer side so both edges stay reachable.
    val left = nearL && (!nearR || abs(p.x - f.left) <= abs(p.x - f.right))
    val right = nearR && !left
    val top = nearT && (!nearB || abs(p.y - f.top) <= abs(p.y - f.bottom))
    val bottom = nearB && !top
    return when {
        left && top -> Grip.TopLeft
        right && top -> Grip.TopRight
        left && bottom -> Grip.BottomLeft
        right && bottom -> Grip.BottomRight
        left -> Grip.Left
        right -> Grip.Right
        top -> Grip.Top
        bottom -> Grip.Bottom
        f.contains(p) -> Grip.Move
        else -> null
    }
}

/**
 * [r0] with the grabbed edges moved by (dx, dy) plane pixels, never
 * inverted or smaller than [minSide]. A locked [aspect] keeps the
 * opposite corner (corner grips) or the opposite edge's centre line
 * (edge grips) fixed.
 */
private fun resized(r0: PlaneRect, grip: Grip, dx: Double, dy: Double, aspect: Double?, minSide: Double): PlaneRect {
    var l = r0.left
    var t = r0.top
    var r = r0.right
    var b = r0.bottom
    if (grip.left) l = min(r0.left + dx, r0.right - minSide)
    if (grip.right) r = max(r0.right + dx, r0.left + minSide)
    if (grip.top) t = min(r0.top + dy, r0.bottom - minSide)
    if (grip.bottom) b = max(r0.bottom + dy, r0.top + minSide)
    if (aspect == null) return PlaneRect(l, t, r, b)
    val minH = max(minSide, minSide / aspect)
    return when {
        grip.isCorner -> {
            val w = r - l
            val h = b - t
            val height = max(max(h, w / aspect), minH)
            val width = height * aspect
            val ax = if (grip.left) r0.right else r0.left
            val ay = if (grip.top) r0.bottom else r0.top
            PlaneRect(
                if (grip.left) ax - width else ax, if (grip.top) ay - height else ay,
                if (grip.left) ax else ax + width, if (grip.top) ay else ay + height
            )
        }
        grip.left || grip.right -> {
            val height = max((r - l) / aspect, minH)
            val width = height * aspect
            val cy = r0.center.y
            val ax = if (grip.left) r0.right else r0.left
            PlaneRect(
                if (grip.left) ax - width else ax, cy - height / 2,
                if (grip.left) ax else ax + width, cy + height / 2
            )
        }
        else -> {
            val height = max(b - t, minH)
            val width = height * aspect
            val cx = r0.center.x
            val ay = if (grip.top) r0.bottom else r0.top
            PlaneRect(
                cx - width / 2, if (grip.top) ay - height else ay,
                cx + width / 2, if (grip.top) ay else ay + height
            )
        }
    }
}

/**
 * Guided-upright drawing: a press near an endpoint moves it, anywhere else
 * draws a new guide whose axis follows the drag's dominant direction (up
 * to two per axis). Guides store rigid-frame points so they stay on the
 * scene; [onChanged] re-solves after the gesture.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.guideGesture(
    down: PointerInputChange,
    ui: PhotoToolUiState,
    plane: com.dot.gallery.editor.core.geometry.WarpPlane,
    screenToPlane: (Offset) -> Vec2,
    planeToScreen: (Vec2) -> Offset,
    grab: Float,
    onChanged: () -> Unit
) {
    // Existing endpoint under the finger?
    var editIndex = -1
    var editEnd = 0
    var best = Float.MAX_VALUE
    ui.guides.forEachIndexed { i, g ->
        listOf(g.a, g.b).forEachIndexed { end, rigid ->
            val p = plane.toPlane(rigid.x, rigid.y) ?: return@forEachIndexed
            val s = planeToScreen(p)
            val d = hypot(s.x - down.position.x, s.y - down.position.y)
            if (d <= grab && d < best) {
                best = d
                editIndex = i
                editEnd = end
            }
        }
    }
    val startPlane = screenToPlane(down.position)
    val startRigid = plane.toRigid(startPlane.x, startPlane.y) ?: return
    var current = down.position
    var created = -1
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
            change.consume()
            break
        }
        change.consume()
        current = change.position
        val p = screenToPlane(current)
        val rigid = plane.toRigid(p.x, p.y) ?: continue
        if (editIndex >= 0) {
            val g = ui.guides[editIndex]
            ui.guides[editIndex] = if (editEnd == 0) g.copy(a = rigid) else g.copy(b = rigid)
        } else {
            val d = current - down.position
            if (created < 0) {
                if (d.getDistance() < 24f) continue
                val axis = if (abs(d.y) >= abs(d.x)) GuideAxis.Vertical else GuideAxis.Horizontal
                if (ui.guides.count { it.axis == axis } >= GuidedUpright.MAX_GUIDES_PER_AXIS) {
                    // Replace the oldest of that axis.
                    val oldest = ui.guides.indexOfFirst { it.axis == axis }
                    ui.guides.removeAt(oldest)
                }
                ui.guides.add(CropGuide(startRigid, rigid, axis))
                created = ui.guides.lastIndex
            } else {
                ui.guides[created] = ui.guides[created].copy(b = rigid)
            }
        }
    }
    if (editIndex >= 0 || created >= 0) onChanged()
}

private fun DrawScope.drawScrim(f: Rect, color: Color) {
    val w = size.width
    val h = size.height
    drawRect(color, Offset(0f, 0f), Size(w, f.top.coerceAtLeast(0f)))
    drawRect(color, Offset(0f, f.bottom), Size(w, (h - f.bottom).coerceAtLeast(0f)))
    drawRect(color, Offset(0f, f.top), Size(f.left.coerceAtLeast(0f), f.height))
    drawRect(color, Offset(f.right, f.top), Size((w - f.right).coerceAtLeast(0f), f.height))
}

/** Thirds while idle; a dense 6×6 grid while a ruler drags. */
private fun DrawScope.drawGrid(f: Rect, color: Color, dense: Boolean) {
    val n = if (dense) 6 else 3
    val tint = color.copy(alpha = if (dense) 0.28f else 0.4f)
    val lw = 1.dp.toPx()
    for (i in 1 until n) {
        val x = f.left + f.width * i / n
        val y = f.top + f.height * i / n
        drawLine(tint, Offset(x, f.top), Offset(x, f.bottom), lw)
        drawLine(tint, Offset(f.left, y), Offset(f.right, y), lw)
    }
}

/** Hairline frame, 3 dp corner brackets and edge-centre bars. */
private fun DrawScope.drawFrame(f: Rect, color: Color) {
    // A hairline bounds the frame; the grip chrome rides on top of it.
    drawRect(color.copy(alpha = 0.5f), f.topLeft, f.size, style = Stroke(1.dp.toPx()))
    val t = 3.dp.toPx()
    val len = min(22.dp.toPx(), min(f.width, f.height) / 3f)
    val o = t / 2
    // Corner brackets: the heavy L's of the reference frame.
    for ((cx, sx) in listOf(f.left to 1f, f.right to -1f)) {
        for ((cy, sy) in listOf(f.top to 1f, f.bottom to -1f)) {
            drawLine(
                color,
                Offset(cx - sx * o, cy - sy * o),
                Offset(cx + sx * len, cy - sy * o),
                t
            )
            drawLine(
                color,
                Offset(cx - sx * o, cy - sy * o),
                Offset(cx - sx * o, cy + sy * len),
                t
            )
        }
    }
    // Edge grips: rounded pills centred on each edge, like the reference.
    val pillW = 7.dp.toPx()
    val pillLen = min(20.dp.toPx(), min(f.width, f.height) / 5f)
    val radius = CornerRadius(pillW / 2)
    listOf(
        Offset(f.center.x, f.top) to Size(pillLen, pillW),
        Offset(f.center.x, f.bottom) to Size(pillLen, pillW),
        Offset(f.left, f.center.y) to Size(pillW, pillLen),
        Offset(f.right, f.center.y) to Size(pillW, pillLen)
    ).forEach { (center, size) ->
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f),
            size = size,
            cornerRadius = radius
        )
    }
    // Centre crosshair: the visual pivot for the fixed frame.
    val cross = 10.dp.toPx()
    val hair = 1.dp.toPx()
    drawLine(
        color.copy(alpha = 0.9f),
        Offset(f.center.x - cross, f.center.y),
        Offset(f.center.x + cross, f.center.y),
        hair
    )
    drawLine(
        color.copy(alpha = 0.9f),
        Offset(f.center.x, f.center.y - cross),
        Offset(f.center.x, f.center.y + cross),
        hair
    )
}
