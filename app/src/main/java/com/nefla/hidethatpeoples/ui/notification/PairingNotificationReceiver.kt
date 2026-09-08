package com.nefla.hidethatpeoples.ui.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PairingNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PairingReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")

        if (action == PairingNotificationHelper.ACTION_CANCEL_PAIRING) {
            PairingNotificationHelper.cancelNotification(context)
            PrivilegeManager.getInstance(context).localAdbProvider.stopPairingPortDiscovery()
            return
        }

        if (action == PairingNotificationHelper.ACTION_SUBMIT_PAIRING) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val inputText = remoteInput?.getCharSequence(PairingNotificationHelper.KEY_PAIRING_INPUT)?.toString()?.trim()

            if (inputText.isNullOrBlank()) {
                Toast.makeText(context, "Kode pairing tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return
            }

            Log.i(TAG, "Received raw pairing input: $inputText")

            // Parse port and pairing code:
            // Case 1: "39105 482910" or "39105, 482910" or "39105:482910"
            // Case 2: "482910" (Uses auto-discovered port)
            val parts = inputText.split(Regex("[,\\s:]+")).filter { it.isNotBlank() }

            val port: Int?
            val code: String

            if (parts.size >= 2) {
                // First part is port, second part is 6-digit code
                port = parts[0].toIntOrNull()
                code = parts[1]
            } else {
                code = parts[0]
                port = PairingNotificationHelper.lastDiscoveredPort
            }

            if (port == null || port <= 0) {
                val errorMsg = "Port belum terdeteksi. Silakan ketik dengan format: [PORT] [KODE] (contoh: 39105 123456)"
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                PairingNotificationHelper.showPairingNotification(context)
                return
            }

            if (code.length != 6 || !code.all { it.isDigit() }) {
                val errorMsg = "Kode pairing harus 6 digit angka (didapat: '$code')"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                PairingNotificationHelper.showPairingNotification(context, port)
                return
            }

            Toast.makeText(context, "Memproses pairing ke port $port...", Toast.LENGTH_SHORT).show()

            val privilegeManager = PrivilegeManager.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                val result = privilegeManager.pairLocalAdb(code, port)
                Handler(Looper.getMainLooper()).post {
                    if (result.isSuccess) {
                        Toast.makeText(context, "Pairing Berhasil! Wireless ADB Terhubung 🟢", Toast.LENGTH_LONG).show()
                        PairingNotificationHelper.showSuccessNotification(context, "Port $port")
                    } else {
                        val err = result.exceptionOrNull()?.localizedMessage ?: "Pairing ditolak oleh sistem"
                        Toast.makeText(context, "Pairing Gagal: $err", Toast.LENGTH_LONG).show()
                        PairingNotificationHelper.showErrorNotification(context, err)
                    }
                }
            }
        }
    }
}
