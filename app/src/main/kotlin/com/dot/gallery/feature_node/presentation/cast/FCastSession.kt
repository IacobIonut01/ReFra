/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.cast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.net.Inet4Address
import com.dot.gallery.R
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.presentation.util.printDebug
import com.dot.gallery.feature_node.presentation.util.printWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class FCastDevice(
    val name: String,
    val host: String,
    val port: Int
)

data class RemotePlaybackState(
    val state: PlaybackState = PlaybackState.IDLE,
    val timeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val speed: Double = 1.0,
    val volume: Double = 1.0,
    val error: String? = null
)

data class CastPermission(
    val permission: String,
    val labelRes: Int,
    val descriptionRes: Int,
    val granted: Boolean
)

data class CastSessionState(
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<FCastDevice> = emptyList(),
    val connectedDevice: FCastDevice? = null,
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val playback: RemotePlaybackState = RemotePlaybackState(),
    val castingMediaId: Long? = null
)

/**
 * Singleton service managing FCast device discovery, TCP connection, and media casting.
 */
@Singleton
class FCastSession @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(CastSessionState())
    val state: StateFlow<CastSessionState> = _state.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var receiverJob: Job? = null

    private var httpServer: LocalMediaHttpServer? = null

    // --- Permission checks ---

    /**
     * Returns true if the cast feature is available (i.e. the required network
     * permissions are declared in the manifest). When permissions are stripped
     * at build time the feature cannot work, so the UI should be hidden.
     */
    fun isCastAvailable(): Boolean {
        val requestedPerms = try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
        } catch (_: Exception) { null }
        return requestedPerms?.contains(Manifest.permission.INTERNET) == true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns a list of all required cast permissions with their current grant status.
     */
    fun checkPermissions(): List<CastPermission> = listOf(
        CastPermission(
            permission = Manifest.permission.INTERNET,
            labelRes = R.string.cast_perm_internet,
            descriptionRes = R.string.cast_perm_internet_desc,
            granted = hasPermission(Manifest.permission.INTERNET)
        ),
        CastPermission(
            permission = Manifest.permission.ACCESS_WIFI_STATE,
            labelRes = R.string.cast_perm_wifi_state,
            descriptionRes = R.string.cast_perm_wifi_state_desc,
            granted = hasPermission(Manifest.permission.ACCESS_WIFI_STATE)
        ),
        CastPermission(
            permission = Manifest.permission.ACCESS_NETWORK_STATE,
            labelRes = R.string.cast_perm_network_state,
            descriptionRes = R.string.cast_perm_network_state_desc,
            granted = hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        ),
        CastPermission(
            permission = Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            labelRes = R.string.cast_perm_multicast,
            descriptionRes = R.string.cast_perm_multicast_desc,
            granted = hasPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
        ),
    )

    /**
     * Returns true if all required permissions are granted.
     */
    fun hasAllPermissions(): Boolean = checkPermissions().all { it.granted }

    private fun checkNetworkPermissions(): String? {
        if (!hasPermission(Manifest.permission.INTERNET))
            return "Internet permission is required for casting"
        return null
    }

    // --- Discovery ---

    fun startDiscovery() {
        if (_state.value.isDiscovering) return
        _state.update { it.copy(isDiscovering = true, discoveredDevices = emptyList(), connectionError = null) }

        // Acquire multicast lock so Android doesn't filter out mDNS packets
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("fcast_discovery").apply {
                setReferenceCounted(true)
                acquire()
            }
            printDebug("FCast: Multicast lock acquired")
        } catch (e: SecurityException) {
            printWarning("FCast: Multicast permission denied: ${e.message}")
            // Discovery can still work for manual IP, just won't find mDNS devices
        } catch (e: Exception) {
            printWarning("FCast: Failed to acquire multicast lock: ${e.message}")
        }

        val manager = try {
            context.getSystemService(Context.NSD_SERVICE) as NsdManager
        } catch (e: Exception) {
            printWarning("FCast: NSD service unavailable: ${e.message}")
            _state.update { it.copy(isDiscovering = false) }
            return
        }
        nsdManager = manager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                printDebug("FCast: Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                printDebug("FCast: Found service: ${serviceInfo.serviceName}")
                resolveServiceCompat(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _state.update { st ->
                    st.copy(discoveredDevices = st.discoveredDevices.filter {
                        it.name != serviceInfo.serviceName
                    })
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                printDebug("FCast: Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                printWarning("FCast: Start discovery failed: $errorCode")
                _state.update { it.copy(isDiscovering = false) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                printWarning("FCast: Stop discovery failed: $errorCode")
            }
        }

        try {
            manager.discoverServices(
                FCastProtocol.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            printWarning("FCast: Failed to start NSD discovery: ${e.message}")
            _state.update { it.copy(isDiscovering = false, connectionError = e.message) }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) { }
        }
        discoveryListener = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                printDebug("FCast: Multicast lock released")
            }
        } catch (_: Exception) { }
        multicastLock = null
        _state.update { it.copy(isDiscovering = false) }
    }

    // --- Service resolution (version-gated) ---

    private fun resolveServiceCompat(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            resolveServiceModern(manager, serviceInfo)
        } else {
            resolveServiceLegacy(manager, serviceInfo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveServiceModern(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                printWarning("FCast: ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceUpdated(si: NsdServiceInfo) {
                val host = si.hostAddresses
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull()?.hostAddress ?: return
                onServiceResolved(si.serviceName, host, si.port)
                manager.unregisterServiceInfoCallback(this)
            }

            override fun onServiceLost() {
                printDebug("FCast: Service lost during resolve")
            }

            override fun onServiceInfoCallbackUnregistered() { }
        }
        manager.registerServiceInfoCallback(serviceInfo, { it.run() }, callback)
    }

    private fun resolveServiceLegacy(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                printWarning("FCast: Resolve failed for ${si.serviceName}: $errorCode")
            }

            override fun onServiceResolved(si: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = si.host?.hostAddress ?: return
                onServiceResolved(si.serviceName, host, si.port)
            }
        })
    }

    private fun onServiceResolved(name: String, host: String, port: Int) {
        val device = FCastDevice(name = name, host = host, port = port)
        printDebug("FCast: Resolved ${device.name} at ${device.host}:${device.port}")
        _state.update { st ->
            val devices = st.discoveredDevices.toMutableList()
            if (devices.none { it.host == device.host && it.port == device.port }) {
                devices.add(device)
            }
            st.copy(discoveredDevices = devices)
        }
    }

    // --- Connection ---

    fun connect(device: FCastDevice) {
        if (_state.value.connectedDevice != null) disconnect()

        // Check network permissions before attempting connection
        checkNetworkPermissions()?.let { error ->
            _state.update {
                it.copy(isConnecting = false, connectionError = error)
            }
            return
        }

        _state.update {
            it.copy(isConnecting = true, connectionError = null)
        }

        scope.launch {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(device.host, device.port), 5000)
                sock.soTimeout = 0

                socket = sock
                outputStream = sock.getOutputStream()
                inputStream = sock.getInputStream()

                // Send version handshake (v2)
                val versionBody = FCastProtocol.json.encodeToString(
                    VersionMessage.serializer(),
                    VersionMessage(version = 2)
                )
                outputStream?.sendPacket(Opcode.VERSION, versionBody)

                _state.update {
                    it.copy(
                        connectedDevice = device,
                        isConnecting = false,
                        connectionError = null
                    )
                }

                startReceiver()
                startHttpServer()

                printDebug("FCast: Connected to ${device.name}")
            } catch (e: SecurityException) {
                printWarning("FCast: Permission denied: ${e.message}")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = "Network permission denied"
                    )
                }
                cleanupConnection()
            } catch (e: Exception) {
                printWarning("FCast: Connection failed: ${e.message}")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = e.message ?: "Connection failed"
                    )
                }
                cleanupConnection()
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                outputStream?.sendPacket(Opcode.STOP)
            } catch (_: Exception) { }
            cleanupConnection()
            stopHttpServer()
            _state.update {
                CastSessionState(
                    isDiscovering = it.isDiscovering,
                    discoveredDevices = it.discoveredDevices
                )
            }
            printDebug("FCast: Disconnected")
        }
    }

    private fun cleanupConnection() {
        receiverJob?.cancel()
        receiverJob = null
        try { inputStream?.close() } catch (_: Exception) { }
        try { outputStream?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        outputStream = null
        inputStream = null
    }

    private fun startReceiver() {
        receiverJob?.cancel()
        receiverJob = scope.launch {
            val input = inputStream ?: return@launch
            try {
                while (isActive) {
                    val packet = input.readPacket() ?: break
                    handleReceiverPacket(packet)
                }
            } catch (_: Exception) {
                // Socket closed or read error
            }
            // Connection lost
            if (_state.value.connectedDevice != null) {
                _state.update {
                    it.copy(
                        connectedDevice = null,
                        connectionError = "Connection lost",
                        castingMediaId = null
                    )
                }
            }
        }
    }

    private fun handleReceiverPacket(packet: ReceivedPacket) {
        when (packet.opcode) {
            Opcode.PLAYBACK_UPDATE -> {
                packet.body?.let { body ->
                    try {
                        val update = FCastProtocol.json.decodeFromString(
                            PlaybackUpdateMessage.serializer(), body
                        )
                        _state.update { st ->
                            st.copy(
                                playback = st.playback.copy(
                                    state = PlaybackState.fromValue(update.state),
                                    timeSeconds = update.time ?: st.playback.timeSeconds,
                                    durationSeconds = update.duration ?: st.playback.durationSeconds,
                                    speed = update.speed ?: st.playback.speed,
                                    error = null
                                )
                            )
                        }
                    } catch (_: Exception) { }
                }
            }
            Opcode.VOLUME_UPDATE -> {
                packet.body?.let { body ->
                    try {
                        val update = FCastProtocol.json.decodeFromString(
                            VolumeUpdateMessage.serializer(), body
                        )
                        _state.update { st ->
                            st.copy(
                                playback = st.playback.copy(volume = update.volume)
                            )
                        }
                    } catch (_: Exception) { }
                }
            }
            Opcode.PLAYBACK_ERROR -> {
                packet.body?.let { body ->
                    try {
                        val error = FCastProtocol.json.decodeFromString(
                            PlaybackErrorMessage.serializer(), body
                        )
                        _state.update { st ->
                            st.copy(
                                playback = st.playback.copy(
                                    state = PlaybackState.IDLE,
                                    error = error.message
                                )
                            )
                        }
                    } catch (_: Exception) { }
                }
            }
            Opcode.PING -> {
                scope.launch {
                    try {
                        outputStream?.sendPacket(Opcode.PONG)
                    } catch (_: Exception) { }
                }
            }
            Opcode.VERSION -> {
                // Receiver version acknowledgment, ignore for now
            }
            else -> {
                printDebug("FCast: Unhandled opcode: ${packet.opcode}")
            }
        }
    }

    // --- HTTP Server ---

    private fun startHttpServer() {
        if (httpServer != null) return
        try {
            httpServer = LocalMediaHttpServer(context).also {
                it.start()
                printDebug("FCast: HTTP server started on port ${it.listeningPort}")
            }
        } catch (e: Exception) {
            printWarning("FCast: Failed to start HTTP server: ${e.message}")
            _state.update {
                it.copy(connectionError = "Failed to start media server: ${e.message}")
            }
        }
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
    }

    // --- Casting commands ---

    fun <T : Media> castMedia(media: T) {
        val server = httpServer ?: return
        val out = outputStream ?: return

        scope.launch {
            try {
                val token = media.id.toString()
                server.registerUri(
                    token = token,
                    uri = media.getUri(),
                    mimeType = media.mimeType,
                    size = media.size
                )

                val url = server.getMediaUrl(token)
                    ?: throw IllegalStateException("Cannot determine LAN URL")

                val playMsg = PlayMessage(
                    container = media.mimeType,
                    url = url,
                    time = 0.0,
                    speed = 1.0
                )
                val body = FCastProtocol.json.encodeToString(
                    PlayMessage.serializer(), playMsg
                )
                out.sendPacket(Opcode.PLAY, body)

                _state.update {
                    it.copy(
                        castingMediaId = media.id,
                        playback = RemotePlaybackState(state = PlaybackState.PLAYING)
                    )
                }
                printDebug("FCast: Casting ${media.label} → $url")
            } catch (e: Exception) {
                printWarning("FCast: Cast failed: ${e.message}")
            }
        }
    }

    fun castFile(file: File, mimeType: String, label: String, mediaId: Long) {
        val server = httpServer ?: return
        val out = outputStream ?: return

        scope.launch {
            try {
                val token = mediaId.toString()
                server.registerFile(token = token, file = file, mimeType = mimeType)

                val url = server.getMediaUrl(token)
                    ?: throw IllegalStateException("Cannot determine LAN URL")

                val playMsg = PlayMessage(
                    container = mimeType,
                    url = url,
                    time = 0.0,
                    speed = 1.0
                )
                val body = FCastProtocol.json.encodeToString(
                    PlayMessage.serializer(), playMsg
                )
                out.sendPacket(Opcode.PLAY, body)

                _state.update {
                    it.copy(
                        castingMediaId = mediaId,
                        playback = RemotePlaybackState(state = PlaybackState.PLAYING)
                    )
                }
                printDebug("FCast: Casting $label → $url")
            } catch (e: Exception) {
                printWarning("FCast: Cast file failed: ${e.message}")
            }
        }
    }

    fun pause() {
        scope.launch {
            try {
                outputStream?.sendPacket(Opcode.PAUSE)
            } catch (_: Exception) { }
        }
    }

    fun resume() {
        scope.launch {
            try {
                outputStream?.sendPacket(Opcode.RESUME)
            } catch (_: Exception) { }
        }
    }

    fun togglePlayPause() {
        if (_state.value.playback.state == PlaybackState.PLAYING) pause() else resume()
    }

    fun seek(timeSeconds: Double) {
        scope.launch {
            try {
                val body = FCastProtocol.json.encodeToString(
                    SeekMessage.serializer(), SeekMessage(time = timeSeconds)
                )
                outputStream?.sendPacket(Opcode.SEEK, body)
            } catch (_: Exception) { }
        }
    }

    fun setVolume(volume: Double) {
        scope.launch {
            try {
                val body = FCastProtocol.json.encodeToString(
                    SetVolumeMessage.serializer(), SetVolumeMessage(volume = volume.coerceIn(0.0, 1.0))
                )
                outputStream?.sendPacket(Opcode.SET_VOLUME, body)
            } catch (_: Exception) { }
        }
    }

    fun setSpeed(speed: Double) {
        scope.launch {
            try {
                val body = FCastProtocol.json.encodeToString(
                    SetSpeedMessage.serializer(), SetSpeedMessage(speed = speed)
                )
                outputStream?.sendPacket(Opcode.SET_SPEED, body)
            } catch (_: Exception) { }
        }
    }

    fun stop() {
        scope.launch {
            try {
                outputStream?.sendPacket(Opcode.STOP)
                httpServer?.unregisterAll()
                _state.update {
                    it.copy(
                        castingMediaId = null,
                        playback = RemotePlaybackState()
                    )
                }
            } catch (_: Exception) { }
        }
    }

    val isConnected: Boolean
        get() = _state.value.connectedDevice != null

    val isCasting: Boolean
        get() = _state.value.castingMediaId != null
}
