package com.dirtbike.weartracker.gpx

import android.content.Context
import com.dirtbike.weartracker.data.RideRepository
import com.dirtbike.weartracker.data.TrackPoint
import com.dirtbike.weartracker.data.Waypoint
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxWriter {

    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private const val DRAFT_FILE_NAME = "draft.gpx"
    private val fileLock = Any()

    @Volatile
    private var draftWritesEnabled = false

    fun enableDraftWrites() {
        synchronized(fileLock) {
            draftWritesEnabled = true
        }
    }

    fun disableDraftWrites() {
        synchronized(fileLock) {
            draftWritesEnabled = false
        }
    }

    fun write(
        context: Context,
        trackPoints: List<TrackPoint>,
        waypoints: List<Waypoint>,
        rideName: String? = null
    ): File {
        synchronized(fileLock) {
            draftWritesEnabled = false
            val timestamp = fileNameFormat.format(Date())
            val finalRideName = rideName?.takeIf { it.isNotBlank() } ?: "GPX Trail Ride $timestamp"
            val rideStartMs = trackPoints.firstOrNull()?.timestampMs ?: System.currentTimeMillis()
            val file = uniqueGpxFile(
                dir = RideRepository.getGpxDir(context),
                baseName = datedFileBaseName(finalRideName, rideStartMs)
            )
            file.writeText(
                buildRideGpx(
                    trackPoints = trackPoints,
                    waypoints = waypoints,
                    rideName = finalRideName
                )
            )
            File(RideRepository.getGpxDir(context), DRAFT_FILE_NAME).delete()
            return file
        }
    }

    fun writeDraft(
        context: Context,
        trackPoints: List<TrackPoint>,
        waypoints: List<Waypoint>
    ) {
        synchronized(fileLock) {
            if (!draftWritesEnabled || trackPoints.isEmpty()) return
            val file = File(RideRepository.getGpxDir(context), DRAFT_FILE_NAME)
            file.writeText(buildRideGpx(trackPoints, waypoints, "Draft Ride (unsaved)"))
        }
    }

    fun getDraftFile(context: Context): File? {
        synchronized(fileLock) {
            val file = File(RideRepository.getGpxDir(context), DRAFT_FILE_NAME)
            return if (file.exists() && file.length() > 0L) file else null
        }
    }

    fun promoteDraft(context: Context): File? {
        synchronized(fileLock) {
            draftWritesEnabled = false
            val draft = File(RideRepository.getGpxDir(context), DRAFT_FILE_NAME)
            if (!draft.exists() || draft.length() == 0L) return null
            val timestamp = fileNameFormat.format(Date(draft.lastModified()))
            val dest = File(RideRepository.getGpxDir(context), "ride_recovered_$timestamp.gpx")
            return if (draft.renameTo(dest)) dest else null
        }
    }

    fun clearDraft(context: Context) {
        synchronized(fileLock) {
            File(RideRepository.getGpxDir(context), DRAFT_FILE_NAME).delete()
        }
    }

    internal fun buildRideGpx(
        trackPoints: List<TrackPoint>,
        waypoints: List<Waypoint>,
        rideName: String,
        metadataTimestampMs: Long = System.currentTimeMillis()
    ): String {
        val safeRideName = escapeXml(rideName)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GPX Trail\"\n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1")
        sb.append(" http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>$safeRideName</name>\n")
        sb.append("    <time>${formatIsoTime(metadataTimestampMs)}</time>\n")
        sb.append("  </metadata>\n")

        for (wpt in waypoints) {
            val safeName = escapeXml(wpt.name)
            sb.append("  <wpt lat=\"${wpt.latitude}\" lon=\"${wpt.longitude}\">\n")
            sb.append("    <ele>${wpt.altitude}</ele>\n")
            sb.append("    <time>${formatIsoTime(wpt.timestampMs)}</time>\n")
            sb.append("    <name>$safeName</name>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("  <trk>\n")
        sb.append("    <name>$safeRideName</name>\n")
        var openSegmentId: Int? = null
        for (pt in trackPoints) {
            if (openSegmentId != pt.segmentId) {
                if (openSegmentId != null) {
                    sb.append("    </trkseg>\n")
                }
                sb.append("    <trkseg>\n")
                openSegmentId = pt.segmentId
            }
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${pt.altitude}</ele>\n")
            sb.append("        <time>${formatIsoTime(pt.timestampMs)}</time>\n")
            sb.append("      </trkpt>\n")
        }
        if (openSegmentId != null) {
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatIsoTime(timestampMs: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestampMs))
    }

    private fun safeFileBaseName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_', '.', ' ')
            .take(80)
            .ifBlank { "ride" }
    }

    private fun datedFileBaseName(name: String, timestampMs: Long): String {
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestampMs))
        val nameBase = safeFileBaseName(name).removeDatePrefix()
        return "${datePrefix}_$nameBase"
    }

    private fun String.removeDatePrefix(): String {
        return replace(Regex("^\\d{8}_+"), "").ifBlank { "ride" }
    }

    private fun uniqueGpxFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.gpx")
        if (!candidate.exists()) return candidate

        var suffix = 2
        while (suffix < 10_000) {
            candidate = File(dir, "${baseName}_$suffix.gpx")
            if (!candidate.exists()) return candidate
            suffix++
        }

        return File(dir, "${baseName}_${System.currentTimeMillis()}.gpx")
    }
}
