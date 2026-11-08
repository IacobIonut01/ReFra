package com.dot.gallery.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoLayerMapping
import com.dot.gallery.editor.core.model.PhotoMapping
import com.dot.gallery.editor.render.photo.PhotoEditorState

/**
 * The Layers surface's selection chrome: a 1.5 dp primary quadrilateral
 * around the selected visible image layer plus a 10 dp circular handle on
 * each corner and each edge midpoint — eight anchors matching p-layers.
 * It is display-only — pointer manipulation is the Transform tool's
 * phase — and only a real visible layer paints one; the base and the
 * pseudo adjustment row select without an outline.
 *
 * The quad is derived through [PhotoLayerMapping.outputPoint] — the exact
 * forward counterpart of the compositor's inverse sampling — evaluated at
 * the full base-output size, then scaled into the canvas's fit box, so
 * the painted frame tracks what the composite actually renders.
 */
@Composable
internal fun PhotoLayerSelectionOverlay(
    layout: EditorImageLayout,
    state: PhotoEditorState,
    selectedLayerId: String?,
    modifier: Modifier = Modifier
) {
    val document = state.document ?: return
    val layer = document.layers.firstOrNull { it.id == selectedLayerId }
        ?: return
    if (!layer.visible) return
    val outputSize = remember(document.source.size, document.geometry) {
        PhotoMapping(document.source.size, 1, document.geometry).outputSize
    }
    val mapping = remember(layer, outputSize) {
        PhotoLayerMapping(
            rawSize = layer.source.size,
            orientation = 1,
            outputSize = outputSize,
            transform = layer.transform
        )
    }
    val corners = remember(mapping) {
        val w = mapping.uprightSize.width.toDouble()
        val h = mapping.uprightSize.height.toDouble()
        listOf(
            mapping.outputPoint(-0.5, -0.5),
            mapping.outputPoint(w - 0.5, -0.5),
            mapping.outputPoint(w - 0.5, h - 0.5),
            mapping.outputPoint(-0.5, h - 0.5)
        )
    }
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_LAYER_SELECTION)
    ) {
        // Output pixels -> overlay dp: the fit box spans the whole output.
        fun toOffset(x: Double, y: Double): Offset = Offset(
            (layout.left.toPx() +
                (x / outputSize.width) * layout.width.toPx()).toFloat(),
            (layout.top.toPx() +
                (y / outputSize.height) * layout.height.toPx()).toFloat()
        )
        val points = corners.map { toOffset(it.x, it.y) }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(
            path = path,
            color = primary,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Eight 10 dp circular anchors — the four corners plus each edge
        // midpoint, so the display chrome reads like the Figma handles
        // the Transform tool later makes interactive.
        val handleRadius = 5.dp.toPx()
        val handles = points + listOf(
            Offset(
                (points[0].x + points[1].x) / 2f,
                (points[0].y + points[1].y) / 2f
            ),
            Offset(
                (points[1].x + points[2].x) / 2f,
                (points[1].y + points[2].y) / 2f
            ),
            Offset(
                (points[2].x + points[3].x) / 2f,
                (points[2].y + points[3].y) / 2f
            ),
            Offset(
                (points[3].x + points[0].x) / 2f,
                (points[3].y + points[0].y) / 2f
            )
        )
        handles.forEach { point ->
            drawCircle(color = primary, radius = handleRadius, center = point)
        }
    }
}
