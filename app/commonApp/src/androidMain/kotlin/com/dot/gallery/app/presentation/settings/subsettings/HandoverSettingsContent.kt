/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.ui.WifiSsidPickerSheet
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.action_save
import com.dot.gallery.generated.resources.handover_auth_bearer
import com.dot.gallery.generated.resources.handover_auth_hmac
import com.dot.gallery.generated.resources.handover_auth_mode
import com.dot.gallery.generated.resources.handover_auth_pick
import com.dot.gallery.generated.resources.handover_auto_switch
import com.dot.gallery.generated.resources.handover_auto_switch_summary
import com.dot.gallery.generated.resources.handover_behavior_header
import com.dot.gallery.generated.resources.handover_connected
import com.dot.gallery.generated.resources.handover_connection_header
import com.dot.gallery.generated.resources.handover_edit_secret
import com.dot.gallery.generated.resources.handover_edit_url
import com.dot.gallery.generated.resources.handover_enable
import com.dot.gallery.generated.resources.handover_engine
import com.dot.gallery.generated.resources.handover_engine_default
import com.dot.gallery.generated.resources.handover_engine_pick
import com.dot.gallery.generated.resources.handover_failed
import com.dot.gallery.generated.resources.handover_local_url
import com.dot.gallery.generated.resources.handover_local_url_hint
import com.dot.gallery.generated.resources.handover_local_url_insecure
import com.dot.gallery.generated.resources.handover_not_configured
import com.dot.gallery.generated.resources.handover_remote_queries
import com.dot.gallery.generated.resources.handover_remote_queries_summary
import com.dot.gallery.generated.resources.handover_remote_url_insecure
import com.dot.gallery.generated.resources.handover_secret
import com.dot.gallery.generated.resources.handover_secret_blank
import com.dot.gallery.generated.resources.handover_secret_hint
import com.dot.gallery.generated.resources.handover_secret_stored
import com.dot.gallery.generated.resources.handover_server_url
import com.dot.gallery.generated.resources.handover_sink
import com.dot.gallery.generated.resources.handover_sink_both
import com.dot.gallery.generated.resources.handover_sink_client
import com.dot.gallery.generated.resources.handover_sink_pick
import com.dot.gallery.generated.resources.handover_sink_server
import com.dot.gallery.generated.resources.handover_status_connected_title
import com.dot.gallery.generated.resources.handover_status_down
import com.dot.gallery.generated.resources.handover_status_ok
import com.dot.gallery.generated.resources.handover_status_unreachable_title
import com.dot.gallery.generated.resources.handover_test
import com.dot.gallery.generated.resources.handover_testing
import com.dot.gallery.generated.resources.handover_title
import com.dot.gallery.generated.resources.handover_url_hint
import com.dot.gallery.generated.resources.handover_url_insecure
import com.dot.gallery.generated.resources.handover_wifi
import com.dot.gallery.handover.EngineInfo
import com.dot.gallery.handover.HandoverConfig
import com.dot.gallery.handover.HandoverEnvelope
import com.dot.gallery.handover.HandoverInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.rememberLazyListState

/** Snapshot of everything the screen renders — no ViewModel, no DataStore. */
@Stable
data class HandoverSettingsState(
    val enabled: Boolean = false,
    val remoteUrl: String = "",
    val localUrl: String = "",
    val wifiSsid: String = "",
    val autoSwitch: Boolean = true,
    val engine: String = "",
    /** HandoverEnvelope.SINK_* */
    val sink: String = HandoverEnvelope.SINK_BOTH,
    val preferRemoteQueries: Boolean = true,
    /** HandoverConfig.AUTH_BEARER | HandoverConfig.AUTH_HMAC */
    val authMode: String = HandoverConfig.AUTH_BEARER,
    val secretSet: Boolean = false,
    val status: HandoverInfo? = null,
    val knownEngines: List<EngineInfo> = emptyList(),
    val probe: HandoverProbeState = HandoverProbeState.Idle,
)

