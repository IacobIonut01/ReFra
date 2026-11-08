package com.dot.gallery.editor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val EditorSymbols.InvertColors: ImageVector
  get() {
    if (_InvertColors != null) {
      return _InvertColors!!
    }
    _InvertColors =
      ImageVector.Builder(
          name = "InvertColors",
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
            moveTo(17.66f, 7.93f)
            lineTo(12f, 2.27f)
            lineTo(6.34f, 7.93f)
            curveToRelative(-3.12f, 3.12f, -3.12f, 8.19f, 0f, 11.31f)
            curveTo(7.78f, 20.68f, 9.91f, 21.43f, 12f, 21.43f)
            curveToRelative(2.09f, 0f, 4.22f, -0.75f, 5.66f, -2.19f)
            curveToRelative(3.12f, -3.12f, 3.12f, -8.19f, 0f, -11.31f)
            close()
            moveTo(12f, 19.59f)
            curveToRelative(-1.6f, 0f, -3.11f, -0.62f, -4.24f, -1.76f)
            curveToRelative(-2.34f, -2.34f, -2.34f, -6.14f, 0f, -8.49f)
            lineTo(12f, 5.1f)
            lineTo(12f, 19.59f)
            close()
          }
        }
        .build()
    return _InvertColors!!
  }

private var _InvertColors: ImageVector? = null
