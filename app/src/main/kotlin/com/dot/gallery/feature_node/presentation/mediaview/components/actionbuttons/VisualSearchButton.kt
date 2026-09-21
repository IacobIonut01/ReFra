/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.feature_node.domain.model.Media

/**
 * Quick-actions-bar entry point for visual search. The caller resolves the provider so the icon
 * and label already reflect it (Lens glyph + "Search with Google Lens", or the generic
 * AI-search icon + "Visual search").
 */
@Composable
fun <T : Media> VisualSearchButton(
    media: T,
    enabled: Boolean,
    followTheme: Boolean,
    icon: ImageVector,
    title: String,
    // False for the Lens glyph so its brand colors survive inside the themed pill.
    tintIcon: Boolean = true,
    onItemClick: (T) -> Unit,
) {
    MediaViewButton(
        currentMedia = media,
        imageVector = icon,
        title = title,
        followTheme = followTheme,
        enabled = enabled,
        tintIcon = tintIcon,
        // The generic glyph fills its viewport edge-to-edge while Material icons keep ~2dp
        // of padding — shrink it to the standard live area or it reads oversized in the pill.
        iconModifier = if (tintIcon) Modifier.scale(0.83f) else Modifier,
        onItemClick = onItemClick,
    )
}
