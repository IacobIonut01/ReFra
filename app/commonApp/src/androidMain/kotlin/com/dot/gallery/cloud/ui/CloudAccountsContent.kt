/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.descriptor.ProviderCategory
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_add_another_account
import com.dot.gallery.generated.resources.cloud_add_provider
import com.dot.gallery.generated.resources.cloud_authenticating
import com.dot.gallery.generated.resources.cloud_cap_archive
import com.dot.gallery.generated.resources.cloud_cap_map
import com.dot.gallery.generated.resources.cloud_cap_memories
import com.dot.gallery.generated.resources.cloud_cap_ocr
import com.dot.gallery.generated.resources.cloud_cap_people
import com.dot.gallery.generated.resources.cloud_cap_remote_albums
import com.dot.gallery.generated.resources.cloud_cap_remote_assets
import com.dot.gallery.generated.resources.cloud_cap_share_link
import com.dot.gallery.generated.resources.cloud_cap_smart_search
import com.dot.gallery.generated.resources.cloud_cap_sync
import com.dot.gallery.generated.resources.cloud_cap_tags
import com.dot.gallery.generated.resources.cloud_cap_text_search
import com.dot.gallery.generated.resources.cloud_connected
import com.dot.gallery.generated.resources.cloud_disconnected
import com.dot.gallery.generated.resources.cloud_error
import com.dot.gallery.generated.resources.cloud_lan_offline_warning
import com.dot.gallery.generated.resources.cloud_lan_only
import com.dot.gallery.generated.resources.cloud_network_shares
import com.dot.gallery.generated.resources.cloud_owncloud_variants
import com.dot.gallery.generated.resources.cloud_settings_header
import com.dot.gallery.generated.resources.cloud_storage_used
import com.dot.gallery.generated.resources.cloud_sync_now
import com.dot.gallery.generated.resources.cloud_synced_items
import com.dot.gallery.generated.resources.cloud_syncing
import com.dot.gallery.generated.resources.settings_cloud_accounts
import org.jetbrains.compose.resources.StringResource

/**
 * Stateless rendering of the cloud accounts list — everything the screen draws is
 * carried by [CloudAccountsContentState] and every user action is routed through
 * [CloudAccountsContentActions], so the composition needs no ViewModel, database or
 * network access. [CloudAccountsScreen] collects the real flows and delegates here;
 * the docs capture tier supplies sample accounts instead.
 */
data class CloudAccountsContentState(
    val configs: List<CloudServerConfigEntity>,
    val connectionStates: Map<Long, ConnectionState>,
    val storageInfo: Map<Long, CloudStorageInfo> = emptyMap(),
    val syncProgress: Map<Long, CloudAccountsViewModel.SyncProgress> = emptyMap(),
    val assetCounts: Map<Long, Int> = emptyMap(),
    val serverVersions: Map<Long, String> = emptyMap(),
    val availableProviders: List<ProviderType>,
    val capabilitiesByType: Map<ProviderType, Set<ProviderCapability>>,
    val onLocalNetwork: Boolean,
)

data class CloudAccountsContentActions(
    val onAdd: (ProviderType) -> Unit = {},
    val onSettings: (Long) -> Unit = {},
    val onSync: (Long) -> Unit = {},
)

