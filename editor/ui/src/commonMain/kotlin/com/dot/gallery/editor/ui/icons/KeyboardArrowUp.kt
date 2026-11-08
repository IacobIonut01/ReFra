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
public val EditorSymbols.KeyboardArrowUp: ImageVector
  get() {
    if (_KeyboardArrowUp != null) {
      return _KeyboardArrowUp!!
    }
    _KeyboardArrowUp =
      ImageVector.Builder(
          name = "KeyboardArrowUp",
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
            moveTo(11.4f, 12f)
            lineTo(7.5f, 15.9f)
            quadTo(7.22f, 16.18f, 6.85f, 16.18f)
            reflectiveQuadTo(6.05f, 15.9f)
            quadTo(5.77f, 15.62f, 5.77f, 15.25f)
            reflectiveQuadTo(6.05f, 14.55f)
            lineToRelative(4.6f, -4.6f)
            quadToRelative(0.15f, -0.15f, 0.33f, -0.21f)
            reflectiveQuadTo(12f, 10.02f)
            reflectiveQuadToRelative(0.38f, 0.06f)
            reflectiveQuadTo(12.7f, 10.3f)
            lineToRelative(4.6f, 4.6f)
            quadTo(17.58f, 15.17f, 17.58f, 15.6f)
            reflectiveQuadTo(17.3f, 16.3f)
            quadTo(17.02f, 16.58f, 16.6f, 16.58f)
            reflectiveQuadTo(15.9f, 16.3f)
            lineTo(12f, 12.4f)
            close()
          }
        }
        .build()
    return _KeyboardArrowUp!!
  }

private var _KeyboardArrowUp: ImageVector? = null
