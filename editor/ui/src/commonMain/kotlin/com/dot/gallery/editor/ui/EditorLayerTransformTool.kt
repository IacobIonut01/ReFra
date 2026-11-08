package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoLayerTransform
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_layer_edge_handle
import com.dot.gallery.editor.ui.generated.resources.editor_transform_align
import com.dot.gallery.editor.ui.generated.resources.editor_transform_lock_proportions
import com.dot.gallery.editor.ui.generated.resources.editor_transform_reset
import com.dot.gallery.editor.ui.generated.resources.editor_transform_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_transform_scale
import com.dot.gallery.editor.ui.generated.resources.editor_transform_scale_rotation
import com.dot.gallery.editor.ui.generated.resources.editor_transform_x
import com.dot.gallery.editor.ui.generated.resources.editor_transform_xy
import com.dot.gallery.editor.ui.generated.resources.editor_transform_y
import com.dot.gallery.editor.ui.generated.resources.editor_flip
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_TRANSFORM = "editor.category.transform"
internal const val TAG_LAYER_TRANSFORM_X = "editor.layer.transform.x"
internal const val TAG_LAYER_TRANSFORM_Y = "editor.layer.transform.y"
internal const val TAG_LAYER_TRANSFORM_SCALE = "editor.layer.transform.scale"
internal const val TAG_LAYER_TRANSFORM_ROTATION = "editor.layer.transform.rotation"
internal const val TAG_LAYER_LOCK_PROPORTIONS = "editor.layer.lockProportions"
internal const val TAG_LAYER_ALIGN = "editor.layer.align"
internal const val TAG_LAYER_FLIP = "editor.layer.flip"
internal const val TAG_LAYER_TRANSFORM_RESET = "editor.layer.transform.reset"
internal const val TAG_LAYER_MOVE = "editor.layer.move"
internal const val TAG_LAYER_GUIDE = "editor.layer.guide"
internal const val TAG_LAYER_HANDLE_TL = "editor.layer.handle.tl"
internal const val TAG_LAYER_HANDLE_TR = "editor.layer.handle.tr"
internal const val TAG_LAYER_HANDLE_BL = "editor.layer.handle.bl"
internal const val TAG_LAYER_HANDLE_BR = "editor.layer.handle.br"
internal const val TAG_LAYER_HANDLE_TOP = "editor.layer.handle.top"
internal const val TAG_LAYER_HANDLE_RIGHT = "editor.layer.handle.right"
internal const val TAG_LAYER_HANDLE_BOTTOM = "editor.layer.handle.bottom"
internal const val TAG_LAYER_HANDLE_LEFT = "editor.layer.handle.left"

/**
 * The Transform inspector per the approved `p-transform` frame: two
 * 48 dp pair rows — `X · Y` in full base-output pixels and
 * `Scale · Rotation` (the model's uniform .01..10 percent, degrees) —
 * each parameter keeping its own semantics while the field opens a
 * pair dialog that edits both atomically, then one FlowRow with the
 * Lock proportions toggle (pinned On: the model only carries a uniform
 * scale, so there is no free axis to unlock) and the Align centre /
 * Flip actions.
 *
 * Every row writes `PhotoImageLayer.transform` of the selected layer in
 * place, inside the open tool transaction; a locked layer disables the
 * whole surface.
 */
