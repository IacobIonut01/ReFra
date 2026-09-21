package com.dot.gallery.feature_node.presentation.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
fun VaultGateSetupScreen(
    onBack: (() -> Unit)? = null,
    onNone: () -> Unit,
    onDeviceSecurity: () -> Unit,
    onCustomComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customSetupSheetState = rememberAppBottomSheetState()

    var lockAllVaults by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        lockAllVaults = VaultPasswordManager.getVaultLockAll(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SetupWizard(
        icon = Icons.Encrypted,
        title = stringResource(R.string.vault_gate_setup_title),
        subtitle = stringResource(R.string.vault_gate_setup_subtitle),
        bottomBar = {},
        content = {
            Text(
                text = stringResource(R.string.vault_gate_setup_summary),
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
                            VaultPasswordManager.setGateMode(context, GateMode.NONE)
                            onNone()
                        }
                    },
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    text = stringResource(R.string.vault_gate_none)
                )
                SetupButton(
                    onClick = {
                        scope.launch {
                            VaultPasswordManager.setGateMode(context, GateMode.DEVICE)
                            onDeviceSecurity()
                        }
                    },
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    text = stringResource(R.string.vault_gate_device)
                )
                SetupButton(
                    onClick = {
                        scope.launch { customSetupSheetState.show() }
                    },
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    text = stringResource(R.string.vault_gate_custom)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vault_gate_lock_all),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.vault_gate_lock_all_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lockAllVaults,
                            onCheckedChange = { checked ->
                                lockAllVaults = checked
                                scope.launch {
                                    VaultPasswordManager.setVaultLockAll(context, checked)
                                }
                            }
                        )
                    }
                }
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
                VaultPasswordManager.setGateMode(context, GateMode.CUSTOM)
                VaultPasswordManager.setPassword(
                    context,
                    VaultPasswordManager.GATE_UUID,
                    secret,
                    type
                )
                onCustomComplete()
            }
        }
    )
}
