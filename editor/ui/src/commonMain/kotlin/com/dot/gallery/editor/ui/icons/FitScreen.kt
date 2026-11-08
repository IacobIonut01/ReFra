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
public val EditorSymbols.FitScreen: ImageVector
  get() {
    if (_FitScreen != null) {
      return _FitScreen!!
    }
    _FitScreen =
      ImageVector.Builder(
          name = "FitScreen",
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
            moveTo(20f, 8f)
            verticalLineTo(6f)
            horizontalLineTo(18f)
            quadTo(17.58f, 6f, 17.29f, 5.71f)
            quadTo(17f, 5.43f, 17f, 5f)
            reflectiveQuadTo(17.29f, 4.29f)
            reflectiveQuadTo(18f, 4f)
            horizontalLineToRelative(2f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(22f, 5.18f, 22f, 6f)
            verticalLineTo(8f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(21f, 9f)
            reflectiveQuadTo(20.29f, 8.71f)
            reflectiveQuadTo(20f, 8f)
            close()
            moveTo(2f, 8f)
            verticalLineTo(6f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            horizontalLineTo(6f)
            quadTo(6.43f, 4f, 6.71f, 4.29f)
            reflectiveQuadTo(7f, 5f)
            reflectiveQuadTo(6.71f, 5.71f)
            reflectiveQuadTo(6f, 6f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            quadTo(4f, 8.42f, 3.71f, 8.71f)
            reflectiveQuadTo(3f, 9f)
            reflectiveQuadTo(2.29f, 8.71f)
            reflectiveQuadTo(2f, 8f)
            close()
            moveTo(20f, 20f)
            horizontalLineTo(18f)
            quadToRelative(-0.43f, 0f, -0.71f, -0.29f)
            quadTo(17f, 19.43f, 17f, 19f)
            reflectiveQuadToRelative(0.29f, -0.71f)
            reflectiveQuadTo(18f, 18f)
            horizontalLineToRelative(2f)
            verticalLineTo(16f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(21f, 15f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 16f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 20f)
            close()
            moveTo(4f, 20f)
            quadTo(3.18f, 20f, 2.59f, 19.41f)
            reflectiveQuadTo(2f, 18f)
            verticalLineTo(16f)
            quadTo(2f, 15.58f, 2.29f, 15.29f)
            reflectiveQuadTo(3f, 15f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(4f, 16f)
            verticalLineToRelative(2f)
            horizontalLineTo(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(7f, 19f)
            reflectiveQuadTo(6.71f, 19.71f)
            reflectiveQuadTo(6f, 20f)
            horizontalLineTo(4f)
            close()
            moveTo(6f, 14f)
            verticalLineTo(10f)
            quadTo(6f, 9.17f, 6.59f, 8.59f)
            reflectiveQuadTo(8f, 8f)
            horizontalLineToRelative(8f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(18f, 10f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(16f, 16f)
            horizontalLineTo(8f)
            quadTo(7.18f, 16f, 6.59f, 15.41f)
            reflectiveQuadTo(6f, 14f)
            close()
          }
        }
        .build()
    return _FitScreen!!
  }

private var _FitScreen: ImageVector? = null
