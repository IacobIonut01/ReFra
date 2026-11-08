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
public val EditorSymbols.PushPin: ImageVector
  get() {
    if (_PushPin != null) {
      return _PushPin!!
    }
    _PushPin =
      ImageVector.Builder(
          name = "PushPin",
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
            // M16,12V4h1V2H7v2h1v8l-2,2v2h5v6h2v-6h5v-2z
            moveTo(16f, 12f)
            verticalLineTo(4f)
            horizontalLineToRelative(1f)
            verticalLineTo(2f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(1f)
            verticalLineToRelative(8f)
            lineToRelative(-2f, 2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-2f)
            close()
          }
        }
        .build()
    return _PushPin!!
  }

private var _PushPin: ImageVector? = null
