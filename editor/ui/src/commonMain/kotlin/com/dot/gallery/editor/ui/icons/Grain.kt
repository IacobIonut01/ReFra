package com.dot.gallery.editor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val EditorSymbols.Grain: ImageVector
  get() {
    if (_Grain != null) {
      return _Grain!!
    }
    _Grain =
      ImageVector.Builder(
          name = "Grain",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            // Staggered speckle field — the classic grain glyph.
            iconDot(6f, 4.5f, 1.7f)
            iconDot(18f, 5.5f, 1.7f)
            iconDot(12f, 9f, 1.7f)
            iconDot(5f, 12.5f, 1.7f)
            iconDot(18.5f, 14f, 1.7f)
            iconDot(9f, 16.5f, 1.7f)
            iconDot(15f, 19.5f, 1.7f)
          }
        }
        .build()
    return _Grain!!
  }

private var _Grain: ImageVector? = null

/** One filled circle approximated by four cubic arcs (kappa = 0.5523). */
private fun PathBuilder.iconDot(cx: Float, cy: Float, r: Float) {
    val k = r * 0.5523f
    moveTo(cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    close()
}
