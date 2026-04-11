package com.dirtbike.weartracker.transfer

import android.net.Uri
import com.dirtbike.weartracker.data.ImportedGpxRepository
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WatchGpxImportService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(IMPORT_CHANNEL_PREFIX)) return

        serviceScope.launch {
            receiveImport(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun receiveImport(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        val fileName = parseFileName(channel.path)

        try {
            channelClient.getInputStream(channel).await().use { input ->
                ImportedGpxRepository.saveImportedGpx(this, fileName, input)
            }
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    private fun parseFileName(path: String): String {
        val segments = Uri.parse("wear://gpxtrail$path").pathSegments
        return segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "imported_waypoints.gpx"
    }

    companion object {
        const val WATCH_CAPABILITY = "gpx_trail_watch_app"
        const val IMPORT_CHANNEL_PREFIX = "/gpxtrail/import"
    }
}
