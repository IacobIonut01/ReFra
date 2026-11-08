/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.OptionPosition.ALONE

const val OptionAttentionTag = "Option.Attention"


@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    textContainer: @Composable () -> Unit,
    summaryContainer: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    attention: Boolean = false,
    position: OptionPosition = ALONE,
    onClick: () -> Unit
) {
    val mod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .background(
            color = containerColor,
            shape = position.shape()
        )
        .clip(position.shape())
        .clickable(
            enabled = enabled,
            onClick = onClick
        )
        .alpha(if (enabled) 1f else 0.4f)
        .padding(16.dp)
        .padding(vertical = 4.dp)
    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .alpha(if (enabled) 1f else 0.4f)
                    .padding(start = 4.dp, end = 12.dp)
            )
        }
        if (summaryContainer != null) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    textContainer()
                }
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    summaryContainer()
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(color = contentColor)
                ) {
                    textContainer()
                }
            }
        }
        if (attention) {
            // Same unread dot the header's overflow button shows, so the reason it is marked
            // stays visible once the menu is open.
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag(OptionAttentionTag)
            )
        }
    }
}