@Stable
class HandoverSettingsActions(
    val onEnabledChange: (Boolean) -> Unit = {},
    val onAutoSwitchChange: (Boolean) -> Unit = {},
    val onWifiSsid: (String) -> Unit = {},
    val onRemoteUrl: (String) -> Unit = {},
    val onLocalUrl: (String) -> Unit = {},
    val onSecret: (String) -> Unit = {},
    val onEngine: (String) -> Unit = {},
    val onSink: (String) -> Unit = {},
    val onPreferRemoteQueriesChange: (Boolean) -> Unit = {},
    val onAuthMode: (String) -> Unit = {},
    val onTestConnection: (url: String, secret: String) -> Unit = { _, _ -> },
    val onClearProbe: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverSettingsContent(
    state: HandoverSettingsState,
    actions: HandoverSettingsActions = HandoverSettingsActions(),
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val wifiSheetState = rememberAppBottomSheetState()
    var showUrlDialog by remember { mutableStateOf(false) }
    var showLocalUrlDialog by remember { mutableStateOf(false) }
    var showSecretDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showSinkDialog by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var testUrl by remember { mutableStateOf("") }
    var testSecret by remember { mutableStateOf("") }

    // Strings resolved outside the non-composable settings{} DSL.
    val notConfigured = stringResource(Res.string.handover_not_configured)
    val statusTitle = stringResource(
        if (state.status != null) Res.string.handover_status_connected_title
        else Res.string.handover_status_unreachable_title
    )
    val statusSummary = if (state.status != null) {
        stringResource(Res.string.handover_status_ok, state.status.engines.size, state.status.principal)
    } else {
        stringResource(Res.string.handover_status_down)
    }
    val serverUrlTitle = stringResource(Res.string.handover_server_url)
    val secretTitle = stringResource(Res.string.handover_secret)
    val engineSummary = state.engine.ifBlank { stringResource(Res.string.handover_engine_default) }
    val sinkSummary = when (state.sink) {
        HandoverEnvelope.SINK_SERVER -> stringResource(Res.string.handover_sink_server)
        HandoverEnvelope.SINK_CLIENT -> stringResource(Res.string.handover_sink_client)
        else -> stringResource(Res.string.handover_sink_both)
    }
    val probeSummary = when (val p = state.probe) {
        is HandoverProbeState.Ok -> stringResource(
            Res.string.handover_connected, p.info.version, p.info.principal)
        is HandoverProbeState.Failed ->
            stringResource(Res.string.handover_failed, p.message.ifBlank { p.code })
        HandoverProbeState.Testing -> stringResource(Res.string.handover_testing)
        HandoverProbeState.Idle -> ""
    }
    val insecureUrlWarning = stringResource(Res.string.handover_url_insecure)
    val authModeSummary = when (state.authMode) {
        HandoverConfig.AUTH_HMAC -> stringResource(Res.string.handover_auth_hmac)
        else -> stringResource(Res.string.handover_auth_bearer)
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.handover_title),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(listState),
            )
        }
    ) { padding ->
        val connectionHeader = stringResource(Res.string.handover_connection_header)
        val behaviorHeader = stringResource(Res.string.handover_behavior_header)
        val enableTitle = stringResource(Res.string.handover_enable)
        val secretStored = stringResource(Res.string.handover_secret_stored)
        val secretBlank = stringResource(Res.string.handover_secret_blank)
        val autoSwitchTitle = stringResource(Res.string.handover_auto_switch)
        val autoSwitchSummary = stringResource(Res.string.handover_auto_switch_summary)
        val wifiTitle = stringResource(Res.string.handover_wifi)
        val localUrlTitle = stringResource(Res.string.handover_local_url)
        val engineTitle = stringResource(Res.string.handover_engine)
        val sinkTitle = stringResource(Res.string.handover_sink)
        val remoteQueriesTitle = stringResource(Res.string.handover_remote_queries)
        val remoteQueriesSummary = stringResource(Res.string.handover_remote_queries_summary)
        val testTitle = stringResource(Res.string.handover_test)
        val localUrlInsecure = stringResource(Res.string.handover_local_url_insecure)
        val remoteUrlInsecure = stringResource(Res.string.handover_remote_url_insecure)
        val authModeTitle = stringResource(Res.string.handover_auth_mode)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(LocalHazeState.current),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
        ) {
            if (state.enabled) {
                item(key = "status") {
                    HandoverStatusCard(
                        connected = state.status != null,
                        title = statusTitle,
                        body = statusSummary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    )
                }
            }
            settings {
                SwitchPreference(
                    title = enableTitle,
                    isChecked = state.enabled,
                    onCheck = actions.onEnabledChange
                )

                if (state.enabled) {
                    Header(connectionHeader)

                    Preference(
                        title = serverUrlTitle,
                        summary = when {
                            state.remoteUrl.isBlank() -> notConfigured
                            !HandoverConfig.isUsableRemoteUrl(state.remoteUrl) ->
                                insecureUrlWarning
                            else -> state.remoteUrl
                        },
                        onClick = { showUrlDialog = true }
                    )
                    if (HandoverConfig.isPlainHttpUrl(state.remoteUrl)) {
                        Preference(
                            title = remoteUrlInsecure,
                            icon = Icons.Outlined.Warning
                        )
                    }
                    Preference(
                        title = secretTitle,
                        summary = if (state.secretSet) secretStored else secretBlank,
                        onClick = { showSecretDialog = true }
                    )
                    Preference(
                        title = authModeTitle,
                        summary = authModeSummary,
                        onClick = { showAuthDialog = true }
                    )
                    SwitchPreference(
                        title = autoSwitchTitle,
                        summary = autoSwitchSummary,
                        isChecked = state.autoSwitch,
                        onCheck = actions.onAutoSwitchChange
                    )
                    if (state.autoSwitch) {
                        Preference(
                            title = wifiTitle,
                            summary = state.wifiSsid.ifBlank { notConfigured },
                            onClick = { scope.launch { wifiSheetState.show() } }
                        )
                        Preference(
                            title = localUrlTitle,
                            summary = state.localUrl.ifBlank { notConfigured },
                            onClick = { showLocalUrlDialog = true }
                        )
                        if (state.localUrl.trimStart().startsWith("http://")) {
                            Preference(
                                title = localUrlInsecure,
                                icon = Icons.Outlined.Warning
                            )
                        }
                    }
                    Preference(
                        title = testTitle,
                        summary = probeSummary,
                        enabled = state.remoteUrl.isNotBlank(),
                        onClick = {
                            testUrl = state.remoteUrl
                            testSecret = ""
                            showTestDialog = true
                        }
                    )

                    Header(behaviorHeader)

                    Preference(
                        title = engineTitle,
                        summary = engineSummary,
                        onClick = { showEngineDialog = true }
                    )
                    Preference(
                        title = sinkTitle,
                        summary = sinkSummary,
                        onClick = { showSinkDialog = true }
                    )
                    SwitchPreference(
                        title = remoteQueriesTitle,
                        summary = remoteQueriesSummary,
                        isChecked = state.preferRemoteQueries,
                        onCheck = actions.onPreferRemoteQueriesChange
                    )
                }
            }
        }
    }

    WifiSsidPickerSheet(
        state = wifiSheetState,
        currentSsid = state.wifiSsid,
        onSave = actions.onWifiSsid
    )

    if (showUrlDialog) {
        TextEditDialog(
            title = stringResource(Res.string.handover_edit_url),
            initial = state.remoteUrl,
            hint = stringResource(Res.string.handover_url_hint),
            validate = { value ->
                if (HandoverConfig.isUsableRemoteUrl(value)) null else insecureUrlWarning
            },
            onSave = { actions.onRemoteUrl(it); showUrlDialog = false },
            onDismiss = { showUrlDialog = false }
        )
    }
    if (showLocalUrlDialog) {
        TextEditDialog(
            title = stringResource(Res.string.handover_local_url),
            initial = state.localUrl,
            hint = stringResource(Res.string.handover_local_url_hint),
            onSave = { actions.onLocalUrl(it); showLocalUrlDialog = false },
            onDismiss = { showLocalUrlDialog = false }
        )
    }
    if (showSecretDialog) {
        TextEditDialog(
            title = stringResource(Res.string.handover_edit_secret),
            initial = "",
            hint = stringResource(Res.string.handover_secret_hint),
            password = true,
            onSave = { actions.onSecret(it); showSecretDialog = false },
            onDismiss = { showSecretDialog = false }
        )
    }
    if (showEngineDialog) {
        val options = buildList {
            add("" to engineSummary)
            state.knownEngines.forEach { add(it.id to it.displayName.ifBlank { it.id }) }
        }
        ChoiceDialog(
            title = stringResource(Res.string.handover_engine_pick),
            options = options,
            selected = state.engine,
            onPick = { actions.onEngine(it); showEngineDialog = false },
            onDismiss = { showEngineDialog = false }
        )
    }
    if (showAuthDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.handover_auth_pick),
            options = listOf(
                HandoverConfig.AUTH_BEARER to stringResource(Res.string.handover_auth_bearer),
                HandoverConfig.AUTH_HMAC to stringResource(Res.string.handover_auth_hmac),
            ),
            selected = state.authMode,
            onPick = { actions.onAuthMode(it); showAuthDialog = false },
            onDismiss = { showAuthDialog = false }
        )
    }
    if (showSinkDialog) {
        ChoiceDialog(
            title = stringResource(Res.string.handover_sink_pick),
            options = listOf(
                HandoverEnvelope.SINK_CLIENT to stringResource(Res.string.handover_sink_client),
                HandoverEnvelope.SINK_SERVER to stringResource(Res.string.handover_sink_server),
                HandoverEnvelope.SINK_BOTH to stringResource(Res.string.handover_sink_both),
            ),
            selected = state.sink,
            onPick = { actions.onSink(it); showSinkDialog = false },
            onDismiss = { showSinkDialog = false }
        )
    }
    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false; actions.onClearProbe() },
            title = { Text(stringResource(Res.string.handover_test)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = { Text(serverUrlTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = testSecret,
                        onValueChange = { testSecret = it },
                        label = { Text(secretTitle) },
                        placeholder = {
                            Text(if (state.secretSet) stringResource(Res.string.handover_secret_stored)
                                else stringResource(Res.string.handover_secret_hint))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (probeSummary.isNotBlank()) {
                        Text(
                            text = probeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actions.onTestConnection(testUrl, testSecret) }) {
                    Text(stringResource(Res.string.handover_test))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false; actions.onClearProbe() }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun HandoverStatusCard(
    connected: Boolean,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val container = if (connected) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.errorContainer
    val content = if (connected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = if (connected) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = if (connected) MaterialTheme.colorScheme.primary else content
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else content
            )
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    hint: String,
    password: Boolean = false,
    validate: (String) -> String? = { null },
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                placeholder = { Text(hint) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                visualTransformation =
                    if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val candidate = value.trim()
                val violation = validate(candidate)
                if (violation == null) onSave(candidate) else error = violation
            }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
