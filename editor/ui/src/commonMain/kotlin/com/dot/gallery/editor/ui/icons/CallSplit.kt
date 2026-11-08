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
public val EditorSymbols.CallSplit: ImageVector
  get() {
    if (_CallSplit != null) {
      return _CallSplit!!
    }
    _CallSplit =
      ImageVector.Builder(
          name = "CallSplit",
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
            moveTo(14f, 4f)
            lineToRelative(2.29f, 2.29f)
            lineToRelative(-2.88f, 2.88f)
            lineToRelative(1.42f, 1.42f)
            lineToRelative(2.88f, -2.88f)
            lineTo(20f, 10f)
            verticalLineTo(4f)
            horizontalLineTo(14f)
            close()
            moveTo(10f, 4f)
            horizontalLineTo(4f)
            verticalLineToRelative(6f)
            lineToRelative(2.29f, -2.29f)
            lineToRelative(4.71f, 4.7f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-8.41f)
            lineToRelative(-5.29f, -5.3f)
            lineTo(10f, 4f)
            close()
          }
        }
        .build()
    return _CallSplit!!
  }

private var _CallSplit: ImageVector? = null
