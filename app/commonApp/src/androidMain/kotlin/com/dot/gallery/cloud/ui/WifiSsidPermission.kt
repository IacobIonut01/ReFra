/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.presentation.util.currentWifiSsid
import com.dot.gallery.core.presentation.util.isOnLocalNetwork


/** User-facing state and action for a configured Wi-Fi name that cannot currently be verified. */
@Stable
class WifiSsidAccessState internal constructor(
    val ssidUnreadable: Boolean,
    val requestAccess: () -> Unit,
)

/**
 * Remembers a callback that requests [Manifest.permission.ACCESS_FINE_LOCATION] at runtime if it
 * is not already granted. This permission is needed for local/external URL switching to read the
 * current Wi-Fi SSID and match it against a configured network name (Android 10+ hides the SSID
 * from apps without it). If the user denies it, URL switching fails closed and keeps using the
 * remote URL.
 *
 * The permission is stripped from the offline variant's manifest, so on offline builds the request
 * simply resolves as denied and URL switching remains disabled until the SSID can be verified.
 */
@Composable
fun rememberWifiSsidPermissionRequester(
    onResult: (Boolean) -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> currentOnResult(granted) }
    return remember(context) {
        {
            if (context.hasWifiSsidPermission()) {
                currentOnResult(true)
            } else {
                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}

/**
 * Tracks whether [targetSsid] is configured for URL switching but cannot currently be read.
 * [WifiSsidAccessState.requestAccess] asks for fine location when needed, or opens Location
 * settings when permission is already present but the platform still hides the SSID.
 */
@Composable
fun rememberWifiSsidAccessState(
    targetSsid: String,
    switchingConfigured: Boolean,
): WifiSsidAccessState {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val requestPermission = rememberWifiSsidPermissionRequester { refreshTick++ }

    LifecycleStartEffect(Unit) {
        refreshTick++
        onStopOrDispose { }
    }

    val openLocationSettings = remember(context) {
        {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                printWarn(
                    tag = "cloud.network",
                    message = "Unable to open location settings for Wi-Fi SSID access",
                    ctx = mapOf("error" to it.javaClass.simpleName),
                    scope = "cloud.network",
                )
            }
            Unit
        }
    }
    val ssidUnreadable = remember(targetSsid, switchingConfigured, refreshTick, context) {
        switchingConfigured && targetSsid.isNotBlank() &&
            context.isOnLocalNetwork() && context.currentWifiSsid() == null
    }
    val requestAccess = remember(context, requestPermission, openLocationSettings) {
        {
            if (context.hasWifiSsidPermission()) openLocationSettings() else requestPermission()
        }
    }
    return remember(ssidUnreadable, requestAccess) {
        WifiSsidAccessState(
            ssidUnreadable = ssidUnreadable,
            requestAccess = requestAccess,
        )
    }
}

private fun Context.hasWifiSsidPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