@Composable
internal fun LayerTransformToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val context = LocalPhotoLayerToolContext.current
    val layer = resolveSelectedLayer(state, context?.selectedLayerId)
    val document = state.document
    val outputSize = state.baseOutputSize()
    val transform = layer?.transform ?: PhotoLayerTransform()
    val layerId = layer?.id
    val enabled = layerId != null && layer != null && !layer.locked &&
        document != null && outputSize != null && !state.opening
    val canEdit = enabled && !state.interactionActive
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val latestLayerId by rememberUpdatedState(layerId)
    val sourceKey = state.info?.reference

    fun writeTransform(
        label: String,
        update: (PhotoLayerTransform) -> PhotoLayerTransform
    ) {
        val id = latestLayerId ?: return
        latestActions.applyLayerEdit(latestState, id, label) {
            it.copy(transform = update(it.transform))
        }
    }

    // The More-menu Reset drops only this layer's transform.
    val resetLabel = stringResource(Res.string.editor_transform_reset)
    PublishLayerToolReset(
        label = resetLabel,
        tag = TAG_LAYER_TRANSFORM_RESET,
        enabled = canEdit && transform != PhotoLayerTransform(),
        onReset = { writeTransform(resetLabel) { PhotoLayerTransform() } }
    )

    val owner = remember { NumericEntryOwner() }
    NumericEntryOwnerEffects(owner, state, actions)
    var dialogPair by remember { mutableStateOf<TransformPair?>(null) }

    val outW = outputSize?.width?.toFloat() ?: 0f
    val outH = outputSize?.height?.toFloat() ?: 0f
    val xRange = -outW..outW
    val yRange = -outH..outH
    val scaleRange = 1f..1000f
    val rotationRange = -180f..180f
    val xyLabel = stringResource(Res.string.editor_transform_xy)
    val srLabel = stringResource(Res.string.editor_transform_scale_rotation)
    val xLabel = stringResource(Res.string.editor_transform_x)
    val yLabel = stringResource(Res.string.editor_transform_y)
    val scaleLabel = stringResource(Res.string.editor_transform_scale)
    val rotationLabel = stringResource(Res.string.editor_transform_rotation)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The two p-transform pair rows. The row carries the first
        // parameter's progress semantics, the field the second's — a
        // SetProgress on either tag lands one apply step; tapping the
        // field opens the pair dialog that confirms both atomically.
        LayerToolPairRow(
            label = xyLabel,
            display = "${transform.translationX.roundToInt()} px · " +
                "${transform.translationY.roundToInt()} px",
            fieldWidth = 124.dp,
            primaryValue = transform.translationX,
            primaryRange = xRange,
            secondaryValue = transform.translationY,
            secondaryRange = yRange,
            enabled = enabled,
            interactionActive = state.interactionActive,
            rowTag = TAG_LAYER_TRANSFORM_X,
            fieldTag = TAG_LAYER_TRANSFORM_Y,
            onPrimaryProgress = { v ->
                writeTransform("Move layer") { it.copy(translationX = v) }
            },
            onSecondaryProgress = { v ->
                writeTransform("Move layer") { it.copy(translationY = v) }
            },
            onFieldClick = {
                if (enabled && !state.interactionActive) {
                    try {
                        latestActions.beginEdit("Move layer")
                    } catch (rejected: IllegalStateException) {
                        return@LayerToolPairRow
                    }
                    owner.open(sourceKey)
                    dialogPair = TransformPair.Position
                }
            }
        )
        LayerToolPairRow(
            label = srLabel,
            display = "${(transform.scale * 100f).roundToInt()}% · " +
                formatDegrees(transform.rotationDegrees),
            fieldWidth = 101.dp,
            primaryValue = transform.scale * 100f,
            primaryRange = scaleRange,
            secondaryValue = transform.rotationDegrees,
            secondaryRange = rotationRange,
            enabled = enabled,
            interactionActive = state.interactionActive,
            rowTag = TAG_LAYER_TRANSFORM_SCALE,
            fieldTag = TAG_LAYER_TRANSFORM_ROTATION,
            onPrimaryProgress = { percent ->
                writeTransform("Scale layer") {
                    it.copy(scale = (percent / 100f).coerceIn(0.01f, 10f))
                }
            },
            onSecondaryProgress = { rotation ->
                writeTransform("Rotate layer") {
                    it.copy(rotationDegrees = rotation)
                }
            },
            onFieldClick = {
                if (enabled && !state.interactionActive) {
                    try {
                        latestActions.beginEdit("Scale · rotate layer")
                    } catch (rejected: IllegalStateException) {
                        return@LayerToolPairRow
                    }
                    owner.open(sourceKey)
                    dialogPair = TransformPair.ScaleRotation
                }
            }
        )

        // One 48 dp FlowRow — the pinned-On Lock toggle plus the two
        // actions. The model carries one uniform scale, so the lock is
        // a real selected/On toggle whose click is inert, never a dim
        // switch pretending a free axis exists.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .semantics { selected = true }
                    .toggleable(
                        value = true,
                        enabled = canEdit,
                        role = Role.Switch,
                        onValueChange = {}
                    )
                    .testTag(TAG_LAYER_LOCK_PROPORTIONS)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        Res.string.editor_transform_lock_proportions
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
            }
            FilledTonalButton(
                onClick = {
                    writeTransform("Align centre") {
                        it.copy(translationX = 0f, translationY = 0f)
                    }
                },
                enabled = canEdit &&
                    (transform.translationX != 0f ||
                        transform.translationY != 0f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag(TAG_LAYER_ALIGN)
            ) {
                Text(stringResource(Res.string.editor_transform_align))
            }
            FilledTonalButton(
                onClick = {
                    writeTransform("Flip layer") {
                        it.copy(flipHorizontal = !it.flipHorizontal)
                    }
                },
                enabled = canEdit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .semantics { selected = transform.flipHorizontal }
                    .testTag(TAG_LAYER_FLIP)
            ) {
                Text(stringResource(Res.string.editor_flip))
            }
        }
    }

    dialogPair?.let { pair ->
        if (owner.showDialog) {
            NumericPairEntryDialog(
                label = when (pair) {
                    TransformPair.Position -> xyLabel
                    TransformPair.ScaleRotation -> srLabel
                },
                firstLabel = when (pair) {
                    TransformPair.Position -> xLabel
                    TransformPair.ScaleRotation -> scaleLabel
                },
                firstInitial = when (pair) {
                    TransformPair.Position -> transform.translationX
                    TransformPair.ScaleRotation -> transform.scale * 100f
                },
                firstRange = when (pair) {
                    TransformPair.Position -> xRange
                    TransformPair.ScaleRotation -> scaleRange
                },
                secondLabel = when (pair) {
                    TransformPair.Position -> yLabel
                    TransformPair.ScaleRotation -> rotationLabel
                },
                secondInitial = when (pair) {
                    TransformPair.Position -> transform.translationY
                    TransformPair.ScaleRotation -> transform.rotationDegrees
                },
                secondRange = when (pair) {
                    TransformPair.Position -> yRange
                    TransformPair.ScaleRotation -> rotationRange
                },
                onConfirm = { first, second ->
                    val id = latestLayerId
                    // `ownsDraft` + the source ref are the ownership
                    // proof — `interactionActive` is only a published
                    // snapshot and can lag the open draft by one
                    // recomposition, which would skip the commit and
                    // leave the draft orphaned.
                    if (id != null && owner.ownsDraft &&
                        owner.sourceRef == sourceKey
                    ) {
                        latestActions.draftLayerEdit(latestState, id) {
                            it.copy(
                                transform = when (pair) {
                                    TransformPair.Position ->
                                        it.transform.copy(
                                            translationX = first,
                                            translationY = second
                                        )
                                    TransformPair.ScaleRotation ->
                                        it.transform.copy(
                                            scale = (first / 100f)
                                                .coerceIn(0.01f, 10f),
                                            rotationDegrees = second
                                        )
                                }
                            )
                        }
                        latestActions.commitEdit()
                    }
                    owner.hide()
                    dialogPair = null
                },
                onDismiss = {
                    dialogPair = null
                    owner.dismiss(sourceKey, latestActions.cancelEdit)
                }
            )
        }
    }
}

