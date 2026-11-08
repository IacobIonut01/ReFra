package com.dot.gallery.editor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A tapered quad frame — the Perspective tool's glyph in the project's
// Material Symbols style (24dp viewport, 2dp frame).
@Suppress("CheckReturnValue")
public val EditorSymbols.Perspective: ImageVector
  get() {
    if (_Perspective != null) {
      return _Perspective!!
    }
    _Perspective =
      ImageVector.Builder(
          name = "Perspective",
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
            pathFillType = PathFillType.Companion.EvenOdd,
          ) {
            // Outer trapezoid — wide top edge tapering to a narrow
            // bottom, the receding-plane read.
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(15.5f, 20f)
            lineTo(8.5f, 20f)
            close()
            // Inner cut-out leaves a uniform ~2 dp frame.
            moveTo(5.15f, 6f)
            lineTo(18.85f, 6f)
            lineTo(14.4f, 18f)
            lineTo(9.6f, 18f)
            close()
          }
        }
        .build()
    return _Perspective!!
  }

private var _Perspective: ImageVector? = null
