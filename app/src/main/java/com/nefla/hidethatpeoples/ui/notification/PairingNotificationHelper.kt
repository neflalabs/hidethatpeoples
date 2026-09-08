package com.nefla.hidethatpeoples.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nefla.hidethatpeoples.MainActivity

object PairingNotificationHelper {

    const val CHANNEL_ID = "adb_wireless_pairing"
    const val NOTIFICATION_ID = 7701
    const val KEY_PAIRING_INPUT = "key_pairing_input"
    const val ACTION_SUBMIT_PAIRING = "com.nefla.hidethatpeoples.ACTION_SUBMIT_PAIRING"
    const val ACTION_CANCEL_PAIRING = "com.nefla.hidethatpeoples.ACTION_CANCEL_PAIRING"

    @Volatile
    var lastDiscoveredPort: Int? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wireless ADB Pairing"
            val descriptionText = "Notifikasi untuk memasukkan kode pairing tanpa menutup Settings"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPairingNotification(context: Context, detectedPort: Int? = null) {
        createNotificationChannel(context)
        if (detectedPort != null && detectedPort > 0) {
            lastDiscoveredPort = detectedPort
        }

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_INPUT)
            .setLabel(
                if (lastDiscoveredPort != null)
                    "Ketik 6 digit code (Port $lastDiscoveredPort)"
                else
                    "Ketik 6 digit code (atau 'PORT KODE')"
            )
            .build()

        val submitIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING
        }
        val submitPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Masukkan Kode Pairing",
            submitPendingIntent
        ).addRemoteInput(remoteInput).build()

        val cancelIntent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = ACTION_CANCEL_PAIRING
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val portText = if (lastDiscoveredPort != null) {
            "🟢 Port $lastDiscoveredPort terdeteksi! Tarik ke bawah & ketik 6 digit kode."
        } else {
            "Mencari port... Tarik ke bawah untuk ketik kode (atau format: PORT KODE)."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Wireless ADB Pairing")
            .setContentText(portText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$portText\n\nDialog 'Pair device with pairing code' jangan ditutup. Ketik kode langsung dari notifikasi ini."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showSuccessNotification(context: Context, detail: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Pairing Berhasil! 🟢")
            .setContentText("Wireless ADB terhubung ($detail). Siap membersihkan Direct Share.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(5000L)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showErrorNotification(context: Context, error: String) {
        // Re-show notification with error and input still available
        showPairingNotification(context, lastDiscoveredPort)
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
