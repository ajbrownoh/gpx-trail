package com.dirtbike.weartracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires on device boot. If tracking was active when the watch restarted,
 * shows a persistent notification so the user knows their session was interrupted.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (TrackingService.wasInterruptedByBoot(context)) {
            TrackingService.clearInterruptedFlag(context)
            showInterruptNotification(context)
        }
    }

    private fun showInterruptNotification(context: Context) {
        val channelId = "boot_interrupt"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Session Interrupted",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle("Session tracking interrupted")
            .setContentText("Your watch restarted during a session. Open GPX Trail to start a new session.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()

        nm.notify(2, notification)
    }
}
