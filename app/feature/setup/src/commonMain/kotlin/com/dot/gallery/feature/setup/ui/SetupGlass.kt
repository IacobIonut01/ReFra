/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.util.hazeEffectScaled
import com.dot.gallery.ui.theme.GalleryGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

/**
 * The wizard's single haze source: the blob background drawn once behind every page. Null when a
 * page renders on its own (previews, docs), in which case glass falls back to its tint.
 */
val LocalSetupHazeState = compositionLocalOf<HazeState?> { null }

/** Position of a row inside a glass panel, for its 22/6 dp corners. */
enum class SetupRowPosition { Alone, Top, Middle, Bottom }

fun setupRowPosition(index: Int, count: Int): SetupRowPosition = when {
    count == 1 -> SetupRowPosition.Alone
    index == 0 -> SetupRowPosition.Top
    index == count - 1 -> SetupRowPosition.Bottom
    else -> SetupRowPosition.Middle
}

private fun SetupRowPosition.shape(): Shape = when (this) {
    SetupRowPosition.Alone -> RoundedCornerShape(22.dp)
    SetupRowPosition.Top -> RoundedCornerShape(22.dp, 22.dp, 6.dp, 6.dp)
    SetupRowPosition.Middle -> RoundedCornerShape(6.dp)
    SetupRowPosition.Bottom -> RoundedCornerShape(6.dp, 6.dp, 22.dp, 22.dp)
}

/**
 * Setup's own frosted material: the 24 dp blur of HazeMaterials.regular with the lighter
 * `glass/panel` tint so the blobs show through, no grain, and the solid container colour when
 * blur is off. `blurEnabled` is inherited from the provided blur style.
 */
@Composable
fun rememberSetupGlassStyle(): HazeBlurStyle {
    val tint = GalleryGlass.panel
    val fallback = MaterialTheme.colorScheme.surfaceContainer
    return remember(tint, fallback) {
        HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(fallback))
        }
    }
}

/**
 * Frosted panel sitting on the blobs: corner 28, padding 6, holding [SetupGlassRow]s that never
 * blur again. Without a haze source it paints its tint over a solid container.
 */
@Composable
fun SetupGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalSetupHazeState.current
    val shape = RoundedCornerShape(28.dp)
    val surface = if (hazeState != null) {
        Modifier.hazeEffectScaled(hazeState, rememberSetupGlassStyle(), MaterialTheme.colorScheme.surface)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(surface)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * A row inside a [SetupGlassPanel]: leading icon, title, summary, an optional coloured status
 * line and a [trailing] slot.
 */
@Composable
fun SetupGlassRow(
    title: String,
    position: SetupRowPosition,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    status: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape())
            .background(GalleryGlass.row)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

/** A small glass pill: an icon and a label, used for the welcome chips. */
@Composable
fun SetupGlassChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GalleryGlass.chrome)
            .padding(PaddingValues(horizontal = 14.dp, vertical = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The one footnote of a setup step. */
@Composable
fun SetupNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The expressive hero shape of each setup step, from the Material 3 shape library. */
enum class SetupHeroShape { Permissions, Looks, Cloud, AiModels, Done }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetupHeroShape.toShape(): Shape = when (this) {
    SetupHeroShape.Permissions -> MaterialShapes.Cookie9Sided
    SetupHeroShape.Looks -> MaterialShapes.Gem
    SetupHeroShape.Cloud -> MaterialShapes.Cookie4Sided
    SetupHeroShape.AiModels -> MaterialShapes.Cookie12Sided
    SetupHeroShape.Done -> MaterialShapes.SoftBurst
}.toShape()
