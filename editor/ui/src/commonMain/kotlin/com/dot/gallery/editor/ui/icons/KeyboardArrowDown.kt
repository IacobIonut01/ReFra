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
public val EditorSymbols.KeyboardArrowDown: ImageVector
  get() {
    if (_KeyboardArrowDown != null) {
      return _KeyboardArrowDown!!
    }
    _KeyboardArrowDown =
      ImageVector.Builder(
          name = "KeyboardArrowDown",
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
            lineTo(16.5f, 8.1f)
            quadTo(16.78f, 7.82f, 17.15f, 7.82f)
            reflectiveQuadTo(17.95f, 8.1f)
            quadTo(18.23f, 8.38f, 18.23f, 8.75f)
            reflectiveQuadTo(17.95f, 9.45f)
            lineToRelative(-4.6f, 4.6f)
            quadToRelative(-0.15f, 0.15f, -0.33f, 0.21f)
            reflectiveQuadTo(12f, 13.98f)
            reflectiveQuadToRelative(-0.38f, -0.06f)
            reflectiveQuadTo(11.3f, 13.7f)
            lineToRelative(-4.6f, -4.6f)
            quadTo(6.42f, 8.83f, 6.42f, 8.4f)
            reflectiveQuadTo(6.7f, 7.7f)
            quadTo(6.98f, 7.42f, 7.4f, 7.42f)
            reflectiveQuadTo(8.1f, 7.7f)
            lineTo(12f, 11.6f)
            close()
          }
        }
        .build()
    return _KeyboardArrowDown!!
  }

private var _KeyboardArrowDown: ImageVector? = null
