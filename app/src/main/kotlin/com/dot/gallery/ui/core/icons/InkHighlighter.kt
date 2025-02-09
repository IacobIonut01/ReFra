package com.dot.gallery.ui.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.core.Icons

val Icons.InkHighlighter: ImageVector
    get() {
        if (_InkHighlighter != null) {
            return _InkHighlighter!!
        }
        _InkHighlighter = ImageVector.Builder(
            name = "InkHighlighter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(544f, 560f)
                lineTo(440f, 456f)
                lineTo(240f, 656f)
                quadTo(240f, 656f, 240f, 656f)
                quadTo(240f, 656f, 240f, 656f)
                lineTo(344f, 760f)
                quadTo(344f, 760f, 344f, 760f)
                quadTo(344f, 760f, 344f, 760f)
                lineTo(544f, 560f)
                close()
                moveTo(497f, 399f)
                lineTo(601f, 503f)
                lineTo(800f, 304f)
                quadTo(800f, 304f, 800f, 304f)
                quadTo(800f, 304f, 800f, 304f)
                lineTo(696f, 200f)
                quadTo(696f, 200f, 696f, 200f)
                quadTo(696f, 200f, 696f, 200f)
                lineTo(497f, 399f)
                close()
                moveTo(413f, 371f)
                lineTo(629f, 587f)
                lineTo(400f, 816f)
                quadTo(376f, 840f, 344f, 840f)
                quadTo(312f, 840f, 288f, 816f)
                lineTo(286f, 814f)
                lineTo(260f, 840f)
                lineTo(60f, 840f)
                lineTo(186f, 714f)
                lineTo(184f, 712f)
                quadTo(160f, 688f, 160f, 656f)
                quadTo(160f, 624f, 184f, 600f)
                lineTo(413f, 371f)
                close()
                moveTo(413f, 371f)
                lineTo(640f, 144f)
                quadTo(664f, 120f, 696f, 120f)
                quadTo(728f, 120f, 752f, 144f)
                lineTo(856f, 248f)
                quadTo(880f, 272f, 880f, 304f)
                quadTo(880f, 336f, 856f, 360f)
                lineTo(629f, 587f)
                lineTo(413f, 371f)
                close()
            }
        }.build()

        return _InkHighlighter!!
    }

@Suppress("ObjectPropertyName")
private var _InkHighlighter: ImageVector? = null
