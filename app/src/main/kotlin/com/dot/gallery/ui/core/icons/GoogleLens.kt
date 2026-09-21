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
 * The true Google Lens glyph — five brand-colored strokes (blue lens, green flash dot,
 * red/yellow/blue camera outline). It carries intrinsic colors: hosts must NOT tint it
 * (`ColorFilter.tint`/`Icon` tint would flatten it to one color).
 */
val Icons.GoogleLens: ImageVector
    get() {
        if (_GoogleLens != null) {
            return _GoogleLens!!
        }
        _GoogleLens = ImageVector.Builder(
            name = "GoogleLens",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            // Camera body — blue top-left stroke
            addPath(
                pathData = addPathNodes(
                    "M112,24l-32,0L68,40H56.8C38.69,40,24,54.69,24,72.8V92h16V74" +
                        "c0-9.71,7.2-18,16-18h80L112,24z"
                ),
                fill = SolidColor(Color(0xFF4285F4)),
            )
            // Camera body — yellow top-right stroke
            addPath(
                pathData = addPathNodes(
                    "M168,72.8c0-18.11-14.69-32.8-32.8-32.8H116l20,16" +
                        "c8.8,0,16,8.29,16,18v30h16V72.8z"
                ),
                fill = SolidColor(Color(0xFFFBBC05)),
            )
            // Camera body — red bottom-left stroke
            addPath(
                pathData = addPathNodes(
                    "M24,135.2c0,18.11,14.69,32.8,32.8,32.8H96v-16l-40.1-0.1" +
                        "c-8.8,0-15.9-8.19-15.9-17.9v-18H24V135.2z"
                ),
                fill = SolidColor(Color(0xFFEA4335)),
            )
            // Lens — blue circle at (96.07, 104) r=24
            addPath(
                pathData = addPathNodes(
                    "M72.07,104a24,24 0 1,1 48,0a24,24 0 1,1 -48,0z"
                ),
                fill = SolidColor(Color(0xFF4285F4)),
            )
            // Flash dot — green circle at (144.07, 144) r=16
            addPath(
                pathData = addPathNodes(
                    "M128.07,144a16,16 0 1,1 32,0a16,16 0 1,1 -32,0z"
                ),
                fill = SolidColor(Color(0xFF34A853)),
            )
        }.build()

        return _GoogleLens!!
    }

@Suppress("ObjectPropertyName")
private var _GoogleLens: ImageVector? = null
