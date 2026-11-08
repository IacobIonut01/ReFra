/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.cast.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetColumn
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetDismissConfirmActions
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.sheetRowPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.dot.gallery.app.presentation.cast.CastPermission
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cast_open_settings
import com.dot.gallery.generated.resources.cast_permissions_message
import com.dot.gallery.generated.resources.cast_permissions_required
import com.dot.gallery.generated.resources.close
import com.dot.gallery.generated.resources.setup_grant_permissions


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastPermissionsDialog(
    permissions: List<CastPermission>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val missingLocalNetworkPermission = Build.VERSION.SDK_INT >= 37 && permissions.any {
        it.permission == Manifest.permission.ACCESS_LOCAL_NETWORK && !it.granted
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            CastPermissionsContent(
                permissions = permissions.map {
                    CastPermissionRowState(stringResource(it.labelRes), stringResource(it.descriptionRes), it.granted)
                },
                confirmText = stringResource(
                    if (missingLocalNetworkPermission) Res.string.setup_grant_permissions
                    else Res.string.cast_open_settings
                ),
                onDismiss = onDismiss,
                onConfirm = {
                    if (missingLocalNetworkPermission) {
                        localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    } else {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                },
            )
        }
    }
}

/** One permission line of [CastPermissionsContent]. */
data class CastPermissionRowState(val label: String, val description: String, val granted: Boolean)

/** Stateless body of [CastPermissionsDialog]: badge and message, one row per permission, actions. */
@Composable
fun ColumnScope.CastPermissionsContent(
    permissions: List<CastPermissionRowState>,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SheetHeader(
        title = stringResource(Res.string.cast_permissions_required),
        body = stringResource(Res.string.cast_permissions_message),
        icon = Icons.Outlined.Cast,
    )
    permissions.forEachIndexed { index, permission ->
        SheetOptionRow(
            title = permission.label,
            summary = permission.description,
            position = sheetRowPosition(index, permissions.size),
            leading = {
                Icon(
                    imageVector = if (permission.granted) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = if (permission.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            },
        )
    }
    SheetDismissConfirmActions(
        dismissText = stringResource(Res.string.close),
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
    )
}
