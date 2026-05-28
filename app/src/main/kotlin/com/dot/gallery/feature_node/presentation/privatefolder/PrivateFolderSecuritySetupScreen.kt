/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.privatefolder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.core.presentation.components.SetupWizard
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.components.VaultPasswordSetupSheet
import com.dot.gallery.feature_node.presentation.vault.utils.GateMode
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import com.dot.gallery.ui.core.Icons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlinx.coroutines.launch

@Composable
fun PrivateFolderSecuritySetupScreen(
    onBack: (() -> Unit)? = null,
    onNone: () -> Unit,
    onDeviceSecurity: () -> Unit,
    onCustomComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customSetupSheetState = rememberAppBottomSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        SetupWizard(
            icon = Icons.Encrypted,
            title = stringResource(R.string.private_folder_security_title),
            subtitle = stringResource(R.string.private_folder_security_subtitle),
            bottomBar = {},
            content = {
                Text(
                    text = stringResource(R.string.private_folder_security_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SetupButton(
                        onClick = {
                            scope.launch {
                                VaultPasswordManager.setPrivateFolderMode(context, GateMode.NONE)
                                onNone()
                            }
                        },
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        text = stringResource(R.string.private_folder_security_none)
                    )
                    SetupButton(
                        onClick = {
                            scope.launch {
                                VaultPasswordManager.setPrivateFolderMode(context, GateMode.DEVICE)
                                onDeviceSecurity()
                            }
                        },
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        text = stringResource(R.string.private_folder_security_device)
                    )
                    SetupButton(
                        onClick = {
                            scope.launch { customSetupSheetState.show() }
                        },
                        applyHorizontalPadding = false,
                        applyBottomPadding = false,
                        applyInsets = false,
                        text = stringResource(R.string.private_folder_security_custom)
                    )
                }
            }
        )

        if (onBack != null) {
            NavigationBackButton(
                modifier = Modifier.statusBarsPadding(),
                forcedAction = onBack
            )
        }
    }

    VaultPasswordSetupSheet(
        state = customSetupSheetState,
        onSecretSet = { type, secret ->
            scope.launch {
                VaultPasswordManager.setPrivateFolderMode(context, GateMode.CUSTOM)
                VaultPasswordManager.setPassword(
                    context,
                    VaultPasswordManager.PRIVATE_FOLDER_UUID,
                    secret,
                    type
                )
                onCustomComplete()
            }
        }
    )
}
