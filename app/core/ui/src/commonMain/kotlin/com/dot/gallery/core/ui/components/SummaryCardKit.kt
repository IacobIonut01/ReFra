/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Lines a custom block up with the [SettingsItem] rows around it: centred and capped at the same
 * 600 dp visible width, with the rows' 16 dp side margin.
 */
fun Modifier.settingsContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = 632.dp)
    .fillMaxWidth()
    .padding(horizontal = 16.dp)

/**
 * The kit's "Summary card": the one coloured card at the top of a hub, on `primaryContainer`. A
 * 48 dp `primary` badge ([icon], or a custom [badge]), a title and body, then [content] (a row of
 * [StatTile]s, progress, up to two [SummaryCardButton]s).
 */
@Composable
fun SummaryCard(
    icon: ImageVector?,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badge: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    bodyModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .settingsContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                badge != null -> badge()
                icon != null -> SummaryCardBadge(icon = icon)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (titleTrailing != null) {
                        Box(modifier = Modifier.alignByBaseline()) { titleTrailing() }
                    }
                }
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = bodyModifier,
                    )
                }
            }
        }
        content()
    }
}

/** The round `primary` badge that opens a [SummaryCard]. */
@Composable
fun SummaryCardBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** The kit's "StatTile": a value and its label on the lowest surface, inside a [SummaryCard]. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A 48 dp pill button for a [SummaryCard]: [filled] is the card's one primary action, otherwise
 * it is the white Tonal button on the lowest surface.
 */
@Composable
fun SummaryCardButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val buttonModifier = modifier.heightIn(min = 48.dp)
    val shape = RoundedCornerShape(24.dp)
    val buttonContent: @Composable () -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        // Wraps rather than clipping when two buttons share a narrow card at large font scales.
        Text(text = label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    if (filled) {
        Button(onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape) {
            buttonContent()
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            buttonContent()
        }
    }
}

/** The kit's "Footnote": one quiet icon + 12 sp line at the end of a page. */
@Composable
fun Footnote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .settingsContentWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The kit's full-width destructive pill (Error), placed after a 24 dp spacer at a page's end. */
@Composable
fun DestructivePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .settingsContentWidth()
            .padding(top = 24.dp)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
