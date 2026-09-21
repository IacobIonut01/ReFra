package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.presentation.common.components.OptionItem
import com.dot.gallery.feature_node.presentation.common.components.OptionLayout
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.vault.utils.GateMode
import com.dot.gallery.feature_node.presentation.vault.utils.VaultAuthType
import com.dot.gallery.feature_node.presentation.vault.utils.VaultCredentialStatus
import com.dot.gallery.feature_node.presentation.vault.utils.VaultGateAction
import com.dot.gallery.feature_node.presentation.vault.utils.VaultGatePolicy
import com.dot.gallery.feature_node.presentation.vault.utils.VaultPasswordManager
import com.dot.gallery.feature_node.presentation.vault.utils.VerifyResult
import com.dot.gallery.feature_node.presentation.vault.utils.rememberBiometricState
import com.dot.gallery.ui.core.Icons
import com.dot.gallery.ui.core.icons.Encrypted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectVaultSheet(
    state: AppBottomSheetState,
    vaultState: VaultState,
    excludeVault: Vault? = null,
    requireGateAuth: Boolean = true,
    onCreateVault: (() -> Unit)? = null,
    onVaultSelected: (Vault) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaults = remember(vaultState, excludeVault) {
        if (excludeVault != null) vaultState.vaults.filter { it.uuid != excludeVault.uuid }
        else vaultState.vaults
    }
    val vaultOptions = remember(vaults, state) {
        vaults.map { vault ->
            OptionItem(
                text = vault.name,
                onClick = {
                    onVaultSelected(vault)
                    scope.launch {
                        state.hide()
                    }
                }
            )
        }.toMutableStateList()
    }

    // The selector gate (GateMode) protects the vault list itself, so this sheet
    // re-challenges on every open and only reveals names after it is satisfied.
    var gateAuthenticated by remember { mutableStateOf(false) }
    var gateChallenge by remember { mutableStateOf<GateMode?>(null) }
    var gateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var gatePasswordError by remember { mutableStateOf<String?>(null) }
    var gateDeviceAuthPending by remember { mutableStateOf(false) }

    val wrongPasswordAttemptsStr = stringResource(R.string.vault_wrong_password_attempts)
    val lockedOutStr = stringResource(R.string.vault_locked_out)

    val gateBiometricState = rememberBiometricState(
        title = stringResource(R.string.biometric_authentication),
        subtitle = stringResource(R.string.verify_identity),
        onSuccess = {
            gateDeviceAuthPending = false
            gateChallenge = null
            gateAuthenticated = true
        },
        onFailed = {
            val wasPending = gateDeviceAuthPending
            gateDeviceAuthPending = false
            if (wasPending) scope.launch { state.hide() }
        }
    )

    LaunchedEffect(state.isVisible) {
        gateAuthenticated = false
        gateChallenge = null
        gateAuthType = null
        gatePasswordError = null
        gateDeviceAuthPending = false
        if (!state.isVisible) {
            gateBiometricState.cancelAuthentication()
            return@LaunchedEffect
        }
        if (!requireGateAuth) {
            gateAuthenticated = true
            return@LaunchedEffect
        }
        val mode = VaultPasswordManager.getGateMode(context)
        val credential = if (mode == GateMode.CUSTOM) {
            VaultPasswordManager.getCredentialStatus(context, VaultPasswordManager.GATE_UUID)
        } else null
        when (val action = VaultGatePolicy.resolve(mode, gateBiometricState.isSupported, credential)) {
            VaultGateAction.Reveal -> gateAuthenticated = true
            VaultGateAction.ChallengeDevice -> {
                gateChallenge = GateMode.DEVICE
                gateDeviceAuthPending = true
                gateBiometricState.authenticate()
            }
            is VaultGateAction.ChallengeCustom -> {
                gateChallenge = GateMode.CUSTOM
                gateAuthType = action.authType
            }
            VaultGateAction.Deny -> state.hide()
        }
    }

    if (state.isVisible) {
        ModalBottomSheet(
            sheetState = state.sheetState,
            onDismissRequest = {
                scope.launch {
                    state.hide()
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            dragHandle = { DragHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            if (!gateAuthenticated) {
                if (gateChallenge == GateMode.CUSTOM) {
                    VaultPasswordUnlockDialog(
                        authType = gateAuthType,
                        onDismiss = { scope.launch { state.hide() } },
                        onSubmit = { secret ->
                            scope.launch {
                                val result = VaultPasswordManager.verifyPassword(
                                    context,
                                    VaultPasswordManager.GATE_UUID,
                                    secret
                                )
                                when (result) {
                                    is VerifyResult.Success -> {
                                        gateChallenge = null
                                        gatePasswordError = null
                                        gateAuthenticated = true
                                    }
                                    is VerifyResult.Failed -> {
                                        gatePasswordError = String.format(
                                            wrongPasswordAttemptsStr,
                                            result.attemptsLeft
                                        )
                                    }
                                    is VerifyResult.LockedOut -> {
                                        val seconds = (result.cooldownMs / 1000).coerceAtLeast(1)
                                        gatePasswordError = String.format(lockedOutStr, seconds)
                                    }
                                }
                            }
                        },
                        errorMessage = gatePasswordError
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Encrypted,
                            contentDescription = stringResource(R.string.vault_icon_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.vault_unlock),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@ModalBottomSheet
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing
                            )
                        ) {
                            append(stringResource(R.string.select_a_vault))
                        }
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                )
                if (vaultOptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.vault_create_first),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (onCreateVault != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SetupButton(
                            onClick = {
                                scope.launch { state.hide() }
                                onCreateVault()
                            },
                            applyHorizontalPadding = false,
                            applyBottomPadding = false,
                            applyInsets = false,
                            text = stringResource(R.string.vault_create_vault)
                        )
                    }
                } else {
                    OptionLayout(
                        modifier = Modifier.fillMaxWidth(),
                        optionList = vaultOptions
                    )
                }
            }
        }
    }
}