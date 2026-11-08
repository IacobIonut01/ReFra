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
public val EditorSymbols.Redo: ImageVector
  get() {
    if (_Redo != null) {
      return _Redo!!
    }
    _Redo =
      ImageVector.Builder(
          name = "Redo",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
          autoMirror = true,
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
            moveTo(16.2f, 10f)
            horizontalLineTo(9.9f)
            quadTo(8.33f, 10f, 7.16f, 11f)
            reflectiveQuadTo(6f, 13.5f)
            reflectiveQuadTo(7.16f, 16f)
            reflectiveQuadTo(9.9f, 17f)
            horizontalLineTo(16f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(17f, 18f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(16f, 19f)
            horizontalLineTo(9.9f)
            quadTo(7.48f, 19f, 5.74f, 17.43f)
            reflectiveQuadTo(4f, 13.5f)
            reflectiveQuadTo(5.74f, 9.57f)
            reflectiveQuadTo(9.9f, 8f)
            horizontalLineToRelative(6.3f)
            lineTo(14.3f, 6.1f)
            quadTo(14.03f, 5.82f, 14.03f, 5.4f)
            reflectiveQuadTo(14.3f, 4.7f)
            quadTo(14.58f, 4.42f, 15f, 4.42f)
            reflectiveQuadTo(15.7f, 4.7f)
            lineToRelative(3.6f, 3.6f)
            quadToRelative(0.15f, 0.15f, 0.21f, 0.33f)
            reflectiveQuadTo(19.58f, 9f)
            reflectiveQuadTo(19.51f, 9.38f)
            reflectiveQuadTo(19.3f, 9.7f)
            lineToRelative(-3.6f, 3.6f)
            quadTo(15.43f, 13.58f, 15f, 13.58f)
            reflectiveQuadTo(14.3f, 13.3f)
            reflectiveQuadTo(14.03f, 12.6f)
            reflectiveQuadTo(14.3f, 11.9f)
            lineTo(16.2f, 10f)
            close()
          }
        }
        .build()
    return _Redo!!
  }

private var _Redo: ImageVector? = null
