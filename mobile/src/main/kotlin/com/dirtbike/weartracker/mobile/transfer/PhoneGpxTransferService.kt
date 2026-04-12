package com.dirtbike.weartracker.mobile.transfer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dirtbike.weartracker.mobile.MainActivity
import com.dirtbike.weartracker.mobile.R
import com.dirtbike.weartracker.mobile.ShareRideActivity
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PhoneGpxTransferService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (!GpxTransferPaths.isRideChannel(path)) return

        serviceScope.launch {
            receiveRide(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun receiveRide(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        val metadata = GpxTransferPaths.parse(channel.path)
        val fileName = sanitizeGpxFileName(metadata.fileName)

        try {
            val uri = channelClient.getInputStream(channel).await().use { input ->
                saveToDownloads(fileName, input)
            }
            ReceivedRideStore.saveLatest(this, fileName, uri.toString())
            showReceivedNotification(fileName, uri)
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    private fun saveToDownloads(fileName: String, input: InputStream): Uri {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GPX Trail")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create Downloads entry for $fileName")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Unable to write $fileName")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun showReceivedNotification(fileName: String, uri: Uri) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureNotificationChannel()

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            android.content.Intent(this, MainActivity::class.java),
            flags
        )
        val shareIntent = PendingIntent.getActivity(
            this,
            101,
            ShareRideActivity.createIntent(this, uri.toString(), fileName),
            flags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gpx)
            .setContentTitle("GPX received")
            .setContentText(fileName)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$fileName saved to Downloads/GPX Trail.")
            )
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_gpx, "Share", shareIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPX Transfers",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun sanitizeGpxFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "session.gpx" }
        return if (cleaned.endsWith(".gpx", ignoreCase = true)) cleaned else "$cleaned.gpx"
    }

    companion object {
        private const val CHANNEL_ID = "gpx_transfers"
        private const val NOTIFICATION_ID = 42
    }
}