/** Which pair row's dialog is open — the fields it edits atomically. */
private enum class TransformPair { Position, ScaleRotation }

private val CORNER_HANDLE_TAGS = listOf(
    TAG_LAYER_HANDLE_TL, TAG_LAYER_HANDLE_TR,
    TAG_LAYER_HANDLE_BR, TAG_LAYER_HANDLE_BL
)
private val EDGE_HANDLE_TAGS = listOf(
    TAG_LAYER_HANDLE_TOP, TAG_LAYER_HANDLE_RIGHT,
    TAG_LAYER_HANDLE_BOTTOM, TAG_LAYER_HANDLE_LEFT
)

/**
 * The Transform canvas overlay — the Layers selection chrome made
 * interactive. It paints the 1.5 dp primary quad with eight 10 dp
 * handle anchors over the selected layer, then routes gestures:
 * a drag starting on the layer slides its translation (the 1 dp centre
 * guide appears when the layer centre comes within 2 dp of the canvas
 * centre); a corner-handle drag scales uniformly around the opposite
 * corner. Edge midpoints stay painted anchors — the model's uniform
 * scale has no free axis for them to pull, so they carry no hit target.
 *
 * Each drag is one local interaction: begin on first claim, draft the
 * transform through `updateEdits` while the pointer moves, commit on
 * release, cancel on disposal or gesture loss. Pointer deltas convert
 * through the layout's fit box into full base-output pixels — the same
 * units the X/Y rows and the recipe use.
 */
