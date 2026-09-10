/**
 * Original from
 * https://medium.com/scalereal/observing-live-connectivity-status-in-jetpack-compose-way-f849ce8431c7
 */
package com.dot.gallery.feature_node.presentation.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal inline fun runNetworkCallbackOperation(operation: () -> Unit): Boolean =
    try {
        operation()
        true
    } catch (_: RuntimeException) {
        false
    }

@ExperimentalCoroutinesApi
@Composable
fun connectivityState(): State<ConnectionState> {
    val context = LocalContext.current

    // Creates a State<ConnectionState> with current connectivity state as initial value
    return produceState(initialValue = context.currentConnectivityState) {
        // In a coroutine, can make suspend calls
        context.observeConnectivityAsFlow().collect { value = it }
    }
}

/**
 * Network Utility to observe availability or unavailability of Internet connection
 */
@ExperimentalCoroutinesApi
private fun Context.observeConnectivityAsFlow() = callbackFlow {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val callback = networkCallback { connectionState -> trySend(connectionState) }

    val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    if (!runNetworkCallbackOperation {
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        }) {
        // ACCESS_NETWORK_STATE permission not available
        trySend(ConnectionState.Unavailable)
        awaitClose()
        return@callbackFlow
    }

    // Set current state
    val currentState = getCurrentConnectivityState(connectivityManager)
    trySend(currentState)

    // Remove callback when not used
    awaitClose {
        // Remove listeners
        runNetworkCallbackOperation {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
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

private fun networkCallback(callback: (ConnectionState) -> Unit): ConnectivityManager.NetworkCallback {
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
private fun getCurrentConnectivityState(
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

internal fun isLanRouteAvailable(isWifi: Boolean, isEthernet: Boolean, isVpn: Boolean): Boolean =
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
 * without them the system returns "<unknown ssid>", which we treat as null). Callers should
 * gracefully degrade — e.g. treat a blank configured SSID as "any local network".
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