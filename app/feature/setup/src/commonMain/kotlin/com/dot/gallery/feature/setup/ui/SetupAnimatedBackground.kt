/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dot.gallery.ui.theme.GalleryGlass

/**
 * The setup backdrop: four soft colour blobs (primary, tertiary and secondary containers plus a
 * primary accent) under a light scrim that is strongest at the top and bottom edges. Each blob is
 * a radial gradient that fades to transparent, so no blur pass is needed. The blobs drift to new
 * positions on every [step].
 */
@Composable
fun SetupAnimatedBackground(modifier: Modifier = Modifier, step: Int = 0) {
    val base = MaterialTheme.colorScheme.surface
    val blobs = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    )
    val layout = BlobLayouts[step.mod(BlobLayouts.size)]
    val animated = layout.mapIndexed { index, target ->
        val position by animateOffsetAsState(
            targetValue = target,
            animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
            label = "setup-blob-$index",
        )
        position
    }
    val scrimEdge = GalleryGlass.scrimEdge
    val scrimMid = GalleryGlass.scrimMid

    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(base)
                .drawBehind {
                    blobs.forEachIndexed { index, color ->
                        drawBlob(color, animated[index], radiusFraction = if (index == 3) 0.45f else 0.7f)
                    }
                }
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrimEdge,
                        0.35f to scrimMid,
                        0.65f to scrimMid,
                        1f to scrimEdge,
                    )
                )
        )
    }
}

/** A blob centred at [center] (fractions of the size) that fades out at its edge. */
private fun DrawScope.drawBlob(color: Color, center: Offset, radiusFraction: Float) {
    val c = Offset(center.x * size.width, center.y * size.height)
    val radius = size.minDimension * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            0f to color,
            0.45f to color.copy(alpha = color.alpha * 0.6f),
            1f to color.copy(alpha = 0f),
            center = c,
            radius = radius,
        ),
        radius = radius,
        center = c,
    )
}

/** Blob centres per step, as fractions of the screen; the order matches the colours above. */
private val BlobLayouts = listOf(
    listOf(Offset(0.15f, 0.12f), Offset(0.95f, 0.45f), Offset(0.2f, 0.85f), Offset(0.85f, 0.15f)),
    listOf(Offset(0.85f, 0.1f), Offset(0.1f, 0.5f), Offset(0.9f, 0.8f), Offset(0.2f, 0.25f)),
    listOf(Offset(0.2f, 0.4f), Offset(0.9f, 0.1f), Offset(0.15f, 0.9f), Offset(0.8f, 0.6f)),
    listOf(Offset(0.9f, 0.35f), Offset(0.2f, 0.15f), Offset(0.75f, 0.9f), Offset(0.1f, 0.65f)),
    listOf(Offset(0.3f, 0.15f), Offset(0.8f, 0.75f), Offset(0.1f, 0.55f), Offset(0.9f, 0.3f)),
    listOf(Offset(0.5f, 0.3f), Offset(0.15f, 0.8f), Offset(0.9f, 0.6f), Offset(0.35f, 0.05f)),
)
