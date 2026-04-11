package com.dirtbike.weartracker.mobile.transfer

import android.net.Uri

object GpxTransferPaths {
    const val PHONE_CAPABILITY = "gpx_trail_phone_app"
    const val WATCH_CAPABILITY = "gpx_trail_watch_app"
    const val CHANNEL_PREFIX = "/gpxtrail/gpx"
    const val IMPORT_CHANNEL_PREFIX = "/gpxtrail/import"

    data class Metadata(
        val fileName: String,
        val rideName: String
    )

    fun isRideChannel(path: String): Boolean {
        return path.startsWith(CHANNEL_PREFIX)
    }

    fun parse(path: String): Metadata {
        val segments = Uri.parse("wear://gpxtrail$path").pathSegments
        val fileName = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "ride.gpx"
        val rideName = segments.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: fileName.removeSuffix(".gpx")
        return Metadata(fileName = fileName, rideName = rideName)
    }
}
