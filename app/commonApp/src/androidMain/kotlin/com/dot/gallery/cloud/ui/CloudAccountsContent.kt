/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.cloud.ui.components.CloudSectionLabel
import com.dot.gallery.cloud.ui.components.ProviderBadge
import com.dot.gallery.cloud.ui.components.cloudContentWidth
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.cloud.ui.descriptor.ProviderCategory
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_accounts_choose_body
import com.dot.gallery.generated.resources.cloud_accounts_choose_title
import com.dot.gallery.generated.resources.cloud_accounts_cloud_storage
import com.dot.gallery.generated.resources.cloud_accounts_home_network
import com.dot.gallery.generated.resources.cloud_accounts_home_network_desc
import com.dot.gallery.generated.resources.cloud_accounts_photo_server
import com.dot.gallery.generated.resources.cloud_accounts_yours
import com.dot.gallery.generated.resources.cloud_add_account
import com.dot.gallery.generated.resources.cloud_add_provider
import com.dot.gallery.generated.resources.cloud_authenticating
import com.dot.gallery.generated.resources.cloud_connected
import com.dot.gallery.generated.resources.cloud_disconnected
import com.dot.gallery.generated.resources.cloud_error
import com.dot.gallery.generated.resources.cloud_lan_offline_warning
import com.dot.gallery.generated.resources.cloud_provider_desc_immich
import com.dot.gallery.generated.resources.cloud_provider_desc_nextcloud
import com.dot.gallery.generated.resources.cloud_provider_desc_nfs
import com.dot.gallery.generated.resources.cloud_provider_desc_owncloud
import com.dot.gallery.generated.resources.cloud_provider_desc_smb
import com.dot.gallery.generated.resources.cloud_provider_desc_webdav
import com.dot.gallery.generated.resources.cloud_storage_used
import com.dot.gallery.generated.resources.cloud_sync_now
import com.dot.gallery.generated.resources.cloud_synced_count
import com.dot.gallery.generated.resources.cloud_syncing
import com.dot.gallery.generated.resources.settings_cloud_accounts
import org.jetbrains.compose.resources.stringResource

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

/**
 * Connected accounts first, then every available service grouped as photo server, cloud
 * storage and home-network shares. Every provider remains addable after the first account,
 * since accounts are keyed by configId and one provider can hold several accounts.
 */
