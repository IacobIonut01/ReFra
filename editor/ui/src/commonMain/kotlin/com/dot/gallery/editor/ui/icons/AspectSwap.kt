package com.dot.gallery.editor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Crop orientation swap: a landscape frame over a portrait frame sharing
 * the bottom-left corner. Drawn for this editor (stroke 2 on 24).
 */
public val EditorSymbols.AspectSwap: ImageVector
  get() {
    _AspectSwap?.let { return it }
    val icon = ImageVector.Builder(
        name = "AspectSwap",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .apply {
        path(
          fill = null,
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 2f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        ) {
          // Portrait frame.
          moveTo(5f, 4f)
          horizontalLineTo(13f)
          verticalLineTo(20f)
          horizontalLineTo(5f)
          close()
          // Landscape frame.
          moveTo(5f, 12f)
          horizontalLineTo(20f)
          verticalLineTo(20f)
          horizontalLineTo(13f)
          // Turn arrow.
          moveTo(16f, 4f)
          quadTo(20f, 4f, 20f, 8f)
          moveTo(18.5f, 6.5f)
          lineTo(20f, 8f)
          lineTo(21.5f, 6.5f)
        }
      }
      .build()
    _AspectSwap = icon
    return icon
  }

private var _AspectSwap: ImageVector? = null