@Composable
fun CloudAccountsContent(
    state: CloudAccountsContentState,
    actions: CloudAccountsContentActions,
) {
    val emptySettingsList = remember { mutableStateListOf<SettingsEntity>() }
    val hasLanProviders = remember(state.configs) {
        state.configs.any { ProviderUiDescriptors.forType(it.providerType).isLanOnly }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_cloud_accounts),
        topContent = {
            if (hasLanProviders && !state.onLocalNetwork) {
                LanOfflineBanner()
            }
            // Connected server cards
            state.configs.forEach { config ->
                val storage = state.storageInfo[config.id]
                val connState = state.connectionStates[config.id] ?: ConnectionState.DISCONNECTED
                val syncProgress = state.syncProgress[config.id]
                val syncedCount = state.assetCounts[config.id] ?: 0
                val version = state.serverVersions[config.id]
                val capabilities = state.capabilitiesByType[config.providerType].orEmpty()
                val isLanOnly = remember(config.providerType) {
                    ProviderUiDescriptors.forType(config.providerType).isLanOnly
                }
                ServerCard(
                    config = config,
                    connState = connState,
                    storage = storage,
                    syncProgress = syncProgress,
                    syncedCount = syncedCount,
                    version = version,
                    capabilities = capabilities,
                    isLanOnly = isLanOnly,
                    onSettings = { actions.onSettings(config.id) },
                    onSync = { actions.onSync(config.id) }
                )
            }
            // Keep every provider available after the first account is configured. Accounts are
            // keyed by configId, so hiding a type here incorrectly prevented same-provider accounts.
            if (state.configs.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.cloud_add_another_account),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                )
            }
            val byCategory = remember(state.availableProviders) {
                state.availableProviders.groupBy { ProviderUiDescriptors.forType(it).category }
            }

            byCategory[ProviderCategory.STANDALONE].orEmpty().forEach { providerType ->
                UnconnectedProviderCard(providerType = providerType, onAdd = { actions.onAdd(providerType) })
            }

            ProviderCategorySection(
                title = stringResource(Res.string.cloud_owncloud_variants),
                types = byCategory[ProviderCategory.OWNCLOUD_VARIANT].orEmpty(),
                onAdd = actions.onAdd
            )

            ProviderCategorySection(
                title = stringResource(Res.string.cloud_network_shares),
                types = byCategory[ProviderCategory.NETWORK_SHARE].orEmpty(),
                onAdd = actions.onAdd
            )
        },
        settingsList = emptySettingsList,
    )
}

