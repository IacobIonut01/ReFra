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
public val EditorSymbols.Crop: ImageVector
  get() {
    if (_Crop != null) {
      return _Crop!!
    }
    _Crop =
      ImageVector.Builder(
          name = "Crop",
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
            moveTo(17f, 22f)
            verticalLineTo(19f)
            horizontalLineTo(7f)
            quadTo(6.18f, 19f, 5.59f, 18.41f)
            reflectiveQuadTo(5f, 17f)
            verticalLineTo(7f)
            horizontalLineTo(2f)
            quadTo(1.58f, 7f, 1.29f, 6.71f)
            quadTo(1f, 6.43f, 1f, 6f)
            reflectiveQuadTo(1.29f, 5.29f)
            reflectiveQuadTo(2f, 5f)
            horizontalLineTo(5f)
            verticalLineTo(2f)
            quadTo(5f, 1.57f, 5.29f, 1.29f)
            reflectiveQuadTo(6f, 1f)
            reflectiveQuadTo(6.71f, 1.29f)
            reflectiveQuadTo(7f, 2f)
            verticalLineTo(17f)
            horizontalLineTo(22f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(23f, 18f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(22f, 19f)
            horizontalLineTo(19f)
            verticalLineToRelative(3f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(18f, 23f)
            reflectiveQuadTo(17.29f, 22.71f)
            quadTo(17f, 22.43f, 17f, 22f)
            close()
            moveToRelative(0f, -7f)
            verticalLineTo(7f)
            horizontalLineTo(9f)
            verticalLineTo(5f)
            horizontalLineToRelative(8f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(19f, 6.18f, 19f, 7f)
            verticalLineToRelative(8f)
            horizontalLineTo(17f)
            close()
          }
        }
        .build()
    return _Crop!!
  }

private var _Crop: ImageVector? = null
