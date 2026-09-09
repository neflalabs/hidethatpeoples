package com.nefla.hidethatpeoples.privilege.adb

import android.content.Context
import android.util.Log
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.privilege.PrivilegeProvider
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import com.nefla.hidethatpeoples.privilege.PrivilegeType
import com.nefla.hidethatpeoples.privilege.adb.crypto.AdbKeyManager
import com.nefla.hidethatpeoples.privilege.adb.mdns.AdbMdnsDiscovery
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.security.PrivateKey
import java.security.cert.Certificate

class LocalAdbPrivilegeProvider(
    private val context: Context,
    private val keyManager: AdbKeyManager = AdbKeyManager(context),
    private val mdnsDiscovery: AdbMdnsDiscovery = AdbMdnsDiscovery(context)
) : AbsAdbConnectionManager(), PrivilegeProvider {

    companion object {
        private const val TAG = "LocalAdbProvider"
        private const val DEFAULT_HOST = "127.0.0.1"
    }

    override val type: PrivilegeType = PrivilegeType.LOCAL_ADB

    private val prefs = AppPreferences(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var pairingJob: Job? = null
    private var connectJob: Job? = null

    private val _state = MutableStateFlow<PrivilegeState>(PrivilegeState.Disconnected)
    override val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private var discoveredPairingPort: Int? = null
    private var discoveredPairingHost: String? = null
    private var discoveredConnectPort: Int? = null
    private var discoveredConnectHost: String? = null

    init {
        // Initialize keypair in background
        scope.launch {
            try {
                keyManager.getOrCreateKeyPair()
            } catch (e: Throwable) {
                Log.e(TAG, "Error pre-loading keys", e)
            }
            checkConnectionStatus()
        }
    }

    override fun getPrivateKey(): PrivateKey {
        return keyManager.getPrivateKey()
    }

    override fun getCertificate(): Certificate {
        return keyManager.getCertificate()
    }

    override fun getDeviceName(): String {
        return "HideThatPeoples"
    }

    suspend fun checkConnectionStatus() = withContext(Dispatchers.IO) {
        if (isConnected()) {
            _state.value = PrivilegeState.Ready(
                type = PrivilegeType.LOCAL_ADB,
                details = "${discoveredConnectHost ?: DEFAULT_HOST}:${prefs.lastAdbConnectPort}"
            )
            return@withContext
        }

        if (!prefs.isAdbPaired) {
            _state.value = PrivilegeState.PairingRequired(null)
            startPairingPortDiscovery()
            return@withContext
        }

        // Already paired before, attempt quick connect
        val lastPort = prefs.lastAdbConnectPort
        if (lastPort > 0) {
            _state.value = PrivilegeState.Connecting
            val success = tryConnect(lastPort)
            if (success) {
                _state.value = PrivilegeState.Ready(
                    type = PrivilegeType.LOCAL_ADB,
                    details = "${discoveredConnectHost ?: DEFAULT_HOST}:$lastPort"
                )
                return@withContext
            }
        }

        // Port might have changed, discover active connect port via mDNS
        startConnectPortDiscoveryAndConnect()
    }

    fun startPairingPortDiscovery() {
        pairingJob?.cancel()
        pairingJob = scope.launch {
            try {
                mdnsDiscovery.discoverServices(AdbMdnsDiscovery.SERVICE_TYPE_PAIRING)
                    .collect { service ->
                        val hostAddr = service.host.hostAddress ?: DEFAULT_HOST
                        Log.d(TAG, "Discovered pairing service on $hostAddr:${service.port}")
                        discoveredPairingPort = service.port
                        discoveredPairingHost = hostAddr
                        com.nefla.hidethatpeoples.ui.notification.PairingNotificationHelper.showPairingNotification(context, service.port)
                        if (_state.value !is PrivilegeState.Ready) {
                            _state.value = PrivilegeState.PairingRequired(
                                detectedPort = service.port,
                                hostIp = hostAddr
                            )
                        }
                    }
            } catch (e: Throwable) {
                Log.w(TAG, "Pairing port discovery stopped or failed", e)
            }
        }
    }

    fun stopPairingPortDiscovery() {
        pairingJob?.cancel()
        pairingJob = null
    }

    fun cancelConnecting() {
        connectJob?.cancel()
        connectJob = null
        _state.value = PrivilegeState.Disconnected
    }

    fun disconnectAdb() {
        connectJob?.cancel()
        connectJob = null
        scope.launch {
            try {
                if (isConnected()) {
                    disconnect()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error disconnecting ADB", e)
            }
            _state.value = PrivilegeState.Disconnected
        }
    }

    fun startConnectPortDiscoveryAndConnect(targetPort: Int? = null) {
        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = PrivilegeState.Connecting
            Log.d(TAG, "Starting connect port discovery and connection...")

            // 0. If user provided an explicit target port, attempt direct connection
            if (targetPort != null && targetPort > 0) {
                prefs.lastAdbConnectPort = targetPort
                val hostAddr = discoveredConnectHost ?: DEFAULT_HOST
                val success = tryConnect(targetPort, hostAddr)
                if (success) {
                    Log.i(TAG, "Connected successfully to manual port $targetPort")
                    _state.value = PrivilegeState.Ready(
                        type = PrivilegeType.LOCAL_ADB,
                        details = "$hostAddr:$targetPort"
                    )
                    return@launch
                } else {
                    val errMsg = "Gagal terhubung ke port $targetPort. Pastikan Wireless Debugging aktif di Developer Options."
                    Log.e(TAG, errMsg)
                    _state.value = PrivilegeState.Error(errMsg)
                    return@launch
                }
            }

            // 1. Try fast connect to last known port if available
            val lastPort = prefs.lastAdbConnectPort
            if (lastPort > 0) {
                Log.d(TAG, "Trying fast connect to last known port: $lastPort")
                if (tryConnect(lastPort)) {
                    Log.i(TAG, "Fast connect succeeded to port $lastPort")
                    _state.value = PrivilegeState.Ready(
                        type = PrivilegeType.LOCAL_ADB,
                        details = "${discoveredConnectHost ?: DEFAULT_HOST}:$lastPort"
                    )
                    return@launch
                }
            }

            // 2. Discover active connect port via mDNS
            val service = withTimeoutOrNull(8000L) {
                try {
                    mdnsDiscovery.discoverServices(AdbMdnsDiscovery.SERVICE_TYPE_CONNECT).first()
                } catch (e: Throwable) {
                    Log.w(TAG, "mDNS connect discovery error: ${e.message}")
                    null
                }
            }

            if (service != null) {
                val rawHost = service.host.hostAddress ?: DEFAULT_HOST
                val hostAddr = rawHost.split("%")[0]
                val port = service.port
                Log.i(TAG, "Discovered active connect port: $hostAddr:$port")
                discoveredConnectPort = port
                discoveredConnectHost = hostAddr
                prefs.lastAdbConnectPort = port

                val success = tryConnect(port, hostAddr)
                if (success) {
                    Log.i(TAG, "Connected successfully to ADB at $hostAddr:$port")
                    _state.value = PrivilegeState.Ready(
                        type = PrivilegeType.LOCAL_ADB,
                        details = "$hostAddr:$port"
                    )
                } else {
                    val errMsg = "Gagal terhubung ke port $hostAddr:$port"
                    Log.e(TAG, errMsg)
                    _state.value = PrivilegeState.Error(errMsg)
                }
            } else {
                Log.w(TAG, "Connect port not discovered via mDNS within timeout")
                if (!prefs.isAdbPaired) {
                    _state.value = PrivilegeState.PairingRequired(discoveredPairingPort)
                } else {
                    _state.value = PrivilegeState.Disconnected
                }
            }
        }
    }

    suspend fun pair(pairingCode: String, port: Int? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val targetPort = port ?: discoveredPairingPort
        if (targetPort == null || targetPort <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Port belum terdeteksi. Buka 'Pair device with pairing code' di Settings."))
        }

        _state.value = PrivilegeState.Connecting

        val candidateHosts = buildList<String> {
            discoveredPairingHost?.let { add(it) }
            add(DEFAULT_HOST)
            addAll(mdnsDiscovery.getLocalIpAddresses())
            add("localhost")
        }.distinct()

        Log.i(TAG, "Pairing candidates for port $targetPort: $candidateHosts")

        var lastError: Throwable? = null
        var isPaired = false

        for (host in candidateHosts) {
            try {
                Log.i(TAG, "Attempting SPAKE2 pairing to $host:$targetPort with code $pairingCode")
                val result = pair(host, targetPort, pairingCode.trim())
                if (result) {
                    isPaired = true
                    Log.i(TAG, "Pairing successful via $host:$targetPort!")
                    break
                } else {
                    Log.w(TAG, "Pairing returned false for $host:$targetPort")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Pairing attempt failed on $host:$targetPort: ${e.message}")
                lastError = e
            }
        }

        if (isPaired) {
            prefs.isAdbPaired = true
            stopPairingPortDiscovery()

            // Immediately try connecting to the newly authorized ADB session
            startConnectPortDiscoveryAndConnect()
            Result.success(Unit)
        } else {
            val errMsg = lastError?.localizedMessage ?: "Pairing rejected by device. Please verify the port and pairing code."
            Log.e(TAG, "All pairing attempts failed: $errMsg")
            _state.value = PrivilegeState.Error("Pairing error: $errMsg")
            Result.failure(lastError ?: RuntimeException(errMsg))
        }
    }

    private suspend fun tryConnect(port: Int, preferredHost: String? = null): Boolean = withContext(Dispatchers.IO) {
        val candidateHosts = buildList<String> {
            preferredHost?.let { add(it) }
            discoveredConnectHost?.let { add(it) }
            add(DEFAULT_HOST)
            addAll(mdnsDiscovery.getLocalIpAddresses())
            add("localhost")
        }.distinct()

        for (host in candidateHosts) {
            try {
                if (isConnected()) {
                    disconnect()
                }
                Log.d(TAG, "Trying to connect to ADB at $host:$port")
                val success = connect(host, port)
                if (success) {
                    Log.i(TAG, "Successfully connected to ADB at $host:$port")
                    return@withContext true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed connecting to $host:$port: ${e.message}")
            }
        }
        return@withContext false
    }

    override suspend fun clearShortcuts(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureConnected()) {
            return@withContext Result.failure(IllegalStateException("Wireless ADB is not connected. Please enable Wireless Debugging."))
        }

        try {
            val command = "shell:cmd shortcut clear-shortcuts --user 0 $packageName"
            val stream = openStream(command)
            val watchdog = scope.launch {
                delay(6000L)
                try { stream.close() } catch (_: Throwable) {}
            }
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val output = StringBuilder()
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            } catch (e: IOException) {
                // AdbStream throws "Stream closed." upon remote EOF
                if (e.message?.contains("Stream closed") != true) {
                    throw e
                }
            } finally {
                watchdog.cancel()
                try { stream.close() } catch (_: Throwable) {}
            }

            val resultStr = output.toString().trim()
            Log.d(TAG, "Cleared shortcuts for $packageName via Local ADB: $resultStr")
            Result.success(resultStr)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed executing clear-shortcuts for $packageName", e)
            if (!isConnected()) {
                _state.value = PrivilegeState.Disconnected
            }
            Result.failure(e)
        }
    }

    override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        if (packages.isEmpty()) return@withContext results
        if (!ensureConnected()) {
            packages.forEach { results[it] = false }
            return@withContext results
        }

        // Batch execution in chunks (up to 25 packages) to run in a single ADB stream.
        // This avoids dozens of separate TLS handshakes that easily block or lag.
        val chunks = packages.chunked(25)
        for (chunk in chunks) {
            try {
                val pkgListStr = chunk.joinToString(" ")
                val command = "shell:for p in $pkgListStr; do res=\$(cmd shortcut clear-shortcuts --user 0 \"\$p\" 2>&1); echo \"\$p:\$res\"; done"
                val stream = openStream(command)
                val watchdog = scope.launch {
                    delay(10000L)
                    try { stream.close() } catch (_: Throwable) {}
                }

                try {
                    val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
                    var line: String?
                    var receivedCount = 0
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.contains(":")) {
                            val parts = currentLine.split(":", limit = 2)
                            val pkg = parts[0].trim()
                            val res = parts[1].trim()
                            results[pkg] = res.contains("Success", ignoreCase = true)
                            Log.d(TAG, "Batch cleared shortcut for $pkg: $res")
                            receivedCount++
                        }
                        if (receivedCount >= chunk.size) {
                            break
                        }
                    }
                } catch (e: IOException) {
                    if (e.message?.contains("Stream closed") != true) {
                        Log.w(TAG, "Stream read error during batch clear: ${e.message}")
                    }
                } finally {
                    watchdog.cancel()
                    try { stream.close() } catch (_: Throwable) {}
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error during batch shortcut clear chunk", e)
                if (!isConnected()) {
                    _state.value = PrivilegeState.Disconnected
                }
            }
        }

        // Fill any unrecorded packages with false
        packages.forEach { pkg ->
            if (!results.containsKey(pkg)) {
                results[pkg] = false
            }
        }
        return@withContext results
    }

    private suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected()) return@withContext true

        val port = prefs.lastAdbConnectPort
        if (port > 0) {
            if (tryConnect(port)) return@withContext true
        }

        startConnectPortDiscoveryAndConnect()
        val timeoutMs = 4000L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isConnected()) return@withContext true
            kotlinx.coroutines.delay(200)
        }
        return@withContext isConnected()
    }

    override fun release() {
        stopPairingPortDiscovery()
        connectJob?.cancel()
        connectJob = null
        try {
            if (isConnected()) {
                disconnect()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error disconnecting ADB on release", e)
        }
    }
}
