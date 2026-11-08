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
public val EditorSymbols.AddPhotoAlternate: ImageVector
  get() {
    if (_AddPhotoAlternate != null) {
      return _AddPhotoAlternate!!
    }
    _AddPhotoAlternate =
      ImageVector.Builder(
          name = "AddPhotoAlternate",
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
            moveTo(5f, 21f)
            quadTo(4.175f, 21f, 3.587f, 20.413f)
            quadTo(3f, 19.825f, 3f, 19f)
            lineTo(3f, 5f)
            quadTo(3f, 4.175f, 3.587f, 3.587f)
            quadTo(4.175f, 3f, 5f, 3f)
            lineTo(13f, 3f)
            quadTo(13.425f, 3f, 13.713f, 3.288f)
            quadTo(14f, 3.575f, 14f, 4f)
            quadTo(14f, 4.425f, 13.713f, 4.713f)
            quadTo(13.425f, 5f, 13f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 11f)
            quadTo(19f, 10.575f, 19.288f, 10.287f)
            quadTo(19.575f, 10f, 20f, 10f)
            quadTo(20.425f, 10f, 20.712f, 10.287f)
            quadTo(21f, 10.575f, 21f, 11f)
            lineTo(21f, 19f)
            quadTo(21f, 19.825f, 20.413f, 20.413f)
            quadTo(19.825f, 21f, 19f, 21f)
            lineTo(5f, 21f)
            close()
            moveTo(17f, 7f)
            lineTo(16f, 7f)
            quadTo(15.575f, 7f, 15.287f, 6.713f)
            quadTo(15f, 6.425f, 15f, 6f)
            quadTo(15f, 5.575f, 15.287f, 5.287f)
            quadTo(15.575f, 5f, 16f, 5f)
            lineTo(17f, 5f)
            lineTo(17f, 4f)
            quadTo(17f, 3.575f, 17.288f, 3.288f)
            quadTo(17.575f, 3f, 18f, 3f)
            quadTo(18.425f, 3f, 18.712f, 3.288f)
            quadTo(19f, 3.575f, 19f, 4f)
            lineTo(19f, 5f)
            lineTo(20f, 5f)
            quadTo(20.425f, 5f, 20.712f, 5.287f)
            quadTo(21f, 5.575f, 21f, 6f)
            quadTo(21f, 6.425f, 20.712f, 6.713f)
            quadTo(20.425f, 7f, 20f, 7f)
            lineTo(19f, 7f)
            lineTo(19f, 8f)
            quadTo(19f, 8.425f, 18.712f, 8.713f)
            quadTo(18.425f, 9f, 18f, 9f)
            quadTo(17.575f, 9f, 17.288f, 8.713f)
            quadTo(17f, 8.425f, 17f, 8f)
            lineTo(17f, 7f)
            close()
            moveTo(11.25f, 16f)
            lineTo(9.4f, 13.525f)
            quadTo(9.25f, 13.325f, 9f, 13.325f)
            quadTo(8.75f, 13.325f, 8.6f, 13.525f)
            lineTo(6.6f, 16.2f)
            quadTo(6.4f, 16.45f, 6.55f, 16.725f)
            quadTo(6.7f, 17f, 7f, 17f)
            lineTo(17f, 17f)
            quadTo(17.3f, 17f, 17.45f, 16.725f)
            quadTo(17.6f, 16.45f, 17.4f, 16.2f)
            lineTo(14.65f, 12.525f)
            quadTo(14.5f, 12.325f, 14.25f, 12.325f)
            quadTo(14f, 12.325f, 13.85f, 12.525f)
            lineTo(11.25f, 16f)
            close()
          }
        }
        .build()
    return _AddPhotoAlternate!!
  }

private var _AddPhotoAlternate: ImageVector? = null
