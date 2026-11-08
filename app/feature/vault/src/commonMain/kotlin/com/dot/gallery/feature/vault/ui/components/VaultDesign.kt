/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.LocalGalleryRailBackHost
import com.dot.gallery.core.presentation.components.RailBackRegistration
import com.dot.gallery.ui.theme.GalleryChrome
import com.dot.gallery.ui.theme.Spacing

/** Widest the vault pages grow before centring, matching the settings rows. */
internal val VaultContentMaxWidth = 600.dp

/** Centres a block and caps it at [VaultContentMaxWidth] on unfolded and tablet widths. */
internal fun Modifier.vaultContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = VaultContentMaxWidth)
    .fillMaxWidth()

/** Where a row sits in its group, for the 24/8 dp grouped corners. */
internal fun vaultRowPosition(index: Int, size: Int): Position = when {
    size <= 1 -> Position.Alone
    index == 0 -> Position.Top
    index == size - 1 -> Position.Bottom
    else -> Position.Middle
}

internal fun Position.groupedShape(): Shape = when (this) {
    Position.Alone -> RoundedCornerShape(24.dp)
    Position.Top -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    Position.Middle -> RoundedCornerShape(8.dp)
    Position.Bottom -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
}

/**
 * Leading Close or Back of the vault headers. Where the navigation rail is shown (unfolded
 * foldables, tablets) the rail's back button runs [onClick] instead, like every other page, and
 * nothing is drawn here; returns whether the button was drawn.
 */
@Composable
fun VaultNavigationButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Boolean {
    val railHost = LocalGalleryRailBackHost.current
    if (railHost != null) {
        RailBackRegistration(railHost, onClick)
        return false
    }
    VaultCircleButton(icon = icon, contentDescription = contentDescription, onClick = onClick, modifier = modifier)
    return true
}

/** 48 dp round tonal button used for Close and Back in the vault headers. */
@Composable
fun VaultCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(GalleryChrome.ToolbarButtonSize),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/** Segmented step indicator of the setup wizard: [step] of [total] segments filled. */
@Composable
fun VaultStepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val color by animateColorAsState(
                if (index < step) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "vaultStepSegment",
            )
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Tonal 96 dp badge, headline and body centred at the top of a wizard or empty page. */
@Composable
fun VaultHero(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    badgeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badgeIconSize: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeContentColor,
                modifier = Modifier.size(badgeIconSize),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Shared frame of every setup step: Close or Back on the left and the step segments on the right,
 * the hero, the step's [content] and a bottom action row.
 *
 * With [scrollable] off the content column has a bounded height, so the step can give a child
 * `Modifier.weight(1f)` (the keypad pages anchor the keypad to the bottom that way).
 */
@Composable
fun VaultWizardScaffold(
    icon: ImageVector,
    title: String,
    body: String?,
    navigationContentDescription: String,
    onNavigation: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector = Icons.Outlined.Close,
    step: Int? = null,
    totalSteps: Int = 4,
    scrollable: Boolean = true,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.ScreenHorizontal, vertical = GalleryChrome.ToolbarVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val drawn = onNavigation != null && VaultNavigationButton(
                    icon = navigationIcon,
                    contentDescription = navigationContentDescription,
                    onClick = onNavigation,
                )
                // Keeps the header height and pins the step segments to the end.
                if (!drawn) Box(Modifier.size(GalleryChrome.ToolbarButtonSize))
                if (step != null) VaultStepProgress(step = step, total = totalSteps)
            }
        },
        bottomBar = {
            if (bottomBar != null) {
                Row(
                    modifier = Modifier
                        .vaultContentWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = Spacing.Medium, end = Spacing.Medium, top = 12.dp, bottom = Spacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    content = bottomBar,
                )
            } else {
                Box(Modifier.navigationBarsPadding())
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .vaultContentWidth()
                .padding(start = Spacing.Medium, end = Spacing.Medium, top = Spacing.Small, bottom = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            VaultHero(icon = icon, title = title, body = body, modifier = Modifier.padding(top = Spacing.Medium))
            content()
        }
    }
}

/** What a [VaultRow] shows at its end. */
sealed interface VaultRowTrailing {
    data object None : VaultRowTrailing
    data object Chevron : VaultRowTrailing
    data class Radio(val selected: Boolean) : VaultRowTrailing
    data class Switch(val checked: Boolean) : VaultRowTrailing
}

/**
 * One row of a grouped list in the Intelligence Hub style: icon, title, optional summary and
 * a primary status line, grouped corners from [position], and a radio, switch or chevron.
 * A selected radio row takes the secondary container.
 */
@Composable
fun VaultRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    status: String? = null,
    position: Position = Position.Alone,
    trailing: VaultRowTrailing = VaultRowTrailing.None,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
) {
    val selected = trailing is VaultRowTrailing.Radio && trailing.selected
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else containerColor,
        label = "vaultRowContainer",
    )
    val gap = when (position) {
        Position.Alone -> PaddingValues(0.dp)
        Position.Top -> PaddingValues(bottom = 1.dp)
        Position.Middle -> PaddingValues(vertical = 1.dp)
        Position.Bottom -> PaddingValues(top = 1.dp)
    }
    val interaction = when {
        onClick == null -> Modifier
        trailing is VaultRowTrailing.Radio ->
            Modifier.selectable(selected = trailing.selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        trailing is VaultRowTrailing.Switch ->
            Modifier.toggleable(value = trailing.checked, enabled = enabled, role = Role.Switch) { onClick() }
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(gap)
            .clip(position.groupedShape())
            .background(container)
            .then(interaction)
            .heightIn(min = 72.dp)
            .padding(
                start = 16.dp,
                end = if (trailing is VaultRowTrailing.Switch || trailing == VaultRowTrailing.None) 16.dp else 8.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = (if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = contentAlpha),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                )
            }
        }
        when (trailing) {
            VaultRowTrailing.None -> Unit
            VaultRowTrailing.Chevron -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            is VaultRowTrailing.Radio -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                RadioButton(selected = trailing.selected, onClick = null, enabled = enabled)
            }
            is VaultRowTrailing.Switch -> Switch(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** Small label above a group of rows, lined up with the row text. */
@Composable
fun VaultSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Small, end = Spacing.Small, top = Spacing.Medium, bottom = Spacing.Small)
            .semantics { heading() },
    )
}

enum class VaultButtonStyle { Filled, Tonal, Text, Error }

/** 48 dp pill button in the vault styles. */
@Composable
fun VaultButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VaultButtonStyle = VaultButtonStyle.Filled,
    enabled: Boolean = true,
) {
    val height = Modifier.heightIn(min = 48.dp)
    val padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    val label: @Composable RowScope.() -> Unit = {
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    when (style) {
        VaultButtonStyle.Filled -> Button(onClick, modifier.then(height), enabled, contentPadding = padding, content = label)
        VaultButtonStyle.Tonal -> FilledTonalButton(onClick, modifier.then(height), enabled, contentPadding = padding, content = label)
        VaultButtonStyle.Text -> TextButton(onClick, modifier.then(height), enabled, contentPadding = PaddingValues(horizontal = 16.dp), content = label)
        VaultButtonStyle.Error -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(height),
            enabled = enabled,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            content = label,
        )
    }
}
