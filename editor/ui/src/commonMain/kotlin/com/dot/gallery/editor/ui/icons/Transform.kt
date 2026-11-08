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
public val EditorSymbols.Transform: ImageVector
  get() {
    if (_Transform != null) {
      return _Transform!!
    }
    _Transform =
      ImageVector.Builder(
          name = "Transform",
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
            moveTo(22f, 18f)
            lineTo(22f, 16f)
            lineTo(8f, 16f)
            lineTo(8f, 4f)
            lineTo(10f, 4f)
            lineTo(7f, 1f)
            lineTo(4f, 4f)
            lineTo(6f, 4f)
            lineTo(6f, 6f)
            lineTo(2f, 6f)
            lineTo(2f, 8f)
            lineTo(6f, 8f)
            lineTo(6f, 16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            lineTo(16f, 18f)
            lineTo(16f, 20f)
            lineTo(14f, 20f)
            lineTo(17f, 23f)
            lineTo(20f, 20f)
            lineTo(18f, 20f)
            lineTo(18f, 18f)
            lineTo(22f, 18f)
            close()
            moveTo(10f, 8f)
            lineTo(16f, 8f)
            lineTo(16f, 14f)
            lineTo(18f, 14f)
            lineTo(18f, 8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            lineTo(10f, 6f)
            lineTo(10f, 8f)
            close()
          }
        }
        .build()
    return _Transform!!
  }

private var _Transform: ImageVector? = null
