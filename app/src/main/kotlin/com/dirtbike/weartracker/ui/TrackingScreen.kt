package com.dirtbike.weartracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.dirtbike.weartracker.TrackingPage
import com.dirtbike.weartracker.data.TrackPoint
import com.dirtbike.weartracker.data.Waypoint
import com.dirtbike.weartracker.ui.theme.BackgroundBlack
import com.dirtbike.weartracker.ui.theme.CompassNeedle
import com.dirtbike.weartracker.ui.theme.GreenStart
import com.dirtbike.weartracker.ui.theme.OrangeAccent
import com.dirtbike.weartracker.ui.theme.OrangeDim
import com.dirtbike.weartracker.ui.theme.RedStop
import com.dirtbike.weartracker.ui.theme.TextGray
import com.dirtbike.weartracker.ui.theme.TextWhite
import com.dirtbike.weartracker.ui.theme.TrackLine
import com.dirtbike.weartracker.ui.theme.WaypointBlue
import com.dirtbike.weartracker.ui.theme.WaypointYellow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TrackingScreen(
    movementBearing: Float,
    heading: Float?,
    bearingToStart: Float?,
    elapsedFormatted: String,
    currentTimeFormatted: String,
    distanceFormatted: String,
    isPaused: Boolean,
    hasGpsFix: Boolean,
    gpsStatus: String,
    trackPoints: List<TrackPoint>,
    latestGpsPoint: TrackPoint?,
    waypoints: List<Waypoint>,
    importedWaypoints: List<Waypoint>,
    activeWaypoint: Waypoint?,
    activeWaypointIndex: Int,
    trackingPage: TrackingPage,
    isMapZoomedIn: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onTrackingPageChange: (TrackingPage) -> Unit,
    onSelectWaypoint: (Int) -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit
) {
    val displayHeading = heading ?: movementBearing
    val hasTrackHistory = trackPoints.isNotEmpty()
    val canMarkWaypoint = hasTrackHistory && !isPaused
    val zoomFactor = if (isMapZoomedIn) 2.35f else 1f
    val currentPoint = latestGpsPoint ?: trackPoints.lastOrNull()
    val bearingToActiveWaypoint = if (currentPoint != null && activeWaypoint != null) {
        bearingTo(
            currentPoint.latitude,
            currentPoint.longitude,
            activeWaypoint.latitude,
            activeWaypoint.longitude
        )
    } else {
        null
    }
    val gpsChipText = when {
        isPaused -> "PAUSED"
        gpsStatus.contains("lost", ignoreCase = true) -> "GPS LOST"
        hasGpsFix -> "GPS LOCKED"
        else -> gpsStatus.removeSuffix("...").uppercase()
    }
    val gpsChipColor = when {
        isPaused -> OrangeAccent
        gpsStatus.contains("lost", ignoreCase = true) -> RedStop
        hasGpsFix -> GreenStart
        else -> OrangeAccent
    }
    var confirmingStop by remember { mutableStateOf(false) }

    LaunchedEffect(confirmingStop) {
        if (confirmingStop) {
            delay(2_500L)
            confirmingStop = false
        }
    }

    var swipeDistance by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .pointerInput(trackingPage) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeDistance = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeDistance += dragAmount
                    },
                    onDragEnd = {
                        when {
                            swipeDistance < -40f -> onTrackingPageChange(TrackingPage.WAYPOINT)
                            swipeDistance > 40f -> onTrackingPageChange(TrackingPage.MAP)
                        }
                    }
                )
            }
    ) {
        if (trackingPage == TrackingPage.WAYPOINT) {
            WaypointNavigationScreen(
                importedWaypoints = importedWaypoints,
                activeWaypoint = activeWaypoint,
                activeWaypointIndex = activeWaypointIndex,
                currentPoint = currentPoint,
                heading = displayHeading,
                onSelectWaypoint = onSelectWaypoint,
                modifier = Modifier.fillMaxSize()
            )
            return@Box
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isMapZoomedIn) {
                    detectTapGestures(
                        onTap = { onZoomIn() },
                        onDoubleTap = { onZoomOut() }
                    )
                }
        ) {
            if (trackPoints.isNotEmpty()) {
                RideMapCanvas(
                    trackPoints = trackPoints,
                    waypoints = waypoints,
                    heading = displayHeading,
                    zoomFactor = zoomFactor,
                    centerOnCurrent = true,
                    showEndPoint = false,
                    activeWaypoint = activeWaypoint,
                    modifier = Modifier.fillMaxSize()
                )
            }

            CompassOverlay(
                heading = displayHeading,
                bearingToStart = bearingToStart,
                bearingToActiveWaypoint = bearingToActiveWaypoint,
                modifier = Modifier.fillMaxSize()
            )

            CompassLabels(
                heading = displayHeading,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTimeFormatted,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBlock(
                    label = "DURATION",
                    value = elapsedFormatted,
                    alignEnd = false
                )
                StatBlock(
                    label = "DISTANCE",
                    value = distanceFormatted,
                    alignEnd = true
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (confirmingStop) {
                Text(
                    text = "Tap stop again",
                    color = RedStop,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            StatusChip(
                text = gpsChipText,
                chipColor = gpsChipColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (confirmingStop) {
                            onStop()
                        } else {
                            confirmingStop = true
                        }
                    },
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(backgroundColor = RedStop)
                ) {
                    Text(
                        text = if (confirmingStop) "STOP?" else "STOP",
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onPauseResume,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isPaused) GreenStart else OrangeDim
                    )
                ) {
                    Text(
                        text = if (isPaused) "RESUME" else "PAUSE",
                        color = TextWhite,
                        fontSize = if (isPaused) 6.sp else 7.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = { if (canMarkWaypoint) onMark() },
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (canMarkWaypoint) WaypointYellow else OrangeDim
                    )
                ) {
                    Text(
                        text = "MARK",
                        color = BackgroundBlack,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    chipColor: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = chipColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = chipColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun WaypointNavigationScreen(
    importedWaypoints: List<Waypoint>,
    activeWaypoint: Waypoint?,
    activeWaypointIndex: Int,
    currentPoint: TrackPoint?,
    heading: Float,
    onSelectWaypoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWaypointList by remember {
        mutableStateOf(activeWaypoint == null)
    }
    var verticalSwipeDistance by remember { mutableStateOf(0f) }
    val distanceText = if (currentPoint != null && activeWaypoint != null) {
        formatWaypointDistance(
            distanceMeters(
                currentPoint.latitude,
                currentPoint.longitude,
                activeWaypoint.latitude,
                activeWaypoint.longitude
            )
        )
    } else {
        "--"
    }
    val relativeBearing = if (currentPoint != null && activeWaypoint != null) {
        normalizeDegrees(
            bearingTo(
                currentPoint.latitude,
                currentPoint.longitude,
                activeWaypoint.latitude,
                activeWaypoint.longitude
            ) - heading
        )
    } else {
        0f
    }

    LaunchedEffect(activeWaypoint, importedWaypoints.size) {
        if (activeWaypoint == null) {
            showWaypointList = true
        }
    }

    if (!showWaypointList) {
        WaypointArrowOnlyScreen(
            activeWaypoint = activeWaypoint,
            currentPoint = currentPoint,
            distanceText = distanceText,
            relativeBearing = relativeBearing,
            modifier = modifier
                .background(BackgroundBlack)
                .pointerInput(activeWaypointIndex) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalSwipeDistance = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            verticalSwipeDistance += dragAmount
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(verticalSwipeDistance) > 32f) {
                                showWaypointList = true
                            }
                        }
                    )
                }
        )
        return
    }

    Column(
        modifier = modifier
            .background(BackgroundBlack)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WAYPOINTS",
            color = WaypointBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (activeWaypoint == null) "Select waypoint" else "Selected: ${activeWaypoint.name}",
            color = if (activeWaypoint == null) TextGray else TextWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (importedWaypoints.isEmpty()) {
                item {
                    Text(
                        text = "No imported waypoints",
                        color = TextGray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                itemsIndexed(importedWaypoints) { index, waypoint ->
                    WaypointSelectRow(
                        waypoint = waypoint,
                        selected = index == activeWaypointIndex,
                        onClick = {
                            onSelectWaypoint(index)
                            showWaypointList = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Tap select | swipe right map",
            color = TextGray,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WaypointArrowOnlyScreen(
    activeWaypoint: Waypoint?,
    currentPoint: TrackPoint?,
    distanceText: String,
    relativeBearing: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "WAYPOINT NAV",
            color = WaypointBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer { rotationZ = relativeBearing }
            ) {
                drawWaypointArrow(
                    color = if (activeWaypoint == null || currentPoint == null) {
                        WaypointBlue.copy(alpha = 0.28f)
                    } else {
                        WaypointBlue
                    }
                )
            }

            Text(
                text = activeWaypoint?.name ?: "Swipe for waypoints",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (currentPoint == null) "Waiting for GPS" else distanceText,
                color = WaypointBlue,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Swipe up/down for list",
            color = TextGray,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun WaypointSelectRow(
    waypoint: Waypoint,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(
                color = if (selected) WaypointBlue else OrangeDim,
                shape = RoundedCornerShape(999.dp)
            )
            .pointerInput(waypoint, selected) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = waypoint.name,
            color = if (selected) BackgroundBlack else TextWhite,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    alignEnd: Boolean
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            color = TextGray,
            fontSize = if (label.length > 6) 6.5.sp else 7.sp,
            letterSpacing = 0.45.sp
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompassOverlay(
    heading: Float,
    bearingToStart: Float?,
    bearingToActiveWaypoint: Float?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f

        drawCircle(
            color = OrangeAccent.copy(alpha = 0.15f),
            radius = radius - 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )

        rotate(degrees = -heading, pivot = Offset(cx, cy)) {
            val tickLen = 6f
            for (i in 0..3) {
                val angleRad = Math.toRadians((i * 90).toDouble()).toFloat()
                val cosValue = kotlin.math.cos(angleRad)
                val sinValue = kotlin.math.sin(angleRad)
                val r1 = radius - 2f
                val r2 = radius - 2f - tickLen
                drawLine(
                    color = OrangeAccent.copy(alpha = 0.4f),
                    start = Offset(cx + r1 * sinValue, cy - r1 * cosValue),
                    end = Offset(cx + r2 * sinValue, cy - r2 * cosValue),
                    strokeWidth = if (i == 0) 3f else 1.5f
                )
            }

            bearingToStart?.let { startBearing ->
                rotate(degrees = startBearing, pivot = Offset(cx, cy)) {
                    drawStartPointer(cx, cy, radius - 6f)
                }
            }

            bearingToActiveWaypoint?.let { waypointBearing ->
                rotate(degrees = waypointBearing, pivot = Offset(cx, cy)) {
                    drawActiveWaypointRimMarker(cx, cy, radius - 5f)
                }
            }
        }

        drawDirectionArrow(cx, cy, radius * 0.30f)
    }
}

@Composable
private fun CompassLabels(
    heading: Float,
    modifier: Modifier = Modifier
) {
    val markers = listOf(
        CompassMarkerData(primary = "N", secondary = "0\u00B0", angle = 0f, color = OrangeAccent),
        CompassMarkerData(primary = "45\u00B0", angle = 45f, color = TextGray, compact = true),
        CompassMarkerData(primary = "E", secondary = "90\u00B0", angle = 90f, color = TextGray),
        CompassMarkerData(primary = "135\u00B0", angle = 135f, color = TextGray, compact = true),
        CompassMarkerData(primary = "S", secondary = "180\u00B0", angle = 180f, color = TextGray),
        CompassMarkerData(primary = "225\u00B0", angle = 225f, color = TextGray, compact = true),
        CompassMarkerData(primary = "W", secondary = "270\u00B0", angle = 270f, color = TextGray),
        CompassMarkerData(primary = "315\u00B0", angle = 315f, color = TextGray, compact = true)
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val minDimension = if (maxWidth < maxHeight) maxWidth else maxHeight
        val labelRadiusPx = with(density) { minDimension.toPx() / 2f - 14.dp.toPx() }

        Box(modifier = Modifier.fillMaxSize()) {
            markers.forEach { marker ->
                val screenAngle = normalizeDegrees(marker.angle - heading)
                val angleRad = Math.toRadians(screenAngle.toDouble())
                val dx = (labelRadiusPx * sin(angleRad)).toFloat()
                val dy = (-labelRadiusPx * cos(angleRad)).toFloat()

                CompassMarker(
                    marker = marker,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = dx
                            translationY = dy
                            rotationZ = screenAngle
                        }
                )
            }
        }
    }
}

private data class CompassMarkerData(
    val primary: String,
    val secondary: String? = null,
    val angle: Float,
    val color: Color,
    val compact: Boolean = false
)

@Composable
private fun CompassMarker(
    marker: CompassMarkerData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = marker.primary,
            color = marker.color,
            fontSize = if (marker.compact) 6.sp else 9.sp,
            fontWeight = if (marker.color == OrangeAccent) FontWeight.ExtraBold else FontWeight.Bold
        )
        marker.secondary?.let { secondary ->
            Text(
                text = secondary,
                color = marker.color,
                fontSize = 6.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun DrawScope.drawDirectionArrow(cx: Float, cy: Float, len: Float) {
    val halfW = len * 0.28f

    val arrowPath = Path().apply {
        moveTo(cx, cy - len)
        lineTo(cx - halfW, cy + len * 0.3f)
        lineTo(cx, cy + len * 0.1f)
        lineTo(cx + halfW, cy + len * 0.3f)
        close()
    }
    drawPath(arrowPath, color = CompassNeedle)

    val tailPath = Path().apply {
        moveTo(cx, cy + len * 0.1f)
        lineTo(cx - halfW * 0.55f, cy + len * 0.55f)
        lineTo(cx, cy + len * 0.38f)
        lineTo(cx + halfW * 0.55f, cy + len * 0.55f)
        close()
    }
    drawPath(tailPath, color = OrangeDim)

    drawCircle(color = BackgroundBlack, radius = len * 0.08f, center = Offset(cx, cy))
    drawCircle(
        color = OrangeAccent,
        radius = len * 0.06f,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
    )
}

private fun DrawScope.drawWaypointArrow(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val len = size.minDimension * 0.42f
    val halfW = len * 0.34f

    val arrowPath = Path().apply {
        moveTo(cx, cy - len)
        lineTo(cx - halfW, cy + len * 0.24f)
        lineTo(cx - halfW * 0.28f, cy + len * 0.10f)
        lineTo(cx - halfW * 0.20f, cy + len * 0.62f)
        lineTo(cx + halfW * 0.20f, cy + len * 0.62f)
        lineTo(cx + halfW * 0.28f, cy + len * 0.10f)
        lineTo(cx + halfW, cy + len * 0.24f)
        close()
    }
    drawPath(arrowPath, color = color.copy(alpha = 0.28f))
    drawPath(
        arrowPath,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.2f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawStartPointer(cx: Float, cy: Float, radius: Float) {
    val pointerPath = Path().apply {
        moveTo(cx, cy - radius)
        lineTo(cx - 9f, cy - radius + 16f)
        lineTo(cx + 9f, cy - radius + 16f)
        close()
    }
    drawPath(
        pointerPath,
        color = GreenStart.copy(alpha = 0.35f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
    )
    drawPath(pointerPath, color = GreenStart)
    drawCircle(
        color = GreenStart.copy(alpha = 0.35f),
        radius = 4.5f,
        center = Offset(cx, cy - radius + 10f)
    )
    drawCircle(
        color = BackgroundBlack,
        radius = 3f,
        center = Offset(cx, cy - radius + 10f)
    )
    drawCircle(
        color = GreenStart,
        radius = 4f,
        center = Offset(cx, cy - radius + 10f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
    )
}

private fun DrawScope.drawActiveWaypointRimMarker(cx: Float, cy: Float, radius: Float) {
    val center = Offset(cx, cy - radius)
    drawCircle(
        color = WaypointBlue.copy(alpha = 0.28f),
        radius = 7.5f,
        center = center
    )
    drawCircle(
        color = WaypointBlue,
        radius = 4.5f,
        center = center
    )
    drawCircle(
        color = BackgroundBlack,
        radius = 2f,
        center = center
    )
}

@Composable
private fun BreadcrumbCanvas(
    trackPoints: List<TrackPoint>,
    waypoints: List<Waypoint>,
    heading: Float,
    zoomFactor: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val pad = size.minDimension * 0.06f
        val currentPoint = trackPoints.last()
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(currentPoint.latitude))

        fun relativeOffset(latitude: Double, longitude: Double): Offset {
            val xMeters = ((longitude - currentPoint.longitude) * metersPerDegreeLon).toFloat()
            val yMeters = ((currentPoint.latitude - latitude) * metersPerDegreeLat).toFloat()
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

        rotate(degrees = -heading, pivot = center) {
            if (relativeTrack.size >= 2) {
                val path = Path()
                relativeTrack.forEachIndexed { index, offset ->
                    val point = project(offset)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path,
                    color = TrackLine.copy(alpha = 0.7f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round
                    )
                )
            }

            val startPoint = project(relativeTrack.first())
            drawCircle(
                color = GreenStart,
                radius = 5f,
                center = startPoint
            )

            val projectedWaypoints = relativeWaypoints.map(::project)
            val visibleWaypointPoints = spreadNearbyPoints(projectedWaypoints)
            visibleWaypointPoints.forEach { waypointPoint ->
                drawCircle(
                    color = WaypointYellow,
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
    }
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
        val spreadRadius = 7f + (cluster.size - 1) * 2f

        cluster.forEachIndexed { clusterIndex, pointIndex ->
            val angle = (-PI / 2.0) + (2.0 * PI * clusterIndex / cluster.size)
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

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2.0).let { it * it } +
        cos(Math.toRadians(lat1)) *
        cos(Math.toRadians(lat2)) *
        sin(dLon / 2.0).let { it * it }
    return radiusMeters * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
}

private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1R = Math.toRadians(lat1)
    val lat2R = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2R)
    val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
    return normalizeDegrees(Math.toDegrees(kotlin.math.atan2(y, x)).toFloat())
}

private fun formatWaypointDistance(meters: Double): String {
    val miles = meters / 1609.344
    return if (miles < 0.25) {
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
