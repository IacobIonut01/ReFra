/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.ui.components.SettingsItem
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Pushed Backup & Sync page in the Library style: [GalleryPageTopBar] over a [LazyColumn] whose
 * rows line up with the shared [SettingsItem] groups. [bottomBar] stays pinned under the list.
 * The list scrolls under the bar, which turns into the blur bar once it does.
 */
@Composable
fun CloudPage(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    Scaffold(
        modifier = modifier,
        topBar = {
            GalleryPageTopBar(
                title = title,
                count = count,
                onBack = onBack,
                actions = actions,
                hazeState = hazeState,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            content = content,
        )
    }
}

/**
 * Lines a custom block up with the [SettingsItem] rows around it: centred and capped at the same
 * 600 dp visible width (632 dp minus the rows' 16 dp side padding).
 */
fun Modifier.cloudContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = 632.dp)
    .fillMaxWidth()
    .padding(horizontal = 16.dp)

fun positionOf(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/** Category title between groups, with an optional explanation under it. */
@Composable
fun CloudSectionLabel(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsItem(item = SettingsEntity.Header(title = title))
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .cloudContentWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

/** Colour pair for a summary card or badge. */
data class CloudTone(val container: Color, val content: Color, val accent: Color, val onAccent: Color)

object CloudTones {
    val primary: CloudTone
        @Composable get() = with(MaterialTheme.colorScheme) {
            CloudTone(primaryContainer, onPrimaryContainer, primary, onPrimary)
        }
    val tertiary: CloudTone
        @Composable get() = with(MaterialTheme.colorScheme) {
            CloudTone(tertiaryContainer, onTertiaryContainer, tertiary, onTertiary)
        }
    val secondary: CloudTone
        @Composable get() = with(MaterialTheme.colorScheme) {
            CloudTone(secondaryContainer, onSecondaryContainer, secondary, onSecondary)
        }
    val error: CloudTone
        @Composable get() = with(MaterialTheme.colorScheme) {
            CloudTone(errorContainer, onErrorContainer, error, onError)
        }
}

/**
 * The coloured card that opens a Backup & Sync page: a round badge, a title and a body line, and
 * any [content] (stats, progress, actions) under them.
 */
@Composable
fun CloudSummaryCard(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    tone: CloudTone = CloudTones.primary,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .cloudContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(tone.container)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (badge != null) {
                badge()
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tone.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tone.onAccent)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = tone.content)
                if (body != null) {
                    Text(text = body, style = MaterialTheme.typography.bodySmall, color = tone.content)
                }
            }
        }
        content()
    }
}

/** A value and its label on the lowest surface, used in rows of two inside a summary card. */
@Composable
fun CloudStat(value: String, label: String, modifier: Modifier = Modifier) {
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

/** Rounded progress bar; [progress] null draws the indeterminate form. */
@Composable
fun CloudProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val barModifier = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(height / 2))
    if (progress == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
            gapSize = 0.dp,
        )
    } else {
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "cloudProgress")
        Box(modifier = barModifier.background(trackColor)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(height / 2))
                    .background(color)
            )
        }
    }
}

/** The tinted 48 dp square that carries a provider's mark in account rows. */
@Composable
fun ProviderBadge(providerType: ProviderType, modifier: Modifier = Modifier, tone: CloudTone = toneFor(providerType)) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tone.container),
        contentAlignment = Alignment.Center
    ) {
        ProviderBrandIcon(providerType = providerType, tint = tone.content, modifier = Modifier.size(24.dp))
    }
}

/** Photo servers use the primary tone, cloud storage tertiary and home-network shares secondary. */
@Composable
fun toneFor(providerType: ProviderType): CloudTone = when {
    ProviderUiDescriptors.forType(providerType).isLanOnly -> CloudTones.secondary
    providerType == ProviderType.IMMICH -> CloudTones.primary
    else -> CloudTones.tertiary
}

/** A quiet icon + text line under a page, like the Intelligence Hub privacy note. */
@Composable
fun CloudNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .cloudContentWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
