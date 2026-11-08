package com.dot.gallery.editor.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Guided upright: a vertical and a horizontal guide segment with their
 * endpoint grips. Drawn for this editor (stroke 2 on 24).
 */
public val EditorSymbols.StraightGuide: ImageVector
  get() {
    _StraightGuide?.let { return it }
    val icon = ImageVector.Builder(
        name = "StraightGuide",
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
          moveTo(8f, 5.5f)
          verticalLineTo(18.5f)
          moveTo(10.5f, 16f)
          horizontalLineTo(18.5f)
        }
        path(fill = SolidColor(Color.Black)) {
          // Endpoint grips.
          moveTo(8f, 2f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close()
          moveTo(8f, 18f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close()
          moveTo(20f, 14f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close()
        }
      }
      .build()
    _StraightGuide = icon
    return icon
  }

private var _StraightGuide: ImageVector? = null
