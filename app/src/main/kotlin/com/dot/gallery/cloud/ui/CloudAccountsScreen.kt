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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.cloud.ui.descriptor.ProviderBrandIcon
import com.dot.gallery.cloud.ui.descriptor.ProviderCategory
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.navigate
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.util.Screen
import com.dot.gallery.feature_node.presentation.util.connectivityState
import com.dot.gallery.feature_node.presentation.util.isLanRouteAvailable
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun CloudAccountsScreen(
    viewModel: CloudAccountsViewModel = hiltViewModel()
) {
    val configs by viewModel.accountState.collectAsStateWithLifecycle()
    val connectionStates by viewModel.connectionStates.collectAsStateWithLifecycle()
    val storageInfoMap by viewModel.storageInfo.collectAsStateWithLifecycle()
    val syncProgressMap by viewModel.syncProgress.collectAsStateWithLifecycle()
    val assetCounts by viewModel.assetCounts.collectAsStateWithLifecycle()
    val serverVersions by viewModel.serverVersions.collectAsStateWithLifecycle()
    val eventHandler = LocalEventHandler.current

    LaunchedEffect(configs) {
        if (configs.isNotEmpty()) {
            viewModel.loadStorageInfo()
            viewModel.loadAssetCounts()
            viewModel.loadServerVersions()
        }
    }

    val remoteProviderTypes = remember { ProviderType.availableRemoteTypes() }

    val emptySettingsList = remember { mutableStateListOf<SettingsEntity>() }

    val networkState = connectivityState()
    val context = LocalContext.current
    val hasLanProviders = remember(configs) {
        configs.any { ProviderUiDescriptors.forType(it.providerType).isLanOnly }
    }
    val onLocalNetwork = remember(networkState.value) { context.isLanRouteAvailable() }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_cloud_accounts),
        topContent = {
            if (hasLanProviders && !onLocalNetwork) {
                LanOfflineBanner()
            }
            // Connected server cards
            configs.forEach { config ->
                val storage = storageInfoMap[config.id]
                val connState = connectionStates[config.id] ?: ConnectionState.DISCONNECTED
                val syncProgress = syncProgressMap[config.id]
                val syncedCount = assetCounts[config.id] ?: 0
                val version = serverVersions[config.id]
                val capabilities = remember(config.providerType) {
                    viewModel.capabilitiesOf(config.providerType)
                }
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
                    onSettings = {
                        eventHandler.navigate(
                            Screen.CloudProviderSettingsScreen.configId(config.id)
                        )
                    },
                    onSync = { viewModel.triggerSync(config.id) }
                )
            }
            // Keep every provider available after the first account is configured. Accounts are
            // keyed by configId, so hiding a type here incorrectly prevented same-provider accounts.
            if (configs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cloud_add_another_account),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                )
            }
            val byCategory = remember(remoteProviderTypes) {
                remoteProviderTypes.groupBy { ProviderUiDescriptors.forType(it).category }
            }
            val addProvider: (ProviderType) -> Unit = { providerType ->
                eventHandler.navigate(
                    Screen.CloudAddServerScreen.providerType(providerType.name)
                )
            }

            byCategory[ProviderCategory.STANDALONE].orEmpty().forEach { providerType ->
                UnconnectedProviderCard(providerType = providerType, onAdd = { addProvider(providerType) })
            }

            ProviderCategorySection(
                title = stringResource(R.string.cloud_owncloud_variants),
                types = byCategory[ProviderCategory.OWNCLOUD_VARIANT].orEmpty(),
                onAdd = addProvider
            )

            ProviderCategorySection(
                title = stringResource(R.string.cloud_network_shares),
                types = byCategory[ProviderCategory.NETWORK_SHARE].orEmpty(),
                onAdd = addProvider
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
        isSyncing -> stringResource(R.string.cloud_syncing)
        connState == ConnectionState.CONNECTED && syncedCount > 0 ->
            stringResource(R.string.cloud_synced_items, syncedCount)
        connState == ConnectionState.CONNECTED -> stringResource(R.string.cloud_connected)
        connState == ConnectionState.DISCONNECTED -> stringResource(R.string.cloud_disconnected)
        connState == ConnectionState.AUTHENTICATING -> stringResource(R.string.cloud_authenticating)
        connState == ConnectionState.ERROR -> stringResource(R.string.cloud_error)
        else -> stringResource(R.string.cloud_disconnected)
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
                            label = stringResource(R.string.cloud_lan_only),
                            highlighted = true
                        )
                    }
                    capabilities
                        .mapNotNull { capabilityLabelRes(it) }
                        .sorted()
                        .forEach { labelRes ->
                            CapabilityChip(label = stringResource(labelRes))
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
                                    R.string.cloud_storage_used,
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
                        text = if (isSyncing) stringResource(R.string.cloud_syncing) else stringResource(R.string.cloud_sync_now),
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
                        text = stringResource(R.string.cloud_settings_header),
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
                    text = stringResource(R.string.cloud_add_provider, providerType.displayName),
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
            text = stringResource(R.string.cloud_lan_offline_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun capabilityLabelRes(capability: ProviderCapability): Int? = when (capability) {
    ProviderCapability.REMOTE_ASSETS -> R.string.cloud_cap_remote_assets
    ProviderCapability.REMOTE_ALBUMS -> R.string.cloud_cap_remote_albums
    ProviderCapability.SYNC -> R.string.cloud_cap_sync
    ProviderCapability.SHARE_CREATE -> R.string.cloud_cap_share_link
    ProviderCapability.SHARE_MANAGE -> null
    ProviderCapability.PEOPLE -> R.string.cloud_cap_people
    ProviderCapability.MAP -> R.string.cloud_cap_map
    ProviderCapability.SMART_SEARCH -> R.string.cloud_cap_smart_search
    ProviderCapability.TEXT_SEARCH -> R.string.cloud_cap_text_search
    ProviderCapability.OCR -> R.string.cloud_cap_ocr
    ProviderCapability.ARCHIVE -> R.string.cloud_cap_archive
    ProviderCapability.MEMORIES -> R.string.cloud_cap_memories
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
