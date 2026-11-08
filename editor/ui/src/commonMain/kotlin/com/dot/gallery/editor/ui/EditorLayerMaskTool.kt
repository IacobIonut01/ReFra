package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.MAX_PHOTO_MASK_POINTS
import com.dot.gallery.editor.core.model.MAX_PHOTO_MASK_STROKES
import com.dot.gallery.editor.core.model.NormalizedPoint
import com.dot.gallery.editor.core.model.PhotoLayerMask
import com.dot.gallery.editor.core.model.PhotoMaskMode
import com.dot.gallery.editor.core.model.PhotoMaskStroke
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_mask_brush
import com.dot.gallery.editor.ui.generated.resources.editor_mask_erase
import com.dot.gallery.editor.ui.generated.resources.editor_mask_flow
import com.dot.gallery.editor.ui.generated.resources.editor_mask_focus
import com.dot.gallery.editor.ui.generated.resources.editor_mask_hardness
import com.dot.gallery.editor.ui.generated.resources.editor_mask_paint
import com.dot.gallery.editor.ui.generated.resources.editor_mask_reset
import com.dot.gallery.editor.ui.generated.resources.editor_mask_size
import com.dot.gallery.editor.ui.generated.resources.editor_mask_undo_stroke
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

internal const val TAG_MASK_PAINT = "editor.mask.paint"
internal const val TAG_MASK_ERASE = "editor.mask.erase"
internal const val TAG_MASK_SIZE = "editor.mask.size"
internal const val TAG_MASK_SIZE_VALUE = "editor.mask.size.value"
internal const val TAG_MASK_HARDNESS = "editor.mask.hardness"
internal const val TAG_MASK_HARDNESS_VALUE = "editor.mask.hardness.value"
internal const val TAG_MASK_FLOW = "editor.mask.flow"
internal const val TAG_MASK_FLOW_VALUE = "editor.mask.flow.value"
internal const val TAG_MASK_FOCUS = "editor.mask.focus"
internal const val TAG_MASK_UNDO_STROKE = "editor.mask.undoStroke"
internal const val TAG_MASK_RESET = "editor.mask.reset"
internal const val TAG_MASK_CANVAS = "editor.mask.canvas"
internal const val TAG_MASK_CURSOR = "editor.mask.cursor"

/** Mirrors the model's private per-stroke cap — kept under it so a
 *  long gesture rolls into a fresh stroke instead of overflowing. */
private const val STROKE_POINT_CAP = 256

