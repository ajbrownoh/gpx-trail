package com.dirtbike.weartracker.gpx

import android.util.Xml
import com.dirtbike.weartracker.data.RideSummary
import com.dirtbike.weartracker.data.TrackPoint
import com.dirtbike.weartracker.data.Waypoint
import java.io.File
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.xmlpull.v1.XmlPullParser

object RideGpxParser {

    data class ParsedRide(
        val name: String?,
        val metadataTimestampMs: Long?,
        val trackPoints: List<TrackPoint>,
        val waypoints: List<Waypoint>
    )

    fun readSummary(file: File, isImported: Boolean = false): RideSummary {
        val parsed = runCatching { readRide(file) }.getOrNull()
        if (parsed == null) {
            return RideSummary(
                file = file,
                name = fallbackName(file),
                recordedAtMs = file.lastModified(),
                durationSeconds = 0L,
                distanceMeters = 0.0,
                waypointCount = 0,
                isImported = isImported
            )
        }

        val trackPoints = parsed.trackPoints
        val startTimeMs = trackPoints.firstOrNull()?.timestampMs
            ?: parsed.metadataTimestampMs
            ?: file.lastModified()
        val durationSeconds = calculateActiveDurationSeconds(trackPoints)

        return RideSummary(
            file = file,
            name = parsed.name?.takeIf { it.isNotBlank() } ?: fallbackName(file),
            recordedAtMs = startTimeMs,
            durationSeconds = durationSeconds,
            distanceMeters = calculateDistanceMeters(trackPoints),
            waypointCount = parsed.waypoints.size,
            isImported = isImported
        )
    }

    fun renameRide(file: File, newName: String): Boolean {
        val parsed = runCatching { readRide(file) }.getOrNull() ?: return false
        val rideName = newName.trim().ifBlank { parsed.name ?: fallbackName(file) }
        val originalModifiedMs = file.lastModified()

        return runCatching {
            file.writeText(
                GpxWriter.buildRideGpx(
                    trackPoints = parsed.trackPoints,
                    waypoints = parsed.waypoints,
                    rideName = rideName,
                    metadataTimestampMs = parsed.metadataTimestampMs ?: originalModifiedMs
                )
            )
            file.setLastModified(originalModifiedMs)
            true
        }.getOrDefault(false)
    }

    fun readRide(file: File): ParsedRide {
        val trackPoints = mutableListOf<TrackPoint>()
        val waypoints = mutableListOf<Waypoint>()
        var metadataName: String? = null
        var trackName: String? = null
        var metadataTimeMs: Long? = null
        var insideMetadata = false
        var insideTrack = false
        var insideTrackPoint = false
        var insideWaypoint = false
        var currentSegmentId = -1
        var currentTrackLat = 0.0
        var currentTrackLon = 0.0
        var currentTrackAlt = 0.0
        var currentTrackTimeMs: Long? = null
        var currentWaypointLat = 0.0
        var currentWaypointLon = 0.0
        var currentWaypointAlt = 0.0
        var currentWaypointTimeMs: Long? = null
        var currentWaypointName = ""
        var textContent = ""

        file.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        textContent = ""
                        when (localName(parser.name)) {
                            "metadata" -> insideMetadata = true
                            "trk" -> insideTrack = true
                            "trkseg" -> currentSegmentId += 1
                            "trkpt" -> {
                                insideTrackPoint = true
                                if (currentSegmentId < 0) currentSegmentId = 0
                                currentTrackLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                currentTrackLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                                currentTrackAlt = 0.0
                                currentTrackTimeMs = null
                            }
                            "wpt" -> {
                                insideWaypoint = true
                                currentWaypointLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                currentWaypointLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                                currentWaypointAlt = 0.0
                                currentWaypointTimeMs = null
                                currentWaypointName = ""
                            }
                        }
                    }

                    XmlPullParser.TEXT,
                    XmlPullParser.CDSECT -> {
                        textContent += parser.text ?: ""
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = localName(parser.name)
                        val value = textContent.trim()
                        when (tagName) {
                            "name" -> {
                                if (insideWaypoint) {
                                    currentWaypointName = value
                                } else if (insideMetadata && metadataName.isNullOrBlank()) {
                                    metadataName = value
                                } else if (insideTrack && !insideTrackPoint && trackName.isNullOrBlank()) {
                                    trackName = value
                                }
                            }

                            "time" -> {
                                val parsedTime = parseIsoTime(value)
                                when {
                                    insideWaypoint -> currentWaypointTimeMs = parsedTime
                                    insideTrackPoint -> currentTrackTimeMs = parsedTime
                                    insideMetadata -> metadataTimeMs = parsedTime
                                }
                            }

                            "ele" -> {
                                val elevation = value.toDoubleOrNull() ?: 0.0
                                when {
                                    insideWaypoint -> currentWaypointAlt = elevation
                                    insideTrackPoint -> currentTrackAlt = elevation
                                }
                            }

                            "trkpt" -> {
                                trackPoints += TrackPoint(
                                    latitude = currentTrackLat,
                                    longitude = currentTrackLon,
                                    altitude = currentTrackAlt,
                                    timestampMs = currentTrackTimeMs
                                        ?: trackPoints.lastOrNull()?.timestampMs
                                        ?: file.lastModified(),
                                    segmentId = currentSegmentId.coerceAtLeast(0)
                                )
                                insideTrackPoint = false
                            }

                            "wpt" -> {
                                waypoints += Waypoint(
                                    name = currentWaypointName.ifBlank { "Spot ${waypoints.size + 1}" },
                                    latitude = currentWaypointLat,
                                    longitude = currentWaypointLon,
                                    altitude = currentWaypointAlt,
                                    timestampMs = currentWaypointTimeMs ?: file.lastModified()
                                )
                                insideWaypoint = false
                            }

                            "metadata" -> insideMetadata = false
                            "trk" -> insideTrack = false
                        }
                        textContent = ""
                    }
                }
                eventType = parser.next()
            }
        }

        return ParsedRide(
            name = metadataName?.takeIf { it.isNotBlank() } ?: trackName,
            metadataTimestampMs = metadataTimeMs,
            trackPoints = trackPoints,
            waypoints = waypoints
        )
    }

    private fun fallbackName(file: File): String {
        return file.nameWithoutExtension
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Session ${SimpleDateFormat("MMM d", Locale.US).format(Date(file.lastModified()))}" }
    }

    private fun localName(name: String?): String {
        return name?.substringAfter(':').orEmpty()
    }

    private fun parseIsoTime(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun calculateDistanceMeters(trackPoints: List<TrackPoint>): Double {
        if (trackPoints.size < 2) return 0.0

        var distance = 0.0
        for (index in 1 until trackPoints.size) {
            val previous = trackPoints[index - 1]
            val current = trackPoints[index]
            if (previous.segmentId != current.segmentId) continue
            distance += haversineMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude
            )
        }
        return distance
    }

    private fun calculateActiveDurationSeconds(trackPoints: List<TrackPoint>): Long {
        if (trackPoints.size < 2) return 0L

        var durationMs = 0L
        for (index in 1 until trackPoints.size) {
            val previous = trackPoints[index - 1]
            val current = trackPoints[index]
            if (previous.segmentId != current.segmentId) continue
            durationMs += (current.timestampMs - previous.timestampMs).coerceAtLeast(0L)
        }
        return durationMs / 1000L
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return radiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
