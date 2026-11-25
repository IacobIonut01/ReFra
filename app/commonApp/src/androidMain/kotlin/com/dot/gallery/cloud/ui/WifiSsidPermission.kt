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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.presentation.util.currentWifiSsid
import com.dot.gallery.core.presentation.util.isOnWifiNetwork


/** User-facing state and action for a configured Wi-Fi name that cannot currently be verified. */
@Stable
class WifiSsidAccessState internal constructor(
    val ssidUnreadable: Boolean,
    val requestAccess: () -> Unit,
    private val refreshState: () -> Unit,
) {
    /** Re-read the platform permission, network, and SSID state. */
    fun refresh() = refreshState()
}

/**
 * Remembers a callback that requests the runtime location permissions needed to read the current
 * Wi-Fi SSID. Android 12+ ignores a fine-only location request, so coarse and fine location are
 * requested together; URL switching still requires the fine grant. If the user denies it, URL
 * switching fails closed and keeps using the remote URL.
 *
 * The permissions are stripped from the offline variant's manifest, so on offline builds the
 * request simply resolves as denied and URL switching remains disabled until the SSID can be
 * verified.
 */
@Composable
fun rememberWifiSsidPermissionRequester(
    onResult: (Boolean) -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        currentOnResult(grants[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }
    return remember(context) {
        {
            if (context.hasWifiSsidPermission()) {
                currentOnResult(true)
            } else {
                launcher.launch(WIFI_SSID_PERMISSIONS)
            }
        }
    }
}

/**
 * Tracks whether [targetSsid] is configured for URL switching but cannot currently be read.
 * [WifiSsidAccessState.requestAccess] asks for fine location when needed, or opens Location
 * settings when permission is already present but the platform still hides the SSID.
 * [onAccessRestored] runs when the state changes from unreadable to readable or otherwise safe.
 */
@Composable
fun rememberWifiSsidAccessState(
    targetSsid: String,
    switchingConfigured: Boolean,
    onAccessRestored: () -> Unit = {},
): WifiSsidAccessState {
    val context = LocalContext.current
    val refreshTick = remember { mutableIntStateOf(0) }
    val refresh = remember { { refreshTick.intValue += 1 } }
    val requestPermission = rememberWifiSsidPermissionRequester { refresh() }
    val currentOnAccessRestored by rememberUpdatedState(onAccessRestored)

    LifecycleStartEffect(Unit) {
        refresh()
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
    val ssidUnreadable = remember(targetSsid, switchingConfigured, refreshTick.intValue, context) {
        switchingConfigured && targetSsid.isNotBlank() &&
            context.isOnWifiNetwork() && context.currentWifiSsid() == null
    }
    var wasUnreadable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ssidUnreadable) {
        if (wasUnreadable == true && !ssidUnreadable) currentOnAccessRestored()
        wasUnreadable = ssidUnreadable
    }
    val requestAccess = remember(context, requestPermission, openLocationSettings) {
        {
            if (context.hasWifiSsidPermission()) openLocationSettings() else requestPermission()
        }
    }
    return remember(ssidUnreadable, requestAccess, refresh) {
        WifiSsidAccessState(
            ssidUnreadable = ssidUnreadable,
            requestAccess = requestAccess,
            refreshState = refresh,
        )
    }
}

private val WIFI_SSID_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun Context.hasWifiSsidPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
