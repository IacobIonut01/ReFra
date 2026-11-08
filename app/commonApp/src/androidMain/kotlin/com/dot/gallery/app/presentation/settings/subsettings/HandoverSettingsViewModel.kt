/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.cloud.core.CredentialEncryptor
import com.dot.gallery.core.Settings
import com.dot.gallery.handover.EngineInfo
import com.dot.gallery.handover.HandoverClient
import com.dot.gallery.handover.HandoverConfig
import com.dot.gallery.handover.HandoverException
import com.dot.gallery.handover.HandoverInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of "Test connection" / the saved server's status probe. */
sealed interface HandoverProbeState {
    data object Idle : HandoverProbeState
    data object Testing : HandoverProbeState
    data class Ok(val info: HandoverInfo) : HandoverProbeState
    data class Failed(val code: String, val message: String) : HandoverProbeState
}

class HandoverSettingsViewModel @Inject constructor(
    private val context: Context,
    private val client: HandoverClient,
    private val encryptor: CredentialEncryptor,
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val enabled = Settings.Handover.enabled(context).state(false)
    val remoteUrl = Settings.Handover.remoteUrl(context).state("")
    val localUrl = Settings.Handover.localUrl(context).state("")
    val localWifiSsid = Settings.Handover.localWifiSsid(context).state("")
    val autoUrlSwitch = Settings.Handover.autoUrlSwitch(context).state(true)
    val engine = Settings.Handover.engine(context).state("")
    val sink = Settings.Handover.sink(context).state("both")
    val preferRemoteQueries = Settings.Handover.preferRemoteQueries(context).state(true)
    val authMode = Settings.Handover.authMode(context).state(HandoverConfig.AUTH_BEARER)
    val secretSet = Settings.Handover.encryptedSecret(context)
        .map { it.isNotBlank() }.state(false)

    private val _probe = MutableStateFlow<HandoverProbeState>(HandoverProbeState.Idle)
    val probe: StateFlow<HandoverProbeState> = _probe.asStateFlow()

    /** Live status of the configured server (index/engine info), refreshed on enable. */
    private val _status = MutableStateFlow<HandoverInfo?>(null)
    val status: StateFlow<HandoverInfo?> = _status.asStateFlow()

    fun setEnabled(value: Boolean) = viewModelScope.launch {
        Settings.Handover.setEnabled(context, value)
        if (value) refreshStatus()
    }

    fun setRemoteUrl(value: String) =
        viewModelScope.launch { Settings.Handover.setRemoteUrl(context, value) }

    fun setLocalUrl(value: String) =
        viewModelScope.launch { Settings.Handover.setLocalUrl(context, value) }

    fun setLocalWifiSsid(value: String) =
        viewModelScope.launch { Settings.Handover.setLocalWifiSsid(context, value) }

    fun setAutoUrlSwitch(value: Boolean) =
        viewModelScope.launch { Settings.Handover.setAutoUrlSwitch(context, value) }

    fun setEngine(value: String) =
        viewModelScope.launch { Settings.Handover.setEngine(context, value) }

    fun setSink(value: String) =
        viewModelScope.launch { Settings.Handover.setSink(context, value) }

    fun setPreferRemoteQueries(value: Boolean) =
        viewModelScope.launch { Settings.Handover.setPreferRemoteQueries(context, value) }

    fun setAuthMode(value: String) =
        viewModelScope.launch { Settings.Handover.setAuthMode(context, value) }

    /** Persists [plaintext] encrypted; blank clears the stored secret. */
    fun setSecret(plaintext: String) = viewModelScope.launch {
        Settings.Handover.setEncryptedSecret(context, encryptor.encrypt(plaintext))
    }

    fun refreshStatus() = viewModelScope.launch {
        val config = client.config()
        _status.value = config?.let {
            runCatching { client.info(it) }.getOrNull()
        }
    }

    /**
     * Probes [url] with [secret] — when [secret] is blank the stored (encrypted)
     * secret is used, so users can re-test without retyping it.
     */
    fun testConnection(url: String, secret: String) = viewModelScope.launch {
        _probe.value = HandoverProbeState.Testing
        val effectiveSecret = secret.ifBlank {
            HandoverConfig.load(context) { encryptor.decryptOrNull(it) ?: "" }.secret
        }
        _probe.value = try {
            HandoverProbeState.Ok(
                client.testConnection(url, effectiveSecret, authMode.value))
        } catch (e: HandoverException) {
            HandoverProbeState.Failed(e.code, e.message ?: e.code)
        }
    }

    fun clearProbe() {
        _probe.value = HandoverProbeState.Idle
    }

    /** Engines exposed by the last successful probe/status, for the picker. */
    val knownEngines: StateFlow<List<EngineInfo>> =
        combine(_probe, _status) { probeResult, status ->
            (probeResult as? HandoverProbeState.Ok)?.info?.engines
                ?: status?.engines ?: emptyList()
        }.state(emptyList())
}
