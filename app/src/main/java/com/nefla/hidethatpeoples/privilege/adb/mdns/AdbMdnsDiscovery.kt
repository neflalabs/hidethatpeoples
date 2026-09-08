package com.nefla.hidethatpeoples.privilege.adb.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.net.NetworkInterface

class AdbMdnsDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "AdbMdnsDiscovery"
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp"
        const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // NsdManager resolution is single-threaded prior to API 34; serialize to avoid FAILURE_ALREADY_ACTIVE
    private val resolveMutex = Mutex()

    data class DiscoveredAdbService(
        val serviceType: String,
        val serviceName: String,
        val host: InetAddress,
        val port: Int,
        val isLocalDevice: Boolean
    )

    /**
     * Discovers ADB services as a cold Kotlin Flow.
     * Acquires MulticastLock and filters for local device IP.
     */
    fun discoverServices(
        serviceType: String,
        filterLocalDeviceOnly: Boolean = true
    ): Flow<DiscoveredAdbService> = callbackFlow {
        val multicastLock = try {
            wifiManager.createMulticastLock("AdbMdnsDiscoveryLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire MulticastLock", e)
            null
        }

        val localIps = if (filterLocalDeviceOnly) getLocalIpAddresses() else emptySet()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}, type: ${serviceInfo.serviceType}")

                CoroutineScope(Dispatchers.IO).launch {
                    resolveMutex.withLock {
                        resolveServiceSafely(serviceInfo) { resolvedInfo ->
                            val host = resolvedInfo.host ?: return@resolveServiceSafely
                            val hostAddress = host.hostAddress ?: ""
                            val port = resolvedInfo.port
                            val isLocal = localIps.contains(hostAddress) || hostAddress.startsWith("127.") || hostAddress.startsWith("fe80") || serviceType == SERVICE_TYPE_PAIRING

                            if (!filterLocalDeviceOnly || isLocal) {
                                val discovered = DiscoveredAdbService(
                                    serviceType = serviceType,
                                    serviceName = resolvedInfo.serviceName,
                                    host = host,
                                    port = port,
                                    isLocalDevice = isLocal
                                )
                                trySend(discovered)
                            }
                        }
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: code $errorCode")
                close(IllegalStateException("Discovery failed to start: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: code $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            close(e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery", e)
            }
            if (multicastLock?.isHeld == true) {
                try {
                    multicastLock.release()
                } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun resolveServiceSafely(
        serviceInfo: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit
    ) {
        val deferred = CompletableDeferred<NsdServiceInfo?>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    deferred.complete(null)
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    deferred.complete(info)
                    try {
                        nsdManager.unregisterServiceInfoCallback(this)
                    } catch (_: Exception) {}
                }

                override fun onServiceLost() {
                    deferred.complete(null)
                }

                override fun onServiceInfoCallbackUnregistered() {}
            }
            nsdManager.registerServiceInfoCallback(serviceInfo, { it.run() }, callback)
        } else {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                    deferred.complete(null)
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    deferred.complete(resolvedInfo)
                }
            })
        }

        val result = deferred.await()
        if (result != null) {
            onResolved(result)
        }
    }

    internal fun getLocalIpAddresses(): Set<String> {
        val ips = mutableSetOf("127.0.0.1")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ips
            for (intf in interfaces) {
                if (intf.isUp && !intf.isLoopback) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress != null) {
                            val ip = addr.hostAddress ?: continue
                            val cleanIp = ip.split("%")[0]
                            ips.add(cleanIp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP addresses", e)
        }
        return ips
    }
}
