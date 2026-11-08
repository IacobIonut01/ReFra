/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.ui.WifiSsidPickerSheet
import com.dot.gallery.cloud.ui.components.CloudNote
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.cloud.ui.components.CloudSummaryCard
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.cloud_net_add_external
import com.dot.gallery.generated.resources.cloud_net_auto_url
import com.dot.gallery.generated.resources.cloud_net_auto_url_summary
import com.dot.gallery.generated.resources.cloud_net_edit_local_url
import com.dot.gallery.generated.resources.cloud_net_external
import com.dot.gallery.generated.resources.cloud_net_external_hint
import com.dot.gallery.generated.resources.cloud_net_footnote
import com.dot.gallery.generated.resources.cloud_net_in_use
import com.dot.gallery.generated.resources.cloud_net_local
import com.dot.gallery.generated.resources.cloud_net_local_url
import com.dot.gallery.generated.resources.cloud_net_no_external
import com.dot.gallery.generated.resources.cloud_net_not_configured
import com.dot.gallery.generated.resources.cloud_net_switching
import com.dot.gallery.generated.resources.cloud_net_wifi_blank_summary
import com.dot.gallery.generated.resources.cloud_net_wifi_name
import com.dot.gallery.generated.resources.cloud_networking

/** Test tags of the Local and External tiles in the summary card. */
internal const val CloudNetLocalTileTag = "cloud-net-tile-local"
internal const val CloudNetExternalTileTag = "cloud-net-tile-external"


private fun parseExternalUrls(json: String): List<String> {
    return try {
        Json.decodeFromString<List<String>>(json)
    } catch (_: Exception) { emptyList() }
}

private fun serializeExternalUrls(urls: List<String>): String {
    return Json.encodeToString(urls)
}

/**
 * Stateless per-account networking page on a [CloudPage] titled with the account name: the
 * connection summary card, the automatic-switching row, the local/external URL groups and their
 * dialogs/sheet. [CloudNetworkingScreen]
 * loads the config, resolves the effective URL and delegates here; the docs
 * capture tier supplies a sample config and example values.
 */
