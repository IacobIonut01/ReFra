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
public val EditorSymbols.WbSunny: ImageVector
  get() {
    if (_WbSunny != null) {
      return _WbSunny!!
    }
    _WbSunny =
      ImageVector.Builder(
          name = "WbSunny",
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
            // Sun disc.
            iconDot(12f, 12f, 3.4f)
            // Eight rays at 45-degree intervals, radius 8.
            iconDot(12f, 3.9f, 1.15f)
            iconDot(12f, 20.1f, 1.15f)
            iconDot(3.9f, 12f, 1.15f)
            iconDot(20.1f, 12f, 1.15f)
            iconDot(6.28f, 6.28f, 1.15f)
            iconDot(17.72f, 6.28f, 1.15f)
            iconDot(6.28f, 17.72f, 1.15f)
            iconDot(17.72f, 17.72f, 1.15f)
          }
        }
        .build()
    return _WbSunny!!
  }

private var _WbSunny: ImageVector? = null

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
