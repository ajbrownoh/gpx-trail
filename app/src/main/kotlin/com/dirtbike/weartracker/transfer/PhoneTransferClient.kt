package com.dirtbike.weartracker.transfer

import android.content.Context
import android.net.Uri
import com.dirtbike.weartracker.data.RideSummary
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class PhoneTransferResult(
    val success: Boolean,
    val message: String
)

class PhoneTransferClient(
    private val context: Context
) {
    suspend fun sendRide(ride: RideSummary): PhoneTransferResult = withContext(Dispatchers.IO) {
        if (!ride.file.exists()) {
            return@withContext PhoneTransferResult(false, "GPX file not found.")
        }

        val phoneNode = findPhoneNode()
            ?: return@withContext PhoneTransferResult(
                false,
                "Phone app not found. Install GPX Trail on your phone first."
            )

        val channelClient = Wearable.getChannelClient(context)
        val channel = try {
            channelClient.openChannel(phoneNode.id, buildChannelPath(ride)).await()
        } catch (_: Exception) {
            return@withContext PhoneTransferResult(false, "Could not connect to phone.")
        }

        try {
            channelClient.getOutputStream(channel).await().use { output ->
                ride.file.inputStream().use { input ->
                    input.copyTo(output)
                }
                output.flush()
            }
            PhoneTransferResult(true, "Sent to phone.")
        } catch (_: Exception) {
            PhoneTransferResult(false, "Could not send GPX to phone.")
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    private suspend fun findPhoneNode() =
        Wearable.getCapabilityClient(context)
            .getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }

    private fun buildChannelPath(ride: RideSummary): String {
        val fileName = Uri.encode(ride.file.name)
        val rideName = Uri.encode(ride.name.ifBlank { ride.file.nameWithoutExtension })
        return "$CHANNEL_PREFIX/$fileName/$rideName"
    }

    companion object {
        private const val PHONE_CAPABILITY = "gpx_trail_phone_app"
        private const val CHANNEL_PREFIX = "/gpxtrail/gpx"
    }
}
