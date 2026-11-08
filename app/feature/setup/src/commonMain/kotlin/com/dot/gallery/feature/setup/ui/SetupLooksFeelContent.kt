/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.setup_appearance_summary
import com.dot.gallery.feature.setup.generated.resources.setup_back
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_looks_restart_note
import com.dot.gallery.feature.setup.generated.resources.setup_looks_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_looks_title
import com.dot.gallery.feature.setup.generated.resources.setup_section_app_logo
import com.dot.gallery.feature.setup.generated.resources.setup_section_app_name
import com.dot.gallery.feature.setup.generated.resources.setup_section_appearance
import com.dot.gallery.feature.setup.generated.resources.setup_step_looks
import com.dot.gallery.ui.theme.GalleryGlass
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2 · Looks & feel: the four real launcher icons, the ReFra / Gallery name as a segmented
 * control, and a row into the appearance settings.
 */
@Composable
fun SetupLooksFeelContent(
    state: SetupLooksFeelState,
    actions: SetupLooksFeelActions,
    logoPainter: @Composable (logoAlias: String) -> Painter,
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var appNameAlias by rememberPreference(SettingsKeys.appNameAlias)
    var appLogoAlias by rememberPreference(SettingsKeys.appLogoAlias)

    SetupStepScaffold(
        modifier = modifier,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        stepName = stringResource(Res.string.setup_step_looks),
        hero = SetupHero(
            icon = Icons.Outlined.Palette,
            shape = SetupHeroShape.Looks.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = stringResource(Res.string.setup_looks_title),
        subtitle = stringResource(Res.string.setup_looks_subtitle),
        onBack = onBack,
        onSkip = onNext,
        bottomBar = {
            SetupBottomActions(
                primaryLabel = stringResource(Res.string.setup_continue),
                onPrimary = onNext,
                secondaryLabel = stringResource(Res.string.setup_back),
                onSecondary = onBack,
            )
        },
    ) {
        SetupGlassPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 6.dp))
                    .background(GalleryGlass.row)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelLabel(stringResource(Res.string.setup_section_app_logo))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.logoAliases.forEach { alias ->
                        LauncherChoice(
                            label = alias,
                            painter = logoPainter(alias),
                            selected = appLogoAlias == alias,
                            onClick = { appLogoAlias = alias },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                PanelLabel(stringResource(Res.string.setup_section_app_name))
                NameSegments(
                    options = state.nameAliases,
                    selected = appNameAlias,
                    onSelect = { appNameAlias = it },
                )
            }
            SetupGlassRow(
                title = stringResource(Res.string.setup_section_appearance),
                summary = stringResource(Res.string.setup_appearance_summary),
                icon = Icons.Outlined.Contrast,
                position = SetupRowPosition.Bottom,
                onClick = actions.onOpenAppearance,
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        SetupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.setup_looks_restart_note))
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** One launcher icon tile; the selected one gets a 2 dp primary outline. */
@Composable
private fun LauncherChoice(
    label: String,
    painter: Painter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Two-option segmented control for the app name. */
@Composable
private fun NameSegments(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
