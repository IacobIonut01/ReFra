/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.help.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.PreviewHost

@Composable
fun WhatsNewHeroCard(
    versionName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer

    val transition = rememberInfiniteTransition()
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000),
            repeatMode = RepeatMode.Reverse
        )
    )
    val cornerRadius = 24.dp

    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .drawWithCache {
                val cx = size.width - size.width * fraction
                val cy = size.height * fraction

                val gradient = Brush.radialGradient(
                    colors = listOf(colorPrimary, colorTertiary),
                    center = Offset(cx, cy),
                    radius = 800f
                )

                onDrawBehind {
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(
                            cornerRadius.toPx(),
                            cornerRadius.toPx()
                        )
                    )
                }
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .padding(all = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.help_whats_new),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.help_whats_new_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WhatsNewHeroCardPreview() {
    PreviewHost {
        WhatsNewHeroCard(
            versionName = "4.2.0",
            onClick = {}
        )
    }
}
