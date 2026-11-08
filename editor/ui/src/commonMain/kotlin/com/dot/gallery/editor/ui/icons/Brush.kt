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
public val EditorSymbols.Brush: ImageVector
  get() {
    if (_Brush != null) {
      return _Brush!!
    }
    _Brush =
      ImageVector.Builder(
          name = "Brush",
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
            moveTo(7f, 14f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            curveToRelative(0f, 1.31f, -1.16f, 2f, -2f, 2f)
            curveToRelative(0.92f, 1.22f, 2.49f, 2f, 4f, 2f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            close()
            moveTo(20.71f, 4.63f)
            lineToRelative(-1.34f, -1.34f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
            lineTo(9f, 12.25f)
            lineTo(11.75f, 15f)
            lineToRelative(8.96f, -8.96f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
            close()
          }
        }
        .build()
    return _Brush!!
  }

private var _Brush: ImageVector? = null
