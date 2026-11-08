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
public val EditorSymbols.Reorder: ImageVector
  get() {
    if (_Reorder != null) {
      return _Reorder!!
    }
    _Reorder =
      ImageVector.Builder(
          name = "Reorder",
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
            moveTo(4f, 19f)
            quadTo(3.58f, 19f, 3.29f, 18.71f)
            quadTo(3f, 18.43f, 3f, 18f)
            reflectiveQuadTo(3.29f, 17.29f)
            reflectiveQuadTo(4f, 17f)
            horizontalLineTo(20f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 18f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(20f, 19f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 15f)
            quadTo(3.58f, 15f, 3.29f, 14.71f)
            reflectiveQuadTo(3f, 14f)
            reflectiveQuadTo(3.29f, 13.29f)
            reflectiveQuadTo(4f, 13f)
            horizontalLineTo(20f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 14f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(20f, 15f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 11f)
            quadTo(3.58f, 11f, 3.29f, 10.71f)
            quadTo(3f, 10.43f, 3f, 10f)
            quadTo(3f, 9.57f, 3.29f, 9.29f)
            reflectiveQuadTo(4f, 9f)
            horizontalLineTo(20f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 10f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(20f, 11f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 7f)
            quadTo(3.58f, 7f, 3.29f, 6.71f)
            quadTo(3f, 6.43f, 3f, 6f)
            reflectiveQuadTo(3.29f, 5.29f)
            reflectiveQuadTo(4f, 5f)
            horizontalLineTo(20f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 6f)
            reflectiveQuadTo(20.71f, 6.71f)
            reflectiveQuadTo(20f, 7f)
            horizontalLineTo(4f)
            close()
          }
        }
        .build()
    return _Reorder!!
  }

private var _Reorder: ImageVector? = null