@Composable
internal fun LayerTransformOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    selectedLayerId: String?,
    toolTitle: String,
    modifier: Modifier = Modifier
) {
    val placement = layerPlacement(state, selectedLayerId) ?: return
    val layer = placement.layer
    val outputSize = placement.outputSize
    val primary = MaterialTheme.colorScheme.primary
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    var gestureOpen by remember { mutableStateOf(false) }
    var guideOn by remember { mutableStateOf(false) }

    // `gestureOpen` must exclude, not include: a down inside a corner
    // handle's box is dispatched to that handle *and* the move surface
    // beneath it — whichever handler resumes first sets the flag, so
    // the sibling must see it closed to keep one gesture per pointer.
    fun canStart() = !layer.locked &&
        !latestState.interactionActive && !gestureOpen

    // An interaction ended elsewhere (tool switch, Apply, layout flip)
    // drops the flag; a mid-gesture disposal cancels the draft it opened.
    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) {
            gestureOpen = false
            guideOn = false
        }
    }
    DisposableEffect(layer.id) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                guideOn = false
                latestActions.cancelEdit()
            }
        }
    }

    val density = LocalDensity.current
    val boxWpx = with(density) { layout.width.toPx() }
    val boxHpx = with(density) { layout.height.toPx() }
    val handlePx = with(density) { 48.dp.toPx() }
    val guideTolerancePx = with(density) { 2.dp.toPx() }

    // Full-output px -> layout-box px (the fit box spans the output).
    fun toLayout(x: Double, y: Double): Offset = Offset(
        (x / outputSize.width * boxWpx).toFloat(),
        (y / outputSize.height * boxHpx).toFloat()
    )

    val points = placement.corners.map { toLayout(it.x, it.y) }
    val midpoints = List(4) { i ->
        Offset(
            (points[i].x + points[(i + 1) % 4].x) / 2f,
            (points[i].y + points[(i + 1) % 4].y) / 2f
        )
    }

    Box(
        modifier = modifier
            .offset(x = layout.left, y = layout.top)
            .size(width = layout.width, height = layout.height)
    ) {
        // The move surface — first so the handle boxes above it win the
        // corner hits; a down outside the layer's own pixels passes
        // through to the canvas pan beneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_LAYER_MOVE)
                .pointerInput(layer.id, boxWpx, boxHpx) {
                    // Raw scope, not awaitEachGesture: event coalescing
                    // can overwrite a down with its own release before
                    // the handler resumes, and awaitFirstDown would hang
                    // on a tap faster than one dispatch window. A
                    // tap's surviving release means nothing to move, so
                    // only an observed down opens a gesture.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.firstOrNull {
                                it.changedToDownIgnoreConsumed()
                            } ?: continue
                            val current = layerPlacement(
                                latestState, selectedLayerId
                            ) ?: continue
                            if (!canStart()) continue
                            // Down in output-pixel indices — the inverse
                            // mapping's own hit domain.
                            val outX = down.position.x / boxWpx *
                                current.outputSize.width - .5
                            val outY = down.position.y / boxHpx *
                                current.outputSize.height - .5
                            if (outX < 0 ||
                                outX >= current.outputSize.width ||
                                outY < 0 ||
                                outY >= current.outputSize.height ||
                                current.mapping.sourcePoint(outX, outY) ==
                                    null
                            ) {
                                continue
                            }
                            val startTransform =
                                latestState.document?.layers
                                    ?.firstOrNull { it.id == layer.id }
                                    ?.transform ?: continue
                            down.consume()
                            gestureOpen = true
                            // `canStart` read a published snapshot — a
                            // draft can still be open if another owner
                            // began inside the same dispatch window. The
                            // session's nested-draft check is the live
                            // verdict; a rejection means the down was
                            // never ours, so skip rather than die here
                            // with someone else's draft on the history.
                            try {
                                latestActions.beginToolEdit(toolTitle)
                                latestActions.beginEdit("Move layer")
                            } catch (rejected: IllegalStateException) {
                                gestureOpen = false
                                continue
                            }
                            var acc = Offset.Zero
                            var committed = false
                            try {
                                while (true) {
                                val nextEvent = awaitPointerEvent()
                                val change = nextEvent.changes.firstOrNull {
                                    it.id == down.id
                                }
                                // `pressed`, not `changedToUp`: the
                                // release event can be overwritten by a
                                // trailing Exit/Move before this resumes
                                // — the state survives the race the
                                // edge does not.
                                if (change == null || !change.pressed) {
                                    committed = true
                                    break
                                }
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    acc += delta
                                    change.consume()
                                    val dxOut =
                                        acc.x / boxWpx * current.outputSize.width
                                    val dyOut =
                                        acc.y / boxHpx * current.outputSize.height
                                    latestActions.draftLayerEdit(
                                        latestState, layer.id
                                    ) {
                                        it.copy(
                                            transform = startTransform.copy(
                                                translationX =
                                                    startTransform.translationX +
                                                        dxOut,
                                                translationY =
                                                    startTransform.translationY +
                                                        dyOut
                                            )
                                        )
                                    }
                                    // The 1 dp guide appears when the
                                    // layer centre approaches the canvas
                                    // centre within 2 dp.
                                    val c = toLayout(
                                        current.outputSize.width / 2.0 +
                                            startTransform.translationX + dxOut,
                                        current.outputSize.height / 2.0 +
                                            startTransform.translationY + dyOut
                                    )
                                    guideOn =
                                        abs(c.x - boxWpx / 2f) <=
                                            guideTolerancePx &&
                                        abs(c.y - boxHpx / 2f) <=
                                            guideTolerancePx
                                }
                                }
                            } finally {
                                gestureOpen = false
                                guideOn = false
                                if (committed) {
                                    latestActions.commitEdit()
                                } else {
                                    latestActions.cancelEdit()
                                }
                            }
                        }
                    }
                }
        )

        Canvas(modifier = Modifier.fillMaxSize().testTag(TAG_LAYER_SELECTION)) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(path = path, color = primary, style = Stroke(1.5.dp.toPx()))
            val r = 5.dp.toPx()
            (points + midpoints).forEach { point ->
                drawCircle(color = primary, radius = r, center = point)
            }
            if (guideOn) {
                val lw = 1.dp.toPx()
                drawRect(
                    color = primary,
                    topLeft = Offset(boxWpx / 2f - lw / 2f, 0f),
                    size = Size(lw, boxHpx)
                )
                drawRect(
                    color = primary,
                    topLeft = Offset(0f, boxHpx / 2f - lw / 2f),
                    size = Size(boxWpx, lw)
                )
            }
        }
        // A semantics marker so tests/assistive tech see the guide.
        if (guideOn) {
            Box(Modifier.fillMaxSize().testTag(TAG_LAYER_GUIDE))
        }

        // The eight anchors: 48 dp hit boxes centred on each point.
        // Corners carry the uniform-scale drag around the opposite
        // corner; the edge midpoints are painted anchors only — the
        // uniform-scale model has no free axis for them to pull.
        points.forEachIndexed { index, point ->
            val fixedIndex = (index + 2) % 4
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - handlePx / 2f).roundToInt(),
                            (point.y - handlePx / 2f).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .testTag(CORNER_HANDLE_TAGS[index])
                    .semantics { contentDescription = "Scale" }
                    .pointerInput("corner-$index", boxWpx, boxHpx) {
                        // Same raw-scope contract as the move surface —
                        // a coalesced down never reaches the handler, so
                        // only an observed down edge opens a gesture.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.firstOrNull {
                                    it.changedToDownIgnoreConsumed()
                                } ?: continue
                                if (!canStart()) continue
                                val start = layerPlacement(
                                    latestState, selectedLayerId
                                ) ?: continue
                                val startTransform =
                                    latestState.document?.layers
                                        ?.firstOrNull { it.id == layer.id }
                                        ?.transform ?: continue
                                down.consume()
                                gestureOpen = true
                                // Same contract as the move surface —
                                // the nested-draft check is the live
                                // verdict on a snapshot-lagged `canStart`.
                                try {
                                    latestActions.beginToolEdit(toolTitle)
                                    latestActions.beginEdit("Scale layer")
                                } catch (rejected: IllegalStateException) {
                                    gestureOpen = false
                                    continue
                                }
                                // The gesture's layout-space geometry: the
                                // opposite corner holds still while the
                                // dragged corner's distance ratio scales.
                                val startCorners = start.corners.map {
                                    toLayout(it.x, it.y)
                                }
                                val origin = startCorners[index] -
                                    Offset(handlePx / 2f, handlePx / 2f)
                                val fixedLayout = startCorners[fixedIndex]
                                val fixedOut = start.corners[fixedIndex]
                                val downLayout = origin + down.position
                                val startDist = hypot(
                                    (downLayout.x - fixedLayout.x).toDouble(),
                                    (downLayout.y - fixedLayout.y).toDouble()
                                ).toFloat()
                                val c0x = start.outputSize.width / 2.0 +
                                    startTransform.translationX
                                val c0y = start.outputSize.height / 2.0 +
                                    startTransform.translationY
                                var acc = Offset.Zero
                                var committed = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull {
                                            it.id == down.id
                                        }
                                        // `pressed`, not `changedToUp` —
                                        // the release can be overwritten by
                                        // a trailing Exit/Move before this
                                        // resumes; the state survives the
                                        // race the edge does not.
                                        if (change == null || !change.pressed) {
                                            committed = true
                                            break
                                        }
                                        val delta = change.positionChange()
                                        if (delta != Offset.Zero && startDist > 1f) {
                                            acc += delta
                                            change.consume()
                                            val cur = downLayout + acc
                                            val dist = hypot(
                                                (cur.x - fixedLayout.x).toDouble(),
                                                (cur.y - fixedLayout.y).toDouble()
                                            ).toFloat()
                                            val nextScale =
                                                (startTransform.scale *
                                                    dist / startDist)
                                                    .coerceIn(0.01f, 10f)
                                            val k = nextScale /
                                                startTransform.scale
                                            // Re-solve the centre so the
                                            // opposite corner stays pinned.
                                            val c1x =
                                                fixedOut.x - k * (fixedOut.x - c0x)
                                            val c1y =
                                                fixedOut.y - k * (fixedOut.y - c0y)
                                            latestActions.draftLayerEdit(
                                                latestState, layer.id
                                            ) {
                                                it.copy(
                                                    transform = startTransform.copy(
                                                        scale = nextScale,
                                                        translationX =
                                                            (c1x -
                                                                start.outputSize
                                                                    .width / 2.0)
                                                                .toFloat(),
                                                        translationY =
                                                            (c1y -
                                                                start.outputSize
                                                                    .height / 2.0)
                                                                .toFloat()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } finally {
                                    gestureOpen = false
                                    if (committed) {
                                        latestActions.commitEdit()
                                    } else {
                                        latestActions.cancelEdit()
                                    }
                                }
                            }
                        }
                    }
            )
        }
        val edgeDescription = stringResource(Res.string.editor_layer_edge_handle)
        midpoints.forEachIndexed { index, point ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - handlePx / 2f).roundToInt(),
                            (point.y - handlePx / 2f).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .testTag(EDGE_HANDLE_TAGS[index])
                    .semantics { contentDescription = edgeDescription }
            )
        }
    }
}
