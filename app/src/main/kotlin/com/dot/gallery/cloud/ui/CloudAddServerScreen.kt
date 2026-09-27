/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.R
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.auth.InteractiveAuthErrorKind
import com.dot.gallery.cloud.ui.descriptor.CredentialField
import com.dot.gallery.cloud.ui.descriptor.CredentialFieldKind
import com.dot.gallery.cloud.ui.descriptor.CredentialValues
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.presentation.components.AppTextField
import com.dot.gallery.core.presentation.components.SetupButton
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.presentation.setup.components.SetupWizardScaffold
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI

@Composable
fun CloudAddServerScreen(
    providerType: ProviderType,
    configId: Long? = null,
    onSaved: () -> Unit = {},
    viewModel: CloudAccountsViewModel = hiltViewModel()
) {
    val eventHandler = LocalEventHandler.current
    val context = LocalContext.current
    val state by viewModel.addServerState.collectAsStateWithLifecycle()
    val localAlbums by viewModel.localAlbums.collectAsStateWithLifecycle()
    val isEditMode = configId != null && configId > 0
    val descriptor = remember(state.providerType) { ProviderUiDescriptors.forType(state.providerType) }
    val credentialValues = CredentialValues(state.apiKey, state.username, state.password)
    val isUrlValid = state.serverUrl.isBlank() || descriptor.urlRegex.matches(state.serverUrl)
    val hasRequiredCredentials = descriptor.credentialsSatisfied(credentialValues)
    val canSave = state.serverUrl.isNotBlank() && isUrlValid && hasRequiredCredentials && !state.isSaving

    LaunchedEffect(providerType, configId) {
        if (configId != null && configId > 0) {
            viewModel.initEditServer(configId)
        } else {
            viewModel.initAddServer(providerType)
        }
    }

    val syncCompleted by viewModel.syncCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(syncCompleted) {
        if (syncCompleted) onSaved()
    }

    LaunchedEffect(state.browserLaunchEvent?.id) {
        val event = state.browserLaunchEvent ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
            viewModel.consumeBrowserLaunchEvent(event.id)
        } catch (_: ActivityNotFoundException) {
            viewModel.browserLaunchFailed()
        }
    }

    // Wizard steps (add mode only). Sync step is skipped in edit mode.
    val steps = remember(isEditMode) {
        buildList {
            add(WizardStep.SERVER)
            add(WizardStep.CREDENTIALS)
            add(WizardStep.NETWORKING)
            if (!isEditMode) add(WizardStep.SYNC)
            add(WizardStep.REVIEW)
        }
    }
    var stepIndex by remember { mutableStateOf(0) }
    val safeIndex = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val canAdvance = when (step) {
        WizardStep.SERVER -> state.serverUrl.isNotBlank() && isUrlValid
        WizardStep.CREDENTIALS -> hasRequiredCredentials && state.testSuccess
        WizardStep.NETWORKING -> !state.autoUrlSwitch || state.localServerUrl.isNotBlank()
        WizardStep.SYNC -> true
        WizardStep.REVIEW -> canSave
    }

    val goBack: () -> Unit = {
        if (isEditMode || safeIndex == 0) eventHandler.navigateUpAction()
        else stepIndex = (safeIndex - 1).coerceAtLeast(0)
    }

    // Warn once before proceeding with an unencrypted HTTP server URL. HTTP is allowed
    // (self-hosted servers over trusted tunnels often use it) but the user must confirm (#990).
    val serverUrlTrimmed = state.serverUrl.trim()
    val isInsecureHttp = serverUrlTrimmed.startsWith("http://", ignoreCase = true)
    var httpAckUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var pendingProceed by remember { mutableStateOf<(() -> Unit)?>(null) }

    val guardHttp: (() -> Unit) -> Unit = { proceed ->
        if (isInsecureHttp && httpAckUrl != serverUrlTrimmed) {
            pendingProceed = proceed
            showHttpWarning = true
        } else {
            proceed()
        }
    }

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = {
                showHttpWarning = false
                pendingProceed = null
            },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.cloud_http_warning_title)) },
            text = { Text(stringResource(R.string.cloud_http_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    httpAckUrl = serverUrlTrimmed
                    showHttpWarning = false
                    val action = pendingProceed
                    pendingProceed = null
                    action?.invoke()
                }) { Text(stringResource(R.string.continue_string)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHttpWarning = false
                    pendingProceed = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    SetupWizardScaffold(
        showBack = true,
        onBack = goBack,
        stepNumber = if (isEditMode) 0 else safeIndex + 1,
        totalSteps = if (isEditMode) 0 else steps.size,
        showProgress = !isEditMode,
        title = if (isEditMode) stringResource(R.string.cloud_credentials)
                else stringResource(step.titleRes),
        subtitle = if (isEditMode) null else step.subtitleRes?.let { stringResource(it) },
        bottomBar = {
            if (isEditMode) {
                SetupButton(
                    text = if (state.isSaving) stringResource(R.string.cloud_save) + "…"
                           else stringResource(R.string.cloud_save),
                    enabled = canSave,
                    applyHorizontalPadding = false,
                    applyBottomPadding = false,
                    applyInsets = false,
                    applyNavigationPadding = false,
                    onClick = { guardHttp { viewModel.saveServer() } }
                )
            } else {
                WizardBottomBar(
                    isLast = step == WizardStep.REVIEW,
                    canAdvance = canAdvance,
                    isSaving = state.isSaving,
                    onNext = {
                        // Gate the first "Next" (leaving the server step) behind the HTTP warning
                        // so the user is warned before continuing with an insecure URL (#990).
                        guardHttp { stepIndex = (safeIndex + 1).coerceAtMost(steps.lastIndex) }
                    },
                    onSave = { guardHttp { viewModel.saveServer() } }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditMode) {
                ServerStep(state, descriptor, isUrlValid, viewModel)
                CredentialsStep(
                    state,
                    descriptor,
                    credentialValues,
                    viewModel.supportsInteractiveAuth(state.providerType),
                    viewModel
                )
            } else {
                AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = {
                        val forward = targetState >= initialState
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(300)) { full -> dir * full } + fadeIn(tween(300)))
                            .togetherWith(
                                slideOutHorizontally(tween(300)) { full -> -dir * full } + fadeOut(tween(300))
                            )
                    },
                    label = "cloud-add-steps"
                ) { idx ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (steps[idx]) {
                            WizardStep.SERVER -> ServerStep(state, descriptor, isUrlValid, viewModel)
                            WizardStep.CREDENTIALS -> CredentialsStep(
                                state,
                                descriptor,
                                credentialValues,
                                viewModel.supportsInteractiveAuth(state.providerType),
                                viewModel
                            )
                            WizardStep.NETWORKING -> NetworkingStep(state, viewModel)
                            WizardStep.SYNC -> SyncStep(state, localAlbums, viewModel)
                            WizardStep.REVIEW -> ReviewStep(state, descriptor)
                        }
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private enum class WizardStep(val titleRes: Int, val subtitleRes: Int? = null) {
    SERVER(R.string.cloud_step_server, R.string.cloud_step_server_desc),
    CREDENTIALS(R.string.cloud_step_credentials, R.string.cloud_step_credentials_desc),
    NETWORKING(R.string.cloud_step_networking, R.string.cloud_step_networking_desc),
    SYNC(R.string.cloud_step_sync, R.string.cloud_step_sync_desc),
    REVIEW(R.string.cloud_step_review, R.string.cloud_step_review_desc)
}

@Composable
private fun WizardBottomBar(
    isLast: Boolean,
    canAdvance: Boolean,
    isSaving: Boolean,
    onNext: () -> Unit,
    onSave: () -> Unit
) {
    SetupButton(
        text = if (isLast) {
            if (isSaving) stringResource(R.string.cloud_save) + "…"
            else stringResource(R.string.cloud_save)
        } else stringResource(R.string.cloud_next),
        enabled = canAdvance,
        applyHorizontalPadding = false,
        applyBottomPadding = false,
        applyInsets = false,
        applyNavigationPadding = false,
        onClick = if (isLast) onSave else onNext
    )
}

/**
 * Frosted, semi-transparent field background for the setup wizard: blurs the animated
 * background behind the input (via the [LocalHazeState] sourced in [SetupWizardScaffold])
 * instead of a solid dark box, while keeping text contrast. The [AppTextField] container is
 * drawn transparent so the soft blur shows through.
 */
@Composable
private fun frostedFieldModifier(): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(16.dp))
    .hazeEffectScaled(state = LocalHazeState.current, style = LocalHazeBlurStyle.current)

@Composable
private fun ServerStep(
    state: AddServerUiState,
    descriptor: com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptor,
    isUrlValid: Boolean,
    viewModel: CloudAccountsViewModel
) {
    AppTextField(
        value = state.serverUrl,
        onValueChange = viewModel::updateServerUrl,
        label = { Text(stringResource(R.string.cloud_server_url)) },
        placeholder = { Text(stringResource(descriptor.urlHintRes)) },
        modifier = frostedFieldModifier(),
        containerColor = Color.Transparent,
        singleLine = true,
        isError = state.serverUrl.isNotBlank() && !isUrlValid,
        supportingText = if (state.serverUrl.isNotBlank() && !isUrlValid) {
            { Text(stringResource(R.string.cloud_url_invalid)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
    if (descriptor.isLanOnly) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.cloud_network_share_lan_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    descriptor.setupHintRes?.let { hintRes ->
        Spacer(Modifier.height(8.dp))
        SetupHelpCard(hintText = stringResource(hintRes))
    }
}

@Composable
private fun CredentialsStep(
    state: AddServerUiState,
    descriptor: com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptor,
    credentialValues: CredentialValues,
    supportsInteractiveAuth: Boolean,
    viewModel: CloudAccountsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLocalNetworkRationale by remember { mutableStateOf(false) }
    var pendingConnectionTest by remember { mutableStateOf<(() -> Unit)?>(null) }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingConnectionTest
        pendingConnectionTest = null
        if (granted) action?.invoke()
    }
    val testConnection: () -> Unit = {
        val action = viewModel::testConnection
        if (Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            scope.launch {
                if (isDirectLocalResource(context, state.serverUrl)) {
                    pendingConnectionTest = action
                    showLocalNetworkRationale = true
                } else {
                    action()
                }
            }
        }
    }

    if (showLocalNetworkRationale) {
        AlertDialog(
            onDismissRequest = {
                showLocalNetworkRationale = false
                pendingConnectionTest?.invoke()
                pendingConnectionTest = null
            },
            title = { Text(stringResource(R.string.local_network_permission_dialog_title)) },
            text = { Text(stringResource(R.string.local_network_permission_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocalNetworkRationale = false
                    localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }) {
                    Text(stringResource(R.string.setup_grant_permissions))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocalNetworkRationale = false
                    pendingConnectionTest?.invoke()
                    pendingConnectionTest = null
                }) {
                    Text(stringResource(R.string.local_network_permission_not_now))
                }
            }
        )
    }

    AppTextField(
        value = state.displayName,
        onValueChange = viewModel::updateDisplayName,
        label = { Text(stringResource(R.string.cloud_display_name)) },
        placeholder = { Text(stringResource(R.string.cloud_display_name_hint)) },
        modifier = frostedFieldModifier(),
        containerColor = Color.Transparent,
        singleLine = true
    )

    val authState = state.authenticationState
    var showManual by rememberSaveable(state.providerType) { mutableStateOf(!supportsInteractiveAuth) }
    LaunchedEffect(authState) {
        if (authState is CloudAuthenticationState.Failed && authState.unsupported) showManual = true
    }

    if (supportsInteractiveAuth) {
        Spacer(Modifier.height(16.dp))
        val authBusy = authState is CloudAuthenticationState.Starting ||
            authState is CloudAuthenticationState.WaitingForBrowser ||
            authState is CloudAuthenticationState.Polling ||
            authState is CloudAuthenticationState.Verifying
        SetupButton(
            text = when (authState) {
                CloudAuthenticationState.Starting -> stringResource(R.string.cloud_nextcloud_starting)
                CloudAuthenticationState.WaitingForBrowser,
                CloudAuthenticationState.Polling -> stringResource(R.string.cloud_nextcloud_waiting)
                CloudAuthenticationState.Verifying -> stringResource(R.string.cloud_nextcloud_verifying)
                is CloudAuthenticationState.Verified -> stringResource(R.string.cloud_nextcloud_connected)
                else -> stringResource(R.string.cloud_nextcloud_sign_in)
            },
            enabled = !authBusy && authState !is CloudAuthenticationState.Verified,
            applyHorizontalPadding = false,
            applyBottomPadding = false,
            applyInsets = false,
            onClick = viewModel::startInteractiveAuth
        )
        when (authState) {
            CloudAuthenticationState.Starting,
            CloudAuthenticationState.WaitingForBrowser,
            CloudAuthenticationState.Polling,
            CloudAuthenticationState.Verifying -> {
                SyncLoadingRow(
                    if (authState is CloudAuthenticationState.Polling ||
                        authState is CloudAuthenticationState.WaitingForBrowser
                    ) stringResource(R.string.cloud_nextcloud_grant_access)
                    else stringResource(R.string.cloud_nextcloud_verifying)
                )
                TextButton(onClick = viewModel::cancelInteractiveAuth) {
                    Text(stringResource(R.string.cancel))
                }
            }
            is CloudAuthenticationState.Verified -> AuthResultRow(
                success = true,
                text = stringResource(R.string.cloud_nextcloud_signed_in_as, authState.username)
            )
            is CloudAuthenticationState.Failed -> AuthResultRow(
                success = false,
                text = if (authState.unsupported) {
                    stringResource(R.string.cloud_nextcloud_flow_unsupported)
                } else when (authState.kind) {
                    InteractiveAuthErrorKind.INVALID_URL -> stringResource(R.string.cloud_nextcloud_invalid_url)
                    InteractiveAuthErrorKind.UNTRUSTED_RESPONSE -> stringResource(R.string.cloud_nextcloud_untrusted)
                    InteractiveAuthErrorKind.AUTHENTICATION -> stringResource(R.string.cloud_nextcloud_auth_failed)
                    InteractiveAuthErrorKind.RATE_LIMITED -> stringResource(R.string.cloud_nextcloud_rate_limited)
                    InteractiveAuthErrorKind.SERVER -> stringResource(R.string.cloud_nextcloud_server_error)
                    InteractiveAuthErrorKind.NETWORK -> stringResource(R.string.cloud_nextcloud_network_error)
                    InteractiveAuthErrorKind.TLS -> stringResource(R.string.cloud_nextcloud_tls_error)
                    InteractiveAuthErrorKind.MALFORMED_RESPONSE -> stringResource(R.string.cloud_nextcloud_invalid_response)
                    else -> authState.message
                }
            )
            CloudAuthenticationState.Expired -> AuthResultRow(
                success = false,
                text = stringResource(R.string.cloud_nextcloud_expired)
            )
            CloudAuthenticationState.Cancelled -> AuthResultRow(
                success = false,
                text = stringResource(R.string.cloud_nextcloud_cancelled)
            )
            else -> Unit
        }
        TextButton(onClick = { showManual = !showManual }) {
            Text(stringResource(R.string.cloud_nextcloud_manual))
            Icon(
                if (showManual) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null
            )
        }
    }

    AnimatedVisibility(showManual) {
        Column {
            descriptor.credentialFields.forEach { field ->
                if (!field.visibleWhen(credentialValues)) return@forEach
                val value = when (field.kind) {
                    CredentialFieldKind.API_KEY -> state.apiKey
                    CredentialFieldKind.USERNAME -> state.username
                    CredentialFieldKind.PASSWORD -> state.password
                }
                val onValueChange: (String) -> Unit = when (field.kind) {
                    CredentialFieldKind.API_KEY -> viewModel::updateApiKey
                    CredentialFieldKind.USERNAME -> viewModel::updateUsername
                    CredentialFieldKind.PASSWORD -> viewModel::updatePassword
                }
                Spacer(Modifier.height(16.dp))
                CredentialTextField(field = field, value = value, onValueChange = onValueChange)
            }
            Spacer(Modifier.height(16.dp))
            SetupButton(
                text = if (state.isTesting) stringResource(R.string.cloud_testing)
                    else stringResource(R.string.cloud_test_connection),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                enabled = !state.isTesting && state.serverUrl.isNotBlank() &&
                    descriptor.credentialsSatisfied(credentialValues),
                applyHorizontalPadding = false,
                applyBottomPadding = false,
                applyInsets = false,
                onClick = testConnection
            )
        }
    }

    if (!supportsInteractiveAuth) {
        state.testResult?.let {
            AuthResultRow(success = state.testSuccess, text = it)
        }
    }
}

@Composable
private fun AuthResultRow(success: Boolean, text: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (success) Icons.Default.Check else Icons.Default.Error,
            contentDescription = null,
            tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

private suspend fun isDirectLocalResource(context: Context, serverUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        val activeCapabilities = connectivityManager?.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        if (activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            return@withContext false
        }

        val host = runCatching { URI(serverUrl).host }.getOrNull()
            ?: runCatching { URI("https://$serverUrl").host }.getOrNull()
            ?: return@withContext false
        runCatching {
            InetAddress.getAllByName(host).any {
                it.isSiteLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress
            }
        }.getOrDefault(false)
    }

/**
 * Credential input that, for secret fields (password / API key / secrets), renders a trailing
 * eye toggle so the user can reveal or hide the entered value.
 */
@Composable
private fun CredentialTextField(
    field: CredentialField,
    value: String,
    onValueChange: (String) -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(field.labelRes)) },
        placeholder = field.hintRes?.let { { Text(stringResource(it)) } },
        modifier = frostedFieldModifier(),
        containerColor = Color.Transparent,
        singleLine = true,
        visualTransformation = if (field.isSecret && !revealed) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
        trailingIcon = if (field.isSecret) {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.cloud_hide_secret
                            else R.string.cloud_show_secret
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null
    )
}

@Composable
private fun NetworkingStep(
    state: AddServerUiState,
    viewModel: CloudAccountsViewModel
) {
    val scope = rememberCoroutineScope()
    val wifiSheetState = rememberAppBottomSheetState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cloud_net_auto_url))
            Text(
                stringResource(R.string.cloud_net_auto_url_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.autoUrlSwitch,
            onCheckedChange = viewModel::updateAutoUrlSwitch
        )
    }
    AnimatedVisibility(visible = state.autoUrlSwitch) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField(
                value = state.localServerUrl,
                onValueChange = viewModel::updateLocalServerUrl,
                label = { Text(stringResource(R.string.cloud_net_local_url)) },
                placeholder = { Text(stringResource(R.string.cloud_net_local_url_hint)) },
                modifier = frostedFieldModifier(),
                containerColor = Color.Transparent,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            // Clickable field that opens the SSID picker sheet (blank = switching disabled).
            Column(
                modifier = frostedFieldModifier()
                    .clickable { scope.launch { wifiSheetState.show() } }
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.cloud_net_wifi_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.localWifiSsid.ifBlank {
                        stringResource(R.string.cloud_net_wifi_blank_summary)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    WifiSsidPickerSheet(
        state = wifiSheetState,
        currentSsid = state.localWifiSsid,
        onSave = viewModel::updateLocalWifiSsid
    )
}

/** Final stage: choose the local folders whose media will be uploaded to this account. */
@Composable
private fun SyncStep(
    state: AddServerUiState,
    localAlbums: List<Album>,
    viewModel: CloudAccountsViewModel
) {
    SyncSectionHeader(
        title = stringResource(R.string.cloud_sync_folders_title),
        subtitle = stringResource(R.string.cloud_sync_folders_desc)
    )
    if (localAlbums.isEmpty()) {
        SyncEmptyHint(stringResource(R.string.cloud_sync_no_local))
    } else {
        SelectionCard {
            localAlbums.forEach { album ->
                SelectableRow(
                    icon = Icons.Outlined.Folder,
                    title = album.label,
                    subtitle = stringResource(R.string.cloud_sync_item_count, album.count),
                    checked = album.id in state.selectedLocalAlbumIds,
                    onToggle = { viewModel.toggleLocalAlbum(album.id) }
                )
            }
        }
    }

}

@Composable
private fun SyncSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SyncLoadingRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content
    )
}

@Composable
private fun SelectableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ReviewStep(
    state: AddServerUiState,
    descriptor: com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptor
) {
    val none = stringResource(R.string.cloud_review_none)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewRow(stringResource(R.string.cloud_review_provider), descriptor.providerType.displayName)
        ReviewRow(stringResource(R.string.cloud_display_name), state.displayName.ifBlank { none })
        ReviewRow(stringResource(R.string.cloud_review_external_url), state.serverUrl.ifBlank { none })
        if (state.autoUrlSwitch) {
            ReviewRow(stringResource(R.string.cloud_review_local_url), state.localServerUrl.ifBlank { none })
            ReviewRow(
                stringResource(R.string.cloud_net_wifi_name),
                state.localWifiSsid.ifBlank { stringResource(R.string.cloud_net_ssid_optional) }
            )
        }
        ReviewRow(
            stringResource(R.string.cloud_review_folders),
            if (state.selectedLocalAlbumIds.isEmpty()) none
            else stringResource(R.string.cloud_sync_count_selected, state.selectedLocalAlbumIds.size)
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SetupHelpCard(hintText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cloud_setup_help),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
