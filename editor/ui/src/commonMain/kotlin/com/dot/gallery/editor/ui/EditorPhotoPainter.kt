package com.dot.gallery.editor.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import com.dot.gallery.editor.core.model.PhotoEdits

/** ZoomImage owns placement; this painter supplies pixels or the live GPU recipe. */
internal class EditorPhotoPainter(
    override val intrinsicSize: Size,
    private val preview: Painter?,
    private val renderer: PhotoShaderRenderer?,
    private val edits: PhotoEdits?,
    private val onFrameFailed: (PhotoShaderRenderer) -> Unit,
    private val checkerA: Color,
    private val checkerB: Color,
    private val cellSize: Float,
    private val corner: Float
) : Painter() {
    override fun DrawScope.onDraw() {
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        }
        clipPath(clip) {
            drawRect(checkerA)
            val checks = Path()
            var row = 0
            var y = 0f
            while (y < size.height) {
                var col = 0
                var x = 0f
                while (x < size.width) {
                    if ((row + col) % 2 != 0) {
                        checks.addRect(
                            Rect(
                                x, y, minOf(x + cellSize, size.width), minOf(y + cellSize, size.height)
                            )
                        )
                    }
                    col++
                    x += cellSize
                }
                row++
                y += cellSize
            }
            drawPath(checks, checkerB)
            if (renderer != null && edits != null) {
                if (!renderer.draw(this, edits)) onFrameFailed(renderer)
            } else {
                preview?.let { with(it) { draw(size) } }
            }
        }
    }
}
