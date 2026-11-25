/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.core.presentation.util.currentWifiSsid
import com.dot.gallery.ui.theme.Shapes
import kotlinx.coroutines.launch
import com.dot.gallery.core.util.or
import com.dot.gallery.core.presentation.components.shape
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.cloud_net_pick_wifi
import com.dot.gallery.generated.resources.cloud_net_wifi_connected
import com.dot.gallery.generated.resources.cloud_net_wifi_disable
import com.dot.gallery.generated.resources.cloud_net_wifi_manual_hint
import com.dot.gallery.generated.resources.cloud_net_wifi_manual_label
import com.dot.gallery.generated.resources.cloud_net_wifi_not_connected
import com.dot.gallery.generated.resources.cloud_net_wifi_sheet_desc


/**
 * Bottom-sheet picker for the local-network Wi-Fi SSID used by cloud URL switching.
 *
 * Shows the currently-connected Wi-Fi network (when readable) as a one-tap pick, plus a manual
 * text entry. Leaving the field empty and saving disables network switching. On open it requests
 * [android.Manifest.permission.ACCESS_FINE_LOCATION] so the connected SSID can be read.
 *
 * [onSave] receives the trimmed SSID (empty string = disable switching).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSsidPickerSheet(
    state: AppBottomSheetState,
    currentSsid: String,
    onSave: (String) -> Unit,
    onPermissionResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ssidRefreshTick by remember { mutableIntStateOf(0) }
    val requestPermission = rememberWifiSsidPermissionRequester { granted ->
        ssidRefreshTick++
        onPermissionResult(granted)
    }

    if (state.isVisible) {
        // Ask for location as soon as the sheet opens so we can read the connected SSID.
        LaunchedEffect(Unit) { requestPermission() }

        var text by remember { mutableStateOf(currentSsid) }
        val connectedSsid = remember(state.isVisible, ssidRefreshTick) { context.currentWifiSsid() }

        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = { scope.launch { state.hide() } },
            dragHandle = { DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.cloud_net_pick_wifi),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    text = stringResource(Res.string.cloud_net_wifi_sheet_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                // Connected-network pick (or a hint when the SSID can't be read).
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = connectedSsid != null) {
                            connectedSsid?.let { text = it }
                        },
                    shape = Shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = if (connectedSsid != null) Icons.Rounded.Wifi
                            else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = connectedSsid?.let {
                                stringResource(Res.string.cloud_net_wifi_connected, it)
                            } ?: stringResource(Res.string.cloud_net_wifi_not_connected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.cloud_net_wifi_manual_label)) },
                    placeholder = { Text(stringResource(Res.string.cloud_net_wifi_manual_hint)) },
                    singleLine = true,
                    shape = Shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val ssid = text.trim()
                        scope.launch {
                            state.hide()
                            onSave(ssid)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.action_save)) }

                TextButton(
                    onClick = {
                        scope.launch {
                            state.hide()
                            onSave("")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.cloud_net_wifi_disable)) }

                Spacer(modifier = Modifier.height(4.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
