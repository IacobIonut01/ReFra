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
public val EditorSymbols.Vignette: ImageVector
  get() {
    if (_Vignette != null) {
      return _Vignette!!
    }
    _Vignette =
      ImageVector.Builder(
          name = "Vignette",
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
            // Frame: 20x16 rounded rect (2.2 corner) as a 2dp ring via
            // the outer fill rule — the inner ellipse reads as the
            // vignette aperture.
            moveTo(4.2f, 4f)
            lineTo(19.8f, 4f)
            quadTo(22f, 4f, 22f, 6.2f)
            lineTo(22f, 17.8f)
            quadTo(22f, 20f, 19.8f, 20f)
            lineTo(4.2f, 20f)
            quadTo(2f, 20f, 2f, 17.8f)
            lineTo(2f, 6.2f)
            quadTo(2f, 4f, 4.2f, 4f)
            close()
            moveTo(4.2f, 6f)
            quadTo(4f, 6f, 4f, 6.2f)
            lineTo(4f, 17.8f)
            quadTo(4f, 18f, 4.2f, 18f)
            lineTo(19.8f, 18f)
            quadTo(20f, 18f, 20f, 17.8f)
            lineTo(20f, 6.2f)
            quadTo(20f, 6f, 19.8f, 6f)
            close()
            // Aperture: wide ellipse, rx 6.4 ry 3.9 centred.
            iconOval(12f, 12f, 6.4f, 3.9f)
          }
        }
        .build()
    return _Vignette!!
  }

private var _Vignette: ImageVector? = null

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
