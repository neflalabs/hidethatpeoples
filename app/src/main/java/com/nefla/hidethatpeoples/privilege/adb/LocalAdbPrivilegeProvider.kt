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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
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
    private var discoveredConnectPort: Int? = null

    init {
        // Initialize keypair in background
        scope.launch {
            try {
                keyManager.getOrCreateKeyPair()
            } catch (e: Throwable) {
                Log.e(TAG, "Error pre-loading keys", e)
            }
            checkInitialStatus()
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

    private suspend fun checkInitialStatus() {
        if (isConnected()) {
            _state.value = PrivilegeState.Ready(PrivilegeType.LOCAL_ADB, "$DEFAULT_HOST:$discoveredConnectPort")
            return
        }

        if (!prefs.isAdbPaired) {
            _state.value = PrivilegeState.PairingRequired(null)
            startPairingPortDiscovery()
            return
        }

        // Already paired before, attempt quick connect
        val lastPort = prefs.lastAdbConnectPort
        if (lastPort > 0) {
            _state.value = PrivilegeState.Connecting
            val success = tryConnect(DEFAULT_HOST, lastPort)
            if (success) {
                _state.value = PrivilegeState.Ready(PrivilegeType.LOCAL_ADB, "$DEFAULT_HOST:$lastPort")
                return
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
                        Log.d(TAG, "Discovered pairing service on port ${service.port}")
                        discoveredPairingPort = service.port
                        com.nefla.hidethatpeoples.ui.notification.PairingNotificationHelper.showPairingNotification(context, service.port)
                        if (_state.value !is PrivilegeState.Ready) {
                            _state.value = PrivilegeState.PairingRequired(
                                detectedPort = service.port,
                                hostIp = service.host.hostAddress ?: DEFAULT_HOST
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
        com.nefla.hidethatpeoples.ui.notification.PairingNotificationHelper.cancelNotification(context)
    }

    fun startConnectPortDiscoveryAndConnect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = PrivilegeState.Connecting
            val discovered = withTimeoutOrNull(8000L) {
                var foundPort: Int? = null
                mdnsDiscovery.discoverServices(AdbMdnsDiscovery.SERVICE_TYPE_CONNECT)
                    .collect { service ->
                        Log.d(TAG, "Discovered active connect port: ${service.port}")
                        foundPort = service.port
                        discoveredConnectPort = service.port
                        prefs.lastAdbConnectPort = service.port
                        return@collect
                    }
                foundPort
            }

            if (discovered != null) {
                val success = tryConnect(DEFAULT_HOST, discovered)
                if (success) {
                    _state.value = PrivilegeState.Ready(PrivilegeType.LOCAL_ADB, "$DEFAULT_HOST:$discovered")
                } else {
                    _state.value = PrivilegeState.Error("Failed to connect to ADB port $discovered")
                }
            } else {
                // If not found via mDNS, prompt pairing or check if wireless debugging is enabled
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
            return@withContext Result.failure(IllegalArgumentException("No valid pairing port detected. Please ensure 'Pair device with pairing code' is open."))
        }

        _state.value = PrivilegeState.Connecting
        Log.i(TAG, "Attempting SPAKE2 pairing to $DEFAULT_HOST:$targetPort with code $pairingCode")

        try {
            val paired = pair(DEFAULT_HOST, targetPort, pairingCode.trim())
            if (paired) {
                Log.i(TAG, "Pairing successful!")
                prefs.isAdbPaired = true
                stopPairingPortDiscovery()

                // Immediately try connecting to the newly authorized ADB session
                startConnectPortDiscoveryAndConnect()
                Result.success(Unit)
            } else {
                val errMsg = "Pairing rejected by device. Please check the 6-digit code and try again."
                Log.e(TAG, errMsg)
                _state.value = PrivilegeState.Error(errMsg)
                Result.failure(RuntimeException(errMsg))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Pairing exception", e)
            _state.value = PrivilegeState.Error("Pairing error: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private suspend fun tryConnect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isConnected()) {
                disconnect()
            }
            connect(host, port)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed connecting to $host:$port", e)
            false
        }
    }

    override suspend fun clearShortcuts(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureConnected()) {
            return@withContext Result.failure(IllegalStateException("Wireless ADB is not connected. Please enable Wireless Debugging."))
        }

        try {
            val command = "shell:cmd shortcut clear-shortcuts --user 0 $packageName"
            val stream = openStream(command)
            val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            stream.close()

            val resultStr = output.toString().trim()
            Log.d(TAG, "Cleared shortcuts for $packageName via Local ADB: $resultStr")
            Result.success(resultStr)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed executing clear-shortcuts for $packageName", e)
            // Mark disconnected if connection dropped
            if (!isConnected()) {
                _state.value = PrivilegeState.Disconnected
            }
            Result.failure(e)
        }
    }

    override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        if (!ensureConnected()) {
            packages.forEach { results[it] = false }
            return@withContext results
        }

        for (pkg in packages) {
            val res = clearShortcuts(pkg)
            results[pkg] = res.isSuccess
        }
        results
    }

    private suspend fun ensureConnected(): Boolean {
        if (isConnected()) return true

        val port = discoveredConnectPort ?: prefs.lastAdbConnectPort
        if (port > 0) {
            val success = tryConnect(DEFAULT_HOST, port)
            if (success) {
                _state.value = PrivilegeState.Ready(PrivilegeType.LOCAL_ADB, "$DEFAULT_HOST:$port")
                return true
            }
        }

        // Try quick discovery
        startConnectPortDiscoveryAndConnect()
        return false
    }

    override fun release() {
        stopPairingPortDiscovery()
        connectJob?.cancel()
        try {
            disconnect()
        } catch (_: Throwable) {}
    }
}
