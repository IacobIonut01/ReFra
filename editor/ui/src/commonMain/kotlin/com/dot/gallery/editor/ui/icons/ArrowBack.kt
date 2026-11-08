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
public val EditorSymbols.ArrowBack: ImageVector
  get() {
    if (_ArrowBack != null) {
      return _ArrowBack!!
    }
    _ArrowBack =
      ImageVector.Builder(
          name = "ArrowBack",
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
            moveTo(7.83f, 13f)
            lineToRelative(4.9f, 4.9f)
            quadToRelative(0.3f, 0.3f, 0.29f, 0.7f)
            reflectiveQuadTo(12.7f, 19.3f)
            quadTo(12.4f, 19.58f, 12f, 19.59f)
            reflectiveQuadTo(11.3f, 19.3f)
            lineTo(4.7f, 12.7f)
            quadTo(4.55f, 12.55f, 4.49f, 12.38f)
            reflectiveQuadTo(4.43f, 12f)
            reflectiveQuadTo(4.49f, 11.63f)
            reflectiveQuadTo(4.7f, 11.3f)
            lineTo(11.3f, 4.7f)
            quadTo(11.58f, 4.42f, 11.99f, 4.42f)
            reflectiveQuadTo(12.7f, 4.7f)
            quadTo(13f, 5f, 13f, 5.41f)
            reflectiveQuadTo(12.7f, 6.13f)
            lineTo(7.83f, 11f)
            horizontalLineTo(19f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 12f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(19f, 13f)
            horizontalLineTo(7.83f)
            close()
          }
        }
        .build()
    return _ArrowBack!!
  }

private var _ArrowBack: ImageVector? = null
