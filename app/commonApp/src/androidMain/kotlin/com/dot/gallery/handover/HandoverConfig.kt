/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.content.Context
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.util.currentWifiSsid
import com.dot.gallery.core.presentation.util.isOnLocalNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Immutable snapshot of the user's Handover settings, resolved for the
 * current network. Built fresh per operation — settings reads are cheap
 * DataStore flows.
 */
data class HandoverConfig(
    val enabled: Boolean,
    val remoteUrl: String,
    val localUrl: String,
    val localWifiSsid: String,
    val autoUrlSwitch: Boolean,
    /** Decrypted bearer secret. Empty means "not configured". */
    val secret: String,
    /** Engine id override; empty = server default. */
    val engine: String,
    /** HandoverEnvelope.SINK_* */
    val sink: String,
    /** Route text search through POST /api/v1/query instead of on-device embed. */
    val preferRemoteQueries: Boolean,
    /** [AUTH_BEARER] or [AUTH_HMAC] — must match the server's HANDOVER_AUTH_MODE. */
    val authMode: String = AUTH_BEARER,
) {
    /**
     * The remote URL may be http:// or https:// (megaplan §transport — https is
     * what real deployments should use, plain http is tolerated with an
     * on-screen warning). A URL with no usable scheme makes the whole config
     * unconfigured: settings validation should stop it earlier, this gate is
     * the runtime backstop for every caller that loads via `client.config()`.
     */
    val remoteUrlUsable: Boolean get() = isUsableRemoteUrl(remoteUrl)

    /** True when the remote URL travels unencrypted — drives the warning row. */
    val remoteUrlPlainHttp: Boolean get() = isPlainHttpUrl(remoteUrl)

    val configured: Boolean
        get() = enabled && secret.isNotBlank() && remoteUrl.isNotBlank() && remoteUrlUsable

    /**
     * Effective base URL. Mirrors ServerUrlResolver semantics: when auto-switch
     * is on and a local URL + SSID are configured, the local URL is used while
     * on that network; an unreadable SSID degrades to "any local network counts".
     */
    fun effectiveUrl(context: Context): String {
        if (!autoUrlSwitch || localUrl.isBlank()) return remoteUrl.trimEnd('/')
        val targetSsid = localWifiSsid.trim()
        if (targetSsid.isBlank()) return remoteUrl.trimEnd('/')
        if (!context.isOnLocalNetwork()) return remoteUrl.trimEnd('/')
        val currentSsid = context.currentWifiSsid()
        if (currentSsid != null && !currentSsid.equals(targetSsid, ignoreCase = true)) {
            return remoteUrl.trimEnd('/')
        }
        return localUrl.trimEnd('/')
    }

    companion object {
        const val AUTH_BEARER = "bearer"
        const val AUTH_HMAC = "hmac"

        fun isUsableRemoteUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.isBlank() ||
                trimmed.startsWith("https://") || trimmed.startsWith("http://")
        }

        fun isPlainHttpUrl(url: String): Boolean = url.trim().startsWith("http://")

        /**
         * Reactive [configured] for UI gating — same stored-value checks a
         * successful [load] would pass (decryption happens at use time, so a
         * corrupted ciphertext still reports ready here and fails at dial).
         */
        fun configuredFlow(context: Context): Flow<Boolean> = combine(
            Settings.Handover.enabled(context),
            Settings.Handover.remoteUrl(context),
            Settings.Handover.encryptedSecret(context),
        ) { enabled, url, secret ->
            enabled && url.isNotBlank() && isUsableRemoteUrl(url) && secret.isNotBlank()
        }

        suspend fun load(context: Context, decryptSecret: (String) -> String): HandoverConfig {
            val s = Settings.Handover
            return HandoverConfig(
                enabled = s.enabled(context).first(),
                remoteUrl = s.remoteUrl(context).first(),
                localUrl = s.localUrl(context).first(),
                localWifiSsid = s.localWifiSsid(context).first(),
                autoUrlSwitch = s.autoUrlSwitch(context).first(),
                secret = s.encryptedSecret(context).first().takeIf { it.isNotBlank() }
                    ?.let(decryptSecret) ?: "",
                engine = s.engine(context).first(),
                sink = s.sink(context).first(),
                preferRemoteQueries = s.preferRemoteQueries(context).first(),
                authMode = s.authMode(context).first(),
            )
        }
    }
}
