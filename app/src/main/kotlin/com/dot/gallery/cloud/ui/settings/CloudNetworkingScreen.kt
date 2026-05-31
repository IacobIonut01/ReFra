/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import kotlinx.serialization.json.Json

private fun parseExternalUrls(json: String): List<String> {
    return try {
        Json.decodeFromString<List<String>>(json)
    } catch (_: Exception) { emptyList() }
}

private fun serializeExternalUrls(urls: List<String>): String {
    return Json.encodeToString(urls)
}

@Composable
fun CloudNetworkingScreen() {
    val settingsVm = hiltViewModel<CloudSettingsViewModel>()
    val config by settingsVm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autoUrlSwitch = config?.autoUrlSwitch ?: false
    val serverUrl = config?.serverUrl ?: ""
    val wifiSsid = config?.localWifiSsid ?: ""
    val localUrl = config?.localServerUrl ?: ""
    val externalUrls = remember(config?.externalUrls) {
        parseExternalUrls(config?.externalUrls ?: "[]")
    }

    var showWifiDialog by remember { mutableStateOf(false) }
    var showLocalUrlDialog by remember { mutableStateOf(false) }
    var showAddExternalDialog by remember { mutableStateOf(false) }

    val settingsList = remember(autoUrlSwitch, serverUrl, wifiSsid, localUrl, externalUrls) {
        buildList {
            // Current server
            add(SettingsEntity.Header(title = context.getString(R.string.cloud_net_connection)))
            add(
                SettingsEntity.Preference(
                    title = context.getString(R.string.cloud_net_current_server),
                    summary = serverUrl.ifBlank { context.getString(R.string.cloud_net_not_configured) },
                    screenPosition = Position.Alone
                )
            )

            // Auto URL switch
            add(SettingsEntity.Header(title = context.getString(R.string.cloud_net_auto_url)))
            add(
                SettingsEntity.SwitchPreference(
                    title = context.getString(R.string.cloud_net_auto_url),
                    summary = context.getString(R.string.cloud_net_auto_url_summary),
                    isChecked = autoUrlSwitch,
                    onCheck = { settingsVm.updateConfig { copy(autoUrlSwitch = it) } },
                    screenPosition = Position.Alone
                )
            )

            if (autoUrlSwitch) {
                // Local network
                add(SettingsEntity.Header(title = context.getString(R.string.cloud_net_local)))
                add(
                    SettingsEntity.Preference(
                        title = context.getString(R.string.cloud_net_wifi_name),
                        summary = wifiSsid.ifBlank { context.getString(R.string.cloud_net_not_configured) },
                        onClick = { showWifiDialog = true },
                        screenPosition = Position.Top
                    )
                )
                add(
                    SettingsEntity.Preference(
                        title = context.getString(R.string.cloud_net_local_url),
                        summary = localUrl.ifBlank { context.getString(R.string.cloud_net_not_configured) },
                        onClick = { showLocalUrlDialog = true },
                        screenPosition = Position.Bottom
                    )
                )

                // External URLs
                add(SettingsEntity.Header(title = context.getString(R.string.cloud_net_external)))
                if (externalUrls.isEmpty()) {
                    add(
                        SettingsEntity.Preference(
                            title = context.getString(R.string.cloud_net_add_external),
                            summary = context.getString(R.string.cloud_net_no_external),
                            onClick = { showAddExternalDialog = true },
                            screenPosition = Position.Alone
                        )
                    )
                } else {
                    externalUrls.forEachIndexed { index, url ->
                        val pos = when {
                            externalUrls.size == 1 -> Position.Alone
                            index == 0 -> Position.Top
                            index == externalUrls.lastIndex -> Position.Bottom
                            else -> Position.Middle
                        }
                        add(
                            SettingsEntity.Preference(
                                title = url,
                                onSwipeToDelete = {
                                    val updated = externalUrls.filter { it != url }
                                    settingsVm.updateConfig { copy(externalUrls = serializeExternalUrls(updated)) }
                                },
                                screenPosition = pos
                            )
                        )
                    }
                    add(
                        SettingsEntity.Preference(
                            title = context.getString(R.string.cloud_net_add_external),
                            onClick = { showAddExternalDialog = true },
                            screenPosition = Position.Alone
                        )
                    )
                }
            }
        }.toMutableStateList()
    }

    BaseSettingsScreen(
        title = stringResource(R.string.cloud_networking),
        settingsList = settingsList
    )

    // WiFi name edit dialog
    if (showWifiDialog) {
        var text by remember { mutableStateOf(wifiSsid) }
        AlertDialog(
            onDismissRequest = { showWifiDialog = false },
            title = { Text(stringResource(R.string.cloud_net_edit_wifi)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.cloud_net_wifi_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsVm.updateConfig { copy(localWifiSsid = text.trim()) }
                    showWifiDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showWifiDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Local URL edit dialog
    if (showLocalUrlDialog) {
        var text by remember { mutableStateOf(localUrl) }
        AlertDialog(
            onDismissRequest = { showLocalUrlDialog = false },
            title = { Text(stringResource(R.string.cloud_net_edit_local_url)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.cloud_net_external_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsVm.updateConfig { copy(localServerUrl = text.trim()) }
                    showLocalUrlDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showLocalUrlDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Add external URL dialog
    if (showAddExternalDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddExternalDialog = false },
            title = { Text(stringResource(R.string.cloud_net_add_external)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.cloud_net_external_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        val current = parseExternalUrls(config?.externalUrls ?: "[]")
                        val updated = current + text.trim()
                        settingsVm.updateConfig { copy(externalUrls = serializeExternalUrls(updated)) }
                    }
                    showAddExternalDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddExternalDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