/**
 * The layer mask inspector — the interim contextual surface captured
 * as `p-layer-mask` (the approved Adjustment-brush `p-brush` frame
 * ships in a later phase): the
 * Paint/Erase segment (Erase selected on a fresh full mask — the brush
 * first cuts), the Size/Hardness/Flow value rows, and the Focus
 * editing / Undo stroke actions. Brush settings are tool UI state —
 * they stamp each stroke's diameter/hardness/flow at draw time, never
 * the recipe directly. Undo stroke pops the mask's last stroke as one
 * local step; the More-menu Reset restores the full mask.
 *
 * The canvas brush surface itself is [LayerMaskBrushSurface], wired
 * into the tool canvas by the workspace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayerMaskToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val context = LocalPhotoLayerToolContext.current
    val layer = resolveSelectedLayer(state, context?.selectedLayerId)
    val document = state.document
    val mask = layer?.mask ?: PhotoLayerMask()
    val layerId = layer?.id
    val uiState = LocalPhotoToolUiState.current
        ?: remember { PhotoToolUiState() }
    val enabled = layerId != null && layer != null && !layer.locked &&
        document != null && !state.opening
    val canEdit = enabled && !state.interactionActive
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val latestLayerId by rememberUpdatedState(layerId)
    val sourceKey = state.info?.reference

    // The More-menu Reset restores the untouched full mask.
    val resetLabel = stringResource(Res.string.editor_mask_reset)
    PublishLayerToolReset(
        label = resetLabel,
        tag = TAG_MASK_RESET,
        enabled = canEdit && mask != PhotoLayerMask(),
        onReset = {
            val id = latestLayerId ?: return@PublishLayerToolReset
            latestActions.applyLayerEdit(latestState, id, resetLabel) {
                it.copy(mask = PhotoLayerMask())
            }
        }
    )

    var dialogParam by remember { mutableStateOf<MaskParam?>(null) }
    val brushParams = listOf(
        MaskParam(
            label = stringResource(Res.string.editor_mask_size),
            value = uiState.brushSizePx,
            range = 1f..512f,
            display = "${uiState.brushSizePx.roundToInt()} px",
            rowTag = TAG_MASK_SIZE,
            valueTag = TAG_MASK_SIZE_VALUE
        ) { uiState.brushSizePx = it },
        MaskParam(
            label = stringResource(Res.string.editor_mask_hardness),
            value = uiState.brushHardness,
            range = 0f..100f,
            display = "${uiState.brushHardness.roundToInt()}%",
            rowTag = TAG_MASK_HARDNESS,
            valueTag = TAG_MASK_HARDNESS_VALUE
        ) { uiState.brushHardness = it },
        MaskParam(
            label = stringResource(Res.string.editor_mask_flow),
            value = uiState.brushFlow,
            range = 0f..100f,
            display = "${uiState.brushFlow.roundToInt()}%",
            rowTag = TAG_MASK_FLOW,
            valueTag = TAG_MASK_FLOW_VALUE
        ) { uiState.brushFlow = it }
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Paint/Erase — the 40 dp equal segment; the selected cell owns
        // the mode the canvas's next stroke replays in.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(
                    PhotoMaskMode.Paint,
                    stringResource(Res.string.editor_mask_paint),
                    TAG_MASK_PAINT
                ),
                Triple(
                    PhotoMaskMode.Erase,
                    stringResource(Res.string.editor_mask_erase),
                    TAG_MASK_ERASE
                )
            ).forEach { (mode, label, tag) ->
                val selected = uiState.brushMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            RoundedCornerShape(18.dp)
                        )
                        .clickable(
                            enabled = canEdit && !selected,
                            role = Role.Tab,
                            onClickLabel = label
                        ) { uiState.brushMode = mode }
                        .semantics { this.selected = selected }
                        .testTag(tag),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }

        // Brush geometry — UI state only; values stamp onto each stroke.
        brushParams.forEach { param ->
            GeometryValueRow(
                label = param.label,
                value = param.value,
                range = param.range,
                step = 1f,
                display = param.display,
                enabled = enabled,
                interactionActive = state.interactionActive,
                identity = sourceKey to param.label,
                rowTag = param.rowTag,
                valueTag = param.valueTag,
                // A brush row is pure UI state — no history draft.
                onBeginInteraction = {},
                onUpdateInteraction = { param.write(it) },
                onCommitInteraction = {},
                onCancelInteraction = {},
                onApplyStep = { param.write(it) },
                onRequestDialog = {
                    if (enabled) dialogParam = param
                }
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { context?.enterFocusMode() },
                enabled = layerId != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag(TAG_MASK_FOCUS)
            ) {
                Text(stringResource(Res.string.editor_mask_focus))
            }
            FilledTonalButton(
                onClick = {
                    val id = latestLayerId ?: return@FilledTonalButton
                    latestActions.applyLayerEdit(
                        latestState, id, "Undo mask stroke"
                    ) {
                        it.copy(
                            mask = it.mask.copy(
                                strokes = it.mask.strokes.removingAt(
                                    it.mask.strokes.lastIndex
                                )
                            )
                        )
                    }
                },
                enabled = canEdit && mask.strokes.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag(TAG_MASK_UNDO_STROKE)
            ) {
                Text(stringResource(Res.string.editor_mask_undo_stroke))
            }
        }
    }

    dialogParam?.let { param ->
        NumericEntryDialog(
            label = param.label,
            initialValue = param.value,
            range = param.range,
            onConfirm = { entered ->
                param.write(entered)
                dialogParam = null
            },
            onDismiss = { dialogParam = null }
        )
    }
}

/** One brush-settings row — presentation range plus the UI-state write. */
private class MaskParam(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val display: String,
    val rowTag: String,
    val valueTag: String,
    val write: (Float) -> Unit
)

/**
 * The mask tool's canvas brush surface, drawn over the actual selected
 * layer. A pointer down inside the layer's pixels starts one local
 * interaction whose first point is a stroke; the drag appends
 * normalized points each time the pointer covers at least one source
 * pixel, rolling into a fresh stroke at the per-stroke cap and
 * stopping cleanly at the mask's global caps. Release commits the
 * stroke(s) through `updateEdits` — Erase cuts, Paint restores, and
 * the CPU preview repaints the composite throughout. Cancel restores
 * the pre-gesture mask. A tap is a one-point dot.
 *
 * The painted chrome is the layer's 1.5 dp quad plus a brush-diameter
 * cursor circle tracking the pointer — paint only, no hit boxes.
 */
