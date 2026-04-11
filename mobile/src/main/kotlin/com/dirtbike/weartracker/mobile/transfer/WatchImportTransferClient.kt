package com.dirtbike.weartracker.mobile.transfer

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class WatchImportResult(
    val success: Boolean,
    val message: String
)

class WatchImportTransferClient(
    private val context: Context
) {
    suspend fun sendGpx(uri: Uri, fileName: String): WatchImportResult = withContext(Dispatchers.IO) {
        val watchNode = findWatchNode()
            ?: return@withContext WatchImportResult(
                false,
                "Watch app not found. Open GPX Trail on the watch and try again."
            )

        val channelClient = Wearable.getChannelClient(context)
        val encodedFileName = Uri.encode(fileName.ifBlank { "imported_waypoints.gpx" })
        val channel = try {
            channelClient.openChannel(
                watchNode.id,
                "${GpxTransferPaths.IMPORT_CHANNEL_PREFIX}/$encodedFileName"
            ).await()
        } catch (_: Exception) {
            return@withContext WatchImportResult(false, "Could not connect to watch.")
        }

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext WatchImportResult(false, "Could not open selected GPX.")

            channelClient.getOutputStream(channel).await().use { output ->
                input.use { it.copyTo(output) }
                output.flush()
            }
            WatchImportResult(true, "Sent GPX to watch.")
        } catch (_: Exception) {
            WatchImportResult(false, "Could not send GPX to watch.")
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    private suspend fun findWatchNode() =
        Wearable.getCapabilityClient(context)
            .getCapability(GpxTransferPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }
}
