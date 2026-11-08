package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.WhiteBalanceModel
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_wb_neutral_point
import com.dot.gallery.editor.ui.generated.resources.editor_wb_pick_failed
import com.dot.gallery.editor.ui.generated.resources.editor_wb_pick_hint
import com.dot.gallery.editor.ui.generated.resources.editor_wb_pick_surface
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val TAG_WB_PICK_SURFACE = "editor.wb.pickSurface"
internal const val TAG_WB_RETICLE = "editor.wb.reticle"
internal const val TAG_WB_PICK_HINT = "editor.wb.pickHint"

/** The neutral-point reticle's outer diameter. */
private val ReticleSize = 40.dp

/**
 * The White balance neutral-point picker's canvas surface, shown while
 * [PhotoToolUiState.whiteBalancePicking] is on. It claims every pointer
 * over the media card so the canvas cannot zoom or pan mid-pick, and
 * resets the viewport to fit on entry: [layout] is the zoom-1 fit box,
 * so a point inside it is exactly a normalized output coordinate of the
 * displayed recipe (geometry, warp and crop already applied by the
 * render).
 *
 * Press or drag moves a loupe-free reticle (clamped to the image);
 * release samples the averaged patch before white balance through
 * [PhotoEditorActions.sampleBeforeWhiteBalance], solves the neutral
 * recipe with [WhiteBalanceModel.neutralFor] and lands it with one
 * `applyEdits` — a single undo step inside the tool transaction — then
 * leaves pick mode. A patch that cannot be solved keeps pick mode on
 * and says so in the hint. Back/Escape leave pick mode through the
 * workspace's close unwinding.
 */
@Composable
internal fun WhiteBalancePickOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    toolUiState: PhotoToolUiState,
    viewport: EditorViewportState?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val historyLabel = stringResource(Res.string.editor_wb_neutral_point)
    val toolTitle = photoToolTitle(EditorToolIds.PhotoWhiteBalance)
    val surfaceLabel = stringResource(Res.string.editor_wb_pick_surface)
    // The reticle in fit-box fractions; null until the first press.
    var point by remember { mutableStateOf<Offset?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sampling by remember { mutableStateOf(false) }

    LaunchedEffect(viewport) {
        viewport?.apply {
            zoom = 1f
            panX = 0f
            panY = 0f
        }
    }

    fun pick(at: Offset) {
        if (sampling) return
        sampling = true
        scope.launch {
            try {
                val rgb = latestActions.sampleBeforeWhiteBalance(at.x, at.y)
                val solved = rgb?.let {
                    WhiteBalanceModel.neutralFor(it[0], it[1], it[2])
                }
                val current = latestState.document?.toEdits()
                if (solved == null || current == null ||
                    latestState.interactionActive
                ) {
                    failed = solved == null
                    return@launch
                }
                failed = false
                latestActions.beginToolEdit(toolTitle)
                latestActions.applyEdits(historyLabel, current.withWhiteBalance(solved))
                toolUiState.whiteBalancePicking = false
            } finally {
                sampling = false
            }
        }
    }

    val fieldOffsetX = layout.left - layout.fieldLeft
    val fieldOffsetY = layout.top - layout.fieldTop
    Box(
        modifier = modifier
            .offset(x = layout.fieldLeft, y = layout.fieldTop)
            .size(width = layout.fieldWidth, height = layout.fieldHeight)
            .semantics { contentDescription = surfaceLabel }
            .pointerInput(layout) {
                val left = fieldOffsetX.toPx()
                val top = fieldOffsetY.toPx()
                val w = layout.width.toPx()
                val h = layout.height.toPx()
                fun fraction(p: Offset) = Offset(
                    ((p.x - left) / w).coerceIn(0f, 1f),
                    ((p.y - top) / h).coerceIn(0f, 1f)
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (w <= 0f || h <= 0f) return@awaitEachGesture
                    point = fraction(down.position)
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        change.consume()
                        last = change.position
                        if (!change.pressed) break
                        point = fraction(last)
                    }
                    point = fraction(last)
                    point?.let(::pick)
                }
            }
            .testTag(TAG_WB_PICK_SURFACE)
    ) {
        val p = point
        if (p != null) {
            WhiteBalanceReticle(
                modifier = Modifier.offset(
                    x = fieldOffsetX + layout.width * p.x - ReticleSize / 2,
                    y = fieldOffsetY + layout.height * p.y - ReticleSize / 2
                )
            )
        }
        EditorFloatingPanel(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 360.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(TAG_WB_PICK_HINT)
        ) {
            Text(
                text = stringResource(
                    if (failed) Res.string.editor_wb_pick_failed
                    else Res.string.editor_wb_pick_hint
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * A crosshair ring that stays legible over any colour — a light ring and
 * ticks over a dark halo — with an open centre so the sampled pixels
 * themselves remain visible.
 */
@Composable
private fun WhiteBalanceReticle(modifier: Modifier = Modifier, size: Dp = ReticleSize) {
    Canvas(modifier.size(size).testTag(TAG_WB_RETICLE)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val ring = this.size.minDimension / 2f - 3.dp.toPx()
        val gap = 6.dp.toPx()
        val halo = Color.Black.copy(alpha = 0.55f)
        val light = Color.White
        for ((color, width) in listOf(halo to 4.dp.toPx(), light to 2.dp.toPx())) {
            drawCircle(color, radius = ring, center = c, style = Stroke(width))
            drawLine(color, Offset(c.x, c.y - ring), Offset(c.x, c.y - gap), width)
            drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + ring), width)
            drawLine(color, Offset(c.x - ring, c.y), Offset(c.x - gap, c.y), width)
            drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + ring, c.y), width)
        }
    }
}

private fun PhotoEdits.withWhiteBalance(wb: PhotoWhiteBalance): PhotoEdits =
    copy(finishing = finishing.copy(whiteBalance = wb))