@Composable
private fun ProviderCategorySection(
    title: String,
    types: List<ProviderType>,
    onAdd: (ProviderType) -> Unit
) {
    if (types.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    )
    types.forEach { providerType ->
        UnconnectedProviderCard(
            providerType = providerType,
            onAdd = { onAdd(providerType) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerCard(
    config: CloudServerConfigEntity,
    connState: ConnectionState,
    storage: CloudStorageInfo?,
    syncProgress: CloudAccountsViewModel.SyncProgress?,
    syncedCount: Int,
    version: String?,
    capabilities: Set<ProviderCapability>,
    isLanOnly: Boolean,
    onSettings: () -> Unit,
    onSync: () -> Unit
) {
    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer

    val transition = rememberInfiniteTransition(label = "serverCard")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientFraction"
    )
    val cornerRadius = 24.dp

    val isSyncing = syncProgress?.isSyncing == true

    val stateText = when {
        isSyncing -> stringResource(Res.string.cloud_syncing)
        connState == ConnectionState.CONNECTED && syncedCount > 0 ->
            stringResource(Res.string.cloud_synced_items, syncedCount)
        connState == ConnectionState.CONNECTED -> stringResource(Res.string.cloud_connected)
        connState == ConnectionState.DISCONNECTED -> stringResource(Res.string.cloud_disconnected)
        connState == ConnectionState.AUTHENTICATING -> stringResource(Res.string.cloud_authenticating)
        connState == ConnectionState.ERROR -> stringResource(Res.string.cloud_error)
        else -> stringResource(Res.string.cloud_disconnected)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .drawWithCache {
                    val cx = size.width - size.width * fraction
                    val cy = size.height * fraction
                    val gradient = Brush.radialGradient(
                        colors = listOf(colorPrimary, colorTertiary),
                        center = Offset(cx, cy),
                        radius = 800f
                    )
                    onDrawBehind {
                        drawRoundRect(
                            brush = gradient,
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                        )
                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(all = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Provider icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ProviderBrandIcon(
                    providerType = config.providerType,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Display name + version chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = config.displayName.ifBlank { config.providerType.displayName },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (version != null) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Status text
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Capability chips (+ LAN-only chip for network shares)
            if (isLanOnly || capabilities.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLanOnly) {
                        CapabilityChip(
                            label = stringResource(Res.string.cloud_lan_only),
                            highlighted = true
                        )
                    }
                    val capabilityLabels = mutableListOf<String>()
                    for (res in capabilities.mapNotNull { capabilityLabelRes(it) }) {
                        capabilityLabels += stringResource(res)
                    }
                    capabilityLabels.sort()
                    capabilityLabels.forEach { label ->
                        CapabilityChip(label = label)
                    }
                }
            }

            // Sync progress message
            AnimatedVisibility(visible = isSyncing && syncProgress?.message?.isNotEmpty() == true) {
                Text(
                    text = syncProgress?.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Storage bar
            if (storage != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.cloud_storage_used,
                                    storage.usedFormatted,
                                    storage.totalFormatted
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = storage.totalFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        StorageProgressBar(storage = storage)
                    }
                }
            }

            // Action buttons - horizontal side by side
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Text(
                        text = if (isSyncing) stringResource(Res.string.cloud_syncing) else stringResource(Res.string.cloud_sync_now),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.cloud_settings_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun UnconnectedProviderCard(
    providerType: ProviderType,
    onAdd: () -> Unit
) {
    val cornerRadius = 24.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(all = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Provider icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                ProviderBrandIcon(
                    providerType = providerType,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = providerType.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            ) {
                Text(
                    text = stringResource(Res.string.cloud_add_provider, providerType.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LanOfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = stringResource(Res.string.cloud_lan_offline_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun capabilityLabelRes(capability: ProviderCapability): StringResource? = when (capability) {
    ProviderCapability.REMOTE_ASSETS -> Res.string.cloud_cap_remote_assets
    ProviderCapability.REMOTE_ALBUMS -> Res.string.cloud_cap_remote_albums
    ProviderCapability.SYNC -> Res.string.cloud_cap_sync
    ProviderCapability.ALBUM_WRITE -> null
    ProviderCapability.ALBUM_MEMBERSHIP -> null
    ProviderCapability.SHARE_CREATE -> Res.string.cloud_cap_share_link
    ProviderCapability.SHARE_MANAGE -> null
    ProviderCapability.PEOPLE -> Res.string.cloud_cap_people
    ProviderCapability.MAP -> Res.string.cloud_cap_map
    ProviderCapability.SMART_SEARCH -> Res.string.cloud_cap_smart_search
    ProviderCapability.TEXT_SEARCH -> Res.string.cloud_cap_text_search
    ProviderCapability.OCR -> Res.string.cloud_cap_ocr
    ProviderCapability.ARCHIVE -> Res.string.cloud_cap_archive
    ProviderCapability.MEMORIES -> Res.string.cloud_cap_memories
    ProviderCapability.TAGS -> Res.string.cloud_cap_tags
    // Selection-only capabilities, not surfaced as account chips.
    ProviderCapability.FAVORITE -> null
    ProviderCapability.TRASH -> null
}

@Composable
private fun CapabilityChip(label: String, highlighted: Boolean = false) {
    val container = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val content = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StorageProgressBar(storage: CloudStorageInfo) {
    val fraction = (storage.usedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "storageFraction"
    )
    val progressColor = when {
        storage.usedPercentage > 90 -> MaterialTheme.colorScheme.error
        storage.usedPercentage > 75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val barHeight = 8.dp
    val barCornerRadius = 4.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barCornerRadius))
            .drawWithCache {
                onDrawBehind {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx())
                    )
                    drawRoundRect(
                        color = progressColor,
                        size = Size(size.width * animatedFraction, size.height),
                        cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx())
                    )
                }
            }
    )
}
