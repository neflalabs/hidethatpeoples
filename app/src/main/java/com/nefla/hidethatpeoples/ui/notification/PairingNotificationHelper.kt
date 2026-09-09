package com.nefla.hidethatpeoples.ui.notification

import android.app.Notification
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
            val descriptionText = "Notification to enter pairing code without closing Settings"
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

    fun buildNotification(context: Context, detectedPort: Int? = null): Notification {
        createNotificationChannel(context)
        if (detectedPort != null && detectedPort > 0) {
            lastDiscoveredPort = detectedPort
        }

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_INPUT)
            .setLabel(
                if (lastDiscoveredPort != null)
                    "Enter 6-digit code (Port $lastDiscoveredPort)"
                else
                    "Enter 6-digit code (or 'PORT CODE')"
            )
            .build()

        val submitIntent = Intent(context, PairingForegroundService::class.java).apply {
            action = PairingForegroundService.ACTION_SUBMIT
        }
        val submitPendingIntent = PendingIntent.getService(
            context,
            0,
            submitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_input_add,
            "Enter Pairing Code",
            submitPendingIntent
        ).addRemoteInput(remoteInput).build()

        val cancelIntent = Intent(context, PairingForegroundService::class.java).apply {
            action = PairingForegroundService.ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getService(
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
            "🟢 Port $lastDiscoveredPort detected! Pull down & enter 6-digit code."
        } else {
            "Searching for port... Pull down & enter: [PORT] [CODE]"
        }

        val bigText = if (lastDiscoveredPort != null) {
            "🟢 Port $lastDiscoveredPort detected!\nEnter the 6-digit pairing code from Settings dialog.\n(If port differs, enter: [PORT] [CODE], e.g. $lastDiscoveredPort 123456)"
        } else {
            "Discovering port via mDNS...\nOpen 'Pair device with pairing code' dialog.\nEnter format: [PORT] [CODE] (e.g. 39481 123456)"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Wireless ADB Pairing")
            .setContentText(portText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    fun showPairingNotification(context: Context, detectedPort: Int? = null) {
        if (detectedPort != null && detectedPort > 0) {
            lastDiscoveredPort = detectedPort
        }
        PairingForegroundService.start(context, detectedPort)
    }

    fun notify(context: Context, notification: Notification) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showSuccessNotification(context: Context, detail: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Pairing Successful! 🟢")
            .setContentText("Wireless ADB connected ($detail). Ready to clear Direct Share contacts.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(6000L)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showErrorNotification(context: Context, error: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Pairing Failed ❌")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$error\n\nEnsure 'Pair device with pairing code' dialog is currently active, verify the port and code, and try again."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        PairingForegroundService.stop(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
