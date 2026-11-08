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
public val EditorSymbols.Timeline: ImageVector
  get() {
    if (_Timeline != null) {
      return _Timeline!!
    }
    _Timeline =
      ImageVector.Builder(
          name = "Timeline",
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
            moveTo(3f, 18f)
            quadTo(2.18f, 18f, 1.59f, 17.41f)
            reflectiveQuadTo(1f, 16f)
            reflectiveQuadTo(1.59f, 14.59f)
            reflectiveQuadTo(3f, 14f)
            quadToRelative(0.15f, 0f, 0.26f, 0f)
            reflectiveQuadTo(3.5f, 14.05f)
            lineTo(8.05f, 9.5f)
            quadTo(8f, 9.38f, 8f, 9.26f)
            reflectiveQuadTo(8f, 9f)
            quadTo(8f, 8.17f, 8.59f, 7.59f)
            reflectiveQuadTo(10f, 7f)
            reflectiveQuadToRelative(1.41f, 0.59f)
            reflectiveQuadTo(12f, 9f)
            quadToRelative(0f, 0.05f, -0.05f, 0.5f)
            lineToRelative(2.55f, 2.55f)
            quadTo(14.63f, 12f, 14.74f, 12f)
            reflectiveQuadTo(15f, 12f)
            reflectiveQuadToRelative(0.26f, 0f)
            reflectiveQuadToRelative(0.24f, 0.05f)
            lineTo(19.05f, 8.5f)
            quadTo(19f, 8.38f, 19f, 8.26f)
            reflectiveQuadTo(19f, 8f)
            quadTo(19f, 7.18f, 19.59f, 6.59f)
            reflectiveQuadTo(21f, 6f)
            reflectiveQuadToRelative(1.41f, 0.59f)
            quadTo(23f, 7.18f, 23f, 8f)
            reflectiveQuadTo(22.41f, 9.41f)
            reflectiveQuadTo(21f, 10f)
            quadToRelative(-0.15f, 0f, -0.26f, 0f)
            reflectiveQuadTo(20.5f, 9.95f)
            lineTo(16.95f, 13.5f)
            quadTo(17f, 13.63f, 17f, 13.74f)
            reflectiveQuadTo(17f, 14f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(15f, 16f)
            reflectiveQuadTo(13.59f, 15.41f)
            reflectiveQuadTo(13f, 14f)
            quadToRelative(0f, -0.15f, 0f, -0.26f)
            quadToRelative(0f, -0.11f, 0.05f, -0.24f)
            lineTo(10.5f, 10.95f)
            quadTo(10.38f, 11f, 10.26f, 11f)
            reflectiveQuadTo(10f, 11f)
            quadTo(9.95f, 11f, 9.5f, 10.95f)
            lineTo(4.95f, 15.5f)
            quadTo(5f, 15.63f, 5f, 15.74f)
            reflectiveQuadTo(5f, 16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(3f, 18f)
            close()
          }
        }
        .build()
    return _Timeline!!
  }

private var _Timeline: ImageVector? = null
