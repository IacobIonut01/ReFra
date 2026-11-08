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
public val EditorSymbols.ViewList: ImageVector
  get() {
    if (_ViewList != null) {
      return _ViewList!!
    }
    _ViewList =
      ImageVector.Builder(
          name = "ViewList",
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
            moveTo(10f, 20f)
            quadTo(9.58f, 20f, 9.29f, 19.71f)
            quadTo(9f, 19.43f, 9f, 19f)
            verticalLineTo(17f)
            quadTo(9f, 16.58f, 9.29f, 16.29f)
            quadTo(9.58f, 16f, 10f, 16f)
            horizontalLineTo(21f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 17f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(21f, 20f)
            horizontalLineTo(10f)
            close()
            moveTo(3f, 8f)
            quadTo(2.58f, 8f, 2.29f, 7.71f)
            quadTo(2f, 7.43f, 2f, 7f)
            verticalLineTo(5f)
            quadTo(2f, 4.57f, 2.29f, 4.29f)
            reflectiveQuadTo(3f, 4f)
            horizontalLineTo(6f)
            quadTo(6.43f, 4f, 6.71f, 4.29f)
            reflectiveQuadTo(7f, 5f)
            verticalLineTo(7f)
            quadTo(7f, 7.43f, 6.71f, 7.71f)
            reflectiveQuadTo(6f, 8f)
            horizontalLineTo(3f)
            close()
            moveToRelative(0f, 6f)
            quadTo(2.58f, 14f, 2.29f, 13.71f)
            quadTo(2f, 13.43f, 2f, 13f)
            verticalLineTo(11f)
            quadTo(2f, 10.58f, 2.29f, 10.29f)
            reflectiveQuadTo(3f, 10f)
            horizontalLineTo(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(7f, 11f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 14f)
            horizontalLineTo(3f)
            close()
            moveToRelative(0f, 6f)
            quadTo(2.58f, 20f, 2.29f, 19.71f)
            quadTo(2f, 19.43f, 2f, 19f)
            verticalLineTo(17f)
            quadTo(2f, 16.58f, 2.29f, 16.29f)
            reflectiveQuadTo(3f, 16f)
            horizontalLineTo(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(7f, 17f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 20f)
            horizontalLineTo(3f)
            close()
            moveToRelative(7f, -6f)
            quadTo(9.58f, 14f, 9.29f, 13.71f)
            quadTo(9f, 13.43f, 9f, 13f)
            verticalLineTo(11f)
            quadTo(9f, 10.58f, 9.29f, 10.29f)
            quadTo(9.58f, 10f, 10f, 10f)
            horizontalLineTo(21f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 11f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(21f, 14f)
            horizontalLineTo(10f)
            close()
            moveTo(10f, 8f)
            quadTo(9.58f, 8f, 9.29f, 7.71f)
            quadTo(9f, 7.43f, 9f, 7f)
            verticalLineTo(5f)
            quadTo(9f, 4.57f, 9.29f, 4.29f)
            quadTo(9.58f, 4f, 10f, 4f)
            horizontalLineTo(21f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 5f)
            verticalLineTo(7f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(21f, 8f)
            horizontalLineTo(10f)
            close()
          }
        }
        .build()
    return _ViewList!!
  }

private var _ViewList: ImageVector? = null
