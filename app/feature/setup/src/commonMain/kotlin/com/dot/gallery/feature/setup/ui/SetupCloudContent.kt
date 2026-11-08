/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.setup_back
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
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_configured
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_footnote
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_none
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_cloud_title
import com.dot.gallery.feature.setup.generated.resources.setup_continue
import com.dot.gallery.feature.setup.generated.resources.setup_step_cloud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 3 · Cloud: connected accounts first, then one row per provider (its icon, name and what it
 * can do) that opens the add-account flow. Optional, so Skip and Continue both move on.
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
    SetupStepScaffold(
        modifier = modifier,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        stepName = stringResource(Res.string.setup_step_cloud),
        hero = SetupHero(
            icon = Icons.Outlined.Cloud,
            shape = SetupHeroShape.Cloud.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = stringResource(Res.string.setup_cloud_title),
        subtitle = stringResource(Res.string.setup_cloud_subtitle),
        onBack = onBack,
        onSkip = onSkip,
        bottomBar = {
            SetupBottomActions(
                primaryLabel = stringResource(Res.string.setup_continue),
                onPrimary = onNext,
                secondaryLabel = stringResource(Res.string.setup_back),
                onSecondary = onBack,
            )
        },
    ) {
        val count = state.accounts.size + state.providers.size
        if (count == 0) {
            Text(
                text = stringResource(Res.string.setup_cloud_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            SetupGlassPanel {
                state.accounts.forEachIndexed { index, account ->
                    SetupGlassRow(
                        title = account.providerName,
                        summary = stringResource(Res.string.setup_cloud_configured, account.serverUrl),
                        icon = Icons.Outlined.CheckCircle,
                        position = setupRowPosition(index, count),
                    )
                }
                state.providers.forEachIndexed { index, provider ->
                    val summary = provider.capabilities
                        .mapNotNull { capabilityLabel(it) }
                        .take(3)
                        .map { stringResource(it) }
                        .joinToString(" · ")
                    SetupGlassRow(
                        title = provider.type.displayName,
                        summary = provider.description ?: summary.ifBlank { null },
                        icon = provider.icon,
                        leading = provider.badge,
                        position = setupRowPosition(state.accounts.size + index, count),
                        onClick = { actions.onSetupProvider(provider.type) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
        SetupNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.setup_cloud_footnote))
    }
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
