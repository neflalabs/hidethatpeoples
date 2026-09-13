package com.nefla.hidethatpeoples.ui.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.nefla.hidethatpeoples.MainActivity
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingForegroundService : Service() {

    companion object {
        private const val TAG = "PairingFgs"
        const val ACTION_START = "com.nefla.hidethatpeoples.ACTION_START_PAIRING_SERVICE"
        const val ACTION_SUBMIT = "com.nefla.hidethatpeoples.ACTION_SUBMIT_PAIRING"
        const val ACTION_STOP = "com.nefla.hidethatpeoples.ACTION_CANCEL_PAIRING"
        const val EXTRA_PORT = "extra_port"

        fun start(context: Context, port: Int? = null) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_START
                if (port != null) putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PairingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                val port = intent?.getIntExtra(EXTRA_PORT, -1)?.takeIf { it > 0 }
                val notification = PairingNotificationHelper.buildNotification(this, port)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        PairingNotificationHelper.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(PairingNotificationHelper.NOTIFICATION_ID, notification)
                }
            }

            ACTION_SUBMIT -> {
                val remoteInput = intent?.let { RemoteInput.getResultsFromIntent(it) }
                val inputText = remoteInput?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_INPUT)?.toString()?.trim()

                if (inputText.isNullOrBlank()) {
                    Toast.makeText(this, "Pairing code cannot be empty", Toast.LENGTH_SHORT).show()
                    return START_NOT_STICKY
                }

                Log.i(TAG, "Received raw pairing input: $inputText")
                val parts = inputText.split(Regex("[,\\s:]+")).filter { it.isNotBlank() }
                val port: Int?
                val code: String

                if (parts.size >= 2) {
                    port = parts[0].toIntOrNull()
                    code = parts[1]
                } else {
                    code = parts[0]
                    port = PairingNotificationHelper.lastDiscoveredPort
                }

                if (port == null || port <= 0) {
                    val msg = "Port not detected yet. Please enter: [PORT] [CODE] (e.g. 39481 123456)"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    val notif = PairingNotificationHelper.buildNotification(this, null)
                    PairingNotificationHelper.notify(this, notif)
                    return START_NOT_STICKY
                }

                if (code.length != 6 || !code.all { it.isDigit() }) {
                    val msg = "Pairing code must be 6 numeric digits (got: '$code')"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    val notif = PairingNotificationHelper.buildNotification(this, port)
                    PairingNotificationHelper.notify(this, notif)
                    return START_NOT_STICKY
                }

                Toast.makeText(this, "Processing pairing to port $port...", Toast.LENGTH_SHORT).show()

                serviceScope.launch {
                    val privilegeManager = PrivilegeManager.getInstance(applicationContext)
                    val result = privilegeManager.pairLocalAdb(code, port)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(applicationContext, "Pairing Successful! Wireless ADB Connected 🟢", Toast.LENGTH_LONG).show()
                            PairingNotificationHelper.showSuccessNotification(applicationContext, "Port $port")
                            
                            // Otomatis bawa kembali user ke aplikasi
                            try {
                                val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                applicationContext.startActivity(openAppIntent)
                            } catch (e: Exception) {
                                android.util.Log.e("PairingService", "Failed to auto-open app", e)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_DETACH)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(false)
                            }
                            stopSelf()
                        } else {
                            val err = result.exceptionOrNull()?.localizedMessage ?: "Pairing rejected by system"
                            Toast.makeText(applicationContext, "Pairing Failed: $err", Toast.LENGTH_LONG).show()
                            PairingNotificationHelper.showErrorNotification(applicationContext, err)
                        }
                    }
                }
            }

            ACTION_STOP -> {
                PrivilegeManager.getInstance(applicationContext).localAdbProvider.stopPairingPortDiscovery()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
