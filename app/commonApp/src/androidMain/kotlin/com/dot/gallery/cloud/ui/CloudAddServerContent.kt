/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.layout.ContentScale
import com.dot.gallery.cloud.ui.components.CloudNote
import com.dot.gallery.cloud.ui.components.ProviderBadge
import com.dot.gallery.cloud.ui.components.cloudContentWidth
import com.dot.gallery.cloud.ui.components.positionOf
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.ui.components.SettingsItem
import com.github.panpf.sketch.AsyncImage
import com.dot.gallery.generated.resources.cloud_add_provider
import com.dot.gallery.generated.resources.cloud_api_key
import com.dot.gallery.generated.resources.cloud_back
import com.dot.gallery.generated.resources.cloud_connect
import com.dot.gallery.generated.resources.cloud_wizard_review_note
import com.dot.gallery.generated.resources.cloud_wizard_step_named
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dot.gallery.cloud.core.auth.InteractiveAuthErrorKind
import com.dot.gallery.cloud.ui.descriptor.CredentialField
import com.dot.gallery.cloud.ui.descriptor.CredentialFieldKind
import com.dot.gallery.cloud.ui.descriptor.CredentialValues
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptor
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.presentation.components.AppTextField
import com.dot.gallery.domain.model.Album
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cancel
import com.dot.gallery.generated.resources.cloud_credentials
import com.dot.gallery.generated.resources.cloud_display_name
import com.dot.gallery.generated.resources.cloud_display_name_hint
import com.dot.gallery.generated.resources.cloud_hide_secret
import com.dot.gallery.generated.resources.cloud_http_warning_message
import com.dot.gallery.generated.resources.cloud_http_warning_title
import com.dot.gallery.generated.resources.cloud_net_auto_url
import com.dot.gallery.generated.resources.cloud_net_auto_url_summary
import com.dot.gallery.generated.resources.cloud_net_local_url
import com.dot.gallery.generated.resources.cloud_net_local_url_hint
import com.dot.gallery.generated.resources.cloud_net_wifi_blank_summary
import com.dot.gallery.generated.resources.cloud_net_wifi_name
import com.dot.gallery.generated.resources.cloud_network_share_lan_note
import com.dot.gallery.generated.resources.cloud_next
import com.dot.gallery.generated.resources.cloud_nextcloud_auth_failed
import com.dot.gallery.generated.resources.cloud_nextcloud_cancelled
import com.dot.gallery.generated.resources.cloud_nextcloud_connected
import com.dot.gallery.generated.resources.cloud_nextcloud_expired
import com.dot.gallery.generated.resources.cloud_nextcloud_flow_unsupported
import com.dot.gallery.generated.resources.cloud_nextcloud_grant_access
import com.dot.gallery.generated.resources.cloud_nextcloud_invalid_response
import com.dot.gallery.generated.resources.cloud_nextcloud_invalid_url
import com.dot.gallery.generated.resources.cloud_nextcloud_manual
import com.dot.gallery.generated.resources.cloud_nextcloud_network_error
import com.dot.gallery.generated.resources.cloud_nextcloud_rate_limited
import com.dot.gallery.generated.resources.cloud_nextcloud_server_error
import com.dot.gallery.generated.resources.cloud_nextcloud_sign_in
import com.dot.gallery.generated.resources.cloud_nextcloud_signed_in_as
import com.dot.gallery.generated.resources.cloud_nextcloud_starting
import com.dot.gallery.generated.resources.cloud_nextcloud_tls_error
import com.dot.gallery.generated.resources.cloud_nextcloud_untrusted
import com.dot.gallery.generated.resources.cloud_nextcloud_verifying
import com.dot.gallery.generated.resources.cloud_nextcloud_waiting
import com.dot.gallery.generated.resources.cloud_review_external_url
import com.dot.gallery.generated.resources.cloud_review_folders
import com.dot.gallery.generated.resources.cloud_review_local_url
import com.dot.gallery.generated.resources.cloud_review_none
import com.dot.gallery.generated.resources.cloud_save
import com.dot.gallery.generated.resources.cloud_server_url
import com.dot.gallery.generated.resources.cloud_setup_help
import com.dot.gallery.generated.resources.cloud_show_secret
import com.dot.gallery.generated.resources.cloud_sync_count_selected
import com.dot.gallery.generated.resources.cloud_sync_folders_desc
import com.dot.gallery.generated.resources.cloud_sync_folders_title
import com.dot.gallery.generated.resources.cloud_sync_item_count
import com.dot.gallery.generated.resources.cloud_sync_no_local
import com.dot.gallery.generated.resources.cloud_test_connection
import com.dot.gallery.generated.resources.cloud_testing
import com.dot.gallery.generated.resources.cloud_url_invalid
import com.dot.gallery.generated.resources.continue_string
import com.dot.gallery.generated.resources.local_network_permission_dialog_message
import com.dot.gallery.generated.resources.local_network_permission_dialog_title
import com.dot.gallery.generated.resources.local_network_permission_not_now
import com.dot.gallery.generated.resources.setup_grant_permissions
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The wizard's interactive surface, decoupled from [CloudAccountsViewModel]:
 * every field update, auth action and save goes through [CloudAddServerActions]
 * so the same composition can run against the production ViewModel or the docs
 * capture fixtures.
 */
