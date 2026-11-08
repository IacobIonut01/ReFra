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
public val EditorSymbols.RotateLeft: ImageVector
  get() {
    if (_RotateLeft != null) {
      return _RotateLeft!!
    }
    _RotateLeft =
      ImageVector.Builder(
          name = "RotateLeft",
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
            moveTo(4.25f, 12.05f)
            quadToRelative(-0.52f, 0f, -0.84f, -0.38f)
            reflectiveQuadTo(3.23f, 10.8f)
            quadTo(3.38f, 10.17f, 3.63f, 9.6f)
            quadTo(3.88f, 9.02f, 4.2f, 8.48f)
            quadTo(4.45f, 8.05f, 4.94f, 8f)
            reflectiveQuadTo(5.8f, 8.3f)
            quadTo(6.03f, 8.52f, 6.08f, 8.86f)
            reflectiveQuadTo(5.95f, 9.48f)
            quadTo(5.7f, 9.9f, 5.51f, 10.36f)
            reflectiveQuadTo(5.2f, 11.3f)
            quadTo(5.13f, 11.63f, 4.84f, 11.84f)
            reflectiveQuadTo(4.25f, 12.05f)
            close()
            moveToRelative(6.7f, 8.7f)
            quadToRelative(0f, 0.55f, -0.38f, 0.85f)
            reflectiveQuadTo(9.7f, 21.78f)
            quadTo(9.1f, 21.6f, 8.53f, 21.36f)
            reflectiveQuadTo(7.38f, 20.8f)
            quadTo(6.95f, 20.55f, 6.9f, 20.06f)
            reflectiveQuadTo(7.2f, 19.2f)
            quadTo(7.43f, 18.98f, 7.76f, 18.93f)
            reflectiveQuadToRelative(0.61f, 0.13f)
            quadTo(8.8f, 19.3f, 9.26f, 19.49f)
            reflectiveQuadTo(10.2f, 19.8f)
            quadToRelative(0.32f, 0.07f, 0.54f, 0.36f)
            reflectiveQuadToRelative(0.21f, 0.59f)
            close()
            moveTo(5.8f, 17.8f)
            quadTo(5.43f, 18.15f, 4.94f, 18.1f)
            reflectiveQuadTo(4.2f, 17.63f)
            quadTo(3.88f, 17.05f, 3.64f, 16.48f)
            reflectiveQuadTo(3.23f, 15.3f)
            quadTo(3.1f, 14.8f, 3.4f, 14.43f)
            reflectiveQuadTo(4.25f, 14.05f)
            quadToRelative(0.33f, 0f, 0.6f, 0.21f)
            reflectiveQuadTo(5.2f, 14.8f)
            quadToRelative(0.13f, 0.48f, 0.31f, 0.94f)
            reflectiveQuadToRelative(0.44f, 0.89f)
            quadToRelative(0.17f, 0.27f, 0.13f, 0.63f)
            reflectiveQuadTo(5.8f, 17.8f)
            close()
            moveToRelative(8.38f, 3.95f)
            quadToRelative(-0.5f, 0.13f, -0.86f, -0.18f)
            reflectiveQuadTo(12.95f, 20.75f)
            quadToRelative(0f, -0.32f, 0.21f, -0.6f)
            quadTo(13.38f, 19.88f, 13.7f, 19.8f)
            quadTo(16f, 19.2f, 17.48f, 17.34f)
            quadToRelative(1.47f, -1.86f, 1.47f, -4.29f)
            quadToRelative(0f, -2.92f, -2.04f, -4.96f)
            reflectiveQuadTo(11.95f, 6.05f)
            horizontalLineToRelative(-0.2f)
            lineToRelative(0.9f, 0.9f)
            quadToRelative(0.28f, 0.27f, 0.28f, 0.7f)
            reflectiveQuadToRelative(-0.28f, 0.7f)
            reflectiveQuadToRelative(-0.7f, 0.28f)
            reflectiveQuadTo(11.25f, 8.35f)
            lineTo(8.65f, 5.75f)
            quadTo(8.5f, 5.6f, 8.44f, 5.43f)
            reflectiveQuadTo(8.38f, 5.05f)
            reflectiveQuadTo(8.44f, 4.67f)
            reflectiveQuadTo(8.65f, 4.35f)
            lineToRelative(2.58f, -2.6f)
            quadToRelative(0.3f, -0.28f, 0.72f, -0.28f)
            reflectiveQuadToRelative(0.7f, 0.28f)
            quadToRelative(0.3f, 0.3f, 0.3f, 0.72f)
            reflectiveQuadToRelative(-0.28f, 0.7f)
            lineTo(11.8f, 4.05f)
            horizontalLineToRelative(0.15f)
            quadToRelative(3.75f, 0f, 6.37f, 2.63f)
            reflectiveQuadToRelative(2.63f, 6.37f)
            quadToRelative(0f, 3.1f, -1.9f, 5.5f)
            reflectiveQuadToRelative(-4.88f, 3.2f)
            close()
          }
        }
        .build()
    return _RotateLeft!!
  }

private var _RotateLeft: ImageVector? = null
