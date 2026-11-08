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
public val EditorSymbols.Filter: ImageVector
  get() {
    if (_Filter != null) {
      return _Filter!!
    }
    _Filter =
      ImageVector.Builder(
          name = "Filter",
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
            moveTo(15.96f, 10.29f)
            lineToRelative(-2.75f, 3.54f)
            lineToRelative(-1.96f, -2.36f)
            lineTo(8.5f, 15f)
            lineToRelative(11f, 0f)
            lineToRelative(-3.54f, -4.71f)
            close()
            moveTo(3f, 5f)
            lineTo(1f, 5f)
            lineToRelative(0f, 16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            lineToRelative(16f, 0f)
            lineToRelative(0f, -2f)
            lineTo(3f, 21f)
            lineTo(3f, 5f)
            close()
            moveToRelative(18f, -4f)
            lineTo(7f, 1f)
            curveTo(5.9f, 1f, 5f, 1.9f, 5f, 3f)
            lineToRelative(0f, 14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            lineToRelative(14f, 0f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(23f, 3f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveToRelative(0f, 16f)
            lineTo(7f, 17f)
            lineTo(7f, 3f)
            lineToRelative(14f, 0f)
            lineToRelative(0f, 14f)
            close()
          }
        }
        .build()
    return _Filter!!
  }

private var _Filter: ImageVector? = null
