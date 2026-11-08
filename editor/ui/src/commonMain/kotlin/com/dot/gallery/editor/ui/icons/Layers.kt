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
public val EditorSymbols.Layers: ImageVector
  get() {
    if (_Layers != null) {
      return _Layers!!
    }
    _Layers =
      ImageVector.Builder(
          name = "Layers",
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
            moveTo(11.99f, 18.54f)
            lineToRelative(-7.37f, -5.73f)
            lineTo(3f, 14.07f)
            lineToRelative(9f, 7f)
            lineToRelative(9f, -7f)
            lineToRelative(-1.63f, -1.27f)
            lineToRelative(-7.38f, 5.74f)
            close()
            moveTo(12f, 16f)
            lineToRelative(7.36f, -5.73f)
            lineTo(21f, 9f)
            lineToRelative(-9f, -7f)
            lineToRelative(-9f, 7f)
            lineToRelative(1.63f, 1.27f)
            lineTo(12f, 16f)
            close()
          }
        }
        .build()
    return _Layers!!
  }

private var _Layers: ImageVector? = null
