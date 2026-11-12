/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.classifier.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.ui.components.dashedBorder
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.classifier.generated.resources.Res
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_balanced
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_balanced_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_broad
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_broad_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_exact
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_exact_desc
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_strict
import com.dot.gallery.feature.classifier.generated.resources.sensitivity_strict_desc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Humanised strictness presets over [Category.threshold]; custom values match none. */
enum class SensitivityPreset(
    val threshold: Float,
    val labelRes: StringResource,
    val descRes: StringResource,
) {
    BROAD(0.05f, Res.string.sensitivity_broad, Res.string.sensitivity_broad_desc),
    BALANCED(Category.DEFAULT_THRESHOLD, Res.string.sensitivity_balanced, Res.string.sensitivity_balanced_desc),
    STRICT(0.5f, Res.string.sensitivity_strict, Res.string.sensitivity_strict_desc),
    EXACT(0.75f, Res.string.sensitivity_exact, Res.string.sensitivity_exact_desc);

    /** The next looser preset, or null when this is already the loosest. */
    val looser: SensitivityPreset? get() = entries.getOrNull(ordinal - 1)

    companion object {
        fun fromThreshold(threshold: Float): SensitivityPreset? =
            entries.find { kotlin.math.abs(it.threshold - threshold) < 0.01f }
    }
}

/** Segment shape of an item in a vertically or horizontally joined group of [count] items. */
fun joinedShape(index: Int, count: Int, vertical: Boolean = true, outer: Dp = 24.dp, inner: Dp = 8.dp): Shape {
    val first = index == 0
    val last = index == count - 1
    val start = if (first) outer else inner
    val end = if (last) outer else inner
    return if (vertical) {
        RoundedCornerShape(topStart = start, topEnd = start, bottomStart = end, bottomEnd = end)
    } else {
        RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    }
}

/** Section title with its count on the baseline, like the People and Library pages. */
@Composable
fun CategorySectionHeader(title: String, count: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** 56 dp secondary-container pill summarising the library state; tapping opens [onClick]. */
@Composable
fun CategoryStatusBanner(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * Card shown while the classifier runs: a round badge, title and progress line, an optional
 * trailing action, a progress bar ([progress] null = indeterminate) and a note.
 */
@Composable
fun CategoryProgressCard(
    icon: ImageVector,
    title: String,
    summary: String,
    progress: Float?,
    note: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        CategoryProgressBar(progress = progress, height = 6.dp)
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rounded progress bar; [progress] null draws the indeterminate form. */
@Composable
fun CategoryProgressBar(progress: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val bar = modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(height / 2))
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    if (progress == null) {
        LinearProgressIndicator(modifier = bar, trackColor = track, gapSize = 0.dp)
    } else {
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "categoryProgress")
        Box(modifier = bar.background(track)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(height / 2))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Category thumbnail, or the category glyph on a tonal surface when it has no photo yet. */
@Composable
fun CategoryCover(
    media: UriMedia?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
) {
    if (media != null) {
        LocalMediaImageRenderer.current.RenderImage(
            modifier = modifier,
            model = media.getUri(),
            contentScale = ContentScale.Crop,
            contentDescription = contentDescription,
            signature = media,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Category,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        // Sits on the photo, so it stays white in both themes like the album overlays.
        Icon(Icons.Outlined.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * Grid tile: square rounded cover with an optional pin badge, the name and a meta line. With
 * [progress] the meta line turns primary and a thin bar shows under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryTile(
    name: String,
    meta: String,
    media: UriMedia?,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    sorting: Boolean = false,
) {
    // The whole tile takes the tap, but nothing is clipped: the press ripple draws on the
    // rounded cover only, so the labels under it are never cut by the corner.
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = coverModifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .indication(interaction, ripple()),
        ) {
            CategoryCover(media = media, contentDescription = name, modifier = Modifier.fillMaxSize())
            if (pinned) PinBadge(Modifier.padding(10.dp))
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (sorting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sorting) CategoryProgressBar(progress = null)
        }
    }
}

/** Tall pinned card with the name over a bottom scrim, matching the Library's pinned albums. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedCategoryCard(
    name: String,
    meta: String,
    media: UriMedia?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(164.dp)
            .height(206.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
    ) {
        CategoryCover(media = media, contentDescription = name, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )
        PinBadge(Modifier.padding(12.dp))
        // Text sits on a dark scrim over the photo, so it stays white in both themes.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

/** Dashed "add" tile used for New category and Add photos. */
@Composable
fun DashedAddTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline,
                shape = shape,
                strokeWidth = 1.5.dp,
                dashLength = 6.dp,
                gapLength = 5.dp,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 48 dp tonal action chip of the category header; destructive uses the error container. */
@Composable
fun CategoryActionChip(icon: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A row of a joined list group (sheet actions, tips): icon, title, optional summary. */
@Composable
fun CategoryListRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Connected four-way button group for [SensitivityPreset]: the picked segment turns primary
 * with a check and rounds fully; a custom threshold leaves every segment unselected.
 */
@Composable
fun StrictnessSelector(
    selected: SensitivityPreset?,
    onSelect: (SensitivityPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = SensitivityPreset.entries
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        presets.forEachIndexed { index, preset ->
            val isSelected = preset == selected
            val label = stringResource(preset.labelRes)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(if (isSelected) RoundedCornerShape(24.dp) else joinedShape(index, presets.size, vertical = false))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.RadioButton) { onSelect(preset) }
                    .semantics { this.selected = isSelected; contentDescription = label }
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp).padding(end = 2.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Segmented step bar of the creation wizard; segments up to [step] (1-based) are filled. */
@Composable
fun WizardStepBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

/** Rounded surface-container card used for the editor's grouped blocks. */
@Composable
fun CategoryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
