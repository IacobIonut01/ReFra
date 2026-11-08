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
public val EditorSymbols.Compare: ImageVector
  get() {
    if (_Compare != null) {
      return _Compare!!
    }
    _Compare =
      ImageVector.Builder(
          name = "Compare",
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
            moveTo(10f, 21f)
            horizontalLineTo(5f)
            quadTo(4.18f, 21f, 3.59f, 20.41f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5f)
            quadTo(3f, 4.17f, 3.59f, 3.59f)
            reflectiveQuadTo(5f, 3f)
            horizontalLineToRelative(5f)
            verticalLineTo(2f)
            quadTo(10f, 1.57f, 10.29f, 1.29f)
            reflectiveQuadTo(11f, 1f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(12f, 2f)
            verticalLineTo(22f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(11f, 23f)
            reflectiveQuadTo(10.29f, 22.71f)
            quadTo(10f, 22.43f, 10f, 22f)
            verticalLineTo(21f)
            close()
            moveTo(5f, 18f)
            horizontalLineToRelative(5f)
            verticalLineTo(12f)
            lineTo(5f, 18f)
            close()
            moveToRelative(9f, 3f)
            verticalLineTo(12f)
            lineToRelative(5f, 6f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            verticalLineTo(3f)
            horizontalLineToRelative(5f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(21f, 5f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 21f)
            horizontalLineTo(14f)
            close()
          }
        }
        .build()
    return _Compare!!
  }

private var _Compare: ImageVector? = null
