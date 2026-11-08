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
public val EditorSymbols.Undo: ImageVector
  get() {
    if (_Undo != null) {
      return _Undo!!
    }
    _Undo =
      ImageVector.Builder(
          name = "Undo",
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
            moveTo(8f, 19f)
            quadTo(7.58f, 19f, 7.29f, 18.71f)
            quadTo(7f, 18.43f, 7f, 18f)
            reflectiveQuadTo(7.29f, 17.29f)
            reflectiveQuadTo(8f, 17f)
            horizontalLineToRelative(6.1f)
            quadToRelative(1.57f, 0f, 2.74f, -1f)
            reflectiveQuadTo(18f, 13.5f)
            reflectiveQuadTo(16.84f, 11f)
            reflectiveQuadTo(14.1f, 10f)
            horizontalLineTo(7.8f)
            lineToRelative(1.9f, 1.9f)
            quadToRelative(0.28f, 0.28f, 0.28f, 0.7f)
            quadToRelative(0f, 0.42f, -0.28f, 0.7f)
            reflectiveQuadTo(9f, 13.58f)
            quadToRelative(-0.42f, 0f, -0.7f, -0.28f)
            lineTo(4.7f, 9.7f)
            quadTo(4.55f, 9.55f, 4.49f, 9.38f)
            reflectiveQuadTo(4.43f, 9f)
            reflectiveQuadTo(4.49f, 8.63f)
            reflectiveQuadTo(4.7f, 8.3f)
            lineTo(8.3f, 4.7f)
            quadTo(8.58f, 4.42f, 9f, 4.42f)
            quadToRelative(0.43f, 0f, 0.7f, 0.28f)
            quadTo(9.98f, 4.97f, 9.98f, 5.4f)
            reflectiveQuadTo(9.7f, 6.1f)
            lineTo(7.8f, 8f)
            horizontalLineToRelative(6.3f)
            quadToRelative(2.43f, 0f, 4.16f, 1.57f)
            reflectiveQuadTo(20f, 13.5f)
            reflectiveQuadToRelative(-1.74f, 3.93f)
            quadTo(16.53f, 19f, 14.1f, 19f)
            horizontalLineTo(8f)
            close()
          }
        }
        .build()
    return _Undo!!
  }

private var _Undo: ImageVector? = null
