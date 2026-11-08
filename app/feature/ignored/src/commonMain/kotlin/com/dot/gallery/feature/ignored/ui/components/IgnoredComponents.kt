/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.domain.model.Album
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.RegularExpression
import com.dot.gallery.ui.theme.ComponentSize
import com.dot.gallery.ui.theme.Spacing

internal fun positionOf(index: Int, count: Int): Position = when {
    count == 1 -> Position.Alone
    index == 0 -> Position.Top
    index == count - 1 -> Position.Bottom
    else -> Position.Middle
}

/** Grouped-list corners shared with the Intelligence Hub: 24 dp outside, 8 dp between rows. */
internal fun groupedShape(position: Position): Shape {
    val outer = 24.dp
    val inner = 8.dp
    return when (position) {
        Position.Alone -> RoundedCornerShape(outer)
        Position.Top -> RoundedCornerShape(outer, outer, inner, inner)
        Position.Middle -> RoundedCornerShape(inner)
        Position.Bottom -> RoundedCornerShape(inner, inner, outer, outer)
    }
}

/** Library-style section label: a labelMedium title with an optional explanation under it. */
@Composable
internal fun IgnoredSectionLabel(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Small, end = Spacing.Small, top = Spacing.Large, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Cover of an ignored rule: the first album's thumbnail, a +N badge for several albums, or the
 * regular-expression glyph for a path pattern.
 */
@Composable
internal fun RuleThumbnail(
    album: Album?,
    isPattern: Boolean,
    extraCount: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 16.dp,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (isPattern) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isPattern -> Icon(
                    imageVector = GalleryIcons.RegularExpression,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                album != null -> LocalMediaImageRenderer.current.RenderImage(
                    model = album.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    signature = album
                )
                else -> Icon(
                    imageVector = Icons.Outlined.PhotoAlbum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isPattern && extraCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.ExtraSmall)
                    .heightIn(min = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** A tappable grouped row: leading content, a title/summary column and trailing content. */
@Composable
internal fun GroupedRow(
    position: Position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = groupedShape(position),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                content()
            }
            trailing?.invoke()
        }
    }
}

/**
 * Radio choice row of the ignore sheet. The picked row takes the primary container so the choice
 * reads at a glance, not only from the radio.
 */
@Composable
internal fun OptionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    selected: Boolean,
    position: Position,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "optionRowContainer"
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "optionRowContent"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupedShape(position))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = Spacing.Medium, top = 12.dp, end = Spacing.Small, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Box(Modifier.size(Spacing.Small))
    }
}

/**
 * Album cell of the picker. Selected cells morph to rounder corners with a primary ring and a
 * check; albums another rule already hides are dimmed and can't be picked.
 */
@Composable
internal fun AlbumTile(
    album: Album,
    countLabel: String,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corner by animateDpAsState(if (selected) 32.dp else 20.dp, label = "albumTileCorner")
    val shape = RoundedCornerShape(corner)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .toggleable(value = selected, enabled = !disabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(bottom = Spacing.ExtraSmall),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
        ) {
            LocalMediaImageRenderer.current.RenderImage(
                model = album.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (disabled) 0.4f else 1f),
                signature = album
            )
            if (selected || disabled) {
                Box(
                    modifier = Modifier
                        .padding(Spacing.Small)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Outlined.Check else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.ExtraSmall)) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    disabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class SheetButtonStyle { Filled, Tonal, Error }

/** Full-width 56 dp pill used for the sheet actions. */
@Composable
internal fun SheetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SheetButtonStyle = SheetButtonStyle.Filled,
    enabled: Boolean = true,
) {
    val colors = when (style) {
        SheetButtonStyle.Filled -> ButtonDefaults.buttonColors()
        SheetButtonStyle.Tonal -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        SheetButtonStyle.Error -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ComponentSize.ButtonHeight),
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = Spacing.Large),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Two-way pill switch between choosing albums and typing a path pattern. */
@Composable
internal fun MethodToggle(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        content = content
    )
}

@Composable
internal fun RowScope.MethodSegment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "methodSegmentContainer"
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "methodSegmentContent"
    )
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}
