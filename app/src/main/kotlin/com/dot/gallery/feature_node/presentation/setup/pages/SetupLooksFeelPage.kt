/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.setup.pages

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppLogoAlias
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.presentation.setup.components.SetupSectionCard
import com.dot.gallery.feature_node.presentation.setup.components.SetupWizardScaffold
import com.dot.gallery.feature_node.presentation.util.Screen
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun SetupLooksFeelPage(
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val handler = LocalEventHandler.current
    var appNameAlias by rememberAppNameAlias()
    var appLogoAlias by rememberAppLogoAlias()

    SetupWizardScaffold(
        showBack = true,
        onBack = onBack,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        title = stringResource(R.string.setup_looks_title),
        subtitle = stringResource(R.string.setup_looks_subtitle),
        bottomBar = {
            SetupButton(
                text = stringResource(R.string.setup_continue),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                applyNavigationPadding = false,
                onClick = onNext
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App name
            SetupSectionCard(title = stringResource(R.string.setup_section_app_name)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(Settings.Misc.ALIAS_REFRA, Settings.Misc.ALIAS_GALLERY).forEach { alias ->
                        LogoChoice(
                            modifier = Modifier.weight(1f),
                            label = alias,
                            iconRes = launcherIconFor(appLogoAlias),
                            selected = appNameAlias == alias,
                            onClick = { appNameAlias = alias }
                        )
                    }
                }
            }

            // App logo
            SetupSectionCard(title = stringResource(R.string.setup_section_app_logo)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        Settings.Misc.ALIAS_REFRA,
                        Settings.Misc.ALIAS_GALLERY,
                        Settings.Misc.ALIAS_MONO,
                        Settings.Misc.ALIAS_GALLERY_MONO
                    ).forEach { alias ->
                        LogoChoice(
                            modifier = Modifier.width(96.dp),
                            label = alias,
                            iconRes = launcherIconFor(alias),
                            selected = appLogoAlias == alias,
                            onClick = { appLogoAlias = alias }
                        )
                    }
                }
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = stringResource(R.string.setup_looks_restart_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Appearance
            SetupSectionCard(
                title = stringResource(R.string.setup_section_appearance),
                subtitle = stringResource(R.string.setup_appearance_summary)
            ) {
                SetupButton(
                    text = stringResource(R.string.setup_open_appearance),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { handler.navigate(Screen.SettingsAppearanceScreen()) }
                )
            }
        }
    }
}

private fun launcherIconFor(logoAlias: String): Int =
    when (logoAlias) {
        Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
        Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
        Settings.Misc.ALIAS_GALLERY_MONO -> R.mipmap.ic_launcher_gallery_mono_round
        else -> R.mipmap.ic_launcher_round
    }

@Composable
private fun LogoChoice(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else Color.Transparent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = rememberDrawablePainter(
                drawable = AppCompatResources.getDrawable(context, iconRes)
            ),
            contentDescription = label,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
