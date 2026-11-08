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
public val EditorSymbols.KeyboardArrowRight: ImageVector
  get() {
    if (_KeyboardArrowRight != null) {
      return _KeyboardArrowRight!!
    }
    _KeyboardArrowRight =
      ImageVector.Builder(
          name = "KeyboardArrowRight",
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
            moveTo(12.6f, 12f)
            lineTo(8.7f, 8.1f)
            quadTo(8.43f, 7.82f, 8.43f, 7.4f)
            reflectiveQuadTo(8.7f, 6.7f)
            reflectiveQuadTo(9.4f, 6.43f)
            reflectiveQuadTo(10.1f, 6.7f)
            lineToRelative(4.6f, 4.6f)
            quadToRelative(0.15f, 0.15f, 0.21f, 0.33f)
            reflectiveQuadTo(14.98f, 12f)
            reflectiveQuadToRelative(-0.06f, 0.38f)
            reflectiveQuadTo(14.7f, 12.7f)
            lineToRelative(-4.6f, 4.6f)
            quadTo(9.83f, 17.58f, 9.4f, 17.58f)
            reflectiveQuadTo(8.7f, 17.3f)
            quadTo(8.43f, 17.02f, 8.43f, 16.6f)
            reflectiveQuadTo(8.7f, 15.9f)
            lineTo(12.6f, 12f)
            close()
          }
        }
        .build()
    return _KeyboardArrowRight!!
  }

private var _KeyboardArrowRight: ImageVector? = null
