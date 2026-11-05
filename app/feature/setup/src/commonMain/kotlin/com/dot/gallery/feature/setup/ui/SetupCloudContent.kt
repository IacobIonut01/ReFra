/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.SetupButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.setup_cap_archive
import com.dot.gallery.feature.setup.generated.resources.setup_cap_map
import com.dot.gallery.feature.setup.generated.resources.setup_cap_memories
import com.dot.gallery.feature.setup.generated.resources.setup_cap_ocr
import com.dot.gallery.feature.setup.generated.resources.setup_cap_people
import com.dot.gallery.feature.setup.generated.resources.setup_cap_remote_albums
import com.dot.gallery.feature.setup.generated.resources.setup_cap_remote_assets
import com.dot.gallery.feature.setup.generated.resources.setup_cap_share_link
import com.dot.gallery.feature.setup.generated.resources.setup_cap_smart_search
import com.dot.gallery.feature.setup.generated.resources.setup_cap_sync
import com.dot.gallery.feature.setup.generated.resources.setup_cap_tags
import com.dot.gallery.feature.setup.generated.resources.setup_cap_text_search
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_add_another
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_choose_provider
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_configured
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_none
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_title
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_skip_for_now


/**
 * Cloud page of the setup wizard: connected accounts rendered as configured cards and,
 * until one exists (or the user asks to add another), the selectable provider list.
 * Provider auth/setup happens on the app-side add-server screen via
 * [SetupCloudActions.onSetupProvider].
 */
@Composable
fun SetupCloudContent(
    state: SetupCloudState,
    actions: SetupCloudActions,
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasConfigured = state.accounts.isNotEmpty()

    SetupWizardScaffold(
        modifier = modifier,
        showBack = true,
        onBack = onBack,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        title = stringResource(Res.string.setup_cloud_title),
        subtitle = stringResource(Res.string.setup_cloud_subtitle),
        bottomBar = {
            // Cloud sync is optional: until an account is connected the only meaningful
            // action is to skip, so a single state-aware button is shown rather than a
            // redundant Continue + Skip pair.
            if (hasConfigured) {
                SetupButton(
                    text = stringResource(Res.string.setup_continue),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    onClick = onNext
                )
            } else {
                SetupButton(
                    text = stringResource(Res.string.setup_skip_for_now),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onSkip
                )
            }
        }
    ) {
        var showProviderPicker by rememberSaveable { mutableStateOf(false) }
        // Show the provider chooser when nothing is configured yet, or when the user has
        // explicitly asked to add another provider on top of the existing one(s).
        val showChooser = !hasConfigured || showProviderPicker

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasConfigured) {
                state.accounts.forEach { account ->
                    SetupSectionCard(
                        title = account.providerName,
                        subtitle = stringResource(Res.string.setup_cloud_configured, account.serverUrl)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = stringResource(Res.string.setup_cap_sync),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (showChooser) {
                Text(
                    text = stringResource(Res.string.setup_cloud_choose_provider),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.providers.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        onSetup = { actions.onSetupProvider(provider.type) }
                    )
                }
                if (state.providers.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.setup_cloud_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // At least one provider is connected — offer to add another.
                SetupButton(
                    text = stringResource(Res.string.setup_cloud_add_another),
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { showProviderPicker = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    provider: SetupProviderUi,
    onSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
            .clickable(onClick = onSetup)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = provider.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                text = provider.type.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            provider.capabilities.forEach { capability ->
                CapabilityChip(capability)
            }
        }
    }
}

@Composable
private fun CapabilityChip(capability: SetupProviderCapability) {
    val label = stringResource(capabilityLabel(capability) ?: return)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun capabilityLabel(capability: SetupProviderCapability): StringResource? = when (capability) {
    SetupProviderCapability.REMOTE_ASSETS -> Res.string.setup_cap_remote_assets
    SetupProviderCapability.REMOTE_ALBUMS -> Res.string.setup_cap_remote_albums
    SetupProviderCapability.SYNC -> Res.string.setup_cap_sync
    SetupProviderCapability.ALBUM_WRITE -> null
    SetupProviderCapability.ALBUM_MEMBERSHIP -> null
    SetupProviderCapability.PEOPLE -> Res.string.setup_cap_people
    SetupProviderCapability.MAP -> Res.string.setup_cap_map
    SetupProviderCapability.SMART_SEARCH -> Res.string.setup_cap_smart_search
    SetupProviderCapability.TEXT_SEARCH -> Res.string.setup_cap_text_search
    SetupProviderCapability.SHARE_CREATE -> Res.string.setup_cap_share_link
    SetupProviderCapability.SHARE_MANAGE -> null
    SetupProviderCapability.OCR -> Res.string.setup_cap_ocr
    SetupProviderCapability.ARCHIVE -> Res.string.setup_cap_archive
    SetupProviderCapability.MEMORIES -> Res.string.setup_cap_memories
    SetupProviderCapability.TAGS -> Res.string.setup_cap_tags
    // Selection-only capabilities, not shown during setup.
    SetupProviderCapability.FAVORITE -> null
    SetupProviderCapability.TRASH -> null
}
