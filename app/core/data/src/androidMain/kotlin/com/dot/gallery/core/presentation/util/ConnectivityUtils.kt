/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager


inline fun runNetworkCallbackOperation(operation: () -> Unit): Boolean =
    try {
        operation()
        true
    } catch (_: RuntimeException) {
        false
    }

inline fun runNetworkCallbackSetup(
    initializeCurrentNetwork: () -> Unit,
    registerCallback: () -> Unit
): Boolean = runNetworkCallbackOperation {
    initializeCurrentNetwork()
    registerCallback()
}

/**
 * Network utility to get current state of internet connection
 */
val Context.currentConnectivityState: ConnectionState
    get() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return getCurrentConnectivityState(connectivityManager)
    }

fun networkCallback(callback: (ConnectionState) -> Unit): ConnectivityManager.NetworkCallback {
    return object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            callback(ConnectionState.Available)
        }

        override fun onLost(network: Network) {
            callback(ConnectionState.Unavailable)
        }
    }
}

@Suppress("DEPRECATION")
fun getCurrentConnectivityState(
    connectivityManager: ConnectivityManager
): ConnectionState {
    val connected = try {
        connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ?: false
        }
    } catch (_: SecurityException) {
        // ACCESS_NETWORK_STATE permission not available
        false
    }

    return if (connected) ConnectionState.Available else ConnectionState.Unavailable
}


sealed class ConnectionState {
    data object Available : ConnectionState()
    data object Unavailable : ConnectionState()

    fun isConnected() = this is Available
}

/**
 * Whether the active network is a local-area transport (Wi-Fi or Ethernet), as opposed to
 * cellular or no network. Used when switching between local and external server URLs.
 */
fun Context.isOnLocalNetwork(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return try {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } catch (_: SecurityException) {
        // ACCESS_NETWORK_STATE unavailable (offline variant / restricted profile).
        false
    }
}

/** Whether the active network uses Wi-Fi specifically. */
fun Context.isOnWifiNetwork(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return try {
        val network = connectivityManager.activeNetwork ?: return false
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: SecurityException) {
        false
    }
}

fun isLanRouteAvailable(isWifi: Boolean, isEthernet: Boolean, isVpn: Boolean): Boolean =
    isWifi || isEthernet || isVpn

fun Context.isLanRouteAvailable(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return try {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        isLanRouteAvailable(
            isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        )
    } catch (_: SecurityException) {
        false
    }
}

/**
 * Best-effort current Wi-Fi SSID. Returns null when not on Wi-Fi or when the SSID cannot be
 * read (on Android 10+ a real SSID requires location permission + enabled location services;
 * without them the system returns "<unknown ssid>", which we treat as null). Callers using the
 * SSID to trust a local server must treat null as failure to verify the network, never as a match.
 */
@Suppress("DEPRECATION")
fun Context.currentWifiSsid(): String? {
    val wifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    val rawSsid = try {
        wifiManager.connectionInfo?.ssid
    } catch (_: SecurityException) {
        null
    } ?: return null
    val cleaned = rawSsid.trim('"')
    return cleaned.takeIf {
        it.isNotBlank() && !it.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true) &&
                !it.equals("<unknown ssid>", ignoreCase = true)
    }
}
