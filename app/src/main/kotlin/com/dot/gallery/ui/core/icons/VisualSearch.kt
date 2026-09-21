/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.ui.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.core.Icons

/**
 * Generic visual-search glyph (camera outline + lens + flash dot), monochrome on purpose —
 * hosts tint it through `ColorFilter`/`Icon` tint. Used as the provider icon for every
 * visual-search target that isn't Google Lens itself.
 */
val Icons.VisualSearch: ImageVector
    get() {
        if (_VisualSearch != null) {
            return _VisualSearch!!
        }
        _VisualSearch = ImageVector.Builder(
            name = "VisualSearch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M12 17.333q-1.667 0-2.833-1.166Q8 15 8 13.333q0-1.666 1.167-2.833" +
                        "Q10.333 9.333 12 9.333q1.667 0 2.833 1.167Q16 11.667 16 13.333" +
                        "q0 1.667-1.167 2.834-1.166 1.166-2.833 1.166Zm8 5.334q-1.1 0" +
                        "-1.883-.784-.784-.783-.784-1.883t.784-1.883q.783-.784 1.883-.784" +
                        "t1.883.784q.784.783.784 1.883t-.784 1.883q-.783.784-1.883.784Z" +
                        "M5.333 24q-2.2 0-3.766-1.567Q0 20.867 0 18.667V16h2.667v2.667" +
                        "q0 1.1.783 1.883.783.783 1.883.783H12V24Zm16-10.667V8q0-1.1" +
                        "-.783-1.883-.783-.784-1.883-.784H5.333q-1.1 0-1.883.784Q2.667 6.9" +
                        " 2.667 8v4H0V8q0-2.2 1.567-3.767 1.566-1.566 3.766-1.566H8L9.333 0" +
                        "h5.334L16 2.667h2.667q2.2 0 3.766 1.566Q24 5.8 24 8v5.333z"
                ),
                fill = SolidColor(Color(0xFF000000)),
            )
        }.build()

        return _VisualSearch!!
    }

@Suppress("ObjectPropertyName")
private var _VisualSearch: ImageVector? = null