@Composable
fun CloudNetworkingContent(
    config: CloudServerConfigEntity,
    effectiveUrl: String,
    localActive: Boolean,
    onUpdateConfig: (CloudServerConfigEntity.() -> CloudServerConfigEntity) -> Unit,
) {
    val autoUrlSwitch = config.autoUrlSwitch
    val wifiSsid = config.localWifiSsid
    val localUrl = config.localServerUrl
    val externalUrls = remember(config.externalUrls) {
        parseExternalUrls(config.externalUrls)
    }

    val scope = rememberCoroutineScope()
    val wifiSheetState = rememberAppBottomSheetState()

    var showLocalUrlDialog by remember { mutableStateOf(false) }
    var showAddExternalDialog by remember { mutableStateOf(false) }
    val switchingHeader = stringResource(Res.string.cloud_net_switching)
    val autoUrlTitle = stringResource(Res.string.cloud_net_auto_url)
    val autoUrlSummary = stringResource(Res.string.cloud_net_auto_url_summary)
    val localHeader = stringResource(Res.string.cloud_net_local)
    val wifiNameTitle = stringResource(Res.string.cloud_net_wifi_name)
    val wifiBlankSummary = stringResource(Res.string.cloud_net_wifi_blank_summary)
    val localUrlTitle = stringResource(Res.string.cloud_net_local_url)
    val notConfiguredSummary = stringResource(Res.string.cloud_net_not_configured)
    val externalHeader = stringResource(Res.string.cloud_net_external)
    val addExternalTitle = stringResource(Res.string.cloud_net_add_external)
    val noExternalSummary = stringResource(Res.string.cloud_net_no_external)
    val resourceStrings = listOf(
        switchingHeader,
        autoUrlTitle,
        autoUrlSummary,
        localHeader,
        wifiNameTitle,
        wifiBlankSummary,
        localUrlTitle,
        notConfiguredSummary,
        externalHeader,
        addExternalTitle,
        noExternalSummary,
    )

    // Each row carries its own list key, since two external URLs may share a title.
    val rows: List<Pair<String, SettingsEntity>> = remember(
        autoUrlSwitch,
        wifiSsid,
        localUrl,
        externalUrls,
        resourceStrings,
    ) {
        buildList {
            add("switching" to SettingsEntity.Header(title = switchingHeader))
            add(
                "auto" to SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.SwapHoriz,
                    title = autoUrlTitle,
                    summary = autoUrlSummary,
                    isChecked = autoUrlSwitch,
                    onCheck = { onUpdateConfig { copy(autoUrlSwitch = it) } },
                    screenPosition = Position.Alone
                )
            )
            if (autoUrlSwitch) {
                add("local" to SettingsEntity.Header(title = localHeader))
                add(
                    "wifi" to SettingsEntity.Preference(
                        icon = Icons.Outlined.Wifi,
                        title = wifiNameTitle,
                        summary = wifiSsid.ifBlank { wifiBlankSummary },
                        onClick = { scope.launch { wifiSheetState.show() } },
                        screenPosition = Position.Top
                    )
                )
                add(
                    "local_url" to SettingsEntity.Preference(
                        icon = Icons.Outlined.Router,
                        title = localUrlTitle,
                        summary = localUrl.ifBlank { notConfiguredSummary },
                        onClick = { showLocalUrlDialog = true },
                        screenPosition = Position.Bottom
                    )
                )

                add("external" to SettingsEntity.Header(title = externalHeader))
                // The add row closes the URL group, so it shares the group's corners.
                val groupSize = externalUrls.size + 1
                externalUrls.forEachIndexed { index, url ->
                    add(
                        "url_${index}_$url" to SettingsEntity.Preference(
                            icon = Icons.Outlined.Link,
                            title = url,
                            onSwipeToDelete = {
                                val updated = externalUrls.filterIndexed { i, _ -> i != index }
                                onUpdateConfig { copy(externalUrls = serializeExternalUrls(updated)) }
                            },
                            screenPosition = positionOf(index, groupSize)
                        )
                    )
                }
                add(
                    "add_external" to SettingsEntity.Preference(
                        icon = Icons.Outlined.Add,
                        title = addExternalTitle,
                        summary = if (externalUrls.isEmpty()) noExternalSummary else null,
                        onClick = { showAddExternalDialog = true },
                        screenPosition = positionOf(groupSize - 1, groupSize)
                    )
                )
            }
        }
    }

    CloudPage(title = stringResource(Res.string.cloud_networking), count = config.accountLabel()) {
        item(key = "summary") {
            NetworkingSummaryCard(
                effectiveUrl = effectiveUrl,
                localActive = localActive,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(rows.size, key = { rows[it].first }) { SettingsItem(item = rows[it].second) }
        if (autoUrlSwitch) {
            item(key = "footnote") {
                CloudNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.cloud_net_footnote))
            }
        }
    }

    // WiFi SSID picker sheet (pick connected network or manual entry; blank = disable switching)
    WifiSsidPickerSheet(
        state = wifiSheetState,
        currentSsid = wifiSsid,
        onSave = { ssid -> onUpdateConfig { copy(localWifiSsid = ssid) } }
    )

    // Local URL edit dialog
    if (showLocalUrlDialog) {
        var text by remember { mutableStateOf(localUrl) }
        AlertDialog(
            onDismissRequest = { showLocalUrlDialog = false },
            title = { Text(stringResource(Res.string.cloud_net_edit_local_url)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(Res.string.cloud_net_external_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateConfig { copy(localServerUrl = text.trim()) }
                    showLocalUrlDialog = false
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showLocalUrlDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    // Add external URL dialog
    if (showAddExternalDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddExternalDialog = false },
            title = { Text(stringResource(Res.string.cloud_net_add_external)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(Res.string.cloud_net_external_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        val current = parseExternalUrls(config.externalUrls)
                        val updated = current + text.trim()
                        onUpdateConfig { copy(externalUrls = serializeExternalUrls(updated)) }
                    }
                    showAddExternalDialog = false
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddExternalDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Summary card at the top of the networking page: which network is in effect as the title, the
 * resolved server address under it, and a white Local and External tile with the active one
 * marked "In use".
 */
@Composable
private fun NetworkingSummaryCard(
    effectiveUrl: String,
    localActive: Boolean,
    modifier: Modifier = Modifier,
) {
    CloudSummaryCard(
        icon = if (localActive) Icons.Outlined.Wifi else Icons.Outlined.Public,
        title = stringResource(if (localActive) Res.string.cloud_net_local else Res.string.cloud_net_external),
        body = effectiveUrl.ifBlank { stringResource(Res.string.cloud_net_not_configured) },
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetworkModeTile(
                icon = Icons.Outlined.Router,
                label = stringResource(Res.string.cloud_net_local),
                active = localActive,
                modifier = Modifier.weight(1f).testTag(CloudNetLocalTileTag)
            )
            NetworkModeTile(
                icon = Icons.Outlined.Public,
                label = stringResource(Res.string.cloud_net_external),
                active = !localActive,
                modifier = Modifier.weight(1f).testTag(CloudNetExternalTileTag)
            )
        }
    }
}

/** A white stat-style tile; the active network's icon takes the primary colour and says "In use". */
@Composable
private fun NetworkModeTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val iconTint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "tileIcon"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .semantics { selected = active }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Blank on the inactive tile so both tiles keep the same height.
        Text(
            text = if (active) stringResource(Res.string.cloud_net_in_use) else " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
