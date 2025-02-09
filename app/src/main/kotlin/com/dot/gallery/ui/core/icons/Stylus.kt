package com.dot.gallery.ui.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.core.Icons

val Icons.Stylus: ImageVector
    get() {
        if (_Stylus != null) {
            return _Stylus!!
        }
        _Stylus = ImageVector.Builder(
            name = "Stylus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(167f, 840f)
                quadTo(146f, 845f, 130.5f, 829.5f)
                quadTo(115f, 814f, 120f, 793f)
                lineTo(160f, 602f)
                lineTo(358f, 800f)
                lineTo(167f, 840f)
                close()
                moveTo(358f, 800f)
                lineTo(160f, 602f)
                lineTo(618f, 144f)
                quadTo(641f, 121f, 675f, 121f)
                quadTo(709f, 121f, 732f, 144f)
                lineTo(816f, 228f)
                quadTo(839f, 251f, 839f, 285f)
                quadTo(839f, 319f, 816f, 342f)
                lineTo(358f, 800f)
                close()
                moveTo(675f, 200f)
                lineTo(261f, 614f)
                lineTo(346f, 699f)
                lineTo(760f, 285f)
                quadTo(760f, 285f, 760f, 285f)
                quadTo(760f, 285f, 760f, 285f)
                lineTo(675f, 200f)
                quadTo(675f, 200f, 675f, 200f)
                quadTo(675f, 200f, 675f, 200f)
                close()
            }
        }.build()

        return _Stylus!!
    }

@Suppress("ObjectPropertyName")
private var _Stylus: ImageVector? = null
