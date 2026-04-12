package com.dirtbike.weartracker.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.dirtbike.weartracker.data.TrackPoint
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.GreenStart
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.TrackLine
import com.dirtbike.weartracker.ui.theme.WaypointBlue
import com.dirtbike.weartracker.ui.theme.WaypointYellow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val RouteStartColor = Color(0xFF4B525C)
private val WaypointGuideDash = PathEffect.dashPathEffect(floatArrayOf(9f, 8f), 0f)

@Composable
fun RideMapCanvas(
    trackPoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    heading: Float,
    zoomFactor: Float,
    centerOnCurrent: Boolean,
    showEndPoint: Boolean,
    activeWaypoint: Waypoint? = null,
    waypointColors: List<Color> = emptyList(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (trackPoints.isEmpty()) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val pad = size.minDimension * 0.06f
        val referencePoint = if (centerOnCurrent) {
            trackPoints.last()
        } else {
            val minLat = trackPoints.minOf { it.latitude }
            val maxLat = trackPoints.maxOf { it.latitude }
            val minLon = trackPoints.minOf { it.longitude }
            val maxLon = trackPoints.maxOf { it.longitude }
            TrackPoint(
                latitude = (minLat + maxLat) / 2.0,
                longitude = (minLon + maxLon) / 2.0,
                altitude = 0.0,
                timestampMs = 0L
            )
        }
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(referencePoint.latitude))

        fun relativeOffset(latitude: Double, longitude: Double): Offset {
            val xMeters = ((longitude - referencePoint.longitude) * metersPerDegreeLon).toFloat()
            val yMeters = ((referencePoint.latitude - latitude) * metersPerDegreeLat).toFloat()
            return Offset(xMeters, yMeters)
        }

        val relativeTrack = trackPoints.map { relativeOffset(it.latitude, it.longitude) }
        val relativeWaypoints = waypoints.map { relativeOffset(it.latitude, it.longitude) }
        val allOffsets = relativeTrack + relativeWaypoints
        val maxX = max(12f, allOffsets.maxOfOrNull { abs(it.x) } ?: 12f)
        val maxY = max(12f, allOffsets.maxOfOrNull { abs(it.y) } ?: 12f)
        val fitScale = min(
            (size.width / 2f - pad) / maxX,
            (size.height / 2f - pad) / maxY
        )
        val scale = fitScale * zoomFactor

        fun project(offset: Offset): Offset {
            return Offset(
                x = center.x + offset.x * scale,
                y = center.y + offset.y * scale
            )
        }

        val waypointGuide = if (centerOnCurrent && activeWaypoint != null) {
            val currentPoint = trackPoints.last()
            Triple(
                first = project(relativeTrack.last()),
                second = project(relativeOffset(activeWaypoint.latitude, activeWaypoint.longitude)),
                third = formatWaypointGuideDistance(
                    haversineMeters(
                        currentPoint.latitude,
                        currentPoint.longitude,
                        activeWaypoint.latitude,
                        activeWaypoint.longitude
                    )
                )
            )
        } else {
            null
        }

        rotate(degrees = -heading, pivot = center) {
            val projectedTrack = relativeTrack.map(::project)

            if (waypointGuide != null) {
                drawLine(
                    color = WaypointBlue.copy(alpha = 0.55f),
                    start = waypointGuide.first,
                    end = waypointGuide.second,
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                    pathEffect = WaypointGuideDash
                )
            }

            if (projectedTrack.size >= 2) {
                for (segmentIndex in 1 until projectedTrack.size) {
                    if (trackPoints[segmentIndex - 1].segmentId != trackPoints[segmentIndex].segmentId) {
                        continue
                    }
                    val start = projectedTrack[segmentIndex - 1]
                    val end = projectedTrack[segmentIndex]
                    val progress = segmentIndex / projectedTrack.lastIndex.toFloat()
                    drawLine(
                        color = routeProgressColor(progress),
                        start = start,
                        end = end,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            val projectedWaypoints = relativeWaypoints.map(::project)
            val markerPoints = buildList {
                add(project(relativeTrack.first()))
                if (showEndPoint && relativeTrack.size >= 2) {
                    add(project(relativeTrack.last()))
                }
                addAll(projectedWaypoints)
            }
            val visibleMarkerPoints = spreadNearbyPoints(markerPoints)
            var markerIndex = 0

            drawCircle(
                color = GreenStart,
                radius = 5f,
                center = visibleMarkerPoints[markerIndex++]
            )

            if (showEndPoint && relativeTrack.size >= 2) {
                val endPoint = visibleMarkerPoints[markerIndex++]
                drawCircle(
                    color = OrangeAccent,
                    radius = 5f,
                    center = endPoint
                )
            }

            visibleMarkerPoints.drop(markerIndex).forEachIndexed { index, waypointPoint ->
                val waypointColor = waypointColors.getOrNull(index) ?: WaypointYellow
                drawCircle(
                    color = waypointColor,
                    radius = 5f,
                    center = waypointPoint
                )
                drawCircle(
                    color = BackgroundBlack,
                    radius = 2.5f,
                    center = waypointPoint
                )
            }
        }

        waypointGuide?.let { guide ->
            val rotatedStart = rotateAround(guide.first, center, -heading)
            val rotatedEnd = rotateAround(guide.second, center, -heading)
            drawWaypointGuideDistanceLabel(
                text = guide.third,
                center = Offset(
                    x = (rotatedStart.x + rotatedEnd.x) / 2f,
                    y = (rotatedStart.y + rotatedEnd.y) / 2f
                )
            )
        }
    }
}

private fun routeProgressColor(progress: Float): Color {
    val t = progress.coerceIn(0f, 1f)
    val delayedBlue = t * t * t
    return Color(
        red = RouteStartColor.red + (TrackLine.red - RouteStartColor.red) * delayedBlue,
        green = RouteStartColor.green + (TrackLine.green - RouteStartColor.green) * delayedBlue,
        blue = RouteStartColor.blue + (TrackLine.blue - RouteStartColor.blue) * delayedBlue,
        alpha = 0.9f
    )
}

private fun spreadNearbyPoints(points: List<Offset>): List<Offset> {
    if (points.size < 2) return points

    val arrangedPoints = MutableList(points.size) { Offset.Zero }
    val used = BooleanArray(points.size)
    val clusterDistancePx = 12f

    for (index in points.indices) {
        if (used[index]) continue

        val cluster = mutableListOf(index)
        used[index] = true

        for (otherIndex in index + 1 until points.size) {
            if (used[otherIndex]) continue
            if (distanceSquared(points[index], points[otherIndex]) <= clusterDistancePx * clusterDistancePx) {
                used[otherIndex] = true
                cluster += otherIndex
            }
        }

        if (cluster.size == 1) {
            arrangedPoints[index] = points[index]
            continue
        }

        val centerX = cluster.sumOf { points[it].x.toDouble() }.toFloat() / cluster.size
        val centerY = cluster.sumOf { points[it].y.toDouble() }.toFloat() / cluster.size
        val spreadRadius = when (cluster.size) {
            2 -> 4.5f
            3 -> 6f
            else -> 6f + (cluster.size - 3) * 1.5f
        }

        cluster.forEachIndexed { clusterIndex, pointIndex ->
            val angle = 2.0 * PI * clusterIndex / cluster.size
            arrangedPoints[pointIndex] = Offset(
                x = centerX + (cos(angle) * spreadRadius).toFloat(),
                y = centerY + (sin(angle) * spreadRadius).toFloat()
            )
        }
    }

    return arrangedPoints
}

private fun distanceSquared(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}

private fun DrawScope.drawWaypointGuideDistanceLabel(text: String, center: Offset) {
    val textSizePx = (size.minDimension * 0.06f).coerceIn(18f, 26f)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = textSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val outlinePaint = Paint(textPaint).apply {
        color = BackgroundBlack.copy(alpha = 0.82f).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val fontMetrics = textPaint.fontMetrics
    val textHeight = fontMetrics.descent - fontMetrics.ascent
    val halfWidth = textPaint.measureText(text) / 2f + 6f
    val labelCenter = Offset(
        x = center.x.coerceIn(halfWidth, size.width - halfWidth),
        y = center.y.coerceIn(textHeight / 2f + 4f, size.height - textHeight / 2f - 4f)
    )
    val baseline = labelCenter.y - (fontMetrics.ascent + fontMetrics.descent) / 2f

    drawContext.canvas.nativeCanvas.drawText(text, labelCenter.x, baseline, outlinePaint)
    drawContext.canvas.nativeCanvas.drawText(text, labelCenter.x, baseline, textPaint)
}

private fun rotateAround(point: Offset, pivot: Offset, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val translatedX = point.x - pivot.x
    val translatedY = point.y - pivot.y
    return Offset(
        x = pivot.x + (translatedX * cos(radians) - translatedY * sin(radians)).toFloat(),
        y = pivot.y + (translatedX * sin(radians) + translatedY * cos(radians)).toFloat()
    )
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1R = Math.toRadians(lat1)
    val lat2R = Math.toRadians(lat2)
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1R) * cos(lat2R) * sin(dLon / 2).let { it * it }
    return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

private fun formatWaypointGuideDistance(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles < 0.1) {
        "%.2f mi".format(miles)
    } else {
        "%.1f mi".format(miles)
    }
}

private fun normalizeDegrees(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}
