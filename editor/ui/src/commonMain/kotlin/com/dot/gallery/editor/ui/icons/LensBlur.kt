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
public val EditorSymbols.LensBlur: ImageVector
  get() {
    if (_LensBlur != null) {
      return _LensBlur!!
    }
    _LensBlur =
      ImageVector.Builder(
          name = "LensBlur",
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
            pathFillType = PathFillType.Companion.EvenOdd,
          ) {
            // Two concentric bokeh rings — the even-odd rule leaves a
            // 2 dp annulus between them and a clear core.
            iconOval(12f, 12f, 9f, 9f)
            iconOval(12f, 12f, 5.5f, 5.5f)
            iconOval(12f, 12f, 2.2f, 2.2f)
          }
        }
        .build()
    return _LensBlur!!
  }

private var _LensBlur: ImageVector? = null

/** One filled ellipse approximated by four cubic arcs (kappa = 0.5523). */
private fun PathBuilder.iconOval(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float
) {
    val kx = rx * 0.5523f
    val ky = ry * 0.5523f
    moveTo(cx - rx, cy)
    curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
    curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
    curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
    curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
    close()
}