@Composable
fun CloudAccountsContent(
    state: CloudAccountsContentState,
    actions: CloudAccountsContentActions,
) {
    val hasLanProviders = remember(state.configs) {
        state.configs.any { ProviderUiDescriptors.forType(it.providerType).isLanOnly }
    }
    val byCategory = remember(state.availableProviders) {
        state.availableProviders.groupBy { ProviderUiDescriptors.forType(it).category }
    }
    CloudPage(
        title = stringResource(
            if (state.configs.isEmpty()) Res.string.cloud_add_account else Res.string.settings_cloud_accounts
        ),
    ) {
        if (hasLanProviders && !state.onLocalNetwork) {
            item(key = "lan_offline") { LanOfflineBanner() }
        }
        if (state.configs.isNotEmpty()) {
            item(key = "yours_label") { CloudSectionLabel(title = stringResource(Res.string.cloud_accounts_yours)) }
            state.configs.forEachIndexed { index, config ->
                item(key = "account_${config.id}") {
                    AccountRow(
                        config = config,
                        connState = state.connectionStates[config.id] ?: ConnectionState.DISCONNECTED,
                        storage = state.storageInfo[config.id],
                        syncProgress = state.syncProgress[config.id],
                        syncedCount = state.assetCounts[config.id] ?: 0,
                        version = state.serverVersions[config.id],
                        position = positionOf(index, state.configs.size),
                        onSettings = { actions.onSettings(config.id) },
                        onSync = { actions.onSync(config.id) },
                    )
                }
            }
        }
        item(key = "choose_header") {
            Column(
                modifier = Modifier
                    .cloudContentWidth()
                    .padding(start = 8.dp, end = 8.dp, top = if (state.configs.isEmpty()) 8.dp else 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_accounts_choose_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.cloud_accounts_choose_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        listOf(
            ProviderCategory.STANDALONE to Res.string.cloud_accounts_photo_server,
            ProviderCategory.OWNCLOUD_VARIANT to Res.string.cloud_accounts_cloud_storage,
            ProviderCategory.NETWORK_SHARE to Res.string.cloud_accounts_home_network,
        ).forEach { (category, title) ->
            val types = byCategory[category].orEmpty()
            if (types.isEmpty()) return@forEach
            item(key = "label_${category.name}") {
                CloudSectionLabel(
                    title = stringResource(title),
                    description = if (category == ProviderCategory.NETWORK_SHARE) {
                        stringResource(Res.string.cloud_accounts_home_network_desc)
                    } else null,
                )
            }
            types.forEachIndexed { index, type ->
                item(key = "provider_${type.name}") {
                    ProviderRow(
                        providerType = type,
                        position = positionOf(index, types.size),
                        onAdd = { actions.onAdd(type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    config: CloudServerConfigEntity,
    connState: ConnectionState,
    storage: CloudStorageInfo?,
    syncProgress: CloudAccountsViewModel.SyncProgress?,
    syncedCount: Int,
    version: String?,
    position: Position,
    onSettings: () -> Unit,
    onSync: () -> Unit,
) {
    val isSyncing = syncProgress?.isSyncing == true
    val colors = MaterialTheme.colorScheme
    val (statusColor, status) = when {
        isSyncing -> colors.primary to (syncProgress.message?.ifEmpty { null } ?: stringResource(Res.string.cloud_syncing))
        connState == ConnectionState.ERROR -> colors.error to stringResource(Res.string.cloud_error)
        connState == ConnectionState.AUTHENTICATING -> colors.primary to stringResource(Res.string.cloud_authenticating)
        connState == ConnectionState.DISCONNECTED -> colors.onSurfaceVariant to stringResource(Res.string.cloud_disconnected)
        syncedCount > 0 -> colors.primary to stringResource(Res.string.cloud_synced_count, syncedCount)
        else -> colors.primary to stringResource(Res.string.cloud_connected)
    }
    val detail = listOfNotNull(
        config.providerType.displayName + (version?.let { " $it" } ?: ""),
        storage?.let { stringResource(Res.string.cloud_storage_used, it.usedFormatted, it.totalFormatted) },
    ).joinToString(" · ")
    val summary = buildAnnotatedString {
        append(detail)
        append('\n')
        withStyle(SpanStyle(color = statusColor)) { append(status) }
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = Icons.Outlined.Cloud,
            title = config.displayName.ifBlank { config.providerType.displayName },
            summary = summary.text,
            summaryAnnotated = summary,
            screenPosition = position,
            onClick = onSettings,
        ),
        customIcon = { _, _, _ -> ProviderBadge(providerType = config.providerType) },
        customTrailingContent = {
            IconButton(onClick = onSync, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, contentDescription = stringResource(Res.string.cloud_sync_now))
                }
            }
        },
    )
}

@Composable
private fun ProviderRow(providerType: ProviderType, position: Position, onAdd: () -> Unit) {
    val description = when (providerType) {
        ProviderType.IMMICH -> stringResource(Res.string.cloud_provider_desc_immich)
        ProviderType.NEXTCLOUD -> stringResource(Res.string.cloud_provider_desc_nextcloud)
        ProviderType.OWNCLOUD -> stringResource(Res.string.cloud_provider_desc_owncloud)
        ProviderType.WEBDAV -> stringResource(Res.string.cloud_provider_desc_webdav)
        ProviderType.SMB -> stringResource(Res.string.cloud_provider_desc_smb)
        ProviderType.NFS -> stringResource(Res.string.cloud_provider_desc_nfs)
        else -> stringResource(Res.string.cloud_add_provider, providerType.displayName)
    }
    SettingsItem(
        item = SettingsEntity.Preference(
            icon = Icons.Outlined.Cloud,
            title = providerType.displayName,
            summary = description,
            screenPosition = position,
            onClick = onAdd,
        ),
        customIcon = { _, _, _ -> ProviderBadge(providerType = providerType) },
        customTrailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.cloud_add_provider, providerType.displayName),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        },
    )
}

@Composable
private fun LanOfflineBanner() {
    Row(
        modifier = Modifier
            .cloudContentWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
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
