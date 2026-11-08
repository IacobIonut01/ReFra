/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.cast.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.cast.CastSessionState
import com.dot.gallery.app.presentation.cast.FCastDevice
import com.dot.gallery.app.presentation.cast.FCastProtocol
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetActions
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetSectionLabel
import com.dot.gallery.core.presentation.components.SheetTextField
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.cast_connect_button
import com.dot.gallery.generated.resources.cast_connected_to
import com.dot.gallery.generated.resources.cast_disconnect
import com.dot.gallery.generated.resources.cast_enter_ip
import com.dot.gallery.generated.resources.cast_ip_hint
import com.dot.gallery.generated.resources.cast_manual_connect
import com.dot.gallery.generated.resources.cast_port_hint
import com.dot.gallery.generated.resources.cast_searching
import com.dot.gallery.generated.resources.cast_select_device
import com.dot.gallery.generated.resources.cast_stop_current
import com.dot.gallery.generated.resources.cast_this_media
import com.dot.gallery.generated.resources.close
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FCastDevicePickerDialog(
    state: CastSessionState,
    onDeviceSelected: (FCastDevice) -> Unit,
    onCastMedia: () -> Unit = {},
    onStopCasting: () -> Unit = {},
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            FCastDevicePickerContent(
                connectedDeviceName = state.connectedDevice?.name,
                isCasting = state.castingMediaId != null,
                isDiscovering = state.isDiscovering,
                isConnecting = state.isConnecting,
                devices = state.discoveredDevices,
                connectionError = state.connectionError,
                onDeviceSelected = onDeviceSelected,
                onCastMedia = { onCastMedia(); onDismiss() },
                onStopCasting = { onStopCasting(); onDismiss() },
                onDisconnect = onDisconnect,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Stateless body of [FCastDevicePickerDialog]. While connected it lists the session actions;
 * otherwise discovered receivers, a progress line while searching, and manual IP entry.
 */
@Composable
fun ColumnScope.FCastDevicePickerContent(
    connectedDeviceName: String?,
    isCasting: Boolean,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    devices: List<FCastDevice>,
    connectionError: String?,
    onDeviceSelected: (FCastDevice) -> Unit,
    onCastMedia: () -> Unit,
    onStopCasting: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (connectedDeviceName != null) {
        SheetHeader(
            title = stringResource(Res.string.cast_connected_to, connectedDeviceName),
            icon = Icons.Outlined.CastConnected,
        )
        val count = if (isCasting) 2 else 1
        SheetOptionRow(
            title = stringResource(Res.string.cast_this_media),
            icon = Icons.Outlined.PlayCircleOutline,
            position = sheetRowPosition(0, count),
            trailing = SheetRowTrailing.Chevron,
            onClick = onCastMedia,
        )
        if (isCasting) {
            SheetOptionRow(
                title = stringResource(Res.string.cast_stop_current),
                icon = Icons.Outlined.StopCircle,
                position = sheetRowPosition(1, count),
                trailing = SheetRowTrailing.Chevron,
                onClick = onStopCasting,
            )
        }
        SheetActions {
            SheetButton(text = stringResource(Res.string.close), onClick = onDismiss, style = SheetButtonStyle.Tonal, modifier = Modifier.weight(1f))
            SheetButton(
                text = stringResource(Res.string.cast_disconnect),
                onClick = onDisconnect,
                style = SheetButtonStyle.Error,
                icon = Icons.Outlined.LinkOff,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    SheetHeader(
        title = stringResource(Res.string.cast_select_device),
        body = if (isDiscovering) stringResource(Res.string.cast_searching) else null,
    )
    if (isDiscovering) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp))
    }
    devices.forEachIndexed { index, device ->
        SheetOptionRow(
            title = device.name,
            summary = "${device.host}:${device.port}",
            icon = Icons.Outlined.Tv,
            position = sheetRowPosition(index, devices.size),
            enabled = !isConnecting,
            trailing = if (isConnecting) SheetRowTrailing.Custom { CircularProgressIndicator(Modifier.size(20.dp)) }
            else SheetRowTrailing.Chevron,
            onClick = { onDeviceSelected(device) },
        )
    }
    if (connectionError != null) {
        Text(
            text = connectionError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
    }

    SheetSectionLabel(stringResource(Res.string.cast_manual_connect))
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("") }
    SheetTextField(
        value = manualIp,
        onValueChange = { manualIp = it.trim() },
        label = stringResource(Res.string.cast_enter_ip),
        placeholder = stringResource(Res.string.cast_ip_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetTextField(
            value = manualPort,
            onValueChange = { manualPort = it.filter { c -> c.isDigit() } },
            label = stringResource(Res.string.cast_port_hint),
            placeholder = FCastProtocol.DEFAULT_PORT.toString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        SheetButton(
            text = stringResource(Res.string.cast_connect_button),
            onClick = {
                if (manualIp.isNotBlank()) {
                    val port = manualPort.toIntOrNull() ?: FCastProtocol.DEFAULT_PORT
                    onDeviceSelected(FCastDevice(name = manualIp, host = manualIp, port = port))
                }
            },
            enabled = manualIp.isNotBlank() && !isConnecting,
            style = SheetButtonStyle.Tonal,
        )
    }
    SheetActions {
        SheetButton(text = stringResource(Res.string.cancel), onClick = onDismiss, style = SheetButtonStyle.Tonal, modifier = Modifier.weight(1f))
    }
}
