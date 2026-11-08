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
public val EditorSymbols.GraphicEq: ImageVector
  get() {
    if (_GraphicEq != null) {
      return _GraphicEq!!
    }
    _GraphicEq =
      ImageVector.Builder(
          name = "GraphicEq",
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
            moveTo(7f, 18f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineTo(7f)
            verticalLineToRelative(12f)
            close()
            moveTo(11f, 22f)
            horizontalLineToRelative(2f)
            verticalLineTo(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(20f)
            close()
            moveTo(3f, 14f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineTo(3f)
            verticalLineToRelative(4f)
            close()
            moveTo(15f, 18f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(12f)
            close()
            moveTo(19f, 10f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(-2f)
            close()
          }
        }
        .build()
    return _GraphicEq!!
  }

private var _GraphicEq: ImageVector? = null