data class CloudAddServerActions(
    val updateServerUrl: (String) -> Unit = {},
    val updateDisplayName: (String) -> Unit = {},
    val updateApiKey: (String) -> Unit = {},
    val updateUsername: (String) -> Unit = {},
    val updatePassword: (String) -> Unit = {},
    val updateAutoUrlSwitch: (Boolean) -> Unit = {},
    val updateLocalWifiSsid: (String) -> Unit = {},
    val updateLocalServerUrl: (String) -> Unit = {},
    val testConnection: () -> Unit = {},
    val startInteractiveAuth: () -> Unit = {},
    val cancelInteractiveAuth: () -> Unit = {},
    val toggleLocalAlbum: (Long) -> Unit = {},
    val saveServer: () -> Unit = {},
)

/**
 * Stateless wizard body for add/edit server flows. Owns only step/dialog UI
 * state — account initialisation, browser auth launches and the actual save
 * belong to the calling screen.
 */
@Composable
fun CloudAddServerContent(
    state: AddServerUiState,
    localAlbums: List<Album>,
    isEditMode: Boolean,
    supportsInteractiveAuth: Boolean,
    actions: CloudAddServerActions,
    onNavigateUp: () -> Unit,
    initialStep: CloudSetupStep = CloudSetupStep.SERVER,
    initialManualAuth: Boolean? = null,
) {
    val descriptor = remember(state.providerType) { ProviderUiDescriptors.forType(state.providerType) }
    val credentialValues = CredentialValues(state.apiKey, state.username, state.password)
    val isUrlValid = state.serverUrl.isBlank() || descriptor.urlRegex.matches(state.serverUrl)
    val hasRequiredCredentials = descriptor.credentialsSatisfied(credentialValues)
    val canSave = state.serverUrl.isNotBlank() && isUrlValid && hasRequiredCredentials && !state.isSaving

    // Wizard steps (add mode only). Sync step is skipped in edit mode.
    val steps = remember(isEditMode) {
        buildList {
            add(CloudSetupStep.SERVER)
            add(CloudSetupStep.CREDENTIALS)
            add(CloudSetupStep.NETWORKING)
            if (!isEditMode) add(CloudSetupStep.SYNC)
            add(CloudSetupStep.REVIEW)
        }
    }
    var stepIndex by remember { mutableStateOf(steps.indexOf(initialStep).coerceAtLeast(0)) }
    val safeIndex = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val canAdvance = when (step) {
        CloudSetupStep.SERVER -> state.serverUrl.isNotBlank() && isUrlValid
        CloudSetupStep.CREDENTIALS -> hasRequiredCredentials && state.testSuccess
        CloudSetupStep.NETWORKING -> !state.autoUrlSwitch || state.localServerUrl.isNotBlank()
        CloudSetupStep.SYNC -> true
        CloudSetupStep.REVIEW -> canSave
    }

    val goBack: () -> Unit = {
        if (isEditMode || safeIndex == 0) onNavigateUp()
        else stepIndex = (safeIndex - 1).coerceAtLeast(0)
    }

    // Warn once before proceeding with an unencrypted HTTP server URL. HTTP is allowed
    // (self-hosted servers over trusted tunnels often use it) but the user must confirm.
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
            title = { Text(stringResource(Res.string.cloud_http_warning_title)) },
            text = { Text(stringResource(Res.string.cloud_http_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    httpAckUrl = serverUrlTrimmed
                    showHttpWarning = false
                    val action = pendingProceed
                    pendingProceed = null
                    action?.invoke()
                }) { Text(stringResource(Res.string.continue_string)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHttpWarning = false
                    pendingProceed = null
                }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    CloudWizardScaffold(
        title = if (isEditMode) stringResource(Res.string.cloud_credentials)
                else stringResource(Res.string.cloud_add_provider, descriptor.providerType.displayName),
        stepIndex = safeIndex,
        totalSteps = if (isEditMode) 0 else steps.size,
        stepLabel = if (isEditMode) null else stringResource(
            Res.string.cloud_wizard_step_named, safeIndex + 1, steps.size, stringResource(step.titleRes)
        ),
        headline = if (isEditMode) null else step.subtitleRes?.let { stringResource(it) },
        onBack = goBack,
        bottomBar = {
            if (isEditMode) {
                Spacer(Modifier.weight(1f))
                WizardPrimaryButton(
                    text = if (state.isSaving) stringResource(Res.string.cloud_save) + "…"
                           else stringResource(Res.string.cloud_save),
                    enabled = canSave,
                    onClick = { guardHttp { actions.saveServer() } }
                )
            } else {
                TextButton(onClick = goBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.cloud_back))
                }
                WizardBottomBar(
                    isLast = step == CloudSetupStep.REVIEW,
                    canAdvance = canAdvance,
                    isSaving = state.isSaving,
                    onNext = {
                        // Gate the first "Next" (leaving the server step) behind the HTTP warning
                        // so the user is warned before continuing with an insecure URL.
                        guardHttp { stepIndex = (safeIndex + 1).coerceAtMost(steps.lastIndex) }
                    },
                    onSave = { guardHttp { actions.saveServer() } }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEditMode) {
                ServerStep(state, descriptor, isUrlValid, actions)
                CredentialsStep(
                    state,
                    descriptor,
                    credentialValues,
                    supportsInteractiveAuth,
                    actions,
                    initialManualAuth,
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
                            CloudSetupStep.SERVER -> ServerStep(state, descriptor, isUrlValid, actions)
                            CloudSetupStep.CREDENTIALS -> CredentialsStep(
                                state,
                                descriptor,
                                credentialValues,
                                supportsInteractiveAuth,
                                actions,
                                initialManualAuth,
                            )
                            CloudSetupStep.NETWORKING -> NetworkingStep(state, actions)
                            CloudSetupStep.SYNC -> SyncStep(state, localAlbums, actions)
                            CloudSetupStep.REVIEW -> ReviewStep(state, descriptor, onEdit = { target ->
                                stepIndex = steps.indexOf(target).coerceAtLeast(0)
                            })
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

@Composable
private fun WizardBottomBar(
    isLast: Boolean,
    canAdvance: Boolean,
    isSaving: Boolean,
    onNext: () -> Unit,
    onSave: () -> Unit
) {
    WizardPrimaryButton(
        text = if (isLast) {
            if (isSaving) stringResource(Res.string.cloud_connect) + "…"
            else stringResource(Res.string.cloud_connect)
        } else stringResource(Res.string.cloud_next),
        enabled = canAdvance,
        onClick = if (isLast) onSave else onNext
    )
}

@Composable
private fun WizardPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 120.dp),
    ) {
        Text(text)
    }
}

/**
 * Page chrome for the add-account wizard in the Library style: [GalleryPageTopBar], a segmented
 * step bar, the step label and question, a scrolling body and Back / Next pinned to the bottom.
 */
@Composable
private fun CloudWizardScaffold(
    title: String,
    stepIndex: Int,
    totalSteps: Int,
    stepLabel: String?,
    headline: String?,
    onBack: () -> Unit,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    val scrollState = rememberScrollState()
    val scrolled = rememberScrolledUnderTopBar(scrollState)
    Scaffold(
        topBar = {
            // Opaque at rest; once scrolled the bar blurs and the step bar floats over the content.
            Column(modifier = if (scrolled) Modifier else Modifier.background(MaterialTheme.colorScheme.surface)) {
                GalleryPageTopBar(title = title, onBack = onBack, hazeState = hazeState, scrolled = scrolled)
                if (totalSteps > 0) {
                    Row(
                        modifier = Modifier.cloudContentWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(totalSteps) { index ->
                            val color by animateColorAsState(
                                if (index <= stepIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                label = "wizardStep"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
                    .cloudContentWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = bottomBar
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(scrollState)
                .padding(padding)
                .cloudContentWidth()
                .padding(bottom = 16.dp)
        ) {
            if (stepLabel != null || headline != null) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stepLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    headline?.let {
                        Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/** Filled field container used by every wizard input. */
private fun frostedFieldModifier(): Modifier = Modifier.fillMaxWidth()

private val wizardFieldColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun ServerStep(
    state: AddServerUiState,
    descriptor: ProviderUiDescriptor,
    isUrlValid: Boolean,
    actions: CloudAddServerActions,
) {
    AppTextField(
        value = state.serverUrl,
        onValueChange = actions.updateServerUrl,
        label = { Text(stringResource(Res.string.cloud_server_url)) },
        placeholder = { Text(stringResource(descriptor.urlHintRes)) },
        modifier = frostedFieldModifier(),
        containerColor = wizardFieldColor,
        singleLine = true,
        isError = state.serverUrl.isNotBlank() && !isUrlValid,
        supportingText = if (state.serverUrl.isNotBlank() && !isUrlValid) {
            { Text(stringResource(Res.string.cloud_url_invalid)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
    if (descriptor.isLanOnly) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.cloud_network_share_lan_note),
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
    descriptor: ProviderUiDescriptor,
    credentialValues: CredentialValues,
    supportsInteractiveAuth: Boolean,
    actions: CloudAddServerActions,
    initialManualAuth: Boolean? = null,
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
        val action = actions.testConnection
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
            title = { Text(stringResource(Res.string.local_network_permission_dialog_title)) },
            text = { Text(stringResource(Res.string.local_network_permission_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocalNetworkRationale = false
                    localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }) {
                    Text(stringResource(Res.string.setup_grant_permissions))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocalNetworkRationale = false
                    pendingConnectionTest?.invoke()
                    pendingConnectionTest = null
                }) {
                    Text(stringResource(Res.string.local_network_permission_not_now))
                }
            }
        )
    }

    AppTextField(
        value = state.displayName,
        onValueChange = actions.updateDisplayName,
        label = { Text(stringResource(Res.string.cloud_display_name)) },
        placeholder = { Text(stringResource(Res.string.cloud_display_name_hint)) },
        modifier = frostedFieldModifier(),
        containerColor = wizardFieldColor,
        singleLine = true
    )

    val authState = state.authenticationState
    var showManual by rememberSaveable(state.providerType) {
        mutableStateOf(initialManualAuth ?: !supportsInteractiveAuth)
    }
    LaunchedEffect(authState) {
        if (authState is CloudAuthenticationState.Failed && authState.unsupported) showManual = true
    }

    if (supportsInteractiveAuth) {
        Spacer(Modifier.height(16.dp))
        val authBusy = authState is CloudAuthenticationState.Starting ||
            authState is CloudAuthenticationState.WaitingForBrowser ||
            authState is CloudAuthenticationState.Polling ||
            authState is CloudAuthenticationState.Verifying
        WizardWideButton(
            text = when (authState) {
                CloudAuthenticationState.Starting -> stringResource(Res.string.cloud_nextcloud_starting)
                CloudAuthenticationState.WaitingForBrowser,
                CloudAuthenticationState.Polling -> stringResource(Res.string.cloud_nextcloud_waiting)
                CloudAuthenticationState.Verifying -> stringResource(Res.string.cloud_nextcloud_verifying)
                is CloudAuthenticationState.Verified -> stringResource(Res.string.cloud_nextcloud_connected)
                else -> stringResource(Res.string.cloud_nextcloud_sign_in)
            },
            enabled = !authBusy && authState !is CloudAuthenticationState.Verified,
            filled = true,
            onClick = actions.startInteractiveAuth
        )
        when (authState) {
            CloudAuthenticationState.Starting,
            CloudAuthenticationState.WaitingForBrowser,
            CloudAuthenticationState.Polling,
            CloudAuthenticationState.Verifying -> {
                SyncLoadingRow(
                    if (authState is CloudAuthenticationState.Polling ||
                        authState is CloudAuthenticationState.WaitingForBrowser
                    ) stringResource(Res.string.cloud_nextcloud_grant_access)
                    else stringResource(Res.string.cloud_nextcloud_verifying)
                )
                TextButton(onClick = actions.cancelInteractiveAuth) {
                    Text(stringResource(Res.string.cancel))
                }
            }
            is CloudAuthenticationState.Verified -> AuthResultRow(
                success = true,
                text = stringResource(Res.string.cloud_nextcloud_signed_in_as, authState.username)
            )
            is CloudAuthenticationState.Failed -> AuthResultRow(
                success = false,
                text = if (authState.unsupported) {
                    stringResource(Res.string.cloud_nextcloud_flow_unsupported)
                } else when (authState.kind) {
                    InteractiveAuthErrorKind.INVALID_URL -> stringResource(Res.string.cloud_nextcloud_invalid_url)
                    InteractiveAuthErrorKind.UNTRUSTED_RESPONSE -> stringResource(Res.string.cloud_nextcloud_untrusted)
                    InteractiveAuthErrorKind.AUTHENTICATION -> stringResource(Res.string.cloud_nextcloud_auth_failed)
                    InteractiveAuthErrorKind.RATE_LIMITED -> stringResource(Res.string.cloud_nextcloud_rate_limited)
                    InteractiveAuthErrorKind.SERVER -> stringResource(Res.string.cloud_nextcloud_server_error)
                    InteractiveAuthErrorKind.NETWORK -> stringResource(Res.string.cloud_nextcloud_network_error)
                    InteractiveAuthErrorKind.TLS -> stringResource(Res.string.cloud_nextcloud_tls_error)
                    InteractiveAuthErrorKind.MALFORMED_RESPONSE -> stringResource(Res.string.cloud_nextcloud_invalid_response)
                    else -> authState.message
                }
            )
            CloudAuthenticationState.Expired -> AuthResultRow(
                success = false,
                text = stringResource(Res.string.cloud_nextcloud_expired)
            )
            CloudAuthenticationState.Cancelled -> AuthResultRow(
                success = false,
                text = stringResource(Res.string.cloud_nextcloud_cancelled)
            )
            else -> Unit
        }
        TextButton(onClick = { showManual = !showManual }) {
            Text(stringResource(Res.string.cloud_nextcloud_manual))
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
                    CredentialFieldKind.API_KEY -> actions.updateApiKey
                    CredentialFieldKind.USERNAME -> actions.updateUsername
                    CredentialFieldKind.PASSWORD -> actions.updatePassword
                }
                Spacer(Modifier.height(16.dp))
                CredentialTextField(field = field, value = value, onValueChange = onValueChange)
            }
            Spacer(Modifier.height(16.dp))
            WizardWideButton(
                text = if (state.isTesting) stringResource(Res.string.cloud_testing)
                    else stringResource(Res.string.cloud_test_connection),
                enabled = !state.isTesting && state.serverUrl.isNotBlank() &&
                    descriptor.credentialsSatisfied(credentialValues),
                filled = false,
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
    val container = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = content
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

/** Full-width wizard action: filled for the main path, outlined for checks like Test connection. */
@Composable
private fun WizardWideButton(text: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    if (filled) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
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
        containerColor = wizardFieldColor,
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
                            if (revealed) Res.string.cloud_hide_secret
                            else Res.string.cloud_show_secret
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
    actions: CloudAddServerActions,
) {
    val scope = rememberCoroutineScope()
    val wifiSheetState = rememberAppBottomSheetState()
    SettingsItem(
        item = SettingsEntity.SwitchPreference(
            icon = Icons.Outlined.Home,
            title = stringResource(Res.string.cloud_net_auto_url),
            summary = stringResource(Res.string.cloud_net_auto_url_summary),
            isChecked = state.autoUrlSwitch,
            onCheck = actions.updateAutoUrlSwitch,
            screenPosition = Position.Alone,
        ),
        applyPaddings = false,
    )
    AnimatedVisibility(visible = state.autoUrlSwitch) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField(
                value = state.localServerUrl,
                onValueChange = actions.updateLocalServerUrl,
                label = { Text(stringResource(Res.string.cloud_net_local_url)) },
                placeholder = { Text(stringResource(Res.string.cloud_net_local_url_hint)) },
                modifier = frostedFieldModifier(),
                containerColor = wizardFieldColor,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            // Clickable field that opens the SSID picker sheet (blank = switching disabled).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(wizardFieldColor)
                    .clickable { scope.launch { wifiSheetState.show() } }
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cloud_net_wifi_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.localWifiSsid.ifBlank {
                        stringResource(Res.string.cloud_net_wifi_blank_summary)
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
        onSave = actions.updateLocalWifiSsid
    )
}

/** Final stage: choose the local folders whose media will be uploaded to this account. */
@Composable
private fun SyncStep(
    state: AddServerUiState,
    localAlbums: List<Album>,
    actions: CloudAddServerActions,
) {
    SyncSectionHeader(
        title = stringResource(Res.string.cloud_sync_folders_title),
        subtitle = stringResource(Res.string.cloud_sync_folders_desc)
    )
    if (localAlbums.isEmpty()) {
        SyncEmptyHint(stringResource(Res.string.cloud_sync_no_local))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            localAlbums.forEachIndexed { index, album ->
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        icon = Icons.Outlined.Folder,
                        title = album.label,
                        summary = stringResource(Res.string.cloud_sync_item_count, album.count),
                        isChecked = album.id in state.selectedLocalAlbumIds,
                        onCheck = { actions.toggleLocalAlbum(album.id) },
                        screenPosition = positionOf(index, localAlbums.size),
                    ),
                    applyPaddings = false,
                    customIcon = { _, _, _ ->
                        AsyncImage(
                            uri = album.uri.toString(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    },
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
private fun ReviewStep(
    state: AddServerUiState,
    descriptor: ProviderUiDescriptor,
    onEdit: (CloudSetupStep) -> Unit,
) {
    val none = stringResource(Res.string.cloud_review_none)
    val host = state.serverUrl.substringAfter("://").trimEnd('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderBadge(descriptor.providerType)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.displayName.ifBlank { descriptor.providerType.displayName },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = descriptor.providerType.displayName + if (host.isNotBlank()) " · $host" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    val rows = buildList {
        add(Triple(Icons.Outlined.Dns, stringResource(Res.string.cloud_review_external_url) to state.serverUrl.ifBlank { none }, CloudSetupStep.SERVER))
        add(
            Triple(
                Icons.Outlined.Key,
                stringResource(Res.string.cloud_credentials) to state.username.ifBlank {
                    if (state.apiKey.isNotBlank()) stringResource(Res.string.cloud_api_key) else none
                },
                CloudSetupStep.CREDENTIALS
            )
        )
        if (state.autoUrlSwitch) {
            add(
                Triple(
                    Icons.Outlined.Home,
                    stringResource(Res.string.cloud_review_local_url) to listOf(
                        state.localServerUrl.ifBlank { none },
                        state.localWifiSsid
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    CloudSetupStep.NETWORKING
                )
            )
        }
        add(
            Triple(
                Icons.Outlined.CloudUpload,
                stringResource(Res.string.cloud_review_folders) to if (state.selectedLocalAlbumIds.isEmpty()) none
                else stringResource(Res.string.cloud_sync_count_selected, state.selectedLocalAlbumIds.size),
                CloudSetupStep.SYNC
            )
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, (icon, labelValue, target) ->
            SettingsItem(
                item = SettingsEntity.Preference(
                    icon = icon,
                    title = labelValue.first,
                    summary = labelValue.second,
                    screenPosition = positionOf(index, rows.size),
                    onClick = { onEdit(target) },
                ),
                applyPaddings = false,
                customTrailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                },
            )
        }
    }
    CloudNote(icon = Icons.Outlined.Wifi, text = stringResource(Res.string.cloud_wizard_review_note))
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
                text = stringResource(Res.string.cloud_setup_help),
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
