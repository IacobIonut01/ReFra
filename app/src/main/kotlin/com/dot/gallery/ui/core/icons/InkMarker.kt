package com.dot.gallery.ui.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.core.Icons

val Icons.InkMarker: ImageVector
    get() {
        if (_InkMarker != null) {
            return _InkMarker!!
        }
        _InkMarker = ImageVector.Builder(
            name = "InkMarker",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(272f, 856f)
                lineTo(234f, 818f)
                lineTo(192f, 860f)
                quadTo(173f, 879f, 145.5f, 879.5f)
                quadTo(118f, 880f, 100f, 860f)
                quadTo(81f, 841f, 81f, 814f)
                quadTo(81f, 787f, 100f, 768f)
                lineTo(142f, 726f)
                lineTo(104f, 686f)
                lineTo(658f, 132f)
                quadTo(670f, 120f, 687f, 120f)
                quadTo(704f, 120f, 716f, 132f)
                lineTo(828f, 244f)
                quadTo(840f, 256f, 840f, 273f)
                quadTo(840f, 290f, 828f, 302f)
                lineTo(272f, 856f)
                close()
                moveTo(444f, 460f)
                lineTo(216f, 686f)
                lineTo(274f, 744f)
                lineTo(500f, 516f)
                lineTo(444f, 460f)
                close()
            }
        }.build()

        return _InkMarker!!
    }

@Suppress("ObjectPropertyName")
private var _InkMarker: ImageVector? = null
