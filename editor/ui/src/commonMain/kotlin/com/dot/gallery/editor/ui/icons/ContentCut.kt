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
public val EditorSymbols.ContentCut: ImageVector
  get() {
    if (_ContentCut != null) {
      return _ContentCut!!
    }
    _ContentCut =
      ImageVector.Builder(
          name = "ContentCut",
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
            moveTo(12f, 14f)
            lineTo(9.65f, 16.35f)
            quadToRelative(0.2f, 0.38f, 0.28f, 0.8f)
            quadTo(10f, 17.58f, 10f, 18f)
            quadToRelative(0f, 1.65f, -1.17f, 2.82f)
            reflectiveQuadTo(6f, 22f)
            reflectiveQuadTo(3.18f, 20.83f)
            reflectiveQuadTo(2f, 18f)
            reflectiveQuadTo(3.18f, 15.18f)
            reflectiveQuadTo(6f, 14f)
            quadToRelative(0.43f, 0f, 0.85f, 0.07f)
            reflectiveQuadToRelative(0.8f, 0.28f)
            lineTo(10f, 12f)
            lineTo(7.65f, 9.65f)
            quadTo(7.28f, 9.85f, 6.85f, 9.92f)
            reflectiveQuadTo(6f, 10f)
            quadTo(4.35f, 10f, 3.18f, 8.82f)
            reflectiveQuadTo(2f, 6f)
            reflectiveQuadTo(3.18f, 3.17f)
            reflectiveQuadTo(6f, 2f)
            reflectiveQuadTo(8.83f, 3.17f)
            reflectiveQuadTo(10f, 6f)
            quadTo(10f, 6.43f, 9.93f, 6.85f)
            reflectiveQuadTo(9.65f, 7.65f)
            lineTo(20.6f, 18.6f)
            quadToRelative(0.68f, 0.67f, 0.3f, 1.54f)
            reflectiveQuadTo(19.58f, 21f)
            quadTo(19.3f, 21f, 19.04f, 20.89f)
            reflectiveQuadTo(18.58f, 20.58f)
            lineTo(12f, 14f)
            close()
            moveToRelative(3f, -3f)
            lineTo(13f, 9f)
            lineTo(18.58f, 3.42f)
            quadToRelative(0.2f, -0.2f, 0.46f, -0.31f)
            reflectiveQuadTo(19.58f, 3f)
            quadToRelative(0.95f, 0f, 1.31f, 0.88f)
            reflectiveQuadTo(20.58f, 5.43f)
            lineTo(15f, 11f)
            close()
            moveTo(7.41f, 7.41f)
            quadTo(8f, 6.82f, 8f, 6f)
            reflectiveQuadTo(7.41f, 4.59f)
            reflectiveQuadTo(6f, 4f)
            reflectiveQuadTo(4.59f, 4.59f)
            quadTo(4f, 5.18f, 4f, 6f)
            reflectiveQuadTo(4.59f, 7.41f)
            reflectiveQuadTo(6f, 8f)
            quadTo(6.83f, 8f, 7.41f, 7.41f)
            close()
            moveToRelative(4.94f, 4.94f)
            quadTo(12.5f, 12.2f, 12.5f, 12f)
            reflectiveQuadTo(12.35f, 11.65f)
            reflectiveQuadTo(12f, 11.5f)
            reflectiveQuadToRelative(-0.35f, 0.15f)
            reflectiveQuadTo(11.5f, 12f)
            reflectiveQuadToRelative(0.15f, 0.35f)
            reflectiveQuadTo(12f, 12.5f)
            reflectiveQuadToRelative(0.35f, -0.15f)
            close()
            moveTo(7.41f, 19.41f)
            quadTo(8f, 18.83f, 8f, 18f)
            reflectiveQuadTo(7.41f, 16.59f)
            reflectiveQuadTo(6f, 16f)
            reflectiveQuadTo(4.59f, 16.59f)
            quadTo(4f, 17.18f, 4f, 18f)
            reflectiveQuadToRelative(0.59f, 1.41f)
            reflectiveQuadTo(6f, 20f)
            quadToRelative(0.83f, 0f, 1.41f, -0.59f)
            close()
          }
        }
        .build()
    return _ContentCut!!
  }

private var _ContentCut: ImageVector? = null