@Composable
internal fun LayerMaskBrushSurface(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    selectedLayerId: String?,
    toolUiState: PhotoToolUiState,
    toolTitle: String,
    modifier: Modifier = Modifier
) {
    val placement = layerPlacement(state, selectedLayerId) ?: return
    val layer = placement.layer
    val outputSize = placement.outputSize
    val mapping = placement.mapping
    val upright = mapping.uprightSize
    val primary = MaterialTheme.colorScheme.primary
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    var gestureOpen by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<Offset?>(null) }

    // `gestureOpen` excludes: a second pointer's down while this
    // surface's gesture owns the interaction must never reach the
    // session's nested-draft check.
    fun canStart() = !layer.locked &&
        !latestState.interactionActive && !gestureOpen

    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) gestureOpen = false
    }
    DisposableEffect(layer.id) {
        onDispose {
            if (gestureOpen) {
                gestureOpen = false
                latestActions.cancelEdit()
            }
        }
    }

    val density = LocalDensity.current
    val boxWpx = with(density) { layout.width.toPx() }
    val boxHpx = with(density) { layout.height.toPx() }
    val brushLabel = stringResource(Res.string.editor_mask_brush)

    // Full-output px -> layout-box px (the fit box spans the output).
    fun toLayout(x: Double, y: Double): Offset = Offset(
        (x / outputSize.width * boxWpx).toFloat(),
        (y / outputSize.height * boxHpx).toFloat()
    )

    val quad = placement.corners.map { toLayout(it.x, it.y) }
    val upW = upright.width.toFloat()
    val upH = upright.height.toFloat()

    fun toNormalized(pos: Offset): NormalizedPoint? {
        val outX = pos.x / boxWpx * outputSize.width - .5
        val outY = pos.y / boxHpx * outputSize.height - .5
        if (outX < 0 || outX >= outputSize.width ||
            outY < 0 || outY >= outputSize.height
        ) {
            return null
        }
        val sample = mapping.sourcePoint(outX, outY) ?: return null
        return NormalizedPoint(
            sample.normalizedX.toFloat(),
            sample.normalizedY.toFloat()
        )
    }

    Box(
        modifier = modifier
            .offset(x = layout.left, y = layout.top)
            .size(width = layout.width, height = layout.height)
            // Hover tracking — the brush cursor follows the pointer
            // before any button is down; consumed events still reach
            // this passive observer.
            .pointerInput(layer.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        cursor = when (event.type) {
                            PointerEventType.Exit -> null
                            else -> change.position
                        }
                    }
                }
            }
            .pointerInput(
                layer.id, boxWpx, boxHpx,
                toolUiState.brushMode, toolUiState.brushSizePx,
                toolUiState.brushHardness, toolUiState.brushFlow
            ) {
                // Raw scope, not awaitEachGesture: its all-pointers-up
                // preamble consumes events, and event coalescing can
                // overwrite a down with its own release before the
                // handler resumes — awaitFirstDown would hang on a tap
                // faster than one dispatch window. Every event reaches
                // this loop; a release whose down was never delivered is
                // an already-finished tap and stamps one dot stroke.
                fun stampStroke(first: NormalizedPoint) {
                    val mode = toolUiState.brushMode
                    val diameter = (
                        toolUiState.brushSizePx / min(upW, upH)
                        ).coerceIn(0.001f, 1f)
                    val stroke = PhotoMaskStroke(
                        points = persistentListOf(first),
                        diameter = diameter,
                        hardness =
                            (toolUiState.brushHardness / 100f)
                                .coerceIn(0f, 1f),
                        flow =
                            (toolUiState.brushFlow / 100f).coerceIn(0f, 1f),
                        mode = mode
                    )
                    gestureOpen = true
                    // `canStart` read a published snapshot — a foreign
                    // draft can still be open, and the session's
                    // nested-draft check is the live verdict. A
                    // rejection means the tap was never ours.
                    try {
                        latestActions.beginToolEdit(toolTitle)
                        latestActions.beginEdit(brushLabel)
                    } catch (rejected: IllegalStateException) {
                        gestureOpen = false
                        return
                    }
                    try {
                        latestActions.draftLayerEdit(latestState, layer.id) {
                            it.copy(
                                mask = it.mask.copy(
                                    strokes = it.mask.strokes.adding(stroke)
                                )
                            )
                        }
                    } finally {
                        gestureOpen = false
                        latestActions.commitEdit()
                    }
                }

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.firstOrNull {
                            it.changedToDownIgnoreConsumed()
                        }
                        if (down == null) {
                            // The coalesced tap — the down never
                            // reached the handler, only the release
                            // survived.
                            val tap = event.changes.firstOrNull {
                                it.changedToUpIgnoreConsumed()
                            } ?: continue
                            if (!canStart()) continue
                            tap.consume()
                            toNormalized(tap.position)
                                ?.let(::stampStroke)
                            continue
                        }
                        val first = toNormalized(down.position)
                            ?: continue
                        if (!canStart()) continue
                        down.consume()
                        gestureOpen = true
                        // Same contract — the nested-draft check is the
                        // live verdict on a snapshot-lagged `canStart`.
                        try {
                            latestActions.beginToolEdit(toolTitle)
                            latestActions.beginEdit(brushLabel)
                        } catch (rejected: IllegalStateException) {
                            gestureOpen = false
                            continue
                        }

                        val mode = toolUiState.brushMode
                        val diameter = (
                            toolUiState.brushSizePx / min(upW, upH)
                            ).coerceIn(0.001f, 1f)
                        val hardness =
                            (toolUiState.brushHardness / 100f)
                                .coerceIn(0f, 1f)
                        val flow =
                            (toolUiState.brushFlow / 100f).coerceIn(0f, 1f)

                        // The gesture's stroke state — rebuilt into the
                        // mask on every draft update; the local lists
                        // stay the source of truth while the draft is
                        // open. `layer` is a stale capture inside this
                        // long-lived pointerInput — the base strokes
                        // MUST come from the live document or every
                        // later drag replays over an empty base.
                        val baseStrokes = latestState.document?.layers
                            ?.firstOrNull { it.id == layer.id }
                            ?.mask?.strokes
                            ?: persistentListOf()
                        val basePoints =
                            baseStrokes.sumOf { it.points.size }
                        var done = persistentListOf<PhotoMaskStroke>()
                        var points = persistentListOf(first)

                        fun builtStroke() = PhotoMaskStroke(
                            points = points,
                            diameter = diameter,
                            hardness = hardness,
                            flow = flow,
                            mode = mode
                        )

                        fun publish() {
                            // `baseStrokes` MUST prefix the replay —
                            // the mask's stroke list is replaced
                            // wholesale, so dropping the pre-gesture
                            // strokes would silently erase every
                            // earlier drag from the published mask.
                            val all = baseStrokes
                                .addingAll(done)
                                .adding(builtStroke())
                            latestActions.draftLayerEdit(
                                latestState, layer.id
                            ) {
                                it.copy(mask = it.mask.copy(strokes = all))
                            }
                        }
                        publish()

                        var last = first
                        var committed = false
                        try {
                            while (true) {
                                val nextEvent = awaitPointerEvent()
                                val change = nextEvent.changes.firstOrNull {
                                    it.id == down.id
                                }
                                // `pressed`, not `changedToUp`: the
                                // release event can be overwritten by a
                                // trailing Exit/Move before this
                                // resumes — the state survives the race
                                // the edge does not.
                                if (change == null || !change.pressed) {
                                    committed = true
                                    break
                                }
                                if (change.positionChange() !=
                                    Offset.Zero
                                ) {
                                    change.consume()
                                    val next =
                                        toNormalized(change.position)
                                    if (next == null) continue
                                    // At least one source pixel of
                                    // travel earns a point — the
                                    // polyline tracks the stroke, not
                                    // the screen refresh.
                                    val dist = hypot(
                                        (next.x - last.x) * upW,
                                        (next.y - last.y) * upH
                                    )
                                    if (dist < 1f) continue
                                    val totalPoints = basePoints +
                                        done.sumOf { it.points.size } +
                                        points.size
                                    if (totalPoints >=
                                        MAX_PHOTO_MASK_POINTS
                                    ) {
                                        continue
                                    }
                                    if (points.size >= STROKE_POINT_CAP) {
                                        // A full stroke rolls into a
                                        // fresh one — until the mask's
                                        // own stroke cap says stop.
                                        if (baseStrokes.size +
                                            done.size >=
                                            MAX_PHOTO_MASK_STROKES
                                        ) {
                                            continue
                                        }
                                        done = done.adding(builtStroke())
                                        points = persistentListOf()
                                    }
                                    points = points.adding(next)
                                    last = next
                                    publish()
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
            .testTag(TAG_MASK_CANVAS)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(quad[0].x, quad[0].y)
                lineTo(quad[1].x, quad[1].y)
                lineTo(quad[2].x, quad[2].y)
                lineTo(quad[3].x, quad[3].y)
                close()
            }
            drawPath(path = path, color = primary, style = Stroke(1.5.dp.toPx()))
            // The brush circle — diameter scaled from source px through
            // the layer's effective scale into layout px.
            val position = cursor
            if (position != null) {
                val radiusPx = toolUiState.brushSizePx / 2f *
                    mapping.effectiveScale.toFloat() *
                    (boxWpx / outputSize.width)
                drawCircle(
                    color = primary,
                    radius = radiusPx,
                    center = position,
                    style = Stroke(1.5.dp.toPx())
                )
            }
        }
    }
}
